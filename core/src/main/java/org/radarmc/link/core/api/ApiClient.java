package org.radarmc.link.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.radarmc.link.core.io.TextFiles;
import org.radarmc.link.core.text.Strings;

public final class ApiClient implements VoteApi {
    public static final int MAX_PENDING_PAGE = 100;
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String JSON = "application/json";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(60);
    private static final long MAX_RETRY_AFTER_SECONDS = 3600;
    private static final int MAX_ERROR_LENGTH = 200;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final byte[] EMPTY_BODY = new byte[0];
    private static final int MIN_STATUS = 100;
    private static final int HTTP_UNPROCESSABLE = 422;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final String serverBase;
    private final String apiKey;
    private final String userAgent;
    private final Duration readTimeout;

    public ApiClient(URI apiUrl, String apiKey, String slug, String userAgent, Duration readTimeout) {
        this.serverBase = withoutTrailingSlash(apiUrl.toString()) + "/api/v1/servers/" + encode(slug);
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.readTimeout = readTimeout;
    }

    public void heartbeat(Heartbeat heartbeat) throws ApiException {
        postJson("/heartbeat", heartbeatJson(heartbeat));
    }

    public int sendJoins(String instanceId, List<JoinEntry> joins) throws ApiException {
        JsonObject body = new JsonObject();
        body.addProperty("instance_id", instanceId);
        body.add("joins", joinsJson(joins));
        JsonObject response = object(postJson("/joins", body));
        return intField(response, "accepted", joins.size());
    }

    @Override
    public PendingVotesPage pendingVotes(String cursor, int limit) throws ApiException {
        StringBuilder path =
                new StringBuilder("/votes/pending?limit=").append(Math.max(1, Math.min(MAX_PENDING_PAGE, limit)));
        if (cursor != null && !cursor.isEmpty()) {
            path.append("&cursor=").append(encode(cursor));
        }
        JsonObject response = object(send("GET", path.toString(), null));
        return new PendingVotesPage(votes(response.get("votes")), stringField(response, "next_cursor"));
    }

    @Override
    public void confirmVote(String voteId) throws ApiException {
        send("POST", "/votes/" + encode(voteId) + "/confirm", EMPTY_BODY);
    }

    private static JsonObject heartbeatJson(Heartbeat h) {
        JsonObject body = new JsonObject();
        body.addProperty("instance_id", h.instanceId());
        body.addProperty("platform", h.platform());
        body.addProperty("software", h.software());
        body.addProperty("plugin_version", h.pluginVersion());
        body.addProperty("online_players", h.onlinePlayers());
        body.addProperty("max_players", h.maxPlayers());
        body.addProperty("behind_proxy", h.behindProxy());
        body.addProperty("send_joins", h.sendJoins());
        body.addProperty("floodgate", h.floodgate());
        body.addProperty("rewards", h.rewards());
        body.addProperty("offline_rewards", h.offlineRewards());
        h.voteReward().ifPresent(vote -> body.add("vote_reward", voteRewardJson(vote)));
        body.add("streak_rewards", streakRewardsJson(h.streakRewards()));
        body.addProperty("stopping", h.stopping());
        return body;
    }

    private static JsonObject voteRewardJson(VoteRewardSummary vote) {
        JsonObject item = new JsonObject();
        item.addProperty("commands", vote.commands());
        item.addProperty("label", vote.label());
        return item;
    }

    private static JsonArray streakRewardsJson(List<StreakRewardSummary> bonuses) {
        JsonArray items = new JsonArray();
        for (StreakRewardSummary bonus : bonuses) {
            JsonObject item = new JsonObject();
            item.addProperty("streak", bonus.streak());
            if (bonus.repeatEvery() > 0) {
                item.addProperty("repeat_every", bonus.repeatEvery());
            }
            item.addProperty("commands", bonus.commands());
            item.addProperty("label", bonus.label());
            items.add(item);
        }
        return items;
    }

    private static JsonArray joinsJson(List<JoinEntry> joins) {
        JsonArray items = new JsonArray();
        for (JoinEntry join : joins) {
            JsonObject item = new JsonObject();
            item.addProperty("uuid", join.uuid().toString());
            item.addProperty("nick", join.nick());
            item.addProperty("ip", join.ip());
            item.addProperty("bedrock", join.bedrock());
            item.addProperty("at", join.at().toString());
            items.add(item);
        }
        return items;
    }

    private static List<PendingVote> votes(JsonElement raw) {
        List<PendingVote> votes = new ArrayList<>();
        if (raw == null || !raw.isJsonArray()) {
            return votes;
        }
        for (JsonElement item : raw.getAsJsonArray()) {
            parseVote(item).ifPresent(votes::add);
        }
        return votes;
    }

