package org.radarmc.link.core.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.radarmc.link.core.rewards.BonusDecision;

public final class RewardMessage {
    public static final String CHANNEL = "radarlink:reward";
    private static final Duration MAX_SKEW = Duration.ofMinutes(5);
    private static final int VERSION = 2;
    private static final int MAC_LENGTH = 32;
    private static final int MAX_LENGTH = 1024;
    private static final String ALGORITHM = "HmacSHA256";
    private static final int GIVE_BONUS = 1;
    private static final int SKIP_BONUS = 0;

    public enum Type {
        REWARD,
        ACK
    }

    private final Type type;
    private final String voteId;
    private final String player;
    private final int streak;
    private final Instant issuedAt;
    private final Instant votedAt;
    private final BonusDecision bonus;

    private RewardMessage(
            Type type,
            String voteId,
            String player,
            int streak,
            Instant issuedAt,
            Instant votedAt,
            BonusDecision bonus) {
        this.type = type;
        this.voteId = voteId;
        this.player = player;
        this.streak = streak;
        this.issuedAt = issuedAt;
        this.votedAt = votedAt;
        this.bonus = bonus;
    }

    public Type type() {
        return type;
    }

    public String voteId() {
        return voteId;
    }

    public String player() {
        return player;
    }

    public int streak() {
        return streak;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant votedAt() {
        return votedAt;
    }

    public BonusDecision bonus() {
        return bonus;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RewardMessage)) {
            return false;
        }
        RewardMessage other = (RewardMessage) o;
        return type == other.type
                && streak == other.streak
                && voteId.equals(other.voteId)
                && player.equals(other.player)
                && issuedAt.equals(other.issuedAt)
                && votedAt.equals(other.votedAt)
                && bonus == other.bonus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, voteId, player, streak, issuedAt, votedAt, bonus);
    }

    @Override
    public String toString() {
        return "RewardMessage[type=" + type + ", voteId=" + voteId + ", player=" + player + ", streak=" + streak
                + ", bonus=" + bonus + "]";
    }

    public static RewardMessage reward(
            String voteId, String player, int streak, Instant now, Instant votedAt, boolean giveBonus) {
        BonusDecision bonus = giveBonus ? BonusDecision.GIVE : BonusDecision.SKIP;
        return new RewardMessage(Type.REWARD, voteId, player, streak, now, votedAt, bonus);
    }

    public RewardMessage ack(Instant now) {
        return new RewardMessage(Type.ACK, voteId, player, streak, now, votedAt, bonus);
    }

    public byte[] encode(byte[] secret) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeByte(type.ordinal());
            out.writeUTF(voteId);
            out.writeUTF(player);
            out.writeInt(streak);
            out.writeLong(issuedAt.toEpochMilli());
            out.writeLong(votedAt.toEpochMilli());
            out.writeByte(bonus == BonusDecision.GIVE ? GIVE_BONUS : SKIP_BONUS);
            out.flush();
            byte[] body = bytes.toByteArray();
            out.write(mac(secret, body));
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("encode reward message", e);
        }
    }

    public static Optional<RewardMessage> decode(byte[] data, byte[] secret, Clock clock) {
        if (data == null || data.length <= MAC_LENGTH || data.length > MAX_LENGTH) {
            return Optional.empty();
        }
        byte[] body = Arrays.copyOfRange(data, 0, data.length - MAC_LENGTH);
        byte[] signature = Arrays.copyOfRange(data, data.length - MAC_LENGTH, data.length);
        if (!MessageDigest.isEqual(mac(secret, body), signature)) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            int version = in.readUnsignedByte();
            int typeIndex = in.readUnsignedByte();
            if (version != VERSION || typeIndex >= Type.values().length) {
                return Optional.empty();
            }
            RewardMessage message = read(Type.values()[typeIndex], in);
            if (in.available() != 0 || !fresh(message.issuedAt(), clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(message);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static RewardMessage read(Type type, DataInputStream in) throws IOException {
        String voteId = in.readUTF();
        String player = in.readUTF();
        int streak = in.readInt();
        Instant issuedAt = Instant.ofEpochMilli(in.readLong());
        Instant votedAt = Instant.ofEpochMilli(in.readLong());
        BonusDecision bonus = in.readUnsignedByte() == GIVE_BONUS ? BonusDecision.GIVE : BonusDecision.SKIP;
        return new RewardMessage(type, voteId, player, streak, issuedAt, votedAt, bonus);
    }

    public static byte[] secretBytes(String secret) {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean fresh(Instant issuedAt, Instant now) {
        return !issuedAt.isBefore(now.minus(MAX_SKEW)) && !issuedAt.isAfter(now.plus(MAX_SKEW));
    }

    private static byte[] mac(byte[] secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
