package com.extracrates.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public record RewardPool(String id, int rollCount, List<Reward> rewards) {
    public RewardPool {
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
    }

    public static RewardPool fromSection(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        int rollCount = section.getInt("roll-count", 1);
        List<Reward> rewards = new ArrayList<>();
        ConfigurationSection rewardsSection = section.getConfigurationSection("rewards");
        if (rewardsSection != null) {
            for (String rewardId : rewardsSection.getKeys(false)) {
                Reward reward = Reward.fromSection(rewardId, rewardsSection.getConfigurationSection(rewardId));
                if (reward != null) {
                    rewards.add(reward);
                }
            }
        }
        return new RewardPool(id, rollCount, rewards);
    }
}
