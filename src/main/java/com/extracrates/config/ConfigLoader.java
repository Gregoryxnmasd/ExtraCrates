package com.extracrates.config;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CutscenePath;
import com.extracrates.model.RewardPool;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {
    private final ExtraCratesPlugin plugin;
    private final Map<String, CrateDefinition> crates = new HashMap<>();
    private final Map<String, RewardPool> rewardPools = new HashMap<>();
    private final Map<String, CutscenePath> paths = new HashMap<>();

    public ConfigLoader(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        crates.clear();
        rewardPools.clear();
        paths.clear();
        loadCrates();
        loadRewards();
        loadPaths();
    }

    public Map<String, CrateDefinition> getCrates() {
        return Collections.unmodifiableMap(crates);
    }

    public Map<String, RewardPool> getRewardPools() {
        return Collections.unmodifiableMap(rewardPools);
    }

    public Map<String, CutscenePath> getPaths() {
        return Collections.unmodifiableMap(paths);
    }

    public FileConfiguration getMainConfig() {
        return plugin.getConfig();
    }

    private void loadCrates() {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("crates");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            CrateDefinition crate = CrateDefinition.fromSection(id, section.getConfigurationSection(id));
            if (crate != null) {
                crates.put(id, crate);
            }
        }
    }

    private void loadRewards() {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("pools");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            RewardPool pool = RewardPool.fromSection(id, section.getConfigurationSection(id));
            if (pool != null) {
                rewardPools.put(id, pool);
            }
        }
    }

    private void loadPaths() {
        File file = new File(plugin.getDataFolder(), "paths.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("paths");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            CutscenePath path = CutscenePath.fromSection(id, section.getConfigurationSection(id));
            if (path != null) {
                paths.put(id, path);
            }
        }
    }
}
