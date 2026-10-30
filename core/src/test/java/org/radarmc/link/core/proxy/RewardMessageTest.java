package org.radarmc.link.core.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.radarmc.link.core.MutableClock;
import org.radarmc.link.core.rewards.BonusDecision;

class RewardMessageTest {
    private static final byte[] SECRET = RewardMessage.secretBytes("0123456789abcdef");
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T12:00:00Z"));

    @Test
    void carriesTheVoteTimeAndTheBonusDecision() {
        Instant voted = Instant.parse("2026-10-04T09:00:00Z");
        RewardMessage message = RewardMessage.reward("v1", "Steve", 7, clock.instant(), voted, false);
        RewardMessage decoded =
                RewardMessage.decode(message.encode(SECRET), SECRET, clock).get();
        assertEquals(message, decoded);
        assertEquals(voted, decoded.votedAt());
        assertEquals(BonusDecision.SKIP, decoded.bonus());
        assertEquals(
                BonusDecision.GIVE,
                RewardMessage.reward("v1", "Steve", 7, clock.instant(), voted, true)
                        .bonus());
        RewardMessage ack = RewardMessage.decode(decoded.ack(clock.instant()).encode(SECRET), SECRET, clock)
                .get();
        assertEquals(RewardMessage.Type.ACK, ack.type());
        assertEquals(BonusDecision.SKIP, ack.bonus());
    }

    @Test
    void otherVersionsAreRejected() throws Exception {
        byte[] data = reward(clock.instant());
        byte[] body = Arrays.copyOf(data, data.length - 32);
        body[0] = 1;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        byte[] signed = Arrays.copyOf(body, body.length + 32);
        System.arraycopy(mac.doFinal(body), 0, signed, body.length, 32);
        assertFalse(RewardMessage.decode(signed, SECRET, clock).isPresent());
    }

    private static byte[] reward(Instant now) {
        return RewardMessage.reward("v1", "Steve", 7, now, now, true).encode(SECRET);
    }

    @Test
    void wrongSecretOrTamperingIsRejected() {
        byte[] data = reward(clock.instant());
        assertFalse(RewardMessage.decode(data, RewardMessage.secretBytes("another-secret-123"), clock)
                .isPresent());
        data[5] ^= 1;
        assertFalse(RewardMessage.decode(data, SECRET, clock).isPresent());
        assertFalse(RewardMessage.decode(new byte[10], SECRET, clock).isPresent());
        assertFalse(RewardMessage.decode(null, SECRET, clock).isPresent());
        assertFalse(RewardMessage.decode(new byte[4096], SECRET, clock).isPresent());
    }

    @Test
    void staleOrFutureMessagesAreRejected() {
        byte[] data = reward(clock.instant());
        clock.advance(Duration.ofMinutes(6));
        assertFalse(RewardMessage.decode(data, SECRET, clock).isPresent());
        byte[] future = reward(clock.instant().plus(Duration.ofMinutes(6)));
        assertFalse(RewardMessage.decode(future, SECRET, clock).isPresent());
    }
}
