package org.radarmc.link.core.config;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.error.YAMLException;

final class ConfigMigrator {
    static final String VERSION_KEY = "config-version";
    static final int CURRENT_VERSION = 2;
    private static final int UNVERSIONED = 1;
    private static final List<Step> STEPS = Collections.<Step>singletonList(new RewardTiersStep());

    interface Step {
        int from();

        void apply(Map<?, ?> root, Rewrite rewrite);
    }

    static final class Rewrite {
        final Map<String, List<String>> bodies = new HashMap<>();
        final Set<String> removed = new HashSet<>();
        final List<String> notes = new ArrayList<>();
    }

    static final class Migration {
        private final int fromVersion;
        private final String text;
        private final List<String> notes;

        Migration(int fromVersion, String text, List<String> notes) {
            this.fromVersion = fromVersion;
            this.text = text;
            this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
        }

        int fromVersion() {
            return fromVersion;
        }

        String text() {
            return text;
        }

        List<String> notes() {
            return notes;
        }
    }

    private ConfigMigrator() {}

    static Optional<Migration> migrate(String text, String defaults) {
        Optional<Map<?, ?>> root = load(text);
        Optional<Map<?, ?>> defaultRoot = load(defaults);
        if (!root.isPresent() || !defaultRoot.isPresent()) {
            return Optional.empty();
        }
        int version = version(root.get());
        if (version >= CURRENT_VERSION) {
            return Optional.empty();
        }
        Rewrite rewrite = new Rewrite();
        for (Step step : STEPS) {
            if (step.from() >= version) {
                step.apply(root.get(), rewrite);
            }
        }
        String rebuilt = rebuild(
                ConfigTemplate.parse(text), ConfigTemplate.parse(defaults), root.get(), defaultRoot.get(), rewrite);
        return Optional.of(new Migration(version, rebuilt, rewrite.notes));
    }

    private static String rebuild(
            ConfigTemplate own, ConfigTemplate template, Map<?, ?> root, Map<?, ?> defaultRoot, Rewrite rewrite) {
        List<Map.Entry<String, ConfigTemplate.Block>> out = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, ConfigTemplate.Block> entry : template.blocks.entrySet()) {
            String key = entry.getKey();
            List<String> body = ownBody(key, own, root, defaultRoot, rewrite).orElse(entry.getValue().body);
            if (!key.equals(VERSION_KEY) && !own.blocks.containsKey(key) && !rewrite.bodies.containsKey(key)) {
                added.add(key);
            }
            out.add(block(key, entry.getValue().comments, body));
        }
        for (Map.Entry<String, ConfigTemplate.Block> entry : own.blocks.entrySet()) {
            if (!template.blocks.containsKey(entry.getKey()) && !rewrite.removed.contains(entry.getKey())) {
                out.add(entry);
            }
        }
        if (!added.isEmpty()) {
            rewrite.notes.add("new settings with default values: " + String.join(", ", added));
        }
        return ConfigTemplate.render(template.header, out);
    }

    private static Optional<List<String>> ownBody(
            String key, ConfigTemplate own, Map<?, ?> root, Map<?, ?> defaultRoot, Rewrite rewrite) {
        if (rewrite.bodies.containsKey(key)) {
            return Optional.of(rewrite.bodies.get(key));
        }
        if (key.equals(VERSION_KEY) || !own.blocks.containsKey(key) || sameValue(root.get(key), defaultRoot.get(key))) {
            return Optional.empty();
        }
        return Optional.of(own.blocks.get(key).body);
    }

    private static boolean sameValue(Object own, Object defaults) {
        return Objects.equals(own, defaults) || (!present(own) && !present(defaults));
    }

    static boolean present(Object raw) {
        if (raw instanceof Map<?, ?>) {
            return !((Map<?, ?>) raw).isEmpty();
        }
        return raw instanceof List<?> ? !((List<?>) raw).isEmpty() : raw != null;
    }

    private static Map.Entry<String, ConfigTemplate.Block> block(String key, List<String> comments, List<String> body) {
        return new AbstractMap.SimpleImmutableEntry<>(key, new ConfigTemplate.Block(comments, body));
    }

    private static int version(Map<?, ?> root) {
        Object raw = root.get(VERSION_KEY);
        return raw instanceof Integer ? (Integer) raw : UNVERSIONED;
    }

    private static Optional<Map<?, ?>> load(String text) {
        try {
            Object loaded = SafeYaml.load(text);
            if (loaded == null) {
                return Optional.of(Collections.emptyMap());
            }
            return loaded instanceof Map<?, ?> ? Optional.of((Map<?, ?>) loaded) : Optional.empty();
        } catch (YAMLException e) {
            return Optional.empty();
        }
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
