package com.extracrates.util;

import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RewardSelectorTest {

    @Test
    void rollCountDefaultsToAtLeastOne() {
        RewardPool pool = new RewardPool("pool", 0, false, List.of(reward("reward", 1.0)));

        List<Reward> results = RewardSelector.roll(pool, new FixedRandom(), null);

        assertEquals(1, results.size());
        assertEquals("reward", results.getFirst().id());
    }

    @Test
    void weightedRollsRespectChanceDistribution() {
        Reward first = reward("first", 0.2);
        Reward second = reward("second", 0.8);
        RewardPool pool = new RewardPool("pool", 2, false, List.of(first, second));

        FixedRandom random = new FixedRandom().withDoubles(0.1, 0.95);

        List<Reward> results = RewardSelector.roll(pool, random, null);

        assertEquals(List.of(first, second), results);
    }

    @Test
    void zeroTotalFallsBackToIndexSelectionAndLogs() {
        Reward first = reward("first", 0.0);
        Reward second = reward("second", 0.0);
        RewardPool pool = new RewardPool("pool", 1, false, List.of(first, second));

        FixedRandom random = new FixedRandom()
                .withDoubles(0.42)
                .withInts(1);

        TestRollLogger logger = new TestRollLogger();

        List<Reward> results = RewardSelector.roll(pool, random, logger);

        assertEquals(1, results.size());
        assertEquals(second, results.getFirst());
        assertNotNull(logger.lastReward);
        assertEquals(0.42, logger.lastRoll, 0.0001);
        assertEquals(0.0, logger.lastTotal, 0.0001);
        assertEquals(second, logger.lastReward);
    }

    private Reward reward(String id, double chance) {
        return new Reward(
                id,
                chance,
                id,
                "STONE",
                1,
                null,
                "",
                false,
                null,
                null,
                new Reward.RewardMessage("", ""),
                new Reward.RewardEffects("", "", ""),
                null,
                "",
                ""
        );
    }

    private static final class TestRollLogger implements RewardSelector.RewardRollLogger {
        private Reward lastReward;
        private double lastRoll;
        private double lastTotal;

        @Override
        public void log(Reward reward, double roll, double total) {
            this.lastReward = reward;
            this.lastRoll = roll;
            this.lastTotal = total;
        }
    }

    private static final class FixedRandom extends Random {
        private final Deque<Double> doubles = new ArrayDeque<>();
        private final Deque<Integer> ints = new ArrayDeque<>();

        FixedRandom withDoubles(double... values) {
            for (double value : values) {
                doubles.add(value);
            }
            return this;
        }

        FixedRandom withInts(int... values) {
            for (int value : values) {
                ints.add(value);
            }
            return this;
        }

        @Override
        public double nextDouble() {
            if (doubles.isEmpty()) {
                return 0.0;
            }
            return doubles.removeFirst();
        }

        @Override
        public int nextInt(int bound) {
            if (ints.isEmpty()) {
                return 0;
            }
            int value = ints.removeFirst();
            return Math.min(Math.max(value, 0), bound - 1);
        }
    }
}
