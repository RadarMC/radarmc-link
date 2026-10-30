package org.radarmc.link.core;

public enum Role {
    SERVER("server"),
    SERVER_BEHIND_PROXY("server behind a proxy"),
    PROXY("proxy");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
