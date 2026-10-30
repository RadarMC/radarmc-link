package org.radarmc.link.core.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class Heartbeat {
    private final String instanceId;
    private final String platform;
    private final String software;
    private final String pluginVersion;
    private final int onlinePlayers;
    private final int maxPlayers;
    private final boolean behindProxy;
    private final boolean sendJoins;
    private final boolean floodgate;
    private final String rewards;
    private final boolean offlineRewards;
    private final VoteRewardSummary voteReward;
    private final List<StreakRewardSummary> streakRewards;
    private final boolean stopping;

    public Heartbeat(
            String instanceId,
            String platform,
            String software,
            String pluginVersion,
            int onlinePlayers,
            int maxPlayers,
            boolean behindProxy,
            boolean sendJoins,
            boolean floodgate,
            String rewards,
            boolean offlineRewards,
            VoteRewardSummary voteReward,
            List<StreakRewardSummary> streakRewards,
            boolean stopping) {
        this.instanceId = instanceId;
        this.platform = platform;
        this.software = software;
        this.pluginVersion = pluginVersion;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.behindProxy = behindProxy;
        this.sendJoins = sendJoins;
        this.floodgate = floodgate;
        this.rewards = rewards;
        this.offlineRewards = offlineRewards;
        this.voteReward = voteReward;
        this.streakRewards = Collections.unmodifiableList(new ArrayList<>(streakRewards));
        this.stopping = stopping;
    }

    public String instanceId() {
        return instanceId;
    }

    public String platform() {
        return platform;
    }

    public String software() {
        return software;
    }

    public String pluginVersion() {
        return pluginVersion;
    }

    public int onlinePlayers() {
        return onlinePlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public boolean behindProxy() {
        return behindProxy;
    }

    public boolean sendJoins() {
        return sendJoins;
    }

    public boolean floodgate() {
        return floodgate;
    }

    public String rewards() {
        return rewards;
    }

    public boolean offlineRewards() {
        return offlineRewards;
    }

    public Optional<VoteRewardSummary> voteReward() {
        return Optional.ofNullable(voteReward);
    }

    public List<StreakRewardSummary> streakRewards() {
        return streakRewards;
    }

    public boolean stopping() {
        return stopping;
    }
}
