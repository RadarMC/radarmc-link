package org.radarmc.link.core.rewards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class VoteReward {
    private final List<String> commands;
    private final String label;

    public VoteReward(List<String> commands, String label) {
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.label = label;
    }

    public List<String> commands() {
        return commands;
    }

    public String label() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VoteReward)) {
            return false;
        }
        VoteReward other = (VoteReward) o;
        return commands.equals(other.commands) && Objects.equals(label, other.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commands, label);
    }

    @Override
    public String toString() {
        return "VoteReward[commands=" + commands.size() + ", label=" + label + "]";
    }
}
