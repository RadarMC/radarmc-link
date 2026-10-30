package org.radarmc.link.core.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.radarmc.link.core.MutableClock;
import org.radarmc.link.core.PlatformAdapter;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.RecordingLogger;
import org.radarmc.link.core.ServerSnapshot;
import org.radarmc.link.core.api.PendingVote;
import org.radarmc.link.core.api.PendingVotesPage;
import org.radarmc.link.core.api.VoteApi;
import org.radarmc.link.core.players.OnlinePlayers;

class LocalDeliveryTest {
    private static final Instant DAY7 = Instant.parse("2026-10-05T11:00:00Z");
    private static final RewardPlan SPLIT = RewardPlan.split(
            new VoteReward(Arrays.asList("give %player% diamond 1"), "Алмаз за голос"),
            Arrays.asList(
                    new StreakReward(7, 0, Arrays.asList("lp user %player% parent addtemp vip 7d"), "VIP"),
                    new StreakReward(
                            30,
                            30,
                            Arrays.asList("crate key give %player% legendary", "say %player% %streak%"),
                            null)));

    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T12:00:00Z"));
    private final RecordingLogger log = new RecordingLogger();
    private final List<String> commands = new ArrayList<>();
    private final OnlinePlayers online = new OnlinePlayers(() -> ".");
    private final Votes api = new Votes();
    private RewardStore store;
    private RewardPlan plan = SPLIT;

    @BeforeEach
    void setUp() {
        store = RewardStore.load(dir, clock, log);
    }

    @Test
    void everyVoteGetsTheVoteRewardAndTheStreakDayGetsTheBonus() {
        online.joined("Steve");
        api.add("v6", "Steve", 6, DAY7.minus(Duration.ofDays(1)));
        api.add("v7", "Steve", 7, DAY7);
        processor(true).poll();
        assertEquals(
                Arrays.asList("give Steve diamond 1", "give Steve diamond 1", "lp user Steve parent addtemp vip 7d"),
                commands);
        assertTrue(log.contains("(vote reward, streak bonus 7) to Steve"));
    }

    @Test
    void repeatingBonusRunsWithItsPlaceholders() {
        online.joined("Steve");
        api.add("v60", "steve", 60, DAY7);
        processor(true).poll();
        assertEquals(Arrays.asList("give Steve diamond 1", "crate key give Steve legendary", "say Steve 60"), commands);
    }

    @Test
    void offlineVoterGetsTheBonusOnJoinAndDuplicatesNeverPayTwice() {
        api.add("v7", "Steve", 7, DAY7);
        VoteProcessor p = processor(true);
        p.poll();
        assertTrue(commands.isEmpty());
        assertTrue(store.isQueued("v7"));

        online.joined("Steve");
        p.playerReady("Steve");
        p.playerReady("Steve");
        api.add("v7", "Steve", 7, DAY7);
        p.poll();
        assertEquals(Arrays.asList("give Steve diamond 1", "lp user Steve parent addtemp vip 7d"), commands);
        assertTrue(RewardStore.load(dir, clock, log).isHandled("v7"));
    }

    @Test
    void secondVoteForTheSameStreakDayGetsOnlyTheVoteReward() {
        online.joined("Steve");
        api.add("v7a", "Steve", 7, DAY7);
        api.add("v7b", "steve", 7, DAY7.plus(Duration.ofHours(5)));
        processor(true).poll();
        assertEquals(
                Arrays.asList("give Steve diamond 1", "lp user Steve parent addtemp vip 7d", "give Steve diamond 1"),
                commands);
        assertTrue(log.contains(
                "Skipped the streak bonus of vote v7b for Steve: another vote on day 7 of the streak already got it"));
    }

    @Test
    void sameDayBonusWithoutVoteRewardRunsNothing() {
        plan = RewardPlan.split(null, Arrays.asList(new StreakReward(7, 0, Arrays.asList("say week"), null)));
        online.joined("Steve");
        api.add("v7a", "Steve", 7, DAY7);
        api.add("v7b", "Steve", 7, DAY7.plus(Duration.ofHours(2)));
        processor(true).poll();
        assertEquals(Arrays.asList("say week"), commands);
        assertEquals(Arrays.asList("v7a", "v7b"), api.confirmed);
        assertTrue(log.contains("Skipped the streak bonus of vote v7b"));
        assertEquals(
                1,
                log.lines.stream()
                        .filter(l -> l.contains("Gave the vote reward"))
                        .count());
    }

    @Test
    void sameStreakFromAnotherStreakRunKeepsItsBonus() {
        api.add("old", "Steve", 7, DAY7.minus(Duration.ofDays(12)));
        api.add("new", "Steve", 7, DAY7);
        VoteProcessor p = processor(true);
        p.poll();
        online.joined("Steve");
        p.playerReady("Steve");
        assertEquals(
                2,
                commands.stream().filter(c -> c.startsWith("lp user Steve")).count(),
                "both queued votes are paid at one join, their site times are 12 days apart");
    }

    @Test
    void bonusOnlyConfigConfirmsOtherVotesWithoutReward() {
        plan = RewardPlan.split(null, Arrays.asList(new StreakReward(7, 0, Arrays.asList("say week"), null)));
        online.joined("Steve");
        api.add("v3", "Steve", 3, DAY7);
        processor(true).poll();
        assertTrue(commands.isEmpty());
        assertEquals(Arrays.asList("v3"), api.confirmed);
        assertTrue(store.isHandled("v3"));
    }

    private VoteProcessor processor(boolean offlineRewards) {
        LocalDelivery delivery = new LocalDelivery(new Platform(), online, () -> plan, store);
        return new VoteProcessor(api, store, delivery, offlineRewards, Runnable::run, clock, log, () -> ".");
    }

    private static final class Votes implements VoteApi {
        final Map<String, PendingVote> pending = new LinkedHashMap<>();
        final List<String> confirmed = new ArrayList<>();

        void add(String id, String nick, int streak, Instant at) {
            pending.put(id, new PendingVote(id, nick, streak, at));
        }

        @Override
        public PendingVotesPage pendingVotes(String cursor, int limit) {
            return new PendingVotesPage(new ArrayList<>(pending.values()), null);
        }

        @Override
        public void confirmVote(String voteId) {
            confirmed.add(voteId);
            pending.remove(voteId);
        }
    }

    private final class Platform implements PlatformAdapter {
        @Override
        public RadarLogger logger() {
            return log;
        }

        @Override
        public Path dataDirectory() {
            return dir;
        }

        @Override
        public ServerSnapshot snapshot() {
            return new ServerSnapshot("paper", "Paper 1.21.4", 1, 20);
        }

        @Override
        public void runOnMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void dispatchConsoleCommand(String command) {
            commands.add(command);
        }
    }
}
