package org.radarmc.link.core.proxy;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.radarmc.link.core.players.OnlinePlayers;
import org.radarmc.link.core.rewards.BonusGuard;
import org.radarmc.link.core.rewards.Reward;
import org.radarmc.link.core.rewards.RewardDelivery;

public final class ForwardingDelivery implements RewardDelivery {
    static final Duration ACK_TIMEOUT = Duration.ofSeconds(15);

    @FunctionalInterface
    public interface Transport {
        boolean sendToServerOf(String playerName, byte[] message);
    }

    private final Transport transport;
    private final OnlinePlayers online;
    private final byte[] secret;
    private final BonusGuard bonuses;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final Duration ackTimeout;
    private final Map<String, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();

    public ForwardingDelivery(
            Transport transport,
            OnlinePlayers online,
            String secret,
            BonusGuard bonuses,
            Clock clock,
            ScheduledExecutorService scheduler) {
        this(transport, online, secret, bonuses, clock, scheduler, ACK_TIMEOUT);
    }

    ForwardingDelivery(
            Transport transport,
            OnlinePlayers online,
            String secret,
            BonusGuard bonuses,
            Clock clock,
            ScheduledExecutorService scheduler,
            Duration ackTimeout) {
        this.transport = transport;
        this.online = online;
        this.secret = RewardMessage.secretBytes(secret);
        this.bonuses = bonuses;
        this.clock = clock;
        this.scheduler = scheduler;
        this.ackTimeout = ackTimeout;
    }

    @Override
    public Optional<String> onlineName(String nick) {
        return online.find(nick);
    }

    @Override
    public boolean hasReward(int streak) {
        return true;
    }

    @Override
    public boolean idempotent() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> deliver(String onlineName, Reward reward) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        CompletableFuture<Boolean> previous = pending.putIfAbsent(reward.voteId(), done);
        if (previous != null) {
            return previous;
        }
        done.whenComplete((ok, error) -> pending.remove(reward.voteId(), done));
        if (!transport.sendToServerOf(onlineName, message(onlineName, reward))) {
            done.complete(false);
            return done;
        }
        scheduler.schedule(() -> done.complete(false), ackTimeout.toMillis(), TimeUnit.MILLISECONDS);
        return done;
    }

    private byte[] message(String player, Reward reward) {
        boolean give = bonuses.claim(player, reward.streak(), reward.votedAt(), reward.voteId());
        return RewardMessage.reward(reward.voteId(), player, reward.streak(), clock.instant(), reward.votedAt(), give)
                .encode(secret);
    }

    public boolean onServerMessage(byte[] data) {
        Optional<RewardMessage> message = RewardMessage.decode(data, secret, clock);
        if (!message.isPresent() || message.get().type() != RewardMessage.Type.ACK) {
            return false;
        }
        CompletableFuture<Boolean> waiting = pending.get(message.get().voteId());
        if (waiting != null) {
            waiting.complete(true);
        }
        return true;
    }

    int waitingForAck() {
        return pending.size();
    }
}
