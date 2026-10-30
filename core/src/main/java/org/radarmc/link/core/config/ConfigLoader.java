package org.radarmc.link.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.radarmc.link.core.io.TextFiles;
import org.radarmc.link.core.rewards.RewardPlan;
import org.radarmc.link.core.rewards.StreakReward;
import org.radarmc.link.core.rewards.VoteReward;
import org.radarmc.link.core.text.Strings;
import org.yaml.snakeyaml.error.YAMLException;

final class ConfigLoader {
    static final String DEFAULT_API_URL = "https://api.radarmc.org";
    static final int MIN_PROXY_SECRET_LENGTH = 16;
    static final int MAX_LABEL_LENGTH = 80;
    private static final int DEFAULT_POLL_SECONDS = 60;
    private static final int MIN_POLL_SECONDS = 30;
    private static final int MAX_POLL_SECONDS = 3600;
    private static final int MAX_STREAK_REWARDS = 20;
    private static final int MAX_COMMANDS = 20;
    private static final int MAX_COMMAND_LENGTH = 256;
    private static final int MAX_STREAK = 3650;
    private static final int MIN_SLUG_LENGTH = 3;
    private static final int MAX_SLUG_LENGTH = 96;
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final Pattern API_KEY = Pattern.compile("^rmc_[A-Za-z0-9]{32}$");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final Map<?, ?> root;

    private ConfigLoader(Map<?, ?> root) {
        this.root = root;
    }

