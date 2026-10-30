package org.radarmc.link.core.api;

import java.time.Instant;
import java.util.UUID;

public final class JoinEntry {
    private final UUID uuid;
    private final String nick;
    private final String ip;
    private final boolean bedrock;
    private final Instant at;

    public JoinEntry(UUID uuid, String nick, String ip, boolean bedrock, Instant at) {
        this.uuid = uuid;
        this.nick = nick;
        this.ip = ip;
        this.bedrock = bedrock;
        this.at = at;
    }

    public UUID uuid() {
        return uuid;
    }

    public String nick() {
        return nick;
    }

    public String ip() {
        return ip;
    }

    public boolean bedrock() {
        return bedrock;
    }

    public Instant at() {
        return at;
    }

    @Override
    public String toString() {
        return "JoinEntry[uuid=" + uuid + ", nick=" + nick + ", bedrock=" + bedrock + ", at=" + at + "]";
    }
}
