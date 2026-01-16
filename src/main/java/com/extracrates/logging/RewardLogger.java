package com.extracrates.logging;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class RewardLogger {
    private final ExtraCratesPlugin plugin;
    private final LogFormat format;
    private final Path logFile;
    private final boolean debug;

    public RewardLogger(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("logging");
        String formatName = section != null ? section.getString("format", "json") : "json";
        String fileName = section != null ? section.getString("file", "logs/rewards.log") : "logs/rewards.log";
        this.format = LogFormat.fromString(formatName);
        this.logFile = plugin.getDataFolder().toPath().resolve(fileName);
        this.debug = section != null && section.getBoolean("debug", false);
    }

    public void logReward(Player player, CrateDefinition crate, Reward reward, long seed, Instant timestamp) {
        String logEntry = format.format(
                DateTimeFormatter.ISO_INSTANT.format(timestamp),
                player.getName(),
                crate.getId(),
                reward.getId(),
                reward.getChance(),
                seed
        );
        write(logEntry);
        if (debug) {
            plugin.getLogger().info("Reward log entry: " + logEntry);
        }
    }

    private void write(String entry) {
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(logFile, entry + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo escribir el log de recompensas: " + ex.getMessage());
        }
    }

    private enum LogFormat {
        JSON,
        YAML;

        static LogFormat fromString(String value) {
            if (value == null) {
                return JSON;
            }
            return switch (value.trim().toLowerCase()) {
                case "yaml", "yml" -> YAML;
                default -> JSON;
            };
        }

        String format(String timestamp, String player, String crate, String reward, double chance, long seed) {
            return switch (this) {
                case YAML -> formatYaml(timestamp, player, crate, reward, chance, seed);
                case JSON -> formatJson(timestamp, player, crate, reward, chance, seed);
            };
        }

        private static String formatJson(String timestamp, String player, String crate, String reward, double chance, long seed) {
            return new StringBuilder()
                    .append("{\"timestamp\":\"").append(escapeJson(timestamp)).append("\",")
                    .append("\"player\":\"").append(escapeJson(player)).append("\",")
                    .append("\"crate\":\"").append(escapeJson(crate)).append("\",")
                    .append("\"reward\":\"").append(escapeJson(reward)).append("\",")
                    .append("\"chance\":").append(chance).append(",")
                    .append("\"seed\":").append(seed)
                    .append("}")
                    .toString();
        }

        private static String formatYaml(String timestamp, String player, String crate, String reward, double chance, long seed) {
            return new StringBuilder()
                    .append("---").append(System.lineSeparator())
                    .append("timestamp: '").append(escapeYaml(timestamp)).append("'").append(System.lineSeparator())
                    .append("player: '").append(escapeYaml(player)).append("'").append(System.lineSeparator())
                    .append("crate: '").append(escapeYaml(crate)).append("'").append(System.lineSeparator())
                    .append("reward: '").append(escapeYaml(reward)).append("'").append(System.lineSeparator())
                    .append("chance: ").append(chance).append(System.lineSeparator())
                    .append("seed: ").append(seed)
                    .toString();
        }

        private static String escapeJson(String value) {
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        private static String escapeYaml(String value) {
            return value.replace("'", "''");
        }
    }
}
