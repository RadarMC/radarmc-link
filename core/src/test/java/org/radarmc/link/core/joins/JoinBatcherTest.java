package org.radarmc.link.core.joins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.radarmc.link.core.MutableClock;
import org.radarmc.link.core.RecordingLogger;
import org.radarmc.link.core.api.ApiException;
import org.radarmc.link.core.api.JoinEntry;

class JoinBatcherTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T12:00:00Z"));
    private final RecordingLogger log = new RecordingLogger();
    private final List<List<JoinEntry>> batches = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private final Executor worker = scheduled::add;
    private ApiException failure;

    private final JoinBatcher batcher = new JoinBatcher(
            joins -> {
                if (failure != null) {
                    throw failure;
                }
                batches.add(new ArrayList<>(joins));
                return joins.size();
            },
            worker,
            clock,
            log);

    @Test
    void flushSendsInBatchesOfFifty() {
        for (int i = 0; i < 120; i++) {
            batcher.add(join(i));
        }
        assertEquals(1, scheduled.size(), "a full batch schedules one flush");
        scheduled.get(0).run();
        assertEquals(Arrays.asList(50, 50, 20), batches.stream().map(List::size).collect(Collectors.toList()));
        assertEquals(0, batcher.pending());
        assertEquals(120, batcher.sentCount());
    }

    @Test
    void periodicFlushSendsPartialBatches() {
        batcher.add(join(1));
        assertTrue(scheduled.isEmpty());
        batcher.flush();
        assertEquals(1, batches.size());
        batcher.flush();
        assertEquals(1, batches.size(), "nothing to send");
    }

    @Test
    void transientFailuresKeepJoinsInOrder() {
        batcher.add(join(1));
        batcher.add(join(2));
        failure = new ApiException(ApiException.Kind.SERVER, 502, null, null, null, null);
        batcher.flush();
        assertEquals(2, batcher.pending());
        failure = null;
        batcher.add(join(3));
        batcher.flush();
        assertEquals(
                Arrays.asList("p1", "p2", "p3"),
                batches.get(0).stream().map(JoinEntry::nick).collect(Collectors.toList()));
    }

    @Test
    void rateLimitPausesUntilRetryAfter() {
        batcher.add(join(1));
        failure = new ApiException(ApiException.Kind.RATE_LIMITED, 429, null, null, Duration.ofSeconds(40), null);
        batcher.flush();
        failure = null;
        batcher.flush();
        assertTrue(batches.isEmpty());
        clock.advance(Duration.ofSeconds(41));
        batcher.flush();
        assertEquals(1, batches.size());
    }

    @Test
    void rejectedKeyDropsTheBatchAndWarnsOnce() {
        failure = new ApiException(ApiException.Kind.UNAUTHORIZED, 401, "INVALID_API_KEY", "Invalid", null, null);
        batcher.add(join(1));
        batcher.flush();
        assertEquals(0, batcher.pending());
        clock.advance(Duration.ofMinutes(6));
        batcher.add(join(2));
        batcher.flush();
        assertEquals(1, log.lines.stream().filter(l -> l.startsWith("ERROR")).count());
        assertEquals(2, batcher.droppedCount());
    }

    @Test
    void oldJoinsAreDropped() {
        batcher.add(join(1));
        clock.advance(Duration.ofMinutes(11));
        batcher.add(join(2));
        batcher.flush();
        assertEquals(
                Collections.singletonList("p2"),
                batches.get(0).stream().map(JoinEntry::nick).collect(Collectors.toList()));
        assertEquals(1, batcher.droppedCount());
    }

    @Test
    void bufferIsBounded() {
        failure = new ApiException(ApiException.Kind.NETWORK, 0, null, null, null, null);
        for (int i = 0; i < JoinBatcher.MAX_BUFFER + 10; i++) {
            batcher.add(join(i));
        }
        assertEquals(JoinBatcher.MAX_BUFFER, batcher.pending());
        assertEquals(10, batcher.droppedCount());
    }

    @Test
    void logsNeverContainIps() {
        failure = new ApiException(ApiException.Kind.BAD_REQUEST, 400, "INVALID_PARAMETER", "bad joins", null, null);
        batcher.add(join(1));
        batcher.flush();
        failure = new ApiException(ApiException.Kind.SERVER, 500, null, null, null, null);
        batcher.add(join(2));
        batcher.flush();
        assertFalse(log.lines.isEmpty());
        assertFalse(log.all().contains("198.51.100."));
    }

    private JoinEntry join(int i) {
        return new JoinEntry(UUID.randomUUID(), "p" + i, "198.51.100." + (i % 250), false, clock.instant());
    }
}
