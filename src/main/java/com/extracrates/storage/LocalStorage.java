package com.extracrates.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LocalStorage implements CrateStorage {
    private static final int MAX_HISTORY = 500;
    private final Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> keys = new HashMap<>();
    private final Map<UUID, Map<String, Instant>> locks = new HashMap<>();
    private final Map<UUID, List<CrateOpenEntry>> openHistory = new HashMap<>();

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
        List<CrateOpenEntry> entries = openHistory.computeIfAbsent(playerId, key -> new ArrayList<>());
        entries.add(0, new CrateOpenEntry(playerId, crateId, rewardId, serverId, timestamp));
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
    }
}
