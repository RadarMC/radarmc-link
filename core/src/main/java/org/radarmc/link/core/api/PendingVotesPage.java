package org.radarmc.link.core.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PendingVotesPage {
    private final List<PendingVote> votes;
    private final String nextCursor;

    public PendingVotesPage(List<PendingVote> votes, String nextCursor) {
        this.votes = Collections.unmodifiableList(new ArrayList<>(votes));
        this.nextCursor = nextCursor;
    }

    public List<PendingVote> votes() {
        return votes;
    }

    public Optional<String> nextCursor() {
        return Optional.ofNullable(nextCursor).filter(cursor -> !cursor.isEmpty());
    }
}
