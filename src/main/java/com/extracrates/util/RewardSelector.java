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
        return roll(pool, random, logger, RewardSelectorSettings.disabled());
    }

    public static List<Reward> roll(RewardPool pool, Random random, RewardRollLogger logger, RewardSelectorSettings settings) {
        List<Reward> results = new ArrayList<>();
        if (pool == null || pool.rewards().isEmpty()) {
            return results;
        }
        RewardSelectorSettings safeSettings = settings == null ? RewardSelectorSettings.disabled() : settings;
        warnIfNeeded(pool, safeSettings);
        int rolls = Math.max(1, pool.rollCount());
        for (int i = 0; i < rolls; i++) {
            RollResult result = selectOne(pool.rewards(), random, safeSettings);
            results.add(result.reward());
            if (logger != null) {
                logger.log(result.reward(), result.roll(), result.total());
            }
        }
        return results;
    }

    private static void warnIfNeeded(RewardPool pool, RewardSelectorSettings settings) {
        if (settings.warningLogger() == null || settings.warningThreshold() <= 0) {
            return;
        }
        for (Reward reward : pool.rewards()) {
            if (reward.chance() > settings.warningThreshold()) {
                settings.warningLogger().warn(pool, reward, settings.warningThreshold());
            }
        }
    }

    private static RollResult selectOne(List<Reward> rewards, Random random, RewardSelectorSettings settings) {
        double total = rewards.stream().mapToDouble(Reward::chance).sum();
        if (total <= 0) {
            double roll = random.nextDouble();
            Reward reward = rewards.get(random.nextInt(rewards.size()));
            return new RollResult(reward, roll, total);
        }
        if (settings.normalizeChances() && Math.abs(total - 1.0) > 1.0e-9) {
            double roll = random.nextDouble();
            double current = 0;
            for (Reward reward : rewards) {
                current += reward.chance() / total;
                if (roll <= current) {
                    return new RollResult(reward, roll, 1.0);
                }
            }
            Reward reward = rewards.getLast();
            return new RollResult(reward, roll, 1.0);
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

    public interface RewardWarningLogger {
        void warn(RewardPool pool, Reward reward, double threshold);
    }

    public record RewardSelectorSettings(
            boolean normalizeChances,
            double warningThreshold,
            RewardWarningLogger warningLogger
    ) {
        public static RewardSelectorSettings disabled() {
            return new RewardSelectorSettings(false, 0, null);
        }
    }

    private record RollResult(Reward reward, double roll, double total) {
    }
}
