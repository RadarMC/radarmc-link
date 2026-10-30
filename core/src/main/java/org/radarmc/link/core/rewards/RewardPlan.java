package org.radarmc.link.core.rewards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RewardPlan {
    private static final RewardPlan NONE = new RewardPlan(null, Collections.<StreakReward>emptyList());

    private final VoteReward voteReward;
    private final List<StreakReward> streakRewards;

    private RewardPlan(VoteReward voteReward, List<StreakReward> streakRewards) {
        this.voteReward = voteReward;
        List<StreakReward> sorted = new ArrayList<>(streakRewards);
        sorted.sort(Comparator.comparingInt(StreakReward::streak));
        this.streakRewards = Collections.unmodifiableList(sorted);
    }

    public static RewardPlan none() {
        return NONE;
    }

    public static RewardPlan split(VoteReward voteReward, List<StreakReward> streakRewards) {
        if (voteReward == null && streakRewards.isEmpty()) {
            return NONE;
        }
        return new RewardPlan(voteReward, streakRewards);
    }

    public boolean isEmpty() {
        return voteReward == null && streakRewards.isEmpty();
    }

    public Optional<VoteReward> voteReward() {
        return Optional.ofNullable(voteReward);
    }

    public List<StreakReward> streakRewards() {
        return streakRewards;
    }

    public List<RewardRun> runsFor(int streak) {
        List<RewardRun> runs = new ArrayList<>();
        if (voteReward != null) {
            runs.add(RewardRun.vote(voteReward));
        }
        for (StreakReward bonus : streakRewards) {
            if (bonus.matches(streak)) {
                runs.add(RewardRun.bonus(bonus));
            }
        }
        return runs;
    }

    public String describe() {
        if (isEmpty()) {
            return "no rewards set";
        }
        return (voteReward == null ? "no vote reward" : "vote reward") + ", " + streakRewards.size()
                + " streak bonuses";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RewardPlan)) {
            return false;
        }
        RewardPlan other = (RewardPlan) o;
        return Objects.equals(voteReward, other.voteReward) && streakRewards.equals(other.streakRewards);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voteReward, streakRewards);
    }

    @Override
    public String toString() {
        return "RewardPlan[" + describe() + "]";
    }
}
