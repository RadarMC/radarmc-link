package org.radarmc.link.core.rewards;

import java.time.Instant;

@FunctionalInterface
public interface BonusGuard {
    BonusGuard NONE = (player, streak, votedAt, voteId) -> true;

    boolean claim(String player, int streak, Instant votedAt, String voteId);
}
