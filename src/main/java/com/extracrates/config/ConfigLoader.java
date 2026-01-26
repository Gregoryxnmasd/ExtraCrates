package com.extracrates.config;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.RarityDefinition;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ConfigLoader {
    private final Supplier<FileConfiguration> mainConfigSupplier;
    private final Supplier<File> dataFolderSupplier;
    private final Logger logger;
    private final Map<String, CrateDefinition> crates = new HashMap<>();
    private final Map<String, RewardPool> rewardPools = new HashMap<>();
    private final Map<String, RarityDefinition> rarities = new HashMap<>();
    private final Map<String, CutscenePath> paths = new HashMap<>();
    private SettingsSnapshot settings;
    private boolean configValid = true;

    public ConfigLoader(ExtraCratesPlugin plugin) {
        this(plugin::getConfig, plugin::getDataFolder, plugin.getLogger());
    }

    ConfigLoader(Supplier<FileConfiguration> mainConfigSupplier, Supplier<File> dataFolderSupplier, Logger logger) {
        this.mainConfigSupplier = mainConfigSupplier;
        this.dataFolderSupplier = dataFolderSupplier;
        this.logger = logger;
    }

    public void loadAll() {
        crates.clear();
        rewardPools.clear();
        rarities.clear();
        paths.clear();
        loadSettings();
        loadCrates();
        loadRewards();
        loadRarities();
        loadPaths();
        logger.info(String.format(
                "Configuracion cargada: crates=%d, pools=%d, rarities=%d, paths=%d",
                crates.size(),
                rewardPools.size(),
                rarities.size(),
                paths.size()
        ));
    }

    public Map<String, CrateDefinition> getCrates() {
        return Collections.unmodifiableMap(crates);
    }

    public Map<String, RewardPool> getRewardPools() {
        return Collections.unmodifiableMap(rewardPools);
    }

    public Map<String, RarityDefinition> getRarities() {
        return Collections.unmodifiableMap(rarities);
    }

    public Optional<Reward> findRewardById(String rewardId) {
        if (rewardId == null || rewardId.isEmpty()) {
            return Optional.empty();
        }
        for (RewardPool pool : rewardPools.values()) {
            for (Reward reward : pool.rewards()) {
                if (reward.id().equalsIgnoreCase(rewardId)) {
                    return Optional.of(reward);
                }
            }
        }
        return Optional.empty();
    }

    public Map<String, CutscenePath> getPaths() {
        return Collections.unmodifiableMap(paths);
    }

    public FileConfiguration getMainConfig() {
        return mainConfigSupplier.get();
    }

    public SettingsSnapshot getSettings() {
        if (settings == null) {
            settings = SettingsSnapshot.fromConfig(getMainConfig());
        }
        return settings;
    }

    public boolean isConfigValid() {
        return configValid;
    }

    public void setConfigValid(boolean configValid) {
        this.configValid = configValid;
    }

    public Integer resolveModelData(String modelKey) {
        if (modelKey == null || modelKey.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(modelKey);
        } catch (NumberFormatException ignored) {
        }
        ConfigurationSection section = getMainConfig().getConfigurationSection("resourcepack.model-data");
        if (section != null && section.contains(modelKey)) {
            return section.getInt(modelKey);
        }
        return null;
    }

    private void loadSettings() {
        settings = SettingsSnapshot.fromConfig(getMainConfig());
    }

    private void loadCrates() {
        File file = new File(dataFolderSupplier.get(), "crates.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("crates");
        if (section == null) {
            logger.warning("No se encontro la seccion 'crates' en " + file.getAbsolutePath());
            return;
        }
        for (String id : section.getKeys(false)) {
            CrateDefinition crate = CrateDefinition.fromSection(id, section.getConfigurationSection(id), getMainConfig());
            if (crate != null) {
                crates.put(id, crate);
            }
        }
        logger.info(String.format(
                "Validacion crates.yml: ruta=%s, crates=%d",
                file.getAbsolutePath(),
                crates.size()
        ));
    }

    private void loadRewards() {
        File file = new File(dataFolderSupplier.get(), "rewards.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("pools");
        if (section == null) {
            logger.warning("No se encontro la seccion 'pools' en " + file.getAbsolutePath());
            return;
        }
        for (String id : section.getKeys(false)) {
            RewardPool pool = RewardPool.fromSection(id, section.getConfigurationSection(id));
            if (pool != null) {
                rewardPools.put(id, pool);
            }
        }
        logger.info(String.format(
                "Validacion rewards.yml: ruta=%s, pools=%d",
                file.getAbsolutePath(),
                rewardPools.size()
        ));
    }

    private void loadRarities() {
        File file = new File(dataFolderSupplier.get(), "rarities.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("rarities");
        if (section == null) {
            logger.warning("No se encontro la seccion 'rarities' en " + file.getAbsolutePath());
            return;
        }
        for (String id : section.getKeys(false)) {
            RarityDefinition rarity = RarityDefinition.fromSection(id, section.getConfigurationSection(id));
            if (rarity != null) {
                rarities.put(rarity.id(), rarity);
            }
        }
        logger.info(String.format(
                "Validacion rarities.yml: ruta=%s, rarities=%d",
                file.getAbsolutePath(),
                rarities.size()
        ));
    }

    private void loadPaths() {
        File file = new File(dataFolderSupplier.get(), "paths.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("paths");
        if (section == null) {
            logger.warning("No se encontro la seccion 'paths' en " + file.getAbsolutePath());
            return;
        }
        for (String id : section.getKeys(false)) {
            CutscenePath path = CutscenePath.fromSection(id, section.getConfigurationSection(id));
            if (path != null) {
                paths.put(id, path);
            }
        }
        logger.info(String.format(
                "Validacion paths.yml: ruta=%s, paths=%d",
                file.getAbsolutePath(),
                paths.size()
        ));
    }

}
