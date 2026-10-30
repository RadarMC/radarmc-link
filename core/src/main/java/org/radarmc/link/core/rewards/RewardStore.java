package org.radarmc.link.core.rewards;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.radarmc.link.core.RadarLogger;
import org.radarmc.link.core.io.TextFiles;

public final class RewardStore implements BonusGuard {
    public static final String FILE_NAME = "rewards.json";
    private static final int FORMAT_VERSION = 1;
    private static final Duration HANDLED_RETENTION = Duration.ofDays(35);
    private static final Duration QUEUE_RETENTION = Duration.ofDays(30);
    private static final Duration BONUS_WINDOW = Duration.ofHours(24);
    private static final int MAX_QUEUED = 10_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Clock clock;
    private final RadarLogger log;
    private final List<Reward> queued = new ArrayList<>();
    private final Map<String, Instant> handled = new LinkedHashMap<>();
    private final Map<String, BonusClaim> bonuses = new LinkedHashMap<>();

    private RewardStore(Path file, Clock clock, RadarLogger log) {
        this.file = file;
        this.clock = clock;
        this.log = log;
    }

    public static RewardStore load(Path dataDirectory, Clock clock, RadarLogger log) {
        RewardStore store = new RewardStore(dataDirectory.resolve(FILE_NAME), clock, log);
        store.read();
        return store;
    }

    public synchronized boolean isHandled(String voteId) {
        return handled.containsKey(voteId);
    }

    public synchronized boolean isQueued(String voteId) {
        return queued.stream().anyMatch(reward -> reward.voteId().equals(voteId));
    }

    public synchronized void markHandled(String voteId) {
        handled.put(voteId, clock.instant());
        save();
    }

    public synchronized boolean enqueue(Reward reward) {
        if (isQueued(reward.voteId()) || handled.containsKey(reward.voteId())) {
            return true;
        }
        if (queued.size() >= MAX_QUEUED) {
            log.warn("The offline reward queue is full (" + MAX_QUEUED + "); vote " + reward.voteId()
                    + " stays pending on the site");
            return false;
        }
        queued.add(reward);
        save();
        return true;
    }

    public synchronized List<Reward> queuedFor(Predicate<String> nickMatches) {
        List<Reward> matching = new ArrayList<>();
        for (Reward reward : queued) {
            if (nickMatches.test(reward.nick())) {
                matching.add(reward);
            }
        }
        return matching;
    }

    public synchronized void complete(String voteId) {
        queued.removeIf(reward -> reward.voteId().equals(voteId));
        handled.put(voteId, clock.instant());
        save();
    }

    @Override
    public synchronized boolean claim(String player, int streak, Instant votedAt, String voteId) {
        String key = player.toLowerCase(Locale.ROOT) + "|" + streak;
        BonusClaim previous = bonuses.get(key);
        if (previous != null && previous.voteId.equals(voteId)) {
            return true;
        }
        if (previous != null
                && Duration.between(previous.votedAt, votedAt).abs().compareTo(BONUS_WINDOW) < 0) {
            return false;
        }
        bonuses.put(key, new BonusClaim(votedAt, voteId));
        save();
        return true;
    }

    public synchronized int queuedCount() {
        return queued.size();
    }

    public synchronized void prune() {
        Instant now = clock.instant();
        Instant handledCutoff = now.minus(HANDLED_RETENTION);
        boolean changed = handled.values().removeIf(at -> at.isBefore(handledCutoff));
        changed |= bonuses.values().removeIf(claim -> claim.votedAt.isBefore(handledCutoff));
        changed |= dropExpiredQueue(now.minus(QUEUE_RETENTION));
        if (changed) {
            save();
        }
    }

    private boolean dropExpiredQueue(Instant cutoff) {
        boolean dropped = false;
        Iterator<Reward> it = queued.iterator();
        while (it.hasNext()) {
            Reward reward = it.next();
            if (reward.queuedAt().isBefore(cutoff)) {
                log.warn("Dropping the reward for vote " + reward.voteId() + " by " + reward.nick()
                        + ": the player did not join for " + QUEUE_RETENTION.toDays() + " days");
                it.remove();
                dropped = true;
            }
        }
        return dropped;
    }

    private void read() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(TextFiles.readUtf8(file)).getAsJsonObject();
            readQueued(root.getAsJsonArray("queued"));
            readHandled(root.getAsJsonObject("handled"));
            readBonuses(root.getAsJsonObject("bonuses"));
        } catch (IOException | RuntimeException e) {
            moveAside(e);
        }
    }

    private void moveAside(Exception cause) {
        Path broken = file.resolveSibling(FILE_NAME + ".broken-" + clock.millis());
        log.error(
                "Cannot read " + FILE_NAME + ", starting with an empty reward queue; the old file is kept as "
                        + broken.getFileName(),
                cause);
        queued.clear();
        handled.clear();
        bonuses.clear();
        try {
            Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Cannot move the unreadable " + FILE_NAME + " aside", e);
        }
    }

    private void readQueued(JsonArray items) {
        if (items == null) {
            return;
        }
        for (JsonElement item : items) {
            JsonObject o = item.getAsJsonObject();
            Instant queuedAt = Instant.parse(o.get("queued_at").getAsString());
            JsonElement votedAt = o.get("voted_at");
            queued.add(new Reward(
                    o.get("vote_id").getAsString(),
                    o.get("nick").getAsString(),
                    o.get("streak").getAsInt(),
                    queuedAt,
                    votedAt == null || votedAt.isJsonNull() ? queuedAt : Instant.parse(votedAt.getAsString())));
        }
    }

    private void readHandled(JsonObject items) {
        if (items == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : items.entrySet()) {
            handled.put(e.getKey(), Instant.parse(e.getValue().getAsString()));
        }
    }

    private void readBonuses(JsonObject items) {
        if (items == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : items.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            bonuses.put(
                    e.getKey(),
                    new BonusClaim(
                            Instant.parse(o.get("voted_at").getAsString()),
                            o.get("vote_id").getAsString()));
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.add("queued", queuedJson());
        root.add("handled", handledJson());
        root.add("bonuses", bonusesJson());
        write(GSON.toJson(root));
    }

    private JsonArray queuedJson() {
        JsonArray items = new JsonArray();
        for (Reward reward : queued) {
            JsonObject o = new JsonObject();
            o.addProperty("vote_id", reward.voteId());
            o.addProperty("nick", reward.nick());
            o.addProperty("streak", reward.streak());
            o.addProperty("queued_at", reward.queuedAt().toString());
            o.addProperty("voted_at", reward.votedAt().toString());
            items.add(o);
        }
        return items;
    }

    private JsonObject handledJson() {
        JsonObject items = new JsonObject();
        handled.forEach((voteId, at) -> items.addProperty(voteId, at.toString()));
        return items;
    }

    private JsonObject bonusesJson() {
        JsonObject items = new JsonObject();
        bonuses.forEach((key, claim) -> {
            JsonObject o = new JsonObject();
            o.addProperty("voted_at", claim.votedAt.toString());
            o.addProperty("vote_id", claim.voteId);
            items.add(key, o);
        });
        return items;
    }

    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            TextFiles.writeUtf8Atomically(file, json);
        } catch (IOException e) {
            log.error("Cannot save " + FILE_NAME, e);
        }
    }

    private static final class BonusClaim {
        final Instant votedAt;
        final String voteId;

        BonusClaim(Instant votedAt, String voteId) {
            this.votedAt = votedAt;
            this.voteId = voteId;
        }
    }
}
