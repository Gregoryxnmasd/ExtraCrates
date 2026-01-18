package com.extracrates.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class StorageFallback implements CrateStorage {
    private final CrateStorage primary;
    private final CrateStorage fallback;
    private final Logger logger;
    private volatile boolean usingFallback;

    public StorageFallback(CrateStorage primary, CrateStorage fallback, Logger logger) {
        this.primary = primary;
        this.fallback = fallback;
        this.logger = logger;
    }

    private <T> T callWithFallback(Supplier<T> primaryCall, Supplier<T> fallbackCall) {
        if (usingFallback) {
            return fallbackCall.get();
        }
        try {
            return primaryCall.get();
        } catch (StorageUnavailableException ex) {
            usingFallback = true;
            logger.warning("Storage SQL no disponible, cambiando a modo local: " + ex.getMessage());
            return fallbackCall.get();
        }
    }

    private void runWithFallback(Runnable primaryCall, Runnable fallbackCall) {
        if (usingFallback) {
            fallbackCall.run();
            return;
        }
        try {
            primaryCall.run();
        } catch (StorageUnavailableException ex) {
            usingFallback = true;
            logger.warning("Storage SQL no disponible, cambiando a modo local: " + ex.getMessage());
            fallbackCall.run();
        }
    }

    @Override
    public Optional<Instant> getCooldown(UUID playerId, String crateId) {
        return callWithFallback(
                () -> primary.getCooldown(playerId, crateId),
                () -> fallback.getCooldown(playerId, crateId)
        );
    }

    @Override
    public void setCooldown(UUID playerId, String crateId, Instant timestamp) {
        runWithFallback(
                () -> primary.setCooldown(playerId, crateId, timestamp),
                () -> fallback.setCooldown(playerId, crateId, timestamp)
        );
    }

    @Override
    public int getKeyCount(UUID playerId, String crateId) {
        return callWithFallback(
                () -> primary.getKeyCount(playerId, crateId),
                () -> fallback.getKeyCount(playerId, crateId)
        );
    }

    @Override
    public boolean consumeKey(UUID playerId, String crateId) {
        return callWithFallback(
                () -> primary.consumeKey(playerId, crateId),
                () -> fallback.consumeKey(playerId, crateId)
        );
    }

    @Override
    public void addKey(UUID playerId, String crateId, int amount) {
        runWithFallback(
                () -> primary.addKey(playerId, crateId, amount),
                () -> fallback.addKey(playerId, crateId, amount)
        );
    }

    @Override
    public void logOpen(UUID playerId, String crateId, String rewardId, String serverId, Instant timestamp) {
        runWithFallback(
                () -> primary.logOpen(playerId, crateId, rewardId, serverId, timestamp),
                () -> fallback.logOpen(playerId, crateId, rewardId, serverId, timestamp)
        );
    }

    @Override
    public Optional<PendingReward> getPendingReward(UUID playerId, String crateId) {
        return callWithFallback(
                () -> primary.getPendingReward(playerId, crateId),
                () -> fallback.getPendingReward(playerId, crateId)
        );
    }

    @Override
    public List<PendingReward> getPendingRewards(UUID playerId) {
        return callWithFallback(
                () -> primary.getPendingRewards(playerId),
                () -> fallback.getPendingRewards(playerId)
        );
    }

    @Override
    public void setPendingReward(UUID playerId, String crateId, String rewardId, Instant timestamp) {
        runWithFallback(
                () -> primary.setPendingReward(playerId, crateId, rewardId, timestamp),
                () -> fallback.setPendingReward(playerId, crateId, rewardId, timestamp)
        );
    }

    @Override
    public boolean markRewardDelivered(UUID playerId, String crateId, String rewardId, Instant timestamp) {
        return callWithFallback(
                () -> primary.markRewardDelivered(playerId, crateId, rewardId, timestamp),
                () -> fallback.markRewardDelivered(playerId, crateId, rewardId, timestamp)
        );
    }

    @Override
    public boolean acquireLock(UUID playerId, String crateId) {
        return callWithFallback(
                () -> primary.acquireLock(playerId, crateId),
                () -> fallback.acquireLock(playerId, crateId)
        );
    }

    @Override
    public void releaseLock(UUID playerId, String crateId) {
        runWithFallback(
                () -> primary.releaseLock(playerId, crateId),
                () -> fallback.releaseLock(playerId, crateId)
        );
    }

    @Override
    public void close() {
        primary.close();
        fallback.close();
    }
}
