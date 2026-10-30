package org.radarmc.link.core.rewards;

import java.time.Instant;
import java.util.Objects;

public final class Reward {
    private final String voteId;
    private final String nick;
    private final int streak;
    private final Instant queuedAt;
    private final Instant votedAt;

    public Reward(String voteId, String nick, int streak, Instant queuedAt) {
        this(voteId, nick, streak, queuedAt, queuedAt);
    }

    public Reward(String voteId, String nick, int streak, Instant queuedAt, Instant votedAt) {
        this.voteId = voteId;
        this.nick = nick;
        this.streak = streak;
        this.queuedAt = queuedAt;
        this.votedAt = votedAt;
    }

    public String voteId() {
        return voteId;
    }

    public String nick() {
        return nick;
    }

    public int streak() {
        return streak;
    }

    public Instant queuedAt() {
        return queuedAt;
    }

    public Instant votedAt() {
        return votedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Reward)) {
            return false;
        }
        Reward other = (Reward) o;
        return streak == other.streak
                && voteId.equals(other.voteId)
                && nick.equals(other.nick)
                && queuedAt.equals(other.queuedAt)
                && votedAt.equals(other.votedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voteId, nick, streak, queuedAt, votedAt);
    }

    @Override
    public String toString() {
        return "Reward[voteId=" + voteId + ", nick=" + nick + ", streak=" + streak + ", queuedAt=" + queuedAt
                + ", votedAt=" + votedAt + "]";
    }
}