    private static Optional<PendingVote> parseVote(JsonElement item) {
        if (!item.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject o = item.getAsJsonObject();
        String id = stringField(o, "vote_id");
        String nick = stringField(o, "minecraft_username");
        if (id == null || id.isEmpty() || nick == null) {
            return Optional.empty();
        }
        int streak = Math.max(1, intField(o, "streak", 1));
        return Optional.of(new PendingVote(id, nick, streak, instantField(o, "created_at")));
    }

    private static Instant instantField(JsonObject o, String name) {
        String raw = stringField(o, name);
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String postJson(String path, JsonObject body) throws ApiException {
        return send("POST", path, body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String send(String method, String path, byte[] body) throws ApiException {
        try {
            HttpURLConnection http = open(method, path);
            if (body != null) {
                writeBody(http, body);
            }
            int status = http.getResponseCode();
            if (status < MIN_STATUS) {
                throw new IOException("malformed HTTP response");
            }
            String text = readBody(http, status);
            if (status >= HttpURLConnection.HTTP_OK && status < HttpURLConnection.HTTP_MULT_CHOICE) {
                return text;
            }
            throw failure(status, text, http.getHeaderField("Retry-After"));
        } catch (IOException e) {
            throw new ApiException(
                    ApiException.Kind.NETWORK, 0, null, e.getClass().getSimpleName(), null, e);
        }
    }

    private HttpURLConnection open(String method, String path) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(serverBase + path).openConnection();
        http.setInstanceFollowRedirects(false);
        http.setUseCaches(false);
        http.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        http.setReadTimeout((int) readTimeout.toMillis());
        http.setRequestMethod(method);
        http.setRequestProperty(API_KEY_HEADER, apiKey);
        http.setRequestProperty("Accept", JSON);
        http.setRequestProperty("User-Agent", userAgent);
        return http;
    }

    private static void writeBody(HttpURLConnection http, byte[] body) throws IOException {
        if (body.length > 0) {
            http.setRequestProperty("Content-Type", JSON);
        }
        http.setDoOutput(true);
        try (OutputStream out = http.getOutputStream()) {
            out.write(body);
        }
    }

    private static String readBody(HttpURLConnection http, int status) throws IOException {
        InputStream in;
        try {
            in = status >= HttpURLConnection.HTTP_BAD_REQUEST ? http.getErrorStream() : http.getInputStream();
        } catch (IOException e) {
            in = http.getErrorStream();
        }
        if (in == null) {
            return "";
        }
        try (InputStream body = in) {
            return TextFiles.readUtf8(body, MAX_RESPONSE_BYTES);
        }
    }

    private static ApiException failure(int status, String body, String retryAfterHeader) {
        JsonObject parsed = objectOrEmpty(body);
        String message = sanitize(stringField(parsed, "error"));
        String code = sanitize(stringField(parsed, "code"));
        ApiException.Kind kind = kindOf(status);
        Duration retry = kind == ApiException.Kind.RATE_LIMITED ? retryAfter(retryAfterHeader) : null;
        return new ApiException(kind, status, code, message, retry, null);
    }

    private static ApiException.Kind kindOf(int status) {
        switch (status) {
            case HttpURLConnection.HTTP_BAD_REQUEST:
            case HttpURLConnection.HTTP_ENTITY_TOO_LARGE:
            case HTTP_UNPROCESSABLE:
                return ApiException.Kind.BAD_REQUEST;
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                return ApiException.Kind.UNAUTHORIZED;
            case HttpURLConnection.HTTP_FORBIDDEN:
                return ApiException.Kind.FORBIDDEN;
            case HttpURLConnection.HTTP_NOT_FOUND:
                return ApiException.Kind.NOT_FOUND;
            case HTTP_TOO_MANY_REQUESTS:
                return ApiException.Kind.RATE_LIMITED;
            default:
                return status >= HttpURLConnection.HTTP_INTERNAL_ERROR
                        ? ApiException.Kind.SERVER
                        : ApiException.Kind.BAD_REQUEST;
        }
    }

    private static Duration retryAfter(String header) {
        if (header == null) {
            return DEFAULT_RETRY_AFTER;
        }
        try {
            long seconds = Long.parseLong(Strings.strip(header));
            return Duration.ofSeconds(Math.max(1, Math.min(MAX_RETRY_AFTER_SECONDS, seconds)));
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER;
        }
    }

    private static JsonObject object(String body) throws ApiException {
        if (Strings.isBlank(body)) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (JsonParseException e) {
            throw new ApiException(ApiException.Kind.SERVER, 0, null, "unexpected response from the API", null, e);
        }
    }

    private static JsonObject objectOrEmpty(String body) {
        try {
            return object(body);
        } catch (ApiException e) {
            return new JsonObject();
        }
    }

    private static String stringField(JsonObject o, String name) {
        JsonElement e = o.get(name);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static int intField(JsonObject o, String name, int fallback) {
        JsonElement e = o.get(name);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return e.getAsInt();
    }

    private static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        return Strings.truncate(Strings.strip(text.replaceAll("\\p{Cntrl}", " ")), MAX_ERROR_LENGTH);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is always supported", e);
        }
    }

    private static String withoutTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
