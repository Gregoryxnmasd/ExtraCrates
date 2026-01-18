package com.extracrates.util;

import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility class for rolling rewards, exposed for integrations and tests.
 */
public final class RewardSelector {
    private RewardSelector() {
    }

    @SuppressWarnings("unused")
    public static List<Reward> roll(RewardPool pool) {
        return roll(pool, new Random(), null);
    }

    public static List<Reward> roll(RewardPool pool, Random random, RewardRollLogger logger) {
        List<Reward> results = new ArrayList<>();
        if (pool == null || pool.rewards().isEmpty()) {
            return results;
        }
        int rolls = Math.max(1, pool.rollCount());
        List<Reward> available = pool.preventDuplicateItems() ? new ArrayList<>(pool.rewards()) : null;
        for (int i = 0; i < rolls; i++) {
            List<Reward> selection = available != null && !available.isEmpty() ? available : pool.rewards();
            RollResult result = selectOne(selection, random);
            results.add(result.reward());
            if (available != null && !available.isEmpty()) {
                available.remove(result.reward());
            }
            if (logger != null) {
                logger.log(result.reward(), result.roll(), result.total());
            }
        }
        return results;
    }

    private static RollResult selectOne(List<Reward> rewards, Random random) {
        double total = rewards.stream().mapToDouble(Reward::chance).sum();
        if (total <= 0) {
            double roll = random.nextDouble();
            Reward reward = rewards.get(random.nextInt(rewards.size()));
            return new RollResult(reward, roll, total);
        }
        double roll = random.nextDouble() * total;
        double current = 0;
        for (Reward reward : rewards) {
            current += reward.chance();
            if (roll <= current) {
                return new RollResult(reward, roll, total);
            }
        }
        Reward reward = rewards.getLast();
        return new RollResult(reward, roll, total);
    }

    public interface RewardRollLogger {
        void log(Reward reward, double roll, double total);
    }

    private record RollResult(Reward reward, double roll, double total) {
    }
}
