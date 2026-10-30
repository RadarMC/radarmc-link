package org.radarmc.link.core.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RewardPlanTest {
    private static final VoteReward DIAMOND = new VoteReward(Arrays.asList("give %player% diamond 1"), "Алмаз");
    private static final StreakReward WEEK = bonus(7, 0, "vip");
    private static final StreakReward MONTH = bonus(30, 30, "key");

    @Test
    void voteRewardRunsOnEveryVote() {
        RewardPlan plan = RewardPlan.split(DIAMOND, Collections.<StreakReward>emptyList());
        for (int streak : new int[] {1, 2, 7, 365}) {
            assertEquals(Arrays.asList("vote reward"), described(plan, streak));
        }
    }

    @Test
    void bonusRunsOnlyOnTheExactDay() {
        RewardPlan plan = RewardPlan.split(DIAMOND, Arrays.asList(WEEK));
        assertEquals(Arrays.asList("vote reward"), described(plan, 6));
        assertEquals(Arrays.asList("vote reward", "streak bonus 7"), described(plan, 7));
        assertEquals(Arrays.asList("vote reward"), described(plan, 8));
        assertEquals(Arrays.asList("vote reward"), described(plan, 14));
    }

    @Test
    void repeatEveryGivesTheBonusAgain() {
        assertTrue(MONTH.matches(30));
        assertFalse(MONTH.matches(31));
        assertFalse(MONTH.matches(45));
        assertTrue(MONTH.matches(60));
        assertTrue(MONTH.matches(90));
        assertFalse(MONTH.matches(29));
        assertFalse(MONTH.matches(0));
        StreakReward offset = bonus(10, 7, "x");
        assertTrue(offset.matches(10));
        assertTrue(offset.matches(17));
        assertTrue(offset.matches(24));
        assertFalse(offset.matches(3), "never before the first day");
    }

    @Test
    void overlappingBonusesAllRunLowestFirst() {
        RewardPlan plan =
                RewardPlan.split(DIAMOND, Arrays.asList(MONTH, bonus(7, 7, "weekly"), bonus(60, 0, "two months")));
        assertEquals(Arrays.asList("vote reward", "streak bonus 7"), described(plan, 14));
        assertEquals(Arrays.asList("vote reward", "streak bonus 30"), described(plan, 30));
        assertEquals(Arrays.asList("vote reward", "streak bonus 7", "streak bonus 30"), described(plan, 210));
        assertEquals(Arrays.asList("vote reward", "streak bonus 30", "streak bonus 60"), described(plan, 60));
    }

    @Test
    void bonusesWithoutVoteRewardGiveNothingOnOtherDays() {
        RewardPlan plan = RewardPlan.split(null, Arrays.asList(WEEK));
        assertTrue(plan.runsFor(3).isEmpty());
        assertEquals(Arrays.asList("streak bonus 7"), described(plan, 7));
        assertFalse(plan.isEmpty());
    }

    @Test
    void emptyPlans() {
        assertTrue(RewardPlan.none().isEmpty());
        assertTrue(RewardPlan.split(null, Collections.<StreakReward>emptyList()).isEmpty());
        assertTrue(RewardPlan.none().runsFor(7).isEmpty());
        assertEquals("no rewards set", RewardPlan.none().describe());
        assertEquals(
                "vote reward, 2 streak bonuses",
                RewardPlan.split(DIAMOND, Arrays.asList(WEEK, MONTH)).describe());
    }

    private static StreakReward bonus(int streak, int repeatEvery, String command) {
        return new StreakReward(streak, repeatEvery, Arrays.asList(command), null);
    }

    private static List<String> described(RewardPlan plan, int streak) {
        List<String> out = new ArrayList<>();
        for (RewardRun run : plan.runsFor(streak)) {
            out.add(run.describe());
        }
        return out;
    }
}
