package com.extracrates.storage;

import com.extracrates.ExtraCratesPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PendingRewardStore {
    private final ExtraCratesPlugin plugin;
    private final File file;

    public PendingRewardStore(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-rewards.yml");
    }

    public synchronized void addPending(UUID playerId, String poolId, String rewardId) {
        List<PendingReward> pending = getPending(playerId);
        pending.add(new PendingReward(poolId, rewardId));
        setPending(playerId, pending);
    }

    public synchronized List<PendingReward> getPending(UUID playerId) {
        FileConfiguration config = loadConfig();
        List<String> entries = config.getStringList(path(playerId));
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        List<PendingReward> results = new ArrayList<>();
        for (String entry : entries) {
            PendingReward parsed = PendingReward.fromEntry(entry);
            if (parsed != null) {
                results.add(parsed);
            }
        }
        return results;
    }

    public synchronized void setPending(UUID playerId, List<PendingReward> pending) {
        FileConfiguration config = loadConfig();
        if (pending == null || pending.isEmpty()) {
            config.set(path(playerId), null);
        } else {
            config.set(path(playerId), pending.stream().map(PendingReward::toEntry).toList());
        }
        saveConfig(config);
    }

    public synchronized List<PendingReward> clearPending(UUID playerId) {
        List<PendingReward> current = getPending(playerId);
        setPending(playerId, Collections.emptyList());
        return current;
    }

    private String path(UUID playerId) {
        return "pending." + playerId;
    }

    private FileConfiguration loadConfig() {
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfig(FileConfiguration config) {
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar pending-rewards.yml: " + ex.getMessage());
        }
    }

    public record PendingReward(String poolId, String rewardId) {
        public static PendingReward fromEntry(String entry) {
            if (entry == null || entry.isBlank() || !entry.contains(":")) {
                return null;
            }
            String[] parts = entry.split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return null;
            }
            return new PendingReward(parts[0], parts[1]);
        }

        public String toEntry() {
            return poolId + ":" + rewardId;
        }
    }
}
