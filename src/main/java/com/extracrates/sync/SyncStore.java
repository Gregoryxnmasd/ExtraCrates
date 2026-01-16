package com.extracrates.sync;

import java.time.Instant;
import java.util.UUID;

public interface SyncStore {
    void init();

    void recordCooldown(UUID playerId, String crateId, Instant timestamp, String serverId);

    void recordKeyConsumed(UUID playerId, String crateId, Instant timestamp, String serverId);

    void recordCrateOpen(UUID playerId, String crateId, Instant timestamp, String serverId);

    void recordRewardGranted(UUID playerId, String crateId, String rewardId, Instant timestamp, String serverId);

    void flush();

    boolean isHealthy();

    void shutdown();

    String getName();
}
