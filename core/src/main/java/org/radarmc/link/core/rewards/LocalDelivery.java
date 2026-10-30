package org.radarmc.link.core.rewards;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.radarmc.link.core.PlatformAdapter;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.players.OnlinePlayers;

public final class LocalDelivery implements RewardDelivery {
    private final PlatformAdapter platform;
    private final OnlinePlayers online;
    private final Supplier<RewardPlan> plan;
    private final BonusGuard bonuses;
    private final RadarLogger log;

    public LocalDelivery(
            PlatformAdapter platform, OnlinePlayers online, Supplier<RewardPlan> plan, BonusGuard bonuses) {
        this.platform = platform;
        this.online = online;
        this.plan = plan;
        this.bonuses = bonuses;
        this.log = platform.logger();
    }

    @Override
    public Optional<String> onlineName(String nick) {
        return online.find(nick);
    }

    @Override
    public boolean hasReward(int streak) {
        return !plan.get().runsFor(streak).isEmpty();
    }

    @Override
    public boolean idempotent() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> deliver(String onlineName, Reward reward) {
        List<RewardRun> runs = runsFor(onlineName, reward, BonusDecision.LOCAL);
        if (runs.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        try {
            platform.runOnMainThread(() -> {
                runCommands(onlineName, reward, runs);
                done.complete(true);
            });
        } catch (RuntimeException e) {
            done.completeExceptionally(e);
        }
        return done;
    }

    public void runNow(String onlineName, Reward reward, BonusDecision decision) {
        List<RewardRun> runs = runsFor(onlineName, reward, decision);
        if (!runs.isEmpty()) {
            runCommands(onlineName, reward, runs);
        }
    }

    private List<RewardRun> runsFor(String player, Reward reward, BonusDecision decision) {
        List<RewardRun> runs = plan.get().runsFor(reward.streak());
        if (!hasBonus(runs) || bonusAllowed(player, reward, decision)) {
            return runs;
        }
        log.info("Skipped the streak bonus of vote " + reward.voteId() + " for " + player + ": another vote on day "
                + reward.streak() + " of the streak already got it");
        List<RewardRun> withoutBonuses = new ArrayList<>();
        for (RewardRun run : runs) {
            if (run.kind() != RewardRun.Kind.BONUS) {
                withoutBonuses.add(run);
            }
        }
        return withoutBonuses;
    }

    private boolean bonusAllowed(String player, Reward reward, BonusDecision decision) {
        if (decision != BonusDecision.LOCAL) {
            return decision == BonusDecision.GIVE;
        }
        return bonuses.claim(player, reward.streak(), reward.votedAt(), reward.voteId());
    }

    private static boolean hasBonus(List<RewardRun> runs) {
        return runs.stream().anyMatch(run -> run.kind() == RewardRun.Kind.BONUS);
    }

    private void runCommands(String player, Reward reward, List<RewardRun> runs) {
        List<String> given = new ArrayList<>();
        for (RewardRun run : runs) {
            for (String command : run.commands()) {
                dispatch(command, player, reward.streak());
            }
            given.add(run.describe());
        }
        log.info("Gave the vote reward for a " + reward.streak() + "-day streak (" + String.join(", ", given) + ") to "
                + player);
    }

    private void dispatch(String command, String player, int streak) {
        try {
            platform.dispatchConsoleCommand(render(command, player, streak));
        } catch (RuntimeException e) {
            log.error("Reward command failed for " + player + ": " + command, e);
        }
    }

    private static String render(String command, String player, int streak) {
        return command.replace("%player%", player).replace("%streak%", Integer.toString(streak));
    }
}
