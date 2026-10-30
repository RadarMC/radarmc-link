package org.radarmc.link.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.radarmc.link.core.config.ProxyRewards;
import org.radarmc.link.core.config.RadarConfig;
import org.radarmc.link.core.io.TextFiles;
import org.radarmc.link.core.rewards.RewardPlan;
import org.radarmc.link.core.rewards.StreakReward;
import org.radarmc.link.core.rewards.VoteReward;
import org.radarmc.link.core.text.Strings;

class RadarCoreTest {
    private static final String SECRET = "0123456789abcdef";
    private static final RewardPlan REWARDS = RewardPlan.split(
            new VoteReward(Arrays.asList("give %player% diamond 3", "say thanks"), "Три алмаза"),
            Collections.<StreakReward>emptyList());

    @TempDir
    Path dir;

    private FakeApiServer server;
    private final RecordingLogger log = new RecordingLogger();

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeApiServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void rewardModeFollowsRoleAndConfig() {
        RadarConfig rewards = TestConfigs.config(server.url(), REWARDS);
        RadarConfig none = TestConfigs.config(server.url(), RewardPlan.none());
        assertMode(RewardMode.LOCAL, rewards, Role.SERVER, false);
        assertMode(RewardMode.OFF, none, Role.SERVER, false);
        assertMode(RewardMode.OFF, rewards, Role.SERVER_BEHIND_PROXY, false);
        assertMode(
                RewardMode.RECEIVE,
                TestConfigs.with(none, true, ProxyRewards.FORWARD, SECRET),
                Role.SERVER_BEHIND_PROXY,
                false);
        assertMode(RewardMode.FORWARD, TestConfigs.with(none, false, ProxyRewards.FORWARD, SECRET), Role.PROXY, true);
        assertMode(RewardMode.OFF, TestConfigs.with(none, false, ProxyRewards.FORWARD, ""), Role.PROXY, true);
        assertMode(RewardMode.OFF, TestConfigs.with(none, false, ProxyRewards.FORWARD, SECRET), Role.PROXY, false);
        assertMode(RewardMode.PROXY, TestConfigs.with(rewards, false, ProxyRewards.COMMANDS, ""), Role.PROXY, true);
        assertMode(RewardMode.OFF, TestConfigs.with(none, false, ProxyRewards.COMMANDS, ""), Role.PROXY, true);
        assertMode(RewardMode.OFF, TestConfigs.with(rewards, false, ProxyRewards.OFF, SECRET), Role.PROXY, true);
    }

    @Test
    void heartbeatAndJoinsReachTheApi() throws Exception {
        RadarCore core = RadarCore.builder(new TestPlatform(), TestConfigs.config(server.url(), REWARDS), Role.SERVER)
                .build();
        core.start();
        waitFor(() -> !server.requestsTo("/heartbeat").isEmpty());
        core.onJoin(UUID.randomUUID(), "Steve", InetAddress.getByName("203.0.113.9"));
        core.stop();

        List<FakeApiServer.Request> joins = server.requestsTo("/joins");
        assertEquals(1, joins.size());
        JsonArray entries =
                JsonParser.parseString(joins.get(0).body()).getAsJsonObject().getAsJsonArray("joins");
        assertEquals("203.0.113.9", entries.get(0).getAsJsonObject().get("ip").getAsString());

        List<FakeApiServer.Request> beats = server.requestsTo("/heartbeat");
        assertEquals("RadarLink/" + BuildInfo.VERSION + " (paper)", beats.get(0).header("User-Agent"));
        JsonObject first = JsonParser.parseString(beats.get(0).body()).getAsJsonObject();
        assertEquals("paper", first.get("platform").getAsString());
        assertEquals(BuildInfo.VERSION, first.get("plugin_version").getAsString());
        assertEquals("local", first.get("rewards").getAsString());
        assertFalse(first.get("behind_proxy").getAsBoolean());
        assertTrue(first.get("send_joins").getAsBoolean());
        JsonObject vote = first.getAsJsonObject("vote_reward");
        assertEquals(2, vote.get("commands").getAsInt());
        assertEquals("Три алмаза", vote.get("label").getAsString());
        assertFalse(first.has("tiers"));
        assertFalse(beats.get(0).body().contains("give"), "command text never leaves the server");
        JsonObject last =
                JsonParser.parseString(beats.get(beats.size() - 1).body()).getAsJsonObject();
        assertTrue(last.get("stopping").getAsBoolean());

        String id = first.get("instance_id").getAsString();
        assertEquals(id, Strings.strip(TextFiles.readUtf8(dir.resolve(InstanceId.FILE_NAME))));
        assertFalse(log.all().contains("203.0.113.9"));
    }

