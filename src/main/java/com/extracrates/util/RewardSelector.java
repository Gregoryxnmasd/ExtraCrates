package com.extracrates.util;

import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RewardSelector {
    private RewardSelector() {
    }

    public static List<Reward> roll(RewardPool pool) {
        return roll(pool, new Random(), null);
    }

    public static List<Reward> roll(RewardPool pool, Random random, RewardRollLogger logger) {
        List<Reward> results = new ArrayList<>();
        if (pool == null || pool.getRewards().isEmpty()) {
            return results;
        }
        int rolls = Math.max(1, pool.getRollCount());
        for (int i = 0; i < rolls; i++) {
            RollResult result = selectOne(pool.getRewards(), random);
            results.add(result.reward());
            if (logger != null) {
                logger.log(result.reward(), result.roll(), result.total());
            }
        }
        return results;
    }

    private static RollResult selectOne(List<Reward> rewards, Random random) {
        double total = rewards.stream().mapToDouble(Reward::getChance).sum();
        if (total <= 0) {
            double roll = random.nextDouble();
            Reward reward = rewards.get(random.nextInt(rewards.size()));
            return new RollResult(reward, roll, total);
        }
        double roll = random.nextDouble() * total;
        double current = 0;
        for (Reward reward : rewards) {
            current += reward.getChance();
            if (roll <= current) {
                return new RollResult(reward, roll, total);
            }
        }
        Reward reward = rewards.get(rewards.size() - 1);
        return new RollResult(reward, roll, total);
    }

    public interface RewardRollLogger {
        void log(Reward reward, double roll, double total);
    }

    private record RollResult(Reward reward, double roll, double total) {
    }
}
