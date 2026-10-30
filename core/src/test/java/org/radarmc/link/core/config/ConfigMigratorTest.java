package org.radarmc.link.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.radarmc.link.core.io.TextFiles;
import org.radarmc.link.core.rewards.RewardPlan;
import org.radarmc.link.core.rewards.StreakReward;
import org.radarmc.link.core.rewards.VoteReward;

class ConfigMigratorTest {
    private static final String KEY = "rmc_0123456789abcdefghijABCDEFGHIJkl";

    @Test
    void radarLink10ConfigMovesItsTiersAndKeepsTheOwnersValues() throws IOException {
        ConfigMigrator.Migration m = ConfigMigrator.migrate(resource("/radarlink-1.0-config.yml"), defaults())
                .get();
        assertEquals(1, m.fromVersion());
        ConfigResult r = ConfigLoader.parse(m.text());
        assertTrue(r.usable(), r.errors().toString());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
        assertEquals(KEY, r.config().apiKey());
        assertEquals("my-server", r.config().serverSlug());
        assertFalse(r.config().sendJoins());
        RewardPlan plan = r.config().rewards();
        assertEquals(
                new VoteReward(Arrays.asList("give %player% diamond 1"), "Алмаз за голос"),
                plan.voteReward().get());
        assertEquals(
                Arrays.asList(
                        new StreakReward(
                                7, 1, Arrays.asList("lp user %player% parent addtemp vip 7d"), "VIP на 7 дней"),
                        new StreakReward(
                                30, 1, Arrays.asList("crate key give %player% legendary"), "Легендарный ключ")),
                plan.streakRewards());
        assertTrue(m.text().contains("config-version: 2\n"));
        assertFalse(m.text().contains("reward-tiers"), "no key and no comment left from 1.0");
        assertTrue(m.text().startsWith("# RadarLink: связь вашего сервера"));
        assertTrue(m.text().contains("# Бонусы за серию сверх награды за голос."));
        assertEquals(1, m.notes().size(), m.notes().toString());
        assertTrue(m.notes().get(0).startsWith("reward-tiers moved"));
    }

    @Test
    void theDefaultIsRebuiltAsItIs() throws IOException {
        String current = defaults();
        String old = current.replace("config-version: 2\n", "");
        ConfigMigrator.Migration m = ConfigMigrator.migrate(old, current).get();
        assertEquals(current, m.text());
        assertTrue(m.notes().isEmpty(), m.notes().toString());
        assertFalse(ConfigMigrator.migrate(m.text(), current).isPresent(), "runs once");
    }

    @Test
    void radarLink11ConfigKeepsItsRewardsAndUnknownSettings() throws IOException {
        String old = "api-key: \"" + KEY + "\"\n"
                + "server-slug: my-server\n"
                + "\n"
                + "# моя награда\n"
                + "vote-reward:\n"
                + "  commands:\n"
                + "    - \"give %player% emerald 1\"\n"
                + "  # не трогать\n"
                + "\n"
                + "streak-rewards: []\n"
                + "\n"
                + "# своя пометка\n"
                + "my-note: keep me\n";
        ConfigMigrator.Migration m = ConfigMigrator.migrate(old, defaults()).get();
        ConfigResult r = ConfigLoader.parse(m.text());
        assertTrue(r.usable(), r.errors().toString());
        assertEquals(
                Arrays.asList("give %player% emerald 1"),
                r.config().rewards().voteReward().get().commands());
        assertTrue(m.text().contains("    - \"give %player% emerald 1\"\n  # не трогать\n"), m.text());
        assertTrue(m.text().endsWith("\n# своя пометка\nmy-note: keep me\n"), m.text());
        assertTrue(m.text().contains("#  - streak: 7\n"), "an empty streak-rewards gets the default examples");
        assertFalse(m.text().contains("# моя награда"), "comments come from the default");
        List<String> notes = m.notes();
        assertEquals(1, notes.size(), notes.toString());
        assertTrue(notes.get(0).startsWith("new settings with default values: api-url, send-joins"), notes.get(0));
    }

    @Test
    void tiersNextToTheNewKeysAreDropped() throws IOException {
        String old = "api-key: \"" + KEY
                + "\"\nserver-slug: s\nvote-reward:\n  commands: [a]\nreward-tiers:\n  - streak: 1\n    commands: [b]\n";
        ConfigMigrator.Migration m = ConfigMigrator.migrate(old, defaults()).get();
        assertFalse(m.text().contains("reward-tiers"));
        assertEquals(
                Arrays.asList("a"),
                ConfigLoader.parse(m.text())
                        .config()
                        .rewards()
                        .voteReward()
                        .get()
                        .commands());
        assertTrue(
                m.notes().get(0).startsWith("removed reward-tiers: vote-reward and streak-rewards were already set"));
    }

    @Test
    void tiersWithoutAStreak1TierLeaveTheVoteRewardEmpty() throws IOException {
        String old =
                "reward-tiers:\n  - streak: 3\n    commands: \"say \\\"hi\\\" \\\\o/\"\n  - streak: 0\n    commands: [bad]\n";
        ConfigMigrator.Migration m = ConfigMigrator.migrate(old, defaults()).get();
        RewardPlan plan = ConfigLoader.parse(m.text()).config().rewards();
        assertFalse(plan.voteReward().isPresent());
        assertEquals(
                Arrays.asList(new StreakReward(3, 1, Arrays.asList("say \"hi\" \\o/"), null)), plan.streakRewards());
    }

    @Test
    void currentNewerOrBrokenFilesAreLeftAlone() throws IOException {
        assertFalse(ConfigMigrator.migrate(defaults(), defaults()).isPresent());
        assertFalse(ConfigMigrator.migrate("config-version: 3\n", defaults()).isPresent());
        assertFalse(ConfigMigrator.migrate("api-key: [unclosed\n", defaults()).isPresent());
        assertFalse(ConfigMigrator.migrate("- a list\n", defaults()).isPresent());
    }

    @Test
    void emptyFileBecomesTheDefault() throws IOException {
        assertEquals(defaults(), ConfigMigrator.migrate("", defaults()).get().text());
    }

    @Test
    void quoteEscapes() {
        assertEquals("\"a \\\"b\\\" c\\\\d\\ne\\tf\"", ConfigMigrator.quote("a \"b\" c\\d\ne\tf"));
    }

    private static String defaults() throws IOException {
        return resource("/radarlink-default-config.yml");
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ConfigMigratorTest.class.getResourceAsStream(name)) {
            return TextFiles.readUtf8(in, 1 << 20);
        }
    }
}
