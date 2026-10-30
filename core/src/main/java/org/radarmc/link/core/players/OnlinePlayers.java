package org.radarmc.link.core.players;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class OnlinePlayers {
    private final Map<String, String> namesByKey = new ConcurrentHashMap<>();
    private final Supplier<String> floodgatePrefix;

    public OnlinePlayers(Supplier<String> floodgatePrefix) {
        this.floodgatePrefix = floodgatePrefix;
    }

    public void joined(String name) {
        namesByKey.put(PlayerNames.key(name), name);
    }

    public void left(String name) {
        namesByKey.remove(PlayerNames.key(name));
    }

    public Optional<String> find(String voteNick) {
        for (String candidate : PlayerNames.candidates(voteNick, floodgatePrefix.get())) {
            String name = namesByKey.get(candidate);
            if (name != null) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return namesByKey.size();
    }
}
