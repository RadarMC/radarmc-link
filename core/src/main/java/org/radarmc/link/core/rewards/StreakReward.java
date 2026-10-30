package org.radarmc.link.core.rewards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class StreakReward {
    public static final int NO_REPEAT = 0;

    private final int streak;
    private final int repeatEvery;
    private final List<String> commands;
    private final String label;

    public StreakReward(int streak, int repeatEvery, List<String> commands, String label) {
        this.streak = streak;
        this.repeatEvery = repeatEvery;
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.label = label;
    }

    public int streak() {
        return streak;
    }

    public int repeatEvery() {
        return repeatEvery;
    }

    public boolean repeats() {
        return repeatEvery != NO_REPEAT;
    }

    public List<String> commands() {
        return commands;
    }

    public String label() {
        return label;
    }

    public boolean matches(int voterStreak) {
        if (voterStreak == streak) {
            return true;
        }
        return repeats() && voterStreak > streak && (voterStreak - streak) % repeatEvery == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StreakReward)) {
            return false;
        }
        StreakReward other = (StreakReward) o;
        return streak == other.streak
                && repeatEvery == other.repeatEvery
                && commands.equals(other.commands)
                && Objects.equals(label, other.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streak, repeatEvery, commands, label);
    }

    @Override
    public String toString() {
        return "StreakReward[streak=" + streak + ", repeatEvery=" + repeatEvery + ", commands=" + commands.size()
                + ", label=" + label + "]";
    }
}
