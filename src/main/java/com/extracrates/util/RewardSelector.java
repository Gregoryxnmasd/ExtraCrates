package com.extracrates.util;

import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RewardSelector {
    private static final Random RANDOM = new Random();

    private RewardSelector() {
    }

    public static List<Reward> roll(RewardPool pool) {
        return roll(pool, RANDOM);
    }

    public static List<Reward> roll(RewardPool pool, long seed) {
        return roll(pool, new Random(seed));
    }

    private static List<Reward> roll(RewardPool pool, Random random) {
        List<Reward> results = new ArrayList<>();
        if (pool == null || pool.getRewards().isEmpty()) {
            return results;
        }
        for (int i = 0; i < Math.max(1, pool.getRollCount()); i++) {
            results.add(selectOne(pool.getRewards(), random));
        }
        return results;
    }

    private static Reward selectOne(List<Reward> rewards, Random random) {
        double total = rewards.stream().mapToDouble(Reward::getChance).sum();
        if (total <= 0) {
            return rewards.get(random.nextInt(rewards.size()));
        }
        double roll = random.nextDouble() * total;
        double current = 0;
        for (Reward reward : rewards) {
            current += reward.getChance();
            if (roll <= current) {
                return reward;
            }
        }
        return rewards.get(rewards.size() - 1);
    }
}