    @Test
    void serverBehindProxyDoesNotReportJoins() throws Exception {
        RadarConfig config =
                TestConfigs.with(TestConfigs.config(server.url(), REWARDS), true, ProxyRewards.FORWARD, SECRET);
        RadarCore core = RadarCore.builder(new TestPlatform(), config, Role.SERVER_BEHIND_PROXY)
                .build();
        assertTrue(core.receiver().isPresent());
        assertFalse(core.forwarding().isPresent());
        core.start();
        core.onJoin(UUID.randomUUID(), "Steve", InetAddress.getByName("203.0.113.9"));
        core.stop();
        assertTrue(server.requestsTo("/joins").isEmpty());
        JsonObject beat = JsonParser.parseString(
                        server.requestsTo("/heartbeat").get(0).body())
                .getAsJsonObject();
        assertTrue(beat.get("behind_proxy").getAsBoolean());
        assertFalse(beat.get("send_joins").getAsBoolean());
        assertEquals("receive", beat.get("rewards").getAsString());
        assertEquals(2, beat.getAsJsonObject("vote_reward").get("commands").getAsInt());
    }

    @Test
    void heartbeatReportsSplitRewardsWithoutCommands() throws Exception {
        RewardPlan plan = RewardPlan.split(
                new VoteReward(Arrays.asList("give %player% diamond 1"), "Алмаз за голос"),
                Arrays.asList(
                        new StreakReward(30, 30, Arrays.asList("crate key give %player% legendary", "say hi"), null)));
        RadarConfig config = TestConfigs.config(server.url(), plan);
        assertEquals(RewardMode.LOCAL, RadarCore.resolveRewardMode(config, Role.SERVER, false));
        RadarCore core =
                RadarCore.builder(new TestPlatform(), config, Role.SERVER).build();
        core.start();
        waitFor(() -> !server.requestsTo("/heartbeat").isEmpty());
        core.stop();
        String body = server.requestsTo("/heartbeat").get(0).body();
        JsonObject beat = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(
                "Алмаз за голос",
                beat.getAsJsonObject("vote_reward").get("label").getAsString());
        JsonObject bonus = beat.getAsJsonArray("streak_rewards").get(0).getAsJsonObject();
        assertEquals(30, bonus.get("streak").getAsInt());
        assertEquals(30, bonus.get("repeat_every").getAsInt());
        assertEquals(2, bonus.get("commands").getAsInt());
        assertFalse(body.contains("give") || body.contains("crate"), "command text never leaves the server");
        assertTrue(
                core.status().get(3).contains("vote reward, 1 streak bonuses"),
                core.status().toString());
    }

    @Test
    void forwardingProxyReportsNoRewards() throws Exception {
        RadarConfig config =
                TestConfigs.with(TestConfigs.config(server.url(), REWARDS), false, ProxyRewards.FORWARD, SECRET);
        RadarCore core = RadarCore.builder(new TestPlatform(), config, Role.PROXY)
                .transport((p, m) -> true)
                .build();
        assertTrue(core.forwarding().isPresent());
        core.start();
        core.stop();
        JsonObject beat = JsonParser.parseString(
                        server.requestsTo("/heartbeat").get(0).body())
                .getAsJsonObject();
        assertEquals("forward", beat.get("rewards").getAsString());
        assertFalse(beat.has("vote_reward"));
        assertEquals(0, beat.getAsJsonArray("streak_rewards").size());
    }

    @Test
    void rejectedKeyIsLoggedOnce() throws Exception {
        server.respond(
                "POST",
                "/heartbeat",
                FakeApiServer.Response.json(
                        401, "{\"error\":\"Invalid or revoked API key\",\"code\":\"INVALID_API_KEY\"}"));
        RadarCore core = RadarCore.builder(
                        new TestPlatform(), TestConfigs.config(server.url(), RewardPlan.none()), Role.SERVER)
                .build();
        core.start();
        waitFor(() -> !server.requestsTo("/heartbeat").isEmpty());
        core.stop();
        assertEquals(
                1,
                log.lines.stream()
                        .filter(l -> l.contains("rejected the connection"))
                        .count());
        assertTrue(core.status().get(1).contains("INVALID_API_KEY"));
    }

    @Test
    void ipv6ScopeIsStripped() throws Exception {
        assertEquals("fe80:0:0:0:0:0:0:1", RadarCore.ipString(InetAddress.getByName("fe80::1%1")));
        assertEquals("203.0.113.9", RadarCore.ipString(InetAddress.getByName("203.0.113.9")));
    }

    private static void assertMode(RewardMode expected, RadarConfig config, Role role, boolean canForward) {
        assertEquals(
                expected, RadarCore.resolveRewardMode(config, role, canForward), role + " " + config.proxyRewards());
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean());
    }

    private final class TestPlatform implements PlatformAdapter {
        @Override
        public RadarLogger logger() {
            return log;
        }

        @Override
        public Path dataDirectory() {
            return dir;
        }

        @Override
        public ServerSnapshot snapshot() {
            return new ServerSnapshot("paper", "Paper 1.21.4", 1, 20);
        }

        @Override
        public void runOnMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void dispatchConsoleCommand(String command) {}
    }
}
