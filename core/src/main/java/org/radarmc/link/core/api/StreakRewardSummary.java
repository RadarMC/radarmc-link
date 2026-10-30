package org.radarmc.link.core.api;

public final class StreakRewardSummary {
    private final int streak;
    private final int repeatEvery;
    private final int commands;
    private final String label;

    public StreakRewardSummary(int streak, int repeatEvery, int commands, String label) {
        this.streak = streak;
        this.repeatEvery = repeatEvery;
        this.commands = commands;
        this.label = label;
    }

    public int streak() {
        return streak;
    }

    public int repeatEvery() {
        return repeatEvery;
    }

    public int commands() {
        return commands;
    }

    public String label() {
        return label;
    }
}
