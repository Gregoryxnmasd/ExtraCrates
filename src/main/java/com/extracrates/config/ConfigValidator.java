package com.extracrates.config;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        Set<String> invalidPools = new HashSet<>();
        Set<String> invalidCrates = new HashSet<>();

        for (RewardPool pool : rewardPools.values()) {
            boolean poolInvalid = false;
            if (pool.rewards().isEmpty()) {
                warnings.add("El pool de recompensas '" + pool.id() + "' está vacío.");
                poolInvalid = true;
            }
            for (Reward reward : pool.rewards()) {
                List<String> issues = new ArrayList<>();
                String itemName = reward.item();
                if (itemName == null || itemName.isBlank()) {
                    issues.add("item vacío");
                } else {
                    Material material = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
                    if (material == null) {
                        issues.add("item desconocido '" + itemName + "'");
                    }
                }
                List<String> invalidEnchantments = new ArrayList<>();
                for (String enchantmentKey : reward.enchantments().keySet()) {
                    Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentKey.toLowerCase(Locale.ROOT)));
                    if (enchantment == null) {
                        invalidEnchantments.add(enchantmentKey);
                    }
                }
                if (!invalidEnchantments.isEmpty()) {
                    issues.add("encantamientos inválidos " + invalidEnchantments);
                }
                boolean hasBlankCommand = reward.commands().stream().anyMatch(command -> command == null || command.isBlank());
                if (reward.commands().isEmpty() || hasBlankCommand) {
                    issues.add("comandos vacíos");
                }
                if (!issues.isEmpty()) {
                    warnings.add("Recompensa inválida '" + reward.id() + "' (pool '" + pool.id() + "'): " + String.join(", ", issues) + ".");
                    poolInvalid = true;
                }
            }
            if (poolInvalid) {
                invalidPools.add(pool.id());
            }
        }

        for (CrateDefinition crate : new ArrayList<>(configLoader.getCrates().values())) {
            String poolId = crate.rewardsPool();
            if (poolId == null || poolId.isBlank()) {
                warnings.add("La crate '" + crate.id() + "' no tiene rewards-pool definido.");
                invalidCrates.add(crate.id());
                continue;
            }
            if (!rewardPools.containsKey(poolId)) {
                warnings.add("La crate '" + crate.id() + "' usa un rewards-pool inexistente: '" + poolId + "'.");
                invalidCrates.add(crate.id());
                continue;
            }
            if (invalidPools.contains(poolId)) {
                warnings.add("La crate '" + crate.id() + "' fue bloqueada por usar el pool inválido '" + poolId + "'.");
                invalidCrates.add(crate.id());
            }
        }

        configLoader.removeCratesById(invalidCrates);

        return new ValidationReport(warnings);
    }

    public void report(ValidationReport report) {
        Logger logger = plugin.getLogger();
        if (report.warnings().isEmpty()) {
            logger.info("Validación de configuración completada sin warnings.");
        } else {
            logger.warning("Validación de configuración detectó " + report.warnings().size() + " warning(s):");
            for (String warning : report.warnings()) {
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
        if (report.warnings().isEmpty()) {
            lines.add("Sin warnings detectados.");
        } else {
            lines.add("Warnings:");
            for (String warning : report.warnings()) {
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

    public record ValidationReport(List<String> warnings) {
        public ValidationReport {
            warnings = List.copyOf(warnings);
        }
    }
}
