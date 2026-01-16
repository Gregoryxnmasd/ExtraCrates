package com.extracrates.sync;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.runtime.core.SessionManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SyncBridge {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final SessionManager sessionManager;
    private SyncSettings settings;
    private SyncProvider provider;
    private SyncStore store;
    private SyncHealthMonitor monitor;
    private volatile boolean degraded;

    public SyncBridge(ExtraCratesPlugin plugin, ConfigLoader configLoader, SessionManager sessionManager) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.sessionManager = sessionManager;
        reload();
    }

    public void reload() {
        shutdown();
        settings = SyncSettings.fromConfig(configLoader.getMainConfig());
        if (!settings.isEnabled()) {
            provider = new NoOpSyncProvider();
            store = new NoOpSyncStore();
            degraded = false;
            return;
        }
        provider = new RedisSyncProvider(plugin, settings);
        store = new PostgresSyncStore(plugin, settings);
        store.init();
        provider.start(this::handleIncoming);
        monitor = new SyncHealthMonitor(plugin, this);
        monitor.start();
    }

    public void shutdown() {
        if (monitor != null) {
            monitor.stop();
        }
        if (provider != null) {
            provider.shutdown();
        }
        if (store != null) {
            store.shutdown();
        }
    }

    public void recordCrateOpen(UUID playerId, String crateId) {
        if (!settings.isEnabled() || degraded) {
            return;
        }
        SyncEvent event = new SyncEvent(SyncEventType.CRATE_OPEN, settings.getServerId(), playerId, crateId, null, Instant.now());
        provider.publish(event);
        store.recordCrateOpen(playerId, crateId, event.getTimestamp(), settings.getServerId());
    }

    public void recordRewardGranted(UUID playerId, String crateId, String rewardId) {
        if (!settings.isEnabled() || degraded) {
            return;
        }
        SyncEvent event = new SyncEvent(SyncEventType.REWARD_GRANTED, settings.getServerId(), playerId, crateId, rewardId, Instant.now());
        provider.publish(event);
        store.recordRewardGranted(playerId, crateId, rewardId, event.getTimestamp(), settings.getServerId());
    }

    public void recordKeyConsumed(UUID playerId, String crateId) {
        if (!settings.isEnabled() || degraded) {
            return;
        }
        SyncEvent event = new SyncEvent(SyncEventType.KEY_CONSUMED, settings.getServerId(), playerId, crateId, null, Instant.now());
        provider.publish(event);
        store.recordKeyConsumed(playerId, crateId, event.getTimestamp(), settings.getServerId());
    }

    public void recordCooldown(UUID playerId, String crateId) {
        if (!settings.isEnabled() || degraded) {
            return;
        }
        SyncEvent event = new SyncEvent(SyncEventType.COOLDOWN_SET, settings.getServerId(), playerId, crateId, null, Instant.now());
        provider.publish(event);
        store.recordCooldown(playerId, crateId, event.getTimestamp(), settings.getServerId());
    }

    public void flush() {
        store.flush();
        sessionManager.flushSyncCaches();
    }

    public SyncProvider getProvider() {
        return provider;
    }

    public SyncStore getStore() {
        return store;
    }

    public SyncSettings getSettings() {
        return settings;
    }

    public SyncHealthMonitor getMonitor() {
        return monitor;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public List<String> getStatusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Sync: " + (settings.isEnabled() ? "enabled" : "disabled"));
        lines.add("Mode: " + settings.getMode().name().toLowerCase());
        lines.add("Server-ID: " + settings.getServerId());
        lines.add("Provider: " + provider.getName() + " (" + (provider.isHealthy() ? "ok" : "down") + ")");
        lines.add("Store: " + store.getName() + " (" + (store.isHealthy() ? "ok" : "down") + ")");
        lines.add("Degraded: " + (degraded ? "yes" : "no"));
        if (monitor != null) {
            lines.add("Redis failures: " + monitor.getRedisFailures());
            lines.add("DB failures: " + monitor.getDbFailures());
        }
        return lines;
    }

    private void handleIncoming(SyncEvent event) {
        if (!settings.isEnabled() || degraded) {
            return;
        }
        if (settings.getServerId().equals(event.getServerId())) {
            return;
        }
        switch (event.getType()) {
            case COOLDOWN_SET -> sessionManager.applyRemoteCooldown(event.getPlayerId(), event.getCrateId(), event.getTimestamp());
            case KEY_CONSUMED -> sessionManager.applyRemoteKeyConsumed(event.getPlayerId(), event.getCrateId());
            case CRATE_OPEN -> sessionManager.applyRemoteOpen(event.getPlayerId(), event.getCrateId());
            case REWARD_GRANTED -> sessionManager.applyRemoteReward(event.getPlayerId(), event.getCrateId(), event.getRewardId());
        }
    }

    private static class NoOpSyncProvider implements SyncProvider {
        @Override
        public void start(SyncListener listener) {
        }

        @Override
        public void publish(SyncEvent event) {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public String getName() {
            return "disabled";
        }
    }

    private static class NoOpSyncStore implements SyncStore {
        @Override
        public void init() {
        }

        @Override
        public void recordCooldown(UUID playerId, String crateId, Instant timestamp, String serverId) {
        }

        @Override
        public void recordKeyConsumed(UUID playerId, String crateId, Instant timestamp, String serverId) {
        }

        @Override
        public void recordCrateOpen(UUID playerId, String crateId, Instant timestamp, String serverId) {
        }

        @Override
        public void recordRewardGranted(UUID playerId, String crateId, String rewardId, Instant timestamp, String serverId) {
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public String getName() {
            return "disabled";
        }
    }
}
