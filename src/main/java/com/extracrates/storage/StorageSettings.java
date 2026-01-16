package com.extracrates.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record StorageSettings(
        boolean enabled,
        String type,
        String jdbcUrl,
        String username,
        String password,
        int poolSize,
        long poolTimeoutMillis
) {
    public static StorageSettings fromConfig(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("storage");
        if (section == null) {
            return new StorageSettings(false, "mysql", "", "", "", 10, 30000);
        }
        boolean enabled = section.getBoolean("enabled", false);
        String type = section.getString("type", "mysql");
        String jdbcUrl = section.getString("jdbc-url", "");
        String username = section.getString("username", "");
        String password = section.getString("password", "");
        ConfigurationSection poolSection = section.getConfigurationSection("pool");
        int poolSize = poolSection != null ? poolSection.getInt("size", 10) : section.getInt("pool.size", 10);
        long poolTimeoutMillis = poolSection != null
                ? poolSection.getLong("timeout", 30000)
                : section.getLong("pool.timeout", 30000);
        return new StorageSettings(enabled, type, jdbcUrl, username, password, poolSize, poolTimeoutMillis);
    }
}
