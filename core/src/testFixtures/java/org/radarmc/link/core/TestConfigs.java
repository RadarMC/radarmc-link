package org.radarmc.link.core;

import java.net.URI;
import java.time.Duration;
import org.radarmc.link.core.config.ProxyRewards;
import org.radarmc.link.core.config.RadarConfig;
import org.radarmc.link.core.rewards.RewardPlan;

public final class TestConfigs {
    public static final String KEY = "rmc_" + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private TestConfigs() {}

    public static RadarConfig config(URI api, RewardPlan rewards) {
        return new RadarConfig(
                api, KEY, "my-server", true, false, Duration.ofSeconds(60), true, ProxyRewards.FORWARD, "", rewards);
    }

    public static RadarConfig with(RadarConfig c, boolean behindProxy, ProxyRewards proxyRewards, String secret) {
        return new RadarConfig(
                c.apiUrl(),
                c.apiKey(),
                c.serverSlug(),
                c.sendJoins(),
                behindProxy,
                c.votePollInterval(),
                c.offlineRewards(),
                proxyRewards,
                secret,
                c.rewards());
    }
}
