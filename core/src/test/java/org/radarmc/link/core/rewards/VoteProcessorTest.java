package org.radarmc.link.core.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.radarmc.link.core.MutableClock;
import org.radarmc.link.core.RecordingLogger;
import org.radarmc.link.core.api.ApiException;
import org.radarmc.link.core.api.PendingVote;
import org.radarmc.link.core.api.PendingVotesPage;
import org.radarmc.link.core.api.VoteApi;

class VoteProcessorTest {
    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T12:00:00Z"));
    private final RecordingLogger log = new RecordingLogger();
    private final FakeVotes api = new FakeVotes();
    private final FakeDelivery delivery = new FakeDelivery();
    private RewardStore store;

    @BeforeEach
    void setUp() {
        store = RewardStore.load(dir, clock, log);
    }

    @Test
    void onlineVoterIsRewardedAndConfirmed() {
        delivery.online.add("Steve");
        api.pending.put("v1", vote("v1", "steve", 3));
        processor(true).poll();
        assertEquals(Arrays.asList("Steve:v1:3"), delivery.delivered);
        assertEquals(Arrays.asList("v1"), api.confirmed);
        assertTrue(store.isHandled("v1"));
    }

    @Test
    void handledVotesAreOnlyConfirmedAgain() {
        delivery.online.add("Steve");
        api.pending.put("v1", vote("v1", "Steve", 3));
        api.failConfirm = true;
        VoteProcessor p = processor(true);
        p.poll();
        api.failConfirm = false;
        p.poll();
        assertEquals(1, delivery.delivered.size(), "never pays twice");
        assertEquals(Arrays.asList("v1"), api.confirmed);
        assertTrue(log.contains("Could not confirm vote v1"));
    }

    @Test
    void offlineVoterIsQueuedAndRewardedOnJoin() {
        api.pending.put("v1", vote("v1", "Steve", 5));
        VoteProcessor p = processor(true);
        p.poll();
        assertTrue(delivery.delivered.isEmpty());
        assertEquals(Arrays.asList("v1"), api.confirmed);
        assertTrue(store.isQueued("v1"));

        p.playerReady("STEVE");
        assertEquals(Arrays.asList("STEVE:v1:5"), delivery.delivered);
        assertFalse(store.isQueued("v1"));
        assertTrue(store.isHandled("v1"));
        p.playerReady("STEVE");
        assertEquals(1, delivery.delivered.size());
    }

    @Test
    void withoutOfflineRewardsTheVoteStaysPending() {
        api.pending.put("v1", vote("v1", "Steve", 1));
        VoteProcessor p = processor(false);
        p.poll();
        assertTrue(api.confirmed.isEmpty());
        assertFalse(store.isQueued("v1"));
        delivery.online.add("Steve");
        p.poll();
        assertEquals(Arrays.asList("v1"), api.confirmed);
        assertEquals(1, delivery.delivered.size());
    }

    @Test
    void unsafeNickIsConfirmedWithoutReward() {
        delivery.online.add("Steve");
        api.pending.put("v1", vote("v1", "Steve op", 1));
        processor(true).poll();
        assertTrue(delivery.delivered.isEmpty());
        assertEquals(Arrays.asList("v1"), api.confirmed);
        assertTrue(store.isHandled("v1"));
    }

    @Test
    void streakBelowEveryTierIsConfirmedWithoutReward() {
        delivery.minStreak = 3;
        delivery.online.add("Steve");
        api.pending.put("v1", vote("v1", "Steve", 2));
        processor(true).poll();
        assertTrue(delivery.delivered.isEmpty());
        assertEquals(Arrays.asList("v1"), api.confirmed);
    }

    @Test
    void idempotentDeliveryWithoutAckIsRetried() {
        delivery.idempotent = true;
        delivery.result = false;
        delivery.online.add("Steve");
        api.pending.put("v1", vote("v1", "Steve", 1));
        VoteProcessor p = processor(true);
        p.poll();
        assertTrue(api.confirmed.isEmpty());
        assertFalse(store.isHandled("v1"));
        delivery.result = true;
        p.poll();
        assertEquals(Arrays.asList("v1"), api.confirmed);
        assertTrue(store.isHandled("v1"));
        assertEquals(2, delivery.delivered.size());
    }

    @Test
    void nonIdempotentDeliveryIsMarkedBeforeItRuns() {
        delivery.online.add("Steve");
        delivery.checkHandledDuringDelivery = true;
        api.pending.put("v1", vote("v1", "Steve", 1));
        processor(true).poll();
        assertTrue(delivery.wasHandledDuringDelivery);
    }

