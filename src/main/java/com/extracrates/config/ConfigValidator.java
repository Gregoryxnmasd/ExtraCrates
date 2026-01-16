package com.extracrates.config;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigValidator {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;

    public ConfigValidator(ExtraCratesPlugin plugin, ConfigLoader configLoader) {
        this.plugin = plugin;
        this.configLoader = configLoader;
    }

    public ValidationReport validate() {
        List<String> warnings = new ArrayList<>();
        Map<String, RewardPool> rewardPools = configLoader.getRewardPools();

        for (CrateDefinition crate : configLoader.getCrates().values()) {
            String poolId = crate.getRewardsPool();
            if (poolId == null || poolId.isBlank()) {
                warnings.add("La crate '" + crate.getId() + "' no tiene rewards-pool definido.");
                continue;
            }
            if (!rewardPools.containsKey(poolId)) {
                warnings.add("La crate '" + crate.getId() + "' usa un rewards-pool inválido: '" + poolId + "'.");
            }
        }

        for (RewardPool pool : rewardPools.values()) {
            if (pool.getRewards().isEmpty()) {
                warnings.add("El pool de recompensas '" + pool.getId() + "' está vacío.");
            }
            for (Reward reward : pool.getRewards()) {
                String itemName = reward.getItem();
                if (itemName == null || itemName.isBlank()) {
                    warnings.add("Material vacío en recompensa '" + reward.getId() + "' (pool '" + pool.getId() + "').");
                    continue;
                }
                Material material = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
                if (material == null) {
                    warnings.add("Material desconocido en recompensa '" + reward.getId() + "' (pool '" + pool.getId() + "'): '" + itemName + "'.");
                }
                for (String enchantmentKey : reward.getEnchantments().keySet()) {
                    Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantmentKey.toLowerCase(Locale.ROOT)));
                    if (enchantment == null) {
                        warnings.add("Encantamiento inválido en recompensa '" + reward.getId() + "' (pool '" + pool.getId() + "'): '" + enchantmentKey + "'.");
                    }
                }
            }
        }

        return new ValidationReport(warnings);
    }

    public void report(ValidationReport report) {
        Logger logger = plugin.getLogger();
        if (report.getWarnings().isEmpty()) {
            logger.info("Validación de configuración completada sin warnings.");
        } else {
            logger.warning("Validación de configuración detectó " + report.getWarnings().size() + " warning(s):");
            for (String warning : report.getWarnings()) {
                logger.warning("- " + warning);
            }
        }
        writeReport(report);
    }

    private void writeReport(ValidationReport report) {
        File output = new File(plugin.getDataFolder(), "validation-report.txt");
        List<String> lines = new ArrayList<>();
        lines.add("ExtraCrates - Reporte de validación");
        lines.add("Generado: " + LocalDateTime.now());
        lines.add("");
        if (report.getWarnings().isEmpty()) {
            lines.add("Sin warnings detectados.");
        } else {
            lines.add("Warnings:");
            for (String warning : report.getWarnings()) {
                lines.add("- " + warning);
            }
        }
        try {
            Files.createDirectories(output.getParentFile().toPath());
            Files.write(output.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo escribir el reporte de validación: " + ex.getMessage());
        }
    }

    public static class ValidationReport {
        private final List<String> warnings;

        public ValidationReport(List<String> warnings) {
            this.warnings = List.copyOf(warnings);
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
