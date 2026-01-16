package com.extracrates.sync;

import com.extracrates.ExtraCratesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class SyncHealthMonitor {
    private final ExtraCratesPlugin plugin;
    private final SyncBridge bridge;
    private final AtomicInteger redisFailures = new AtomicInteger();
    private final AtomicInteger dbFailures = new AtomicInteger();
    private BukkitTask task;

    public SyncHealthMonitor(ExtraCratesPlugin plugin, SyncBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkHealth, 100L, 200L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public int getRedisFailures() {
        return redisFailures.get();
    }

    public int getDbFailures() {
        return dbFailures.get();
    }

    private void checkHealth() {
        boolean providerHealthy = bridge.getProvider().isHealthy();
        boolean storeHealthy = bridge.getStore().isHealthy();
        if (!providerHealthy) {
            redisFailures.incrementAndGet();
            plugin.getLogger().log(Level.WARNING, "[Sync] Redis está fuera de línea. Modo local activado.");
        }
        if (!storeHealthy) {
            dbFailures.incrementAndGet();
            plugin.getLogger().log(Level.WARNING, "[Sync] Postgres está fuera de línea. Modo local activado.");
        }
        bridge.setDegraded(!providerHealthy || !storeHealthy);
    }
}
