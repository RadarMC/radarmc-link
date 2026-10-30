package org.radarmc.link.core.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class PendingVote {
    private final String voteId;
    private final String nick;
    private final int streak;
    private final Instant createdAt;

    public PendingVote(String voteId, String nick, int streak, Instant createdAt) {
        this.voteId = voteId;
        this.nick = nick;
        this.streak = streak;
        this.createdAt = createdAt;
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

    public Optional<Instant> createdAt() {
        return Optional.ofNullable(createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PendingVote)) {
            return false;
        }
        PendingVote other = (PendingVote) o;
        return streak == other.streak
                && voteId.equals(other.voteId)
                && nick.equals(other.nick)
                && Objects.equals(createdAt, other.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voteId, nick, streak, createdAt);
    }

    @Override
    public String toString() {
        return "PendingVote[voteId=" + voteId + ", nick=" + nick + ", streak=" + streak + ", createdAt=" + createdAt
                + "]";
    }
}
