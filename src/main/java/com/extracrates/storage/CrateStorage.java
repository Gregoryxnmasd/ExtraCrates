package com.extracrates.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrateStorage {
    Optional<Instant> getCooldown(UUID playerId, String crateId);

    void setCooldown(UUID playerId, String crateId, Instant timestamp);

    int getKeyCount(UUID playerId, String crateId);

    boolean consumeKey(UUID playerId, String crateId);

    void addKey(UUID playerId, String crateId, int amount);

    void logOpen(UUID playerId, String crateId, String rewardId, String serverId, Instant timestamp);

    Optional<PendingReward> getPendingReward(UUID playerId, String crateId);

    List<PendingReward> getPendingRewards(UUID playerId);

    void setPendingReward(UUID playerId, String crateId, String rewardId, Instant timestamp);

    boolean markRewardDelivered(UUID playerId, String crateId, String rewardId, Instant timestamp);

    boolean acquireLock(UUID playerId, String crateId);

    void releaseLock(UUID playerId, String crateId);

    Optional<PendingReward> getPendingReward(UUID playerId);

    void setPendingReward(UUID playerId, String crateId, String rewardId);

    void markRewardDelivered(UUID playerId, String crateId, String rewardId);

    void close();
}
