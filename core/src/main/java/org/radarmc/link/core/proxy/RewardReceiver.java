package org.radarmc.link.core.proxy;

import java.time.Clock;
import java.util.Optional;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.rewards.LocalDelivery;
import org.radarmc.link.core.rewards.Reward;
import org.radarmc.link.core.rewards.RewardStore;

public final class RewardReceiver {
    private final byte[] secret;
    private final RewardStore store;
    private final LocalDelivery delivery;
    private final Clock clock;
    private final RadarLogger log;
    private boolean warnedInvalid;

    public RewardReceiver(String secret, RewardStore store, LocalDelivery delivery, Clock clock, RadarLogger log) {
        this.secret = RewardMessage.secretBytes(secret);
        this.store = store;
        this.delivery = delivery;
        this.clock = clock;
        this.log = log;
    }

    public synchronized Optional<byte[]> receive(String connectionPlayer, byte[] data) {
        Optional<RewardMessage> decoded = RewardMessage.decode(data, secret, clock)
                .filter(message -> message.type() == RewardMessage.Type.REWARD)
                .filter(message -> message.player().equalsIgnoreCase(connectionPlayer));
        if (!decoded.isPresent()) {
            warnInvalidOnce();
            return Optional.empty();
        }
        RewardMessage message = decoded.get();
        if (!store.isHandled(message.voteId())) {
            store.markHandled(message.voteId());
            Reward reward = new Reward(
                    message.voteId(), message.player(), message.streak(), clock.instant(), message.votedAt());
            delivery.runNow(connectionPlayer, reward, message.bonus());
        }
        return Optional.of(message.ack(clock.instant()).encode(secret));
    }

    private void warnInvalidOnce() {
        if (warnedInvalid) {
            return;
        }
        warnedInvalid = true;
        log.warn("Ignored an unsigned or invalid reward message. Check that proxy-secret is the same on the proxy"
                + " and this server.");
    }
}
