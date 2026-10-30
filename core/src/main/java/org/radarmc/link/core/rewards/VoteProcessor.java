package org.radarmc.link.core.rewards;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.api.ApiClient;
import org.radarmc.link.core.api.ApiException;
import org.radarmc.link.core.api.PendingVote;
import org.radarmc.link.core.api.PendingVotesPage;
import org.radarmc.link.core.api.VoteApi;
import org.radarmc.link.core.players.PlayerNames;

public final class VoteProcessor {
    static final int MAX_PAGES_PER_POLL = 5;
    private static final Duration AUTH_PAUSE = Duration.ofMinutes(5);

    private final VoteApi api;
    private final RewardStore store;
    private final RewardDelivery delivery;
    private final boolean offlineRewards;
    private final Executor worker;
    private final Clock clock;
    private final RadarLogger log;
    private final Supplier<String> floodgatePrefix;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private Instant pausedUntil = Instant.MIN;
    private boolean authWarned;

    public VoteProcessor(
            VoteApi api,
            RewardStore store,
            RewardDelivery delivery,
            boolean offlineRewards,
            Executor worker,
            Clock clock,
            RadarLogger log,
            Supplier<String> floodgatePrefix) {
        this.api = api;
        this.store = store;
        this.delivery = delivery;
        this.offlineRewards = offlineRewards;
        this.worker = worker;
        this.clock = clock;
        this.log = log;
        this.floodgatePrefix = floodgatePrefix;
    }

    public void poll() {
        if (clock.instant().isBefore(pausedUntil)) {
            return;
        }
        try {
            pollPages();
            authWarned = false;
        } catch (ApiException e) {
            pollFailed(e);
        }
    }

    public void playerReady(String name) {
        String prefix = floodgatePrefix.get();
        for (Reward reward : store.queuedFor(nick -> PlayerNames.matches(nick, name, prefix))) {
            if (inFlight.contains(reward.voteId())) {
                continue;
            }
            if (!delivery.hasReward(reward.streak())) {
                store.complete(reward.voteId());
                continue;
            }
            start(name, reward, true);
        }
    }

    private void pollPages() throws ApiException {
        String cursor = null;
        for (int page = 0; page < MAX_PAGES_PER_POLL; page++) {
            PendingVotesPage result = api.pendingVotes(cursor, ApiClient.MAX_PENDING_PAGE);
            result.votes().forEach(this::handle);
            Optional<String> next = result.nextCursor();
            if (!next.isPresent()) {
                return;
            }
            cursor = next.get();
        }
    }

    private void handle(PendingVote vote) {
        String id = vote.voteId();
        if (inFlight.contains(id)) {
            return;
        }
        if (store.isHandled(id) || store.isQueued(id)) {
            confirm(id);
            return;
        }
        if (!PlayerNames.isValidVoteNick(vote.nick())) {
            log.warn("Vote " + id + " has a nick that cannot be used in a command; confirming it without a reward");
            finishWithoutReward(id);
            return;
        }
        if (!delivery.hasReward(vote.streak())) {
            finishWithoutReward(id);
            return;
        }
        Instant now = clock.instant();
        Reward reward =
                new Reward(id, vote.nick(), vote.streak(), now, vote.createdAt().orElse(now));
        Optional<String> online = delivery.onlineName(vote.nick());
        if (online.isPresent()) {
            start(online.get(), reward, false);
        } else if (offlineRewards && store.enqueue(reward)) {
            confirm(id);
        }
    }

    private void finishWithoutReward(String id) {
        store.markHandled(id);
        confirm(id);
    }

    private void start(String name, Reward reward, boolean fromQueue) {
        String id = reward.voteId();
        inFlight.add(id);
        if (!delivery.idempotent()) {
            markDone(id, fromQueue);
        }
        delivery.deliver(name, reward)
                .whenCompleteAsync((delivered, error) -> finish(name, id, fromQueue, delivered, error), worker);
    }

    private void finish(String name, String id, boolean fromQueue, Boolean delivered, Throwable error) {
        inFlight.remove(id);
        if (error != null) {
            log.error("Could not give the vote reward to " + name, error);
        }
        boolean success = error == null && Boolean.TRUE.equals(delivered);
        if (success && delivery.idempotent()) {
            markDone(id, fromQueue);
        }
        if ((success || !delivery.idempotent()) && !fromQueue) {
            confirm(id);
        }
    }

    private void markDone(String id, boolean fromQueue) {
        if (fromQueue) {
            store.complete(id);
        } else {
            store.markHandled(id);
        }
    }

    private void confirm(String id) {
        try {
            api.confirmVote(id);
        } catch (ApiException e) {
            if (e.kind() == ApiException.Kind.NOT_FOUND) {
                return;
            }
            if (e.kind() == ApiException.Kind.RATE_LIMITED) {
                pausedUntil = clock.instant().plus(e.retryAfter());
            }
            log.warn("Could not confirm vote " + id + ", will retry on the next poll: " + e.describe());
        }
    }

    private void pollFailed(ApiException e) {
        switch (e.kind()) {
            case RATE_LIMITED:
                pausedUntil = clock.instant().plus(e.retryAfter());
                break;
            case UNAUTHORIZED:
            case FORBIDDEN:
            case NOT_FOUND:
                pausedUntil = clock.instant().plus(AUTH_PAUSE);
                if (!authWarned) {
                    authWarned = true;
                    log.error("The API rejected the vote request (" + e.describe()
                            + "). Check api-key and server-slug in config.yml.");
                }
                return;
            default:
                break;
        }
        log.warn("Could not load pending votes: " + e.describe());
    }
}
