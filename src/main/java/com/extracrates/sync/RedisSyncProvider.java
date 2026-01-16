package com.extracrates.sync;

import com.extracrates.ExtraCratesPlugin;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class RedisSyncProvider implements SyncProvider {
    private static final Map<String, List<SyncListener>> CHANNEL_LISTENERS = new ConcurrentHashMap<>();

    private final ExtraCratesPlugin plugin;
    private final SyncSettings settings;
    private volatile boolean healthy = true;
    private SyncListener listener;

    public RedisSyncProvider(ExtraCratesPlugin plugin, SyncSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @Override
    public void start(SyncListener listener) {
        this.listener = listener;
        String channel = settings.getRedis().getChannel();
        CHANNEL_LISTENERS.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(listener);
        plugin.getLogger().info(() -> "[Sync] Redis provider listo en canal " + channel
                + " (" + settings.getRedis().getHost() + ":" + settings.getRedis().getPort() + ")");
    }

    @Override
    public void publish(SyncEvent event) {
        if (!healthy) {
            return;
        }
        String channel = settings.getRedis().getChannel();
        List<SyncListener> listeners = CHANNEL_LISTENERS.get(channel);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (SyncListener subscriber : listeners) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    subscriber.onEvent(event);
                } catch (Exception ex) {
                    healthy = false;
                    plugin.getLogger().log(Level.WARNING, "[Sync] Error publicando evento en Redis simulado", ex);
                }
            });
        }
    }

    @Override
    public void shutdown() {
        if (listener != null) {
            List<SyncListener> listeners = CHANNEL_LISTENERS.get(settings.getRedis().getChannel());
            if (listeners != null) {
                listeners.remove(listener);
            }
        }
        listener = null;
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public String getName() {
        return "Redis";
    }
}
