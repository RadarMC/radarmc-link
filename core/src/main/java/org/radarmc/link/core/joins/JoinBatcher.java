package org.radarmc.link.core.joins;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.api.ApiException;
import org.radarmc.link.core.api.JoinEntry;

public final class JoinBatcher {
    static final int MAX_BUFFER = 1000;
    private static final int BATCH_SIZE = 50;
    private static final Duration MAX_AGE = Duration.ofMinutes(10);
    private static final Duration AUTH_PAUSE = Duration.ofMinutes(5);

    private final JoinSender sender;
    private final Executor worker;
    private final Clock clock;
    private final RadarLogger log;
    private final Deque<JoinEntry> queue = new ArrayDeque<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private Instant pausedUntil = Instant.MIN;
    private boolean authWarned;
    private long sent;
    private long dropped;

    public JoinBatcher(JoinSender sender, Executor worker, Clock clock, RadarLogger log) {
        this.sender = sender;
        this.worker = worker;
        this.clock = clock;
        this.log = log;
    }

    public void add(JoinEntry join) {
        boolean full;
        synchronized (queue) {
            if (queue.size() >= MAX_BUFFER) {
                queue.pollFirst();
                dropped++;
            }
            queue.addLast(join);
            full = queue.size() >= BATCH_SIZE;
        }
        if (full && flushScheduled.compareAndSet(false, true)) {
            worker.execute(() -> {
                flushScheduled.set(false);
                flush();
            });
        }
    }

    public void flush() {
        while (!clock.instant().isBefore(pausedUntil)) {
            List<JoinEntry> batch = takeBatch();
            if (batch.isEmpty() || !sendBatch(batch)) {
                return;
            }
        }
    }

    public int pending() {
        synchronized (queue) {
            return queue.size();
        }
    }

    public synchronized long sentCount() {
        return sent;
    }

    long droppedCount() {
        synchronized (queue) {
            return dropped;
        }
    }

    private List<JoinEntry> takeBatch() {
        Instant oldest = clock.instant().minus(MAX_AGE);
        List<JoinEntry> batch = new ArrayList<>(BATCH_SIZE);
        synchronized (queue) {
            while (!queue.isEmpty() && batch.size() < BATCH_SIZE) {
                JoinEntry next = queue.pollFirst();
                if (next.at().isBefore(oldest)) {
                    dropped++;
                } else {
                    batch.add(next);
                }
            }
        }
        return batch;
    }

    private boolean sendBatch(List<JoinEntry> batch) {
        try {
            int accepted = sender.send(batch);
            synchronized (this) {
                sent += accepted;
            }
            authWarned = false;
            return true;
        } catch (ApiException e) {
            handleFailure(batch, e);
            return false;
        }
    }

    private void handleFailure(List<JoinEntry> batch, ApiException e) {
        if (e.transientFailure()) {
            requeue(batch);
            if (e.kind() == ApiException.Kind.RATE_LIMITED) {
                pausedUntil = clock.instant().plus(e.retryAfter());
            }
            log.warn("Could not send " + batch.size() + " player joins, will retry: " + e.describe());
            return;
        }
        synchronized (queue) {
            dropped += batch.size();
        }
        if (e.kind() == ApiException.Kind.UNAUTHORIZED || e.kind() == ApiException.Kind.FORBIDDEN) {
            pauseAfterRejectedKey(e);
            return;
        }
        log.warn("The API refused " + batch.size() + " player joins: " + e.describe());
    }

    private void pauseAfterRejectedKey(ApiException e) {
        pausedUntil = clock.instant().plus(AUTH_PAUSE);
        if (!authWarned) {
            authWarned = true;
            log.error("The API rejected the key while sending joins (" + e.describe()
                    + "). Check api-key and server-slug in config.yml.");
        }
    }

    private void requeue(List<JoinEntry> batch) {
        synchronized (queue) {
            for (int i = batch.size() - 1; i >= 0; i--) {
                if (queue.size() >= MAX_BUFFER) {
                    dropped++;
                } else {
                    queue.addFirst(batch.get(i));
                }
            }
        }
    }
}
