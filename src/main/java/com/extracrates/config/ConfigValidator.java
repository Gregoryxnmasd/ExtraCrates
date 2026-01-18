package com.extracrates.config;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.model.RewardPool;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

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
    private static final Set<String> OPEN_MODES = Set.of(
            "reward-only",
            "preview-only",
            "key-required",
            "economy-required",
            "full"
    );

    public ConfigValidator(ExtraCratesPlugin plugin, ConfigLoader configLoader) {
        this.plugin = plugin;
        this.configLoader = configLoader;
    }

    public ValidationReport validate() {
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Map<String, RewardPool> rewardPools = configLoader.getRewardPools();
        Set<String> uiModes = Set.of("actionbar", "chat", "none");

        File cratesFile = new File(plugin.getDataFolder(), "crates.yml");
        FileConfiguration cratesConfig = YamlConfiguration.loadConfiguration(cratesFile);
        ConfigurationSection cratesSection = cratesConfig.getConfigurationSection("crates");
        if (cratesSection == null) {
            errors.add(new ValidationIssue(
                    "crates.yml:crates",
                    "No se encontró la sección 'crates'.",
                    "Agrega la sección 'crates' con al menos una crate definida."
            ));
        } else {
            for (String crateId : cratesSection.getKeys(false)) {
                ConfigurationSection crateSection = cratesSection.getConfigurationSection(crateId);
                if (crateSection == null) {
                    continue;
                }
                String openMode = crateSection.getString("open-mode", "reward-only");
                if (!OPEN_MODES.contains(openMode)) {
                    errors.add(new ValidationIssue(
                            "crates.yml:crates." + crateId + ".open-mode",
                            "open-mode inválido: '" + openMode + "'.",
                            "Usa uno de: " + String.join(", ", OPEN_MODES) + "."
                    ));
                }
                String poolId = crateSection.getString("rewards-pool", "");
                if (poolId == null || poolId.isBlank()) {
                    errors.add(new ValidationIssue(
                            "crates.yml:crates." + crateId + ".rewards-pool",
                            "La crate no tiene rewards-pool definido.",
                            "Configura un rewards-pool válido existente en rewards.yml."
                    ));
                } else if (!rewardPools.containsKey(poolId)) {
                    errors.add(new ValidationIssue(
                            "crates.yml:crates." + crateId + ".rewards-pool",
                            "Rewards-pool inválido: '" + poolId + "'.",
                            "Define el pool en rewards.yml o corrige el ID."
                    ));
                }
                ConfigurationSection animationSection = crateSection.getConfigurationSection("animation");
                if (animationSection != null) {
                    String pathId = animationSection.getString("path", "");
                    if (pathId != null && !pathId.isBlank() && !configLoader.getPaths().containsKey(pathId)) {
                        errors.add(new ValidationIssue(
                                "crates.yml:crates." + crateId + ".animation.path",
                                "Ruta de cutscene inexistente: '" + pathId + "'.",
                                "Crea la ruta en paths.yml o corrige el ID."
                        ));
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

        File rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        FileConfiguration rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
        ConfigurationSection poolsSection = rewardsConfig.getConfigurationSection("pools");
        if (poolsSection == null) {
            errors.add(new ValidationIssue(
                    "rewards.yml:pools",
                    "No se encontró la sección 'pools'.",
                    "Agrega la sección 'pools' con al menos un rewards-pool."
            ));
        } else {
            for (String poolId : poolsSection.getKeys(false)) {
                ConfigurationSection poolSection = poolsSection.getConfigurationSection(poolId);
                if (poolSection == null) {
                    continue;
                }
                ConfigurationSection rewardsSection = poolSection.getConfigurationSection("rewards");
                if (rewardsSection == null || rewardsSection.getKeys(false).isEmpty()) {
                    errors.add(new ValidationIssue(
                            "rewards.yml:pools." + poolId + ".rewards",
                            "El pool de recompensas está vacío.",
                            "Agrega al menos una recompensa en el pool."
                    ));
                    continue;
                }
                for (String rewardId : rewardsSection.getKeys(false)) {
                    ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(rewardId);
                    if (rewardSection == null) {
                        continue;
                    }
                    String itemName = rewardSection.getString("item", "");
                    if (itemName == null || itemName.isBlank()) {
                        errors.add(new ValidationIssue(
                                "rewards.yml:pools." + poolId + ".rewards." + rewardId + ".item",
                                "La recompensa no tiene item definido.",
                                "Define un item válido (por ejemplo: DIAMOND)."
                        ));
                    } else {
                        Material material = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
                        if (material == null) {
                            errors.add(new ValidationIssue(
                                    "rewards.yml:pools." + poolId + ".rewards." + rewardId + ".item",
                                    "Material desconocido: '" + itemName + "'.",
                                    "Usa un material válido de Minecraft."
                            ));
                        }
                    }
                    double chance = rewardSection.getDouble("chance", 0);
                    if (chance <= 0) {
                        errors.add(new ValidationIssue(
                                "rewards.yml:pools." + poolId + ".rewards." + rewardId + ".chance",
                                "Chance inválida: " + chance + ".",
                                "Usa un valor mayor a 0."
                        ));
                    }
                    ConfigurationSection enchantmentSection = rewardSection.getConfigurationSection("enchantments");
                    if (enchantmentSection != null) {
                        for (String enchantmentKey : enchantmentSection.getKeys(false)) {
                            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentKey.toLowerCase(Locale.ROOT)));
                            if (enchantment == null) {
                                errors.add(new ValidationIssue(
                                        "rewards.yml:pools." + poolId + ".rewards." + rewardId + ".enchantments." + enchantmentKey,
                                        "Encantamiento inválido: '" + enchantmentKey + "'.",
                                        "Usa un encantamiento válido de Minecraft."
                                ));
                            }
                        }
                    }
                }
            }
        }

        File pathsFile = new File(plugin.getDataFolder(), "paths.yml");
        FileConfiguration pathsConfig = YamlConfiguration.loadConfiguration(pathsFile);
        ConfigurationSection pathsSection = pathsConfig.getConfigurationSection("paths");
        if (pathsSection == null) {
            errors.add(new ValidationIssue(
                    "paths.yml:paths",
                    "No se encontró la sección 'paths'.",
                    "Agrega la sección 'paths' con al menos una ruta."
            ));
        } else {
            for (String pathId : pathsSection.getKeys(false)) {
                ConfigurationSection pathSection = pathsSection.getConfigurationSection(pathId);
                if (pathSection == null) {
                    continue;
                }
                List<?> points = pathSection.getList("points", List.of());
                if (points.size() < 2) {
                    errors.add(new ValidationIssue(
                            "paths.yml:paths." + pathId + ".points",
                            "La ruta necesita al menos 2 puntos.",
                            "Define dos o más puntos en la lista de points."
                    ));
                }
                double duration = pathSection.getDouble("duration-seconds", 4.0);
                if (duration <= 0) {
                    errors.add(new ValidationIssue(
                            "paths.yml:paths." + pathId + ".duration-seconds",
                            "duration-seconds inválido: " + duration + ".",
                            "Usa un valor mayor a 0."
                    ));
                }
                double stepResolution = pathSection.getDouble("step-resolution", 0.15);
                if (stepResolution <= 0) {
                    errors.add(new ValidationIssue(
                            "paths.yml:paths." + pathId + ".step-resolution",
                            "step-resolution inválido: " + stepResolution + ".",
                            "Usa un valor mayor a 0."
                    ));
                }
            }
        }

        return new ValidationReport(errors, warnings);
    }

    public void report(ValidationReport report) {
        Logger logger = plugin.getLogger();
        if (report.errors().isEmpty() && report.warnings().isEmpty()) {
            logger.info("Validación de configuración completada sin errores ni warnings.");
        } else {
            if (!report.errors().isEmpty()) {
                logger.severe("Validación de configuración detectó " + report.errors().size() + " error(es):");
                for (ValidationIssue error : report.errors()) {
                    logger.severe("- " + formatIssue(error));
                }
            }
            if (!report.warnings().isEmpty()) {
                logger.warning("Validación de configuración detectó " + report.warnings().size() + " warning(s):");
                for (ValidationIssue warning : report.warnings()) {
                    logger.warning("- " + formatIssue(warning));
                }
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
        if (report.errors().isEmpty() && report.warnings().isEmpty()) {
            lines.add("Sin errores ni warnings detectados.");
        } else {
            if (!report.errors().isEmpty()) {
                lines.add("Errores:");
                for (ValidationIssue error : report.errors()) {
                    lines.add("- " + formatIssue(error));
                }
                lines.add("");
            }
            if (!report.warnings().isEmpty()) {
                lines.add("Warnings:");
                for (ValidationIssue warning : report.warnings()) {
                    lines.add("- " + formatIssue(warning));
                }
            }
        }
        try {
            Files.createDirectories(output.getParentFile().toPath());
            Files.write(output.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo escribir el reporte de validación: " + ex.getMessage());
        }
    }

    private String formatIssue(ValidationIssue issue) {
        return issue.path() + " -> " + issue.message() + " Sugerencia: " + issue.suggestion();
    }

    public record ValidationIssue(String path, String message, String suggestion) {
    }

    public record ValidationReport(List<ValidationIssue> errors, List<ValidationIssue> warnings) {
        public ValidationReport {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}
