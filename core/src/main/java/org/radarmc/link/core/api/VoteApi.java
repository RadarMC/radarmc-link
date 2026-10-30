package org.radarmc.link.core.api;

public interface VoteApi {
    PendingVotesPage pendingVotes(String cursor, int limit) throws ApiException;

    void confirmVote(String voteId) throws ApiException;
}
