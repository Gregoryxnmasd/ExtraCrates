package com.extracrates.sync;

import java.time.Instant;
import java.util.UUID;

public interface SyncStore {
    void init();

    void recordCooldown(UUID playerId, String crateId, Instant timestamp, String serverId);

    void recordKeyConsumed(UUID playerId, String crateId, Instant timestamp, String serverId);

    void recordCrateOpen(UUID playerId, String crateId, Instant timestamp, String serverId);

    void recordRewardGranted(UUID playerId, String crateId, String rewardId, Instant timestamp, String serverId);

    void recordEvent(UUID playerId, String crateId, SyncEventType type, String rewardId, Instant timestamp, String serverId);

    java.util.List<CrateHistoryEntry> getHistory(UUID playerId, String crateId, int limit, int offset);

    void clearPlayerHistory(UUID playerId);

    void flush();

    boolean isHealthy();

    void shutdown();

    String getName();
}
