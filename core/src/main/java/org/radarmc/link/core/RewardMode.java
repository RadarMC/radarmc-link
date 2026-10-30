package org.radarmc.link.core;

import java.util.Locale;

public enum RewardMode {
    LOCAL,
    FORWARD,
    PROXY,
    RECEIVE,
    OFF;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean runsCommands() {
        return this == LOCAL || this == RECEIVE || this == PROXY;
    }

    public boolean pollsVotes() {
        return this == LOCAL || this == FORWARD || this == PROXY;
    }
}
