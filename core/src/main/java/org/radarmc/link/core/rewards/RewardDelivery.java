package org.radarmc.link.core.rewards;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface RewardDelivery {
    Optional<String> onlineName(String nick);

    boolean hasReward(int streak);

    boolean idempotent();

    CompletableFuture<Boolean> deliver(String onlineName, Reward reward);
}
