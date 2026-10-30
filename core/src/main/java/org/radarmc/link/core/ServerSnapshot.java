package org.radarmc.link.core;

import org.radarmc.link.core.text.Strings;

public final class ServerSnapshot {
    private static final int MAX_SOFTWARE_LENGTH = 64;

    private final String platform;
    private final String software;
    private final int onlinePlayers;
    private final int maxPlayers;

    public ServerSnapshot(String platform, String software, int onlinePlayers, int maxPlayers) {
        this.platform = platform;
        this.software = Strings.truncate(software, MAX_SOFTWARE_LENGTH);
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
    }

    public String platform() {
        return platform;
    }

    public String software() {
        return software;
    }

    public int onlinePlayers() {
        return onlinePlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }
}
