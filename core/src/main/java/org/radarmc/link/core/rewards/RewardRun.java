package org.radarmc.link.core.rewards;

import java.util.List;

public final class RewardRun {
    public enum Kind {
        VOTE,
        BONUS
    }

    private final Kind kind;
    private final int streak;
    private final List<String> commands;

    private RewardRun(Kind kind, int streak, List<String> commands) {
        this.kind = kind;
        this.streak = streak;
        this.commands = commands;
    }

    static RewardRun vote(VoteReward reward) {
        return new RewardRun(Kind.VOTE, 0, reward.commands());
    }

    static RewardRun bonus(StreakReward bonus) {
        return new RewardRun(Kind.BONUS, bonus.streak(), bonus.commands());
    }

    public Kind kind() {
        return kind;
    }

    public List<String> commands() {
        return commands;
    }

    public String describe() {
        return kind == Kind.BONUS ? "streak bonus " + streak : "vote reward";
    }
}
