package com.extracrates.storage;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LocalStorage implements CrateStorage {
    private final Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> keys = new HashMap<>();
    private final Map<UUID, Map<String, Instant>> locks = new HashMap<>();
    private final Map<UUID, Map<String, Map<String, DeliveryRecord>>> deliveries = new HashMap<>();

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
    public void recordDelivery(UUID playerId, String crateId, String rewardId, DeliveryStatus status, int attempt, Instant timestamp) {
        deliveries
                .computeIfAbsent(playerId, key -> new HashMap<>())
                .computeIfAbsent(crateId, key -> new HashMap<>())
                .put(rewardId, new DeliveryRecord(status, attempt, timestamp));
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
        deliveries.clear();
    }

    private record DeliveryRecord(DeliveryStatus status, int attempt, Instant timestamp) {
    }
}