    static ConfigResult load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(TextFiles.readUtf8(in, MAX_FILE_BYTES));
        } catch (IOException e) {
            return ConfigResult.failed("cannot read " + file.getFileName() + ": " + e.getMessage());
        }
    }

    static ConfigResult parse(String yaml) {
        Object loaded;
        try {
            loaded = SafeYaml.load(yaml);
        } catch (YAMLException e) {
            return ConfigResult.failed("config.yml is not valid YAML: " + firstLine(e.getMessage()));
        }
        if (loaded != null && !(loaded instanceof Map<?, ?>)) {
            return ConfigResult.failed("config.yml must be a map of settings");
        }
        ConfigLoader loader = new ConfigLoader(loaded == null ? Collections.emptyMap() : (Map<?, ?>) loaded);
        RadarConfig config = loader.build();
        return new ConfigResult(config, loader.warnings, loader.errors);
    }

    private RadarConfig build() {
        return new RadarConfig(
                apiUrl(),
                apiKey(),
                serverSlug(),
                bool("send-joins", true),
                bool("behind-proxy", false),
                pollInterval(),
                bool("offline-rewards", true),
                proxyRewards(),
                proxySecret(),
                rewards());
    }

    private URI apiUrl() {
        String raw = Strings.strip(string("api-url", DEFAULT_API_URL));
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        try {
            return validApiUrl(new URI(raw));
        } catch (URISyntaxException e) {
            errors.add("api-url is not a valid URL");
            return URI.create(DEFAULT_API_URL);
        }
    }

    private URI validApiUrl(URI uri) {
        if (uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            errors.add("api-url must look like https://api.radarmc.org");
            return URI.create(DEFAULT_API_URL);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("http")) {
            warnings.add("api-url uses plain http: the API key travels unencrypted. Use https outside local testing.");
        } else if (!scheme.equals("https")) {
            errors.add("api-url must start with https://");
            return URI.create(DEFAULT_API_URL);
        }
        return uri;
    }

    private String apiKey() {
        String key = Strings.strip(string("api-key", ""));
        if (key.isEmpty()) {
            errors.add("api-key is empty: create a key on the «API keys» tab of your server dashboard and paste it"
                    + " into config.yml");
        } else if (!API_KEY.matcher(key).matches()) {
            errors.add("api-key does not look like a RadarMC key (rmc_ followed by 32 letters and digits)");
        }
        return key;
    }

    private String serverSlug() {
        String slug = Strings.strip(string("server-slug", "")).toLowerCase(Locale.ROOT);
        if (slug.isEmpty()) {
            errors.add("server-slug is empty: use the last part of your server page address,"
                    + " radarmc.org/servers/<slug>");
        } else if (slug.length() < MIN_SLUG_LENGTH
                || slug.length() > MAX_SLUG_LENGTH
                || !SLUG.matcher(slug).matches()) {
            errors.add("server-slug may contain only latin letters, digits and single hyphens (" + MIN_SLUG_LENGTH + "-"
                    + MAX_SLUG_LENGTH + " characters)");
        }
        return slug;
    }

    private Duration pollInterval() {
        int seconds = integer("vote-poll-interval", DEFAULT_POLL_SECONDS);
        if (seconds >= MIN_POLL_SECONDS && seconds <= MAX_POLL_SECONDS) {
            return Duration.ofSeconds(seconds);
        }
        int clamped = Math.max(MIN_POLL_SECONDS, Math.min(MAX_POLL_SECONDS, seconds));
        warnings.add("vote-poll-interval must be between " + MIN_POLL_SECONDS + " and " + MAX_POLL_SECONDS
                + " seconds; using " + clamped);
        return Duration.ofSeconds(clamped);
    }

    private ProxyRewards proxyRewards() {
        String raw = Strings.strip(string("proxy-rewards", "forward")).toUpperCase(Locale.ROOT);
        try {
            return ProxyRewards.valueOf(raw);
        } catch (IllegalArgumentException e) {
            warnings.add("proxy-rewards must be forward, commands or off; using forward");
            return ProxyRewards.FORWARD;
        }
    }

    private String proxySecret() {
        String secret = Strings.strip(string("proxy-secret", ""));
        if (!secret.isEmpty() && secret.length() < MIN_PROXY_SECRET_LENGTH) {
            warnings.add("proxy-secret must be at least " + MIN_PROXY_SECRET_LENGTH
                    + " characters; forwarded rewards stay off");
            return "";
        }
        return secret;
    }

    private RewardPlan rewards() {
        if (root.containsKey("reward-tiers")) {
            warnings.add("reward-tiers is not a RadarLink setting; use vote-reward and streak-rewards");
        }
        return RewardPlan.split(voteReward().orElse(null), streakRewards());
    }

    private Optional<VoteReward> voteReward() {
        Object raw = root.get("vote-reward");
        if (raw == null) {
            return Optional.empty();
        }
        if (!(raw instanceof Map<?, ?>)) {
            warnings.add("vote-reward must have commands; skipped");
            return Optional.empty();
        }
        Map<?, ?> map = (Map<?, ?>) raw;
        List<String> commands = commands("vote-reward", map.get("commands"));
        if (commands.isEmpty()) {
            warnings.add("vote-reward: no commands; skipped");
            return Optional.empty();
        }
        return Optional.of(new VoteReward(commands, label("vote-reward", map.get("label"))));
    }

    private List<StreakReward> streakRewards() {
        String key = "streak-rewards";
        Object raw = root.get(key);
        if (raw == null) {
            return Collections.emptyList();
        }
        if (!(raw instanceof List<?>)) {
            warnings.add(key + " must be a list; it is ignored");
            return Collections.emptyList();
        }
        List<?> items = (List<?>) raw;
        List<StreakReward> bonuses = new ArrayList<>();
        Set<Integer> streaks = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            String where = key + " #" + (i + 1);
            Optional<StreakReward> bonus = streakReward(where, items.get(i));
            if (!bonus.isPresent()) {
                continue;
            }
            if (!streaks.add(bonus.get().streak())) {
                warnings.add(where + ": another bonus already uses streak "
                        + bonus.get().streak() + "; skipped");
                continue;
            }
            if (bonuses.size() == MAX_STREAK_REWARDS) {
                warnings.add(key + ": only the first " + MAX_STREAK_REWARDS + " entries are used");
                break;
            }
            bonuses.add(bonus.get());
        }
        return bonuses;
    }

    private Optional<StreakReward> streakReward(String where, Object raw) {
        if (!(raw instanceof Map<?, ?>)) {
            warnings.add(where + " must have streak and commands; skipped");
            return Optional.empty();
        }
        Map<?, ?> map = (Map<?, ?>) raw;
        Optional<Integer> streak = streakValue(map.get("streak"));
        if (!streak.isPresent()) {
            warnings.add(where + ": streak must be a whole number from 1 to " + MAX_STREAK + "; skipped");
            return Optional.empty();
        }
        Optional<Integer> repeatEvery = repeatEvery(where, map.get("repeat-every"));
        if (!repeatEvery.isPresent()) {
            return Optional.empty();
        }
        List<String> commands = commands(where, map.get("commands"));
        if (commands.isEmpty()) {
            warnings.add(where + ": no commands; skipped");
            return Optional.empty();
        }
        return Optional.of(new StreakReward(streak.get(), repeatEvery.get(), commands, label(where, map.get("label"))));
    }

    private Optional<Integer> repeatEvery(String where, Object raw) {
        if (raw == null || (isWholeNumber(raw) && ((Number) raw).longValue() == 0)) {
            return Optional.of(StreakReward.NO_REPEAT);
        }
        Optional<Integer> every = streakValue(raw);
        if (!every.isPresent()) {
            warnings.add(where + ": repeat-every must be a whole number from 1 to " + MAX_STREAK + "; skipped");
        }
        return every;
    }

    private static Optional<Integer> streakValue(Object raw) {
        if (!isWholeNumber(raw)) {
            return Optional.empty();
        }
        long value = ((Number) raw).longValue();
        return value < 1 || value > MAX_STREAK ? Optional.empty() : Optional.of((int) value);
    }

    private static boolean isWholeNumber(Object raw) {
        return raw instanceof Integer || raw instanceof Long;
    }

    private List<String> commands(String where, Object raw) {
        List<String> commands = new ArrayList<>();
        for (Object item : asList(raw)) {
            if (!(item instanceof String) || Strings.isBlank((String) item)) {
                continue;
            }
            String command = withoutSlash(Strings.strip((String) item));
            if (command.length() > MAX_COMMAND_LENGTH || command.contains("\n") || command.contains("\r")) {
                warnings.add(where + ": a command longer than " + MAX_COMMAND_LENGTH
                        + " characters or with a line break was skipped");
                continue;
            }
            if (commands.size() == MAX_COMMANDS) {
                warnings.add(where + ": only the first " + MAX_COMMANDS + " commands are used");
                break;
            }
            commands.add(command);
        }
        return commands;
    }

    private static List<?> asList(Object raw) {
        if (raw instanceof List<?>) {
            return (List<?>) raw;
        }
        return raw == null ? Collections.emptyList() : Collections.singletonList(raw);
    }

    private static String withoutSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private String label(String where, Object raw) {
        if (!(raw instanceof String) || Strings.isBlank((String) raw)) {
            return null;
        }
        String label = singleLine((String) raw);
        if (label.isEmpty()) {
            return null;
        }
        if (label.codePointCount(0, label.length()) > MAX_LABEL_LENGTH) {
            warnings.add(where + ": label is longer than " + MAX_LABEL_LENGTH + " characters and was cut");
            label = Strings.strip(label.substring(0, label.offsetByCodePoints(0, MAX_LABEL_LENGTH)));
        }
        return label;
    }

    static String singleLine(String raw) {
        StringBuilder out = new StringBuilder();
        boolean gap = false;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (isGap(cp)) {
                gap = true;
            } else if (!isDropped(cp)) {
                if (gap && out.length() > 0) {
                    out.append(' ');
                }
                gap = false;
                out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    private static boolean isGap(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == '\u0085' || cp == ' ' || cp == ' ';
    }

    private static boolean isDropped(int cp) {
        return Character.isISOControl(cp)
                || (cp >= '‪' && cp <= '‮')
                || (cp >= '⁦' && cp <= '⁩')
                || (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
    }

    private String string(String key, String fallback) {
        Object value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        warnings.add(key + " must be text; using the default");
        return fallback;
    }

    private boolean bool(String key, boolean fallback) {
        Object value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String && isBooleanText((String) value)) {
            return Boolean.parseBoolean((String) value);
        }
        warnings.add(key + " must be true or false; using " + fallback);
        return fallback;
    }

    private static boolean isBooleanText(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
    }

    private int integer(String key, int fallback) {
        Object value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (isWholeNumber(value)) {
            long number = ((Number) value).longValue();
            return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, number));
        }
        warnings.add(key + " must be a whole number; using " + fallback);
        return fallback;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