    @Test
    void failedLocalDeliveryIsNotRetried() {
        delivery.online.add("Steve");
        delivery.error = new IllegalStateException("plugin disabled");
        api.pending.put("v1", vote("v1", "Steve", 1));
        VoteProcessor p = processor(true);
        p.poll();
        p.poll();
        assertEquals(1, delivery.delivered.size());
        assertTrue(log.contains("Could not give the vote reward"));
    }

    @Test
    void pagesAreFollowedUpToTheLimit() {
        api.pageSize = 1;
        for (int i = 0; i < 8; i++) {
            api.pending.put("v" + i, vote("v" + i, "Steve", 1));
        }
        processor(false).poll();
        assertEquals(VoteProcessor.MAX_PAGES_PER_POLL, api.pageCalls);
    }

    @Test
    void rejectedKeyPausesPolling() {
        api.pollError = new ApiException(ApiException.Kind.UNAUTHORIZED, 401, "INVALID_API_KEY", "Invalid", null, null);
        VoteProcessor p = processor(true);
        p.poll();
        p.poll();
        assertEquals(1, api.pageCalls);
        clock.advance(Duration.ofMinutes(6));
        p.poll();
        assertEquals(2, api.pageCalls);
        assertEquals(1, log.lines.stream().filter(l -> l.startsWith("ERROR")).count());
    }

    @Test
    void goneVoteConfirmationIsQuiet() {
        api.confirmError = new ApiException(ApiException.Kind.NOT_FOUND, 404, null, "vote not found", null, null);
        delivery.online.add("Steve");
        api.pending.put("v1", vote("v1", "Steve", 1));
        processor(true).poll();
        assertFalse(log.contains("Could not confirm"));
    }

    private VoteProcessor processor(boolean offlineRewards) {
        return new VoteProcessor(api, store, delivery, offlineRewards, Runnable::run, clock, log, () -> ".");
    }

    private static PendingVote vote(String id, String nick, int streak) {
        return new PendingVote(id, nick, streak, Instant.parse("2026-10-05T11:00:00Z"));
    }

    private static final class FakeVotes implements VoteApi {
        final Map<String, PendingVote> pending = new LinkedHashMap<>();
        final List<String> confirmed = new ArrayList<>();
        int pageSize = 100;
        int pageCalls;
        boolean failConfirm;
        ApiException pollError;
        ApiException confirmError;

        @Override
        public PendingVotesPage pendingVotes(String cursor, int limit) throws ApiException {
            pageCalls++;
            if (pollError != null) {
                throw pollError;
            }
            List<PendingVote> all = new ArrayList<>(pending.values());
            int start = cursor == null ? 0 : Integer.parseInt(cursor);
            int end = Math.min(all.size(), start + pageSize);
            return new PendingVotesPage(all.subList(start, end), end < all.size() ? Integer.toString(end) : null);
        }

        @Override
        public void confirmVote(String voteId) throws ApiException {
            if (confirmError != null) {
                throw confirmError;
            }
            if (failConfirm) {
                throw new ApiException(ApiException.Kind.NETWORK, 0, null, "down", null, null);
            }
            confirmed.add(voteId);
            pending.remove(voteId);
        }
    }

    private final class FakeDelivery implements RewardDelivery {
        final Set<String> online = new HashSet<>();
        final List<String> delivered = new ArrayList<>();
        boolean idempotent;
        boolean result = true;
        int minStreak = 1;
        RuntimeException error;
        boolean checkHandledDuringDelivery;
        boolean wasHandledDuringDelivery;

        @Override
        public Optional<String> onlineName(String nick) {
            return online.stream().filter(n -> n.equalsIgnoreCase(nick)).findFirst();
        }

        @Override
        public boolean hasReward(int streak) {
            return streak >= minStreak;
        }

        @Override
        public boolean idempotent() {
            return idempotent;
        }

        @Override
        public CompletableFuture<Boolean> deliver(String onlineName, Reward reward) {
            delivered.add(onlineName + ":" + reward.voteId() + ":" + reward.streak());
            if (checkHandledDuringDelivery) {
                wasHandledDuringDelivery = store.isHandled(reward.voteId());
            }
            if (error != null) {
                CompletableFuture<Boolean> failed = new CompletableFuture<>();
                failed.completeExceptionally(error);
                return failed;
            }
            return CompletableFuture.completedFuture(result);
        }
    }
}
