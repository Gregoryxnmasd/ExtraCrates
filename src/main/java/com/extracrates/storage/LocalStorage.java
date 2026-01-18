package com.extracrates.storage;

import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LocalStorage implements CrateStorage {
    private final Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> keys = new HashMap<>();
    private final Map<UUID, Map<String, Instant>> locks = new HashMap<>();
    private final Map<UUID, Map<String, PendingReward>> pendingRewards = new HashMap<>();

    @Override
    public Optional<Instant> getCooldown(UUID playerId, String crateId) {
        Map<String, Instant> userCooldowns = cooldowns.get(playerId);
        if (userCooldowns == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userCooldowns.get(crateId));
    }

    @Override
    public void setCooldown(UUID playerId, String crateId, Instant timestamp) {
        cooldowns.computeIfAbsent(playerId, key -> new HashMap<>()).put(crateId, timestamp);
    }

    @Override
    public int getKeyCount(UUID playerId, String crateId) {
        Map<String, Integer> userKeys = keys.get(playerId);
        if (userKeys == null) {
            return 0;
        }
        return userKeys.getOrDefault(crateId, 0);
    }

    @Override
    public boolean consumeKey(UUID playerId, String crateId) {
        Map<String, Integer> userKeys = keys.get(playerId);
        if (userKeys == null) {
            return false;
        }
        int current = userKeys.getOrDefault(crateId, 0);
        if (current <= 0) {
            return false;
        }
        int next = current - 1;
        if (next <= 0) {
            userKeys.remove(crateId);
        } else {
            userKeys.put(crateId, next);
        }
        return true;
    }

    @Override
    public void addKey(UUID playerId, String crateId, int amount) {
        if (amount <= 0) {
            return;
        }
        Map<String, Integer> userKeys = keys.computeIfAbsent(playerId, key -> new HashMap<>());
        userKeys.put(crateId, userKeys.getOrDefault(crateId, 0) + amount);
    }

    @Override
    public void logOpen(UUID playerId, String crateId, String rewardId, String serverId, Instant timestamp) {
        // No-op for local storage.
    }

    @Override
    public Optional<PendingReward> getPendingReward(UUID playerId, String crateId) {
        Map<String, PendingReward> userPending = pendingRewards.get(playerId);
        if (userPending == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userPending.get(crateId));
    }

    @Override
    public List<PendingReward> getPendingRewards(UUID playerId) {
        Map<String, PendingReward> userPending = pendingRewards.get(playerId);
        if (userPending == null || userPending.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(userPending.values());
    }

    @Override
    public void setPendingReward(UUID playerId, String crateId, String rewardId, Instant timestamp) {
        pendingRewards.computeIfAbsent(playerId, key -> new HashMap<>())
                .put(crateId, new PendingReward(crateId, rewardId, RewardDeliveryStatus.PENDING, timestamp));
    }

    @Override
    public boolean markRewardDelivered(UUID playerId, String crateId, String rewardId, Instant timestamp) {
        Map<String, PendingReward> userPending = pendingRewards.computeIfAbsent(playerId, key -> new HashMap<>());
        PendingReward existing = userPending.get(crateId);
        if (existing != null && existing.status() == RewardDeliveryStatus.DELIVERED) {
            return false;
        }
        userPending.put(crateId, new PendingReward(crateId, rewardId, RewardDeliveryStatus.DELIVERED, timestamp));
        return true;
    }

    @Override
    public boolean acquireLock(UUID playerId, String crateId) {
        Map<String, Instant> userLocks = locks.computeIfAbsent(playerId, key -> new HashMap<>());
        if (userLocks.containsKey(crateId)) {
            return false;
        }
        userLocks.put(crateId, Instant.now());
        return true;
    }

    @Override
    public void releaseLock(UUID playerId, String crateId) {
        Map<String, Instant> userLocks = locks.get(playerId);
        if (userLocks != null) {
            userLocks.remove(crateId);
        }
    }

    @Override
    public void close() {
        cooldowns.clear();
        keys.clear();
        locks.clear();
        pendingRewards.clear();
    }
}
