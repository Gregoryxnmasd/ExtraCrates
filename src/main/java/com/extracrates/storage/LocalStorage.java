package com.extracrates.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.Comparator;

public class LocalStorage implements CrateStorage {
    private static final int MAX_HISTORY = 500;
    private final Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> keys = new HashMap<>();
    private final Map<UUID, Map<String, Instant>> locks = new HashMap<>();
    private final Map<UUID, Map<String, Map<String, DeliveryRecord>>> deliveries = new HashMap<>();
    private final Map<UUID, List<CrateOpenEntry>> openHistory = new HashMap<>();
    private final Map<UUID, List<CrateOpenStartedEntry>> openStarts = new HashMap<>();
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
    public void clearCooldown(UUID playerId, String crateId) {
        Map<String, Instant> userCooldowns = cooldowns.get(playerId);
        if (userCooldowns != null) {
            userCooldowns.remove(crateId);
        }
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
        List<CrateOpenEntry> entries = openHistory.computeIfAbsent(playerId, key -> new ArrayList<>());
        entries.add(0, new CrateOpenEntry(playerId, crateId, rewardId, serverId, timestamp));
        if (entries.size() > MAX_HISTORY) {
            entries.remove(entries.size() - 1);
        }
    }

    @Override
    public void logOpenStarted(UUID playerId, String crateId, String serverId, Instant timestamp) {
        List<CrateOpenStartedEntry> entries = openStarts.computeIfAbsent(playerId, key -> new ArrayList<>());
        entries.add(0, new CrateOpenStartedEntry(playerId, crateId, serverId, timestamp));
        if (entries.size() > MAX_HISTORY) {
            entries.remove(entries.size() - 1);
        }
    }

    @Override
    public List<CrateOpenEntry> getOpenHistory(UUID playerId, OpenHistoryFilter filter, int limit, int offset) {
        if (limit <= 0) {
            return List.of();
        }
        List<CrateOpenEntry> entries = openHistory.get(playerId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        OpenHistoryFilter safeFilter = filter != null ? filter : OpenHistoryFilter.none();
        List<CrateOpenEntry> result = new ArrayList<>();
        int skipped = 0;
        for (CrateOpenEntry entry : entries) {
            if (!safeFilter.matches(entry)) {
                continue;
            }
            if (skipped < offset) {
                skipped++;
                continue;
            }
            result.add(entry);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public Optional<PendingReward> getPendingReward(UUID playerId) {
        Map<String, PendingReward> userPending = pendingRewards.get(playerId);
        if (userPending == null) {
            return Optional.empty();
        }
        return userPending.values().stream()
                .max(Comparator.comparing(PendingReward::updatedAt));
    }

    @Override
    public void setPendingReward(UUID playerId, String crateId, String rewardId) {
        pendingRewards.computeIfAbsent(playerId, key -> new HashMap<>())
                .put(crateId, new PendingReward(crateId, rewardId, RewardDeliveryStatus.PENDING, Instant.now()));
    }

    @Override
    public void markRewardDelivered(UUID playerId, String crateId, String rewardId) {
        Map<String, PendingReward> userPending = pendingRewards.computeIfAbsent(playerId, key -> new HashMap<>());
        PendingReward existing = userPending.get(crateId);
        if (existing != null && existing.status() == RewardDeliveryStatus.DELIVERED) {
            return;
        }
        userPending.put(crateId, new PendingReward(crateId, rewardId, RewardDeliveryStatus.DELIVERED, Instant.now()));
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
        openHistory.clear();
        openStarts.clear();
        pendingRewards.clear();
    }

    Map<UUID, Map<String, Instant>> getCooldownsSnapshot() {
        Map<UUID, Map<String, Instant>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Instant>> entry : cooldowns.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return snapshot;
    }

    Map<UUID, Map<String, Integer>> getKeysSnapshot() {
        Map<UUID, Map<String, Integer>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : keys.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return snapshot;
    }

    void setKeyCount(UUID playerId, String crateId, int amount) {
        if (amount <= 0) {
            Map<String, Integer> userKeys = keys.get(playerId);
            if (userKeys != null) {
                userKeys.remove(crateId);
            }
            return;
        }
        keys.computeIfAbsent(playerId, key -> new HashMap<>()).put(crateId, amount);
    }

    void clearAll() {
        close();
    }

    private record DeliveryRecord(DeliveryStatus status, int attempt, Instant timestamp) {
    }
}
