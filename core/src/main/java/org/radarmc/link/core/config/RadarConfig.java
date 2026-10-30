package org.radarmc.link.core.config;

import java.net.URI;
import java.time.Duration;
import org.radarmc.link.core.rewards.RewardPlan;

public final class RadarConfig {
    private final URI apiUrl;
    private final String apiKey;
    private final String serverSlug;
    private final boolean sendJoins;
    private final boolean behindProxy;
    private final Duration votePollInterval;
    private final boolean offlineRewards;
    private final ProxyRewards proxyRewards;
    private final String proxySecret;
    private final RewardPlan rewards;

    public RadarConfig(
            URI apiUrl,
            String apiKey,
            String serverSlug,
            boolean sendJoins,
            boolean behindProxy,
            Duration votePollInterval,
            boolean offlineRewards,
            ProxyRewards proxyRewards,
            String proxySecret,
            RewardPlan rewards) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.serverSlug = serverSlug;
        this.sendJoins = sendJoins;
        this.behindProxy = behindProxy;
        this.votePollInterval = votePollInterval;
        this.offlineRewards = offlineRewards;
        this.proxyRewards = proxyRewards;
        this.proxySecret = proxySecret;
        this.rewards = rewards;
    }

    public URI apiUrl() {
        return apiUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public String serverSlug() {
        return serverSlug;
    }

    public boolean sendJoins() {
        return sendJoins;
    }

    public boolean behindProxy() {
        return behindProxy;
    }

    public Duration votePollInterval() {
        return votePollInterval;
    }

    public boolean offlineRewards() {
        return offlineRewards;
    }

    public ProxyRewards proxyRewards() {
        return proxyRewards;
    }

    public String proxySecret() {
        return proxySecret;
    }

    public RewardPlan rewards() {
        return rewards;
    }

    public boolean hasProxySecret() {
        return proxySecret.length() >= ConfigLoader.MIN_PROXY_SECRET_LENGTH;
    }
}
