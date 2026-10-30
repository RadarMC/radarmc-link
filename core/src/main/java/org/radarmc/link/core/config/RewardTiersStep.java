package org.radarmc.link.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class RewardTiersStep implements ConfigMigrator.Step {
    private static final String TIERS = "reward-tiers";
    private static final int VOTE_TIER_STREAK = 1;

    @Override
    public int from() {
        return 1;
    }

    @Override
    public void apply(Map<?, ?> root, ConfigMigrator.Rewrite rewrite) {
        if (!root.containsKey(TIERS)) {
            return;
        }
        rewrite.removed.add(TIERS);
        List<Tier> tiers = tiers(root.get(TIERS));
        if (tiers.isEmpty()) {
            rewrite.notes.add("removed the empty reward-tiers");
            return;
        }
        if (ConfigMigrator.present(root.get("vote-reward")) || ConfigMigrator.present(root.get("streak-rewards"))) {
            rewrite.notes.add("removed reward-tiers: vote-reward and streak-rewards were already set and used instead");
            return;
        }
        moveTiers(tiers, rewrite);
        rewrite.notes.add(
                "reward-tiers moved: the streak 1 tier is now vote-reward, the other tiers are streak-rewards "
                        + "with repeat-every: 1 (they run on top of the vote reward from that day on); check the rewards");
    }

    private static void moveTiers(List<Tier> tiers, ConfigMigrator.Rewrite rewrite) {
        List<Tier> bonuses = new ArrayList<>();
        for (Tier tier : tiers) {
            if (tier.streak == VOTE_TIER_STREAK) {
                rewrite.bodies.put("vote-reward", voteReward(tier));
            } else {
                bonuses.add(tier);
            }
        }
        if (!bonuses.isEmpty()) {
            rewrite.bodies.put("streak-rewards", streakRewards(bonuses));
        }
    }

    private static List<String> voteReward(Tier tier) {
        List<String> lines = new ArrayList<>();
        lines.add("vote-reward:");
        if (tier.label != null) {
            lines.add("  label: " + ConfigMigrator.quote(tier.label));
        }
        lines.add("  commands:");
        for (String command : tier.commands) {
            lines.add("    - " + ConfigMigrator.quote(command));
        }
        return lines;
    }

    private static List<String> streakRewards(List<Tier> tiers) {
        List<String> lines = new ArrayList<>();
        lines.add("streak-rewards:");
        for (Tier tier : tiers) {
            lines.add("  - streak: " + tier.streak);
            lines.add("    repeat-every: 1");
            if (tier.label != null) {
                lines.add("    label: " + ConfigMigrator.quote(tier.label));
            }
            lines.add("    commands:");
            for (String command : tier.commands) {
                lines.add("      - " + ConfigMigrator.quote(command));
            }
        }
        return lines;
    }

    private static List<Tier> tiers(Object raw) {
        List<Tier> tiers = new ArrayList<>();
        if (!(raw instanceof List<?>)) {
            return tiers;
        }
        Set<Integer> streaks = new HashSet<>();
        for (Object item : (List<?>) raw) {
            Optional<Tier> tier = Tier.of(item);
            if (tier.isPresent() && streaks.add(tier.get().streak)) {
                tiers.add(tier.get());
            }
        }
        tiers.sort(Comparator.comparingInt(tier -> tier.streak));
        return tiers;
    }

    private static final class Tier {
        final int streak;
        final List<String> commands;
        final String label;

        private Tier(int streak, List<String> commands, String label) {
            this.streak = streak;
            this.commands = commands;
            this.label = label;
        }

        static Optional<Tier> of(Object raw) {
            if (!(raw instanceof Map<?, ?>)) {
                return Optional.empty();
            }
            Map<?, ?> map = (Map<?, ?>) raw;
            Object streak = map.get("streak");
            List<String> commands = commands(map.get("commands"));
            if (!(streak instanceof Integer) || (Integer) streak < 1 || commands.isEmpty()) {
                return Optional.empty();
            }
            Object label = map.get("label");
            return Optional.of(new Tier((Integer) streak, commands, label instanceof String ? (String) label : null));
        }

        private static List<String> commands(Object raw) {
            List<?> items = raw instanceof List<?>
                    ? (List<?>) raw
                    : raw instanceof String ? Collections.singletonList(raw) : Collections.emptyList();
            List<String> commands = new ArrayList<>();
            for (Object item : items) {
                if (item instanceof String && !((String) item).trim().isEmpty()) {
                    commands.add((String) item);
                }
            }
            return commands;
        }
    }
}
