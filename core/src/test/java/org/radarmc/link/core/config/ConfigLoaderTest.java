package org.radarmc.link.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.radarmc.link.core.io.TextFiles;
import org.radarmc.link.core.rewards.RewardPlan;
import org.radarmc.link.core.rewards.StreakReward;
import org.radarmc.link.core.rewards.VoteReward;

class ConfigLoaderTest {
    private static final String KEY = "rmc_0123456789abcdefghijABCDEFGHIJkl";
    private static final int MAX_BYTES = 1 << 20;

    @Test
    void fullConfigParses() {
        ConfigResult r = ConfigLoader.parse(String.format(
                "api-url: \"https://api.example.org/\"\n"
                        + "api-key: \"%s\"\n"
                        + "server-slug: \"My-Server\"\n"
                        + "send-joins: false\n"
                        + "behind-proxy: true\n"
                        + "vote-poll-interval: 120\n"
                        + "offline-rewards: false\n"
                        + "proxy-rewards: commands\n"
                        + "proxy-secret: \"0123456789abcdef\"\n"
                        + "vote-reward:\n"
                        + "  commands: \"give %%player%% diamond 1\"\n"
                        + "streak-rewards:\n"
                        + "  - streak: 7\n"
                        + "    label: \"VIP\"\n"
                        + "    commands: [\"/lp user %%player%% parent addtemp vip 7d\"]\n",
                KEY));
        assertTrue(r.usable(), r.errors().toString());
        RadarConfig c = r.config();
        assertEquals("https://api.example.org", c.apiUrl().toString());
        assertEquals("my-server", c.serverSlug());
        assertFalse(c.sendJoins());
        assertTrue(c.behindProxy());
        assertEquals(Duration.ofSeconds(120), c.votePollInterval());
        assertFalse(c.offlineRewards());
        assertEquals(ProxyRewards.COMMANDS, c.proxyRewards());
        assertTrue(c.hasProxySecret());
        assertEquals(
                new VoteReward(Arrays.asList("give %player% diamond 1"), null),
                c.rewards().voteReward().get());
        assertEquals(
                Arrays.asList(new StreakReward(7, 0, Arrays.asList("lp user %player% parent addtemp vip 7d"), "VIP")),
                c.rewards().streakRewards());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test
    void defaultsApply() {
        ConfigResult r = ConfigLoader.parse("api-key: " + KEY + "\nserver-slug: abc\n");
        assertTrue(r.usable());
        RadarConfig c = r.config();
        assertEquals(ConfigLoader.DEFAULT_API_URL, c.apiUrl().toString());
        assertTrue(c.sendJoins());
        assertFalse(c.behindProxy());
        assertTrue(c.offlineRewards());
        assertEquals(Duration.ofSeconds(60), c.votePollInterval());
        assertEquals(ProxyRewards.FORWARD, c.proxyRewards());
        assertFalse(c.hasProxySecret());
        assertTrue(c.rewards().isEmpty());
    }

    @Test
    void missingKeyAndSlugAreErrors() {
        ConfigResult r = ConfigLoader.parse("api-key: \"\"\n");
        assertFalse(r.usable());
        assertEquals(2, r.errors().size());
    }

    @Test
    void badKeyAndSlugAreErrors() {
        ConfigResult r = ConfigLoader.parse("api-key: abc\nserver-slug: \"bad slug!\"\n");
        assertFalse(r.usable());
        assertTrue(r.errors().get(0).contains("api-key"));
        assertTrue(r.errors().get(1).contains("server-slug"));
    }

    @Test
    void urlRules() {
        assertFalse(ConfigLoader.parse(base() + "api-url: ftp://x.org\n").usable());
        assertFalse(
                ConfigLoader.parse(base() + "api-url: \"https://u:p@x.org\"\n").usable());
        assertFalse(
                ConfigLoader.parse(base() + "api-url: \"https://x.org/?a=1\"\n").usable());
        ConfigResult http = ConfigLoader.parse(base() + "api-url: http://localhost:8080\n");
        assertTrue(http.usable());
        assertTrue(http.warnings().get(0).contains("plain http"));
    }

    @Test
    void pollIntervalIsClamped() {
        ConfigResult r = ConfigLoader.parse(base() + "vote-poll-interval: 5\n");
        assertEquals(Duration.ofSeconds(30), r.config().votePollInterval());
        assertEquals(1, r.warnings().size());
        r = ConfigLoader.parse(base() + "vote-poll-interval: 99999\n");
        assertEquals(Duration.ofSeconds(3600), r.config().votePollInterval());
    }

    @Test
    void shortProxySecretIsIgnored() {
        ConfigResult r = ConfigLoader.parse(base() + "proxy-secret: short\n");
        assertFalse(r.config().hasProxySecret());
        assertEquals(1, r.warnings().size());
    }

    @Test
    void badBonusesAreSkippedWithWarnings() {
        ConfigResult r = ConfigLoader.parse(base()
                + String.format(
                        "streak-rewards:\n"
                                + "  - streak: 0\n"
                                + "    commands: [a]\n"
                                + "  - streak: 3\n"
                                + "    commands: []\n"
                                + "  - streak: 3\n"
                                + "    commands: [\"say one\"]\n"
                                + "  - streak: 3\n"
                                + "    commands: [\"say two\"]\n"
                                + "  - \"nonsense\"\n"
                                + "  - streak: 5\n"
                                + "    commands: [\"say %%player%%\", 42, \"  \"]\n"
                                + "    label: \"%s\"\n",
                        new String(new char[100]).replace("\0", "x")));
        assertTrue(r.usable());
        List<StreakReward> bonuses = r.config().rewards().streakRewards();
        assertEquals(
                Arrays.asList(3, 5), bonuses.stream().map(StreakReward::streak).collect(Collectors.toList()));
        assertEquals(Arrays.asList("say one"), bonuses.get(0).commands());
        assertEquals(ConfigLoader.MAX_LABEL_LENGTH, bonuses.get(1).label().length());
        assertEquals(5, r.warnings().size(), r.warnings().toString());
    }

    @Test
    void multilineCommandIsRejected() {
        ConfigResult r =
                ConfigLoader.parse(base() + "vote-reward:\n" + "  commands: [\"say a\\nop hacker\", \"say ok\"]\n");
        assertEquals(
                Arrays.asList("say ok"), r.config().rewards().voteReward().get().commands());
    }

    @Test
    void wrongTypesFallBack() {
        ConfigResult r =
                ConfigLoader.parse(base() + "send-joins: maybe\nvote-poll-interval: soon\nstreak-rewards: 5\n");
        assertTrue(r.config().sendJoins());
        assertEquals(Duration.ofSeconds(60), r.config().votePollInterval());
        assertEquals(3, r.warnings().size());
    }

    @Test
    void splitRewardsParse() {
        ConfigResult r = ConfigLoader.parse(base() + "vote-reward:\n"
                + "  label: \"Алмаз за голос\"\n"
                + "  commands: [\"/give %player% diamond 1\"]\n"
                + "streak-rewards:\n"
                + "  - streak: 30\n"
                + "    repeat-every: 30\n"
                + "    label: \"Легендарный ключ\"\n"
                + "    commands: [\"crate key give %player% legendary\"]\n"
                + "  - streak: 7\n"
                + "    commands: \"lp user %player% parent addtemp vip 7d\"\n");
        assertTrue(r.usable(), r.errors().toString());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
        RewardPlan plan = r.config().rewards();
        assertEquals(
                new VoteReward(Arrays.asList("give %player% diamond 1"), "Алмаз за голос"),
                plan.voteReward().get());
        assertEquals(
                Arrays.asList(
                        new StreakReward(7, 0, Arrays.asList("lp user %player% parent addtemp vip 7d"), null),
                        new StreakReward(
                                30, 30, Arrays.asList("crate key give %player% legendary"), "Легендарный ключ")),
                plan.streakRewards());
    }

    @Test
    void voteRewardAloneOrBonusesAloneWork() {
        RewardPlan voteOnly = ConfigLoader.parse(
                        base() + "vote-reward:\n  commands: [\"say hi\"]\nstreak-rewards: []\n")
                .config()
                .rewards();
        assertEquals(1, voteOnly.runsFor(5).size());
        RewardPlan bonusOnly = ConfigLoader.parse(
                        base() + "vote-reward:\nstreak-rewards:\n  - streak: 3\n    commands: [a]\n")
                .config()
                .rewards();
        assertFalse(bonusOnly.voteReward().isPresent());
        assertTrue(bonusOnly.runsFor(2).isEmpty());
        assertEquals(1, bonusOnly.runsFor(3).size());
    }

    @Test
    void badSplitRewardsAreSkippedWithWarnings() {
        ConfigResult r = ConfigLoader.parse(base() + "vote-reward:\n"
                + "  label: \"no commands\"\n"
                + "streak-rewards:\n"
                + "  - streak: 0\n"
                + "    commands: [a]\n"
                + "  - streak: 5\n"
                + "    repeat-every: 0\n"
                + "    commands: [a]\n"
                + "  - streak: 6\n"
                + "    repeat-every: weekly\n"
                + "    commands: [a]\n"
                + "  - streak: 7\n"
                + "    commands: []\n"
                + "  - streak: 8\n"
                + "    commands: [b]\n"
                + "  - streak: 8\n"
                + "    commands: [c]\n");
        assertTrue(r.usable());
        RewardPlan plan = r.config().rewards();
        assertFalse(plan.voteReward().isPresent());
        assertEquals(
                Arrays.asList(
                        new StreakReward(5, 0, Arrays.asList("a"), null),
                        new StreakReward(8, 0, Arrays.asList("b"), null)),
                plan.streakRewards(),
                "repeat-every: 0 means no repeat");
        assertEquals(5, r.warnings().size(), r.warnings().toString());
        assertTrue(r.warnings().get(0).startsWith("vote-reward: no commands"));
        assertTrue(r.warnings().get(2).contains("repeat-every must be a whole number from 1"));
        assertTrue(ConfigLoader.parse(base() + "vote-reward: \"give me\"\n")
                .warnings()
                .get(0)
                .startsWith("vote-reward must have commands"));
        assertTrue(ConfigLoader.parse(base() + "streak-rewards: 7\n")
                .warnings()
                .get(0)
                .startsWith("streak-rewards must be a list"));
    }

    @Test
    void rewardTiersAreNotReadAndWarnOnce() {
        ConfigResult r = ConfigLoader.parse(base() + "reward-tiers:\n  - streak: 1\n    commands: [old]\n"
                + "streak-rewards:\n  - streak: 7\n    commands: [new]\n");
        assertEquals(
                Arrays.asList("new"), r.config().rewards().runsFor(7).get(0).commands());
        assertTrue(r.config().rewards().runsFor(1).isEmpty());
        assertEquals(
                Arrays.asList("reward-tiers is not a RadarLink setting; use vote-reward and streak-rewards"),
                r.warnings());
        assertTrue(ConfigLoader.parse(base() + "reward-tiers:\n  - streak: 1\n    commands: [old]\n")
                .config()
                .rewards()
                .isEmpty());
    }

    @Test
    void onlyWholeNumbersInRangeAreStreaks() {
        ConfigResult r = ConfigLoader.parse(base() + "streak-rewards:\n"
                + "  - streak: 4294967303\n    commands: [overflow]\n"
                + "  - streak: 7.0\n    commands: [fraction]\n"
                + "  - streak: 99999999999999999999\n    commands: [huge]\n"
                + "  - streak: 3\n    repeat-every: 4294967297\n    commands: [overflow-repeat]\n"
                + "  - streak: \"7\"\n    commands: [text]\n"
                + "  - streak: 3650\n    repeat-every: 3650\n    commands: [max]\n");
        assertEquals(
                Arrays.asList(new StreakReward(3650, 3650, Arrays.asList("max"), null)),
                r.config().rewards().streakRewards());
        assertEquals(5, r.warnings().size(), r.warnings().toString());
    }

    @Test
    void labelsBecomeOneLineTheSiteAccepts() {
        String raw = "  VIP\u2028на\u2029 7\u0085дней\t\u202Eкупить\u2066\u0007\u009b\ud800 ";
        assertEquals("VIP на 7 дней купить", ConfigLoader.singleLine(raw).trim());
        ConfigResult r = ConfigLoader.parse(base() + "vote-reward:\n  label: \"\\u202E\\u2066\"\n  commands: [a]\n");
        assertNull(r.config().rewards().voteReward().get().label(), "only controls left: no label");
        ConfigResult cut = ConfigLoader.parse(base() + "vote-reward:\n  label: \""
                + new String(new char[79]).replace("\0", "x") + " y\"\n  commands: [a]\n");
        assertEquals(79, cut.config().rewards().voteReward().get().label().length(), "no trailing space after the cut");
    }

    @Test
    void uncommentedDefaultExamplesAreValid() throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/radarlink-default-config.yml")) {
            String yaml = TextFiles.readUtf8(in, MAX_BYTES).replaceAll("(?m)^#(  )", "$1");
            ConfigResult r = ConfigLoader.parse(yaml);
            assertEquals(2, r.errors().size(), r.errors().toString());
            assertTrue(r.warnings().isEmpty(), r.warnings().toString());
            RewardPlan plan = r.config().rewards();
            assertEquals("Алмаз за голос", plan.voteReward().get().label());
            assertEquals(
                    Arrays.asList(7, 30),
                    plan.streakRewards().stream().map(StreakReward::streak).collect(Collectors.toList()));
            assertEquals(30, plan.streakRewards().get(1).repeatEvery());
        }
    }

    @Test
    void invalidYamlIsAnError() {
        ConfigResult r = ConfigLoader.parse("api-key: [unclosed\n");
        assertFalse(r.usable());
        assertNull(r.config());
        assertFalse(ConfigLoader.parse("- just\n- a list\n").usable());
        assertFalse(ConfigLoader.parse("a: 1\na: 2\n").usable());
    }

    @Test
    void bundledDefaultConfigOnlyLacksKeyAndSlug() throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/radarlink-default-config.yml")) {
            ConfigResult r = ConfigLoader.parse(TextFiles.readUtf8(in, MAX_BYTES));
            assertEquals(2, r.errors().size(), r.errors().toString());
            assertTrue(r.warnings().isEmpty(), r.warnings().toString());
            assertTrue(r.config().rewards().isEmpty());
            assertTrue(r.config().sendJoins());
        }
    }

    private static String base() {
        return "api-key: " + KEY + "\nserver-slug: abc\n";
    }
}
