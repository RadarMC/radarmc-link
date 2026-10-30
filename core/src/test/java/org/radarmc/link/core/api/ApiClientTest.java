package org.radarmc.link.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.radarmc.link.core.FakeApiServer;
import org.radarmc.link.core.FakeApiServer.Response;
import org.radarmc.link.core.TestConfigs;

class ApiClientTest {
    private FakeApiServer server;
    private ApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeApiServer();
        client = new ApiClient(
                server.url(), TestConfigs.KEY, "my-server", "RadarLink/test (paper)", Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void heartbeatSendsKeyAndBody() throws ApiException {
        client.heartbeat(new Heartbeat(
                "inst-1",
                "paper",
                "Paper 1.21.4",
                "1.1.1",
                3,
                100,
                false,
                true,
                true,
                "local",
                true,
                null,
                Collections.<StreakRewardSummary>emptyList(),
                false));
        FakeApiServer.Request req = server.requestsTo("/heartbeat").get(0);
        assertEquals("POST", req.method());
        assertEquals("/api/v1/servers/my-server/heartbeat", req.path());
        assertEquals(TestConfigs.KEY, req.header("X-API-Key"));
        assertEquals("application/json", req.header("Content-Type"));
        assertEquals("RadarLink/test (paper)", req.header("User-Agent"));
        JsonObject body = JsonParser.parseString(req.body()).getAsJsonObject();
        assertEquals("inst-1", body.get("instance_id").getAsString());
        assertEquals("paper", body.get("platform").getAsString());
        assertEquals("Paper 1.21.4", body.get("software").getAsString());
        assertEquals(3, body.get("online_players").getAsInt());
        assertEquals("local", body.get("rewards").getAsString());
        assertFalse(body.has("tiers"));
        assertFalse(body.has("vote_reward"));
        assertEquals(0, body.getAsJsonArray("streak_rewards").size());
        assertFalse(body.get("stopping").getAsBoolean());
    }

    @Test
    void heartbeatSendsSplitRewards() throws ApiException {
        client.heartbeat(new Heartbeat(
                "inst-1",
                "paper",
                "Paper 1.21.4",
                "1.1.0",
                0,
                100,
                false,
                true,
                false,
                "local",
                true,
                new VoteRewardSummary(1, "Алмаз за голос"),
                Arrays.asList(new StreakRewardSummary(7, 0, 2, null), new StreakRewardSummary(30, 30, 1, "Ключ")),
                false));
        JsonObject body = JsonParser.parseString(
                        server.requestsTo("/heartbeat").get(0).body())
                .getAsJsonObject();
        JsonObject vote = body.getAsJsonObject("vote_reward");
        assertEquals(1, vote.get("commands").getAsInt());
        assertEquals("Алмаз за голос", vote.get("label").getAsString());
        JsonObject week = body.getAsJsonArray("streak_rewards").get(0).getAsJsonObject();
        assertEquals(7, week.get("streak").getAsInt());
        assertFalse(week.has("repeat_every"));
        assertEquals(2, week.get("commands").getAsInt());
        JsonObject month = body.getAsJsonArray("streak_rewards").get(1).getAsJsonObject();
        assertEquals(30, month.get("repeat_every").getAsInt());
        assertEquals("Ключ", month.get("label").getAsString());
    }

    @Test
    void joinsAreSentAsOneBatch() throws ApiException {
        server.respond("POST", "/joins", Response.json(200, "{\"accepted\":1,\"ignored\":1}"));
        UUID id = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
        int accepted = client.sendJoins(
                "inst-1",
                Arrays.asList(
                        new JoinEntry(id, "Steve", "203.0.113.7", false, Instant.parse("2026-10-05T12:00:00Z")),
                        new JoinEntry(id, ".Gamer", "2001:db8::1", true, Instant.parse("2026-10-05T12:00:01Z"))));
        assertEquals(1, accepted);
        JsonObject body = JsonParser.parseString(
                        server.requestsTo("/joins").get(0).body())
                .getAsJsonObject();
        assertEquals("inst-1", body.get("instance_id").getAsString());
        JsonObject first = body.getAsJsonArray("joins").get(0).getAsJsonObject();
        assertEquals(id.toString(), first.get("uuid").getAsString());
        assertEquals("Steve", first.get("nick").getAsString());
        assertEquals("203.0.113.7", first.get("ip").getAsString());
        assertFalse(first.get("bedrock").getAsBoolean());
        assertEquals("2026-10-05T12:00:00Z", first.get("at").getAsString());
        assertTrue(body.getAsJsonArray("joins")
                .get(1)
                .getAsJsonObject()
                .get("bedrock")
                .getAsBoolean());
    }

    @Test
    void pendingVotesParseStreakAndCursor() throws ApiException {
        server.respond(
                "GET",
                "/votes/pending",
                Response.json(
                        200,
                        "{\"votes\":[\n"
                                + "  {\"vote_id\":\"v1\",\"minecraft_username\":\"Steve\",\"created_at\":\"2026-10-05T10:00:00Z\",\"streak\":4},\n"
                                + "  {\"vote_id\":\"v2\",\"minecraft_username\":\"Alex\",\"created_at\":\"bad\"},\n"
                                + "  {\"minecraft_username\":\"NoId\"},\n"
                                + "  \"junk\"\n"
                                + "],\"next_cursor\":\"abc==\"}\n"));
        PendingVotesPage page = client.pendingVotes("prev cursor", 500);
        assertEquals(2, page.votes().size());
        assertEquals(
                new PendingVote("v1", "Steve", 4, Instant.parse("2026-10-05T10:00:00Z")),
                page.votes().get(0));
        assertEquals(1, page.votes().get(1).streak());
        assertFalse(page.votes().get(1).createdAt().isPresent());
        assertEquals("abc==", page.nextCursor().get());
        FakeApiServer.Request req = server.requestsTo("/votes/pending").get(0);
        assertEquals("limit=100&cursor=prev+cursor", req.query());
    }

    @Test
    void emptyCursorMeansLastPage() throws ApiException {
        assertFalse(client.pendingVotes(null, 10).nextCursor().isPresent());
        assertEquals("limit=10", server.requestsTo("/votes/pending").get(0).query());
    }

    @Test
    void confirmPostsToTheVote() throws ApiException {
        client.confirmVote("3f1c1f7e-0000-4000-8000-000000000001");
        FakeApiServer.Request req = server.requestsTo("/confirm").get(0);
        assertEquals("POST", req.method());
        assertEquals("/api/v1/servers/my-server/votes/3f1c1f7e-0000-4000-8000-000000000001/confirm", req.path());
    }

    @Test
    void errorsAreClassified() {
        server.respond(
                "POST",
                "/heartbeat",
                Response.json(401, "{\"error\":\"Invalid or revoked API key\",\"code\":\"INVALID_API_KEY\"}"));
        ApiException e = assertThrows(ApiException.class, () -> client.heartbeat(sample()));
        assertEquals(ApiException.Kind.UNAUTHORIZED, e.kind());
        assertEquals("INVALID_API_KEY", e.code());
        assertFalse(e.transientFailure());
        assertTrue(e.describe().contains("401"));

        server.respond(
                "POST",
                "/heartbeat",
                new Response(
                        429,
                        "{\"error\":\"Rate limit exceeded\",\"code\":\"RATE_LIMITED\"}",
                        Collections.singletonMap("Retry-After", "17")));
        e = assertThrows(ApiException.class, () -> client.heartbeat(sample()));
        assertEquals(ApiException.Kind.RATE_LIMITED, e.kind());
        assertEquals(Duration.ofSeconds(17), e.retryAfter());
        assertTrue(e.transientFailure());

        server.respond("POST", "/heartbeat", Response.json(503, "<html>oops</html>"));
        e = assertThrows(ApiException.class, () -> client.heartbeat(sample()));
        assertEquals(ApiException.Kind.SERVER, e.kind());

        server.respond("POST", "/heartbeat", Response.json(404, "{\"error\":\"server not found\"}"));
        assertEquals(
                ApiException.Kind.NOT_FOUND,
                assertThrows(ApiException.class, () -> client.heartbeat(sample()))
                        .kind());

        server.respond("POST", "/heartbeat", Response.json(400, "{\"error\":\"bad\\u0007thing\"}"));
        e = assertThrows(ApiException.class, () -> client.heartbeat(sample()));
        assertEquals(ApiException.Kind.BAD_REQUEST, e.kind());
        assertEquals("bad thing", e.getMessage());
    }

    @Test
    void redirectsAreNotFollowed() {
        server.respond(
                "POST",
                "/heartbeat",
                new Response(302, "", Collections.singletonMap("Location", "https://evil.example/steal")));
        ApiException e = assertThrows(ApiException.class, () -> client.heartbeat(sample()));
        assertEquals(302, e.status());
        assertEquals(1, server.requests.size());
    }

    @Test
    void networkErrorsAreTransient() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        ApiClient offline = new ApiClient(
                URI.create("http://127.0.0.1:" + port), TestConfigs.KEY, "s", "ua", Duration.ofSeconds(2));
        ApiException e = assertThrows(ApiException.class, () -> offline.confirmVote("v"));
        assertEquals(ApiException.Kind.NETWORK, e.kind());
        assertTrue(e.transientFailure());
    }

    @Test
    void joinEntryNeverPrintsTheIp() {
        JoinEntry entry = new JoinEntry(UUID.randomUUID(), "Steve", "203.0.113.7", false, Instant.EPOCH);
        assertFalse(entry.toString().contains("203.0.113.7"));
    }

    private static Heartbeat sample() {
        return new Heartbeat(
                "i",
                "paper",
                "Paper",
                "1.0.0",
                0,
                0,
                false,
                true,
                false,
                "off",
                true,
                null,
                Collections.<StreakRewardSummary>emptyList(),
                false);
    }
}
