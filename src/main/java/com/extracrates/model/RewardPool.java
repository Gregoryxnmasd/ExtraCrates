package com.extracrates.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RewardPool {
    private final String id;
    private final int rollCount;
    private final List<Reward> rewards;

    public RewardPool(String id, int rollCount, List<Reward> rewards) {
        this.id = id;
        this.rollCount = rollCount;
        this.rewards = rewards;
    }

    public String getId() {
        return id;
    }

    public int getRollCount() {
        return rollCount;
    }

    public List<Reward> getRewards() {
        return Collections.unmodifiableList(rewards);
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
