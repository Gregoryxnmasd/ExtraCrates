package com.extracrates.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StorageSnapshot(
        Map<UUID, Map<String, Instant>> cooldowns,
        Map<UUID, Map<String, Integer>> keys,
        List<OpenHistoryEntry> history
) {
    public int cooldownCount() {
        return cooldowns.values().stream().mapToInt(Map::size).sum();
    }

    public int keyCount() {
        return keys.values().stream().mapToInt(Map::size).sum();
    }

    public int historyCount() {
        return history.size();
    }
}
