package org.radarmc.link.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.radarmc.link.core.FakeApiServer;
import org.radarmc.link.core.RadarCore;
import org.radarmc.link.core.RewardMode;
import org.radarmc.link.core.TestConfigs;

class RadarLinkBukkitTest {
    private ServerMock server;
    private RadarLinkBukkit plugin;
    private FakeApiServer api;

    @BeforeEach
    void setUp() throws IOException {
        api = new FakeApiServer();
        server = MockBukkit.mock();
        plugin = MockBukkit.load(RadarLinkBukkit.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        api.close();
    }

    @Test
    void firstStartWritesTheDefaultConfigAndStaysIdle() {
        Path config = plugin.getDataFolder().toPath().resolve("config.yml");
        assertTrue(Files.exists(config));
        assertFalse(plugin.core().isPresent());
        List<String> out = run("status");
        assertTrue(out.get(0).contains("not connected"), out.toString());
    }

    @Test
    void reloadConnectsAndReportsJoins() throws Exception {
        writeConfig("vote-reward:\n" + "  label: \"Награда\"\n" + "  commands: [\"radarlink status\"]\n");
        assertEquals(List.of("RadarLink: reloaded"), run("reload"));
        assertEquals(RewardMode.LOCAL, core().rewardMode());
        waitFor(() -> !api.requestsTo("/heartbeat").isEmpty());

        JsonObject beat = JsonParser.parseString(
                        api.requestsTo("/heartbeat").get(0).body())
                .getAsJsonObject();
        assertEquals("spigot", beat.get("platform").getAsString());
        assertEquals("Награда", beat.getAsJsonObject("vote_reward").get("label").getAsString());

        server.addPlayer("Steve");
        assertTrue(core().online().find("steve").isPresent());
        plugin.onDisable();
        List<FakeApiServer.Request> joins = api.requestsTo("/joins");
        assertEquals(1, joins.size());
        assertTrue(joins.get(0).body().contains("\"nick\":\"Steve\""));
    }

    @Test
    void onlineVoterGetsTheVoteRewardAndTheStreakBonus() throws Exception {
        api.respond(
                "GET",
                "/votes/pending",
                FakeApiServer.Response.json(
                        200,
                        "{\"votes\":[{\"vote_id\":\"v1\",\"minecraft_username\":\"steve\",\"created_at\":\"2026-10-05T10:00:00Z\",\"streak\":2}],\"next_cursor\":\"\"}"));
        writeConfig("vote-reward:\n"
                + "  label: \"Алмаз\"\n"
                + "  commands: [\"radarlink status\"]\n"
                + "streak-rewards:\n"
                + "  - streak: 2\n"
                + "    commands: [\"radarlink status\"]\n");
        server.addPlayer("Steve");
        run("reload");
        waitFor(() -> {
            server.getScheduler().performOneTick();
            return !api.requestsTo("/confirm").isEmpty();
        });
        JsonObject beat = JsonParser.parseString(
                        api.requestsTo("/heartbeat").get(0).body())
                .getAsJsonObject();
        assertEquals("Алмаз", beat.getAsJsonObject("vote_reward").get("label").getAsString());
        assertEquals(
                2,
                beat.getAsJsonArray("streak_rewards")
                        .get(0)
                        .getAsJsonObject()
                        .get("streak")
                        .getAsInt());
        String rewards = Files.readString(plugin.getDataFolder().toPath().resolve("rewards.json"));
        assertTrue(rewards.contains("\"v1\""));
        assertTrue(rewards.contains("\"steve|2\": {"), rewards);
        assertTrue(rewards.contains("\"voted_at\": \"2026-10-05T10:00:00Z\""), rewards);
    }

    @Test
    void radarLink10ConfigIsMigratedAndPaysTheVote() throws Exception {
        api.respond(
                "GET",
                "/votes/pending",
                FakeApiServer.Response.json(
                        200,
                        "{\"votes\":[{\"vote_id\":\"v1\",\"minecraft_username\":\"steve\",\"created_at\":\"2026-10-05T10:00:00Z\",\"streak\":2}],\"next_cursor\":\"\"}"));
        writeConfig("reward-tiers:\n" + "  - streak: 2\n" + "    commands: [\"radarlink status\"]\n");
        server.addPlayer("Steve");
        run("reload");
        waitFor(() -> {
            server.getScheduler().performOneTick();
            return !api.requestsTo("/confirm").isEmpty();
        });
        assertTrue(api.requestsTo("/confirm").get(0).path().endsWith("/votes/v1/confirm"));
        String rewards = Files.readString(plugin.getDataFolder().toPath().resolve("rewards.json"));
        assertTrue(rewards.contains("\"v1\""));
        assertTrue(rewards.contains("\"queued\": []"), rewards);
        Path dir = plugin.getDataFolder().toPath();
        String migrated = Files.readString(dir.resolve("config.yml"));
        assertTrue(migrated.contains("config-version: 2"), migrated);
        assertTrue(migrated.contains("streak-rewards:\n  - streak: 2\n    repeat-every: 1\n"), migrated);
        assertFalse(migrated.contains("reward-tiers"), migrated);
        assertTrue(Files.readString(dir.resolve("config.yml.v1.bak")).contains("reward-tiers"));
        JsonObject beat = JsonParser.parseString(
                        api.requestsTo("/heartbeat").get(0).body())
                .getAsJsonObject();
        assertEquals(
                1,
                beat.getAsJsonArray("streak_rewards")
                        .get(0)
                        .getAsJsonObject()
                        .get("repeat_every")
                        .getAsInt());
    }

    @Test
    void behindProxyListensOnTheRewardChannel() throws Exception {
        writeConfig("behind-proxy: true\nproxy-secret: \"0123456789abcdef\"\n");
        run("reload");
        assertEquals(RewardMode.RECEIVE, core().rewardMode());
        assertTrue(server.getMessenger().isIncomingChannelRegistered(plugin, "radarlink:reward"));
        server.addPlayer("Steve");
        plugin.onDisable();
        assertTrue(api.requestsTo("/joins").isEmpty());
        assertFalse(api.requestsTo("/heartbeat").isEmpty());
    }

    @Test
    @SuppressWarnings("deprecation")
    void descriptorDeclaresNameCommandAliasAndFolia() {
        assertEquals("RadarLink", plugin.getDescription().getName());
        assertEquals("radarlink.admin", plugin.getCommand("radarlink").getPermission());
        assertTrue(plugin.getCommand("radarlink").getAliases().contains("rlink"));
        assertEquals("1.13", plugin.getDescription().getAPIVersion());
    }

    @Test
    void onlinePlayersWorkThroughReflection() {
        server.addPlayer("Steve");
        assertEquals(1, BukkitCompat.onlinePlayers().size());
        assertEquals("spigot", BukkitPlatform.detectPlatform());
    }

    @Test
    void shippedClassesTargetJava8() throws IOException {
        for (Class<?> type :
                new Class<?>[] {RadarLinkBukkit.class, ServerScheduler.FoliaScheduler.class, RadarCore.class}) {
            try (InputStream in = type.getResourceAsStream(
                    type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class")) {
                assertNotNull(in, type.getName());
                byte[] header = in.readNBytes(8);
                assertEquals(52, header[7], type.getName() + " must be Java 8 bytecode");
            }
        }
    }

    private RadarCore core() {
        return plugin.core().orElseThrow();
    }

    private void writeConfig(String extra) throws IOException {
        String yaml = "api-url: \"" + api.url() + "\"\napi-key: \"" + TestConfigs.KEY
                + "\"\nserver-slug: \"my-server\"\n" + extra;
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), yaml);
    }

    private List<String> run(String... args) {
        List<String> out = new ArrayList<>();
        CommandSender sender = server.addPlayer("Admin");
        sender.setOp(true);
        Command command = plugin.getCommand("radarlink");
        plugin.onCommand(sender, command, "radarlink", args);
        PlayerMock admin = (PlayerMock) sender;
        String line;
        while ((line = admin.nextMessage()) != null) {
            out.add(line);
        }
        admin.disconnect();
        return out;
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 200 && !condition.getAsBoolean(); i++) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean());
    }
}
