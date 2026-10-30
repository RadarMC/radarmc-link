package org.radarmc.link.core.api;

public final class VoteRewardSummary {
    private final int commands;
    private final String label;

    public VoteRewardSummary(int commands, String label) {
        this.commands = commands;
        this.label = label;
    }

    public int commands() {
        return commands;
    }

    public String label() {
        return label;
    }
}
