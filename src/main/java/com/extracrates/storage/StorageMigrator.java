package com.extracrates.storage;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class StorageMigrator {
    public StorageMigrationReport migrate(
            CrateStorage source,
            StorageSettings settings,
            StorageTarget target,
            Logger logger
    ) {
        CrateStorage activeSource = unwrapStorage(source);
        StorageSnapshot sourceSnapshot = snapshot(activeSource);
        logger.info(() -> "[Migration] Fuente cooldowns=" + sourceSnapshot.cooldownCount()
                + " keys=" + sourceSnapshot.keyCount()
                + " historial=" + sourceSnapshot.historyCount());

        CrateStorage targetStorage = null;
        try {
            targetStorage = createTarget(settings, target, logger);
            if (targetStorage == null) {
                return StorageMigrationReport.failure("No se pudo crear el storage destino.");
            }
            applySnapshot(targetStorage, sourceSnapshot, logger);
            StorageSnapshot targetSnapshot = snapshot(targetStorage);
            logger.info(() -> "[Migration] Destino cooldowns=" + targetSnapshot.cooldownCount()
                    + " keys=" + targetSnapshot.keyCount()
                    + " historial=" + targetSnapshot.historyCount());
            logIntegrity(sourceSnapshot, targetSnapshot, logger);
            return new StorageMigrationReport(
                    true,
                    sourceSnapshot.cooldownCount(),
                    sourceSnapshot.keyCount(),
                    sourceSnapshot.historyCount(),
                    targetSnapshot.cooldownCount(),
                    targetSnapshot.keyCount(),
                    targetSnapshot.historyCount(),
                    "Migración completada."
            );
        } catch (Exception ex) {
            logger.warning("[Migration] Error durante migración: " + ex.getMessage());
            return StorageMigrationReport.failure("Error durante migración: " + ex.getMessage());
        } finally {
            if (targetStorage != null) {
                targetStorage.close();
            }
        }
    }

    private CrateStorage unwrapStorage(CrateStorage storage) {
        if (storage instanceof StorageFallback fallback) {
            return fallback.activeStorage();
        }
        return storage;
    }

    private CrateStorage createTarget(StorageSettings settings, StorageTarget target, Logger logger) {
        if (target == StorageTarget.LOCAL) {
            return new LocalStorage();
        }
        if (settings.jdbcUrl().isBlank()) {
            logger.warning("[Migration] jdbc-url vacío, no se puede crear storage SQL.");
            return null;
        }
        return new SqlStorage(settings, logger);
    }

    private StorageSnapshot snapshot(CrateStorage storage) {
        if (storage instanceof LocalStorage local) {
            return new StorageSnapshot(local.getCooldownsSnapshot(), local.getKeysSnapshot(), List.of());
        }
        if (storage instanceof SqlStorage sql) {
            return new StorageSnapshot(sql.fetchCooldowns(), sql.fetchKeys(), sql.fetchOpenHistory());
        }
        Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();
        Map<UUID, Map<String, Integer>> keys = new HashMap<>();
        return new StorageSnapshot(cooldowns, keys, List.of());
    }

    private void applySnapshot(CrateStorage targetStorage, StorageSnapshot snapshot, Logger logger) {
        if (targetStorage instanceof LocalStorage local) {
            local.clearAll();
            applyCooldowns(local, snapshot);
            applyKeys(local, snapshot);
            if (snapshot.historyCount() > 0) {
                logger.warning("[Migration] El storage local no guarda historial, se omiten "
                        + snapshot.historyCount() + " registros.");
            }
            return;
        }
        if (targetStorage instanceof SqlStorage sql) {
            sql.clearMigrationData();
            applyCooldowns(sql, snapshot);
            applyKeys(sql, snapshot);
            applyHistory(sql, snapshot);
        }
    }

    private void applyCooldowns(CrateStorage targetStorage, StorageSnapshot snapshot) {
        for (Map.Entry<UUID, Map<String, Instant>> entry : snapshot.cooldowns().entrySet()) {
            UUID playerId = entry.getKey();
            for (Map.Entry<String, Instant> cooldown : entry.getValue().entrySet()) {
                targetStorage.setCooldown(playerId, cooldown.getKey(), cooldown.getValue());
            }
        }
    }

    private void applyKeys(LocalStorage local, StorageSnapshot snapshot) {
        for (Map.Entry<UUID, Map<String, Integer>> entry : snapshot.keys().entrySet()) {
            UUID playerId = entry.getKey();
            for (Map.Entry<String, Integer> keyEntry : entry.getValue().entrySet()) {
                local.setKeyCount(playerId, keyEntry.getKey(), keyEntry.getValue());
            }
        }
    }

    private void applyKeys(SqlStorage sql, StorageSnapshot snapshot) {
        for (Map.Entry<UUID, Map<String, Integer>> entry : snapshot.keys().entrySet()) {
            UUID playerId = entry.getKey();
            for (Map.Entry<String, Integer> keyEntry : entry.getValue().entrySet()) {
                sql.setKeyCount(playerId, keyEntry.getKey(), keyEntry.getValue());
            }
        }
    }

    private void applyHistory(SqlStorage sql, StorageSnapshot snapshot) {
        for (OpenHistoryEntry entry : snapshot.history()) {
            sql.logOpen(entry.playerId(), entry.crateId(), entry.rewardId(), entry.serverId(), entry.openedAt());
        }
    }

    private void logIntegrity(StorageSnapshot source, StorageSnapshot target, Logger logger) {
        if (source.cooldownCount() != target.cooldownCount()) {
            logger.warning("[Migration] Diferencia en cooldowns: " + source.cooldownCount()
                    + " vs " + target.cooldownCount());
        }
        if (source.keyCount() != target.keyCount()) {
            logger.warning("[Migration] Diferencia en keys: " + source.keyCount()
                    + " vs " + target.keyCount());
        }
        if (source.historyCount() != target.historyCount()) {
            logger.warning("[Migration] Diferencia en historial: " + source.historyCount()
                    + " vs " + target.historyCount());
        }
    }
}
