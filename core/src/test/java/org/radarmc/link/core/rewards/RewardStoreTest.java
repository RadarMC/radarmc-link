package org.radarmc.link.core.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.radarmc.link.core.MutableClock;
import org.radarmc.link.core.RecordingLogger;
import org.radarmc.link.core.io.TextFiles;

class RewardStoreTest {
    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T12:00:00Z"));
    private final RecordingLogger log = new RecordingLogger();

    @Test
    void stateSurvivesARestart() {
        RewardStore store = RewardStore.load(dir, clock, log);
        store.markHandled("v1");
        assertTrue(store.enqueue(new Reward("v2", "Steve", 3, clock.instant())));
        assertTrue(store.enqueue(new Reward("v2", "Steve", 3, clock.instant())), "enqueue is idempotent");

        RewardStore reloaded = RewardStore.load(dir, clock, log);
        assertTrue(reloaded.isHandled("v1"));
        assertTrue(reloaded.isQueued("v2"));
        assertEquals(1, reloaded.queuedCount());
        assertEquals(
                Collections.singletonList(new Reward("v2", "Steve", 3, clock.instant())),
                reloaded.queuedFor(n -> n.equalsIgnoreCase("steve")));
        assertTrue(reloaded.queuedFor(n -> n.equals("Alex")).isEmpty());
    }

    @Test
    void completeMovesFromQueueToHandled() {
        RewardStore store = RewardStore.load(dir, clock, log);
        store.enqueue(new Reward("v1", "Steve", 1, clock.instant()));
        store.complete("v1");
        RewardStore reloaded = RewardStore.load(dir, clock, log);
        assertFalse(reloaded.isQueued("v1"));
        assertTrue(reloaded.isHandled("v1"));
        assertTrue(reloaded.enqueue(new Reward("v1", "Steve", 1, clock.instant())));
        assertEquals(0, reloaded.queuedCount(), "a handled vote is never queued again");
    }

    @Test
    void pruneDropsOldEntries() {
        RewardStore store = RewardStore.load(dir, clock, log);
        store.markHandled("old");
        store.enqueue(new Reward("q-old", "Steve", 1, clock.instant()));
        clock.advance(Duration.ofDays(31));
        store.markHandled("new");
        store.enqueue(new Reward("q-new", "Alex", 1, clock.instant()));
        store.prune();
        assertTrue(store.isHandled("old"), "handled ids are kept 35 days");
        assertFalse(store.isQueued("q-old"));
        assertTrue(store.isQueued("q-new"));
        clock.advance(Duration.ofDays(5));
        store.prune();
        assertFalse(store.isHandled("old"));
        assertTrue(store.isHandled("new"));
        assertTrue(log.contains("did not join"));
    }

    @Test
    void brokenFileIsMovedAside() throws IOException {
        TextFiles.writeUtf8(dir.resolve(RewardStore.FILE_NAME), "{not json");
        RewardStore store = RewardStore.load(dir, clock, log);
        assertEquals(0, store.queuedCount());
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("rewards.json.broken-")));
        }
        store.markHandled("v1");
        assertTrue(RewardStore.load(dir, clock, log).isHandled("v1"));
    }

    @Test
    void writesLeaveNoTempFile() throws IOException {
        RewardStore store = RewardStore.load(dir, clock, log);
        store.markHandled("v1");
        assertFalse(Files.exists(dir.resolve(RewardStore.FILE_NAME + ".tmp")));
        assertTrue(TextFiles.readUtf8(dir.resolve(RewardStore.FILE_NAME)).contains("\"v1\""));
    }

    @Test
    void streakBonusIsClaimedOncePerStreakDay() {
        RewardStore store = RewardStore.load(dir, clock, log);
        Instant day7 = Instant.parse("2026-10-05T10:00:00Z");
        assertTrue(store.claim("Steve", 7, day7, "a"));
        assertTrue(store.claim("Steve", 7, day7, "a"), "the same vote claims again on a retry");
        assertFalse(store.claim("steve", 7, day7.plus(Duration.ofHours(13)), "b"), "same nick, same streak, same day");
        assertTrue(store.claim("Steve", 8, day7.plus(Duration.ofHours(23)), "c"), "another streak value");
        assertTrue(store.claim("Alex", 7, day7, "d"), "another player");
        RewardStore reloaded = RewardStore.load(dir, clock, log);
        assertFalse(reloaded.claim("STEVE", 7, day7.plus(Duration.ofHours(1)), "e"), "survives a restart");
        assertTrue(reloaded.claim("Steve", 7, day7, "a"), "and so does the vote id");
        assertTrue(
                store.claim("Steve", 7, day7.plus(Duration.ofDays(9)), "f"), "the streak came back to 7 after a reset");
    }

    @Test
    void pruneDropsOldBonusClaims() {
        RewardStore store = RewardStore.load(dir, clock, log);
        assertTrue(store.claim("Steve", 7, clock.instant(), "a"));
        clock.advance(Duration.ofDays(36));
        store.prune();
        assertTrue(store.claim(
                "Steve", 7, clock.instant().minus(Duration.ofDays(36)).plus(Duration.ofHours(1)), "b"));
    }

    @Test
    void queuedRewardKeepsTheVoteTime() throws IOException {
        RewardStore store = RewardStore.load(dir, clock, log);
        Instant voted = Instant.parse("2026-10-05T09:30:00Z");
        store.enqueue(new Reward("v1", "Steve", 7, clock.instant(), voted));
        Reward reloaded = RewardStore.load(dir, clock, log).queuedFor(n -> true).get(0);
        assertEquals(voted, reloaded.votedAt());
        assertEquals(clock.instant(), reloaded.queuedAt());
    }

    @Test
    void fileWithoutVoteTimesFromVersion10Loads() throws IOException {
        TextFiles.writeUtf8(
                dir.resolve(RewardStore.FILE_NAME),
                "{\"version\":1,\"queued\":[{\"vote_id\":\"v1\",\"nick\":\"Steve\","
                        + "\"streak\":3,\"queued_at\":\"2026-10-05T11:00:00Z\"}],\"handled\":{\"v0\":\"2026-10-05T10:00:00Z\"}}");
        RewardStore store = RewardStore.load(dir, clock, log);
        Reward r = store.queuedFor(n -> true).get(0);
        assertEquals(Instant.parse("2026-10-05T11:00:00Z"), r.votedAt());
        assertTrue(store.isHandled("v0"));
        assertTrue(store.claim("Steve", 3, r.votedAt(), r.voteId()));
    }
}
