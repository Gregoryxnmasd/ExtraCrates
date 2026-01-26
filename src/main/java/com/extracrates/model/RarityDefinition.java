package com.extracrates.model;

import org.bukkit.configuration.ConfigurationSection;

public record RarityDefinition(String id, String displayName, double chance, String path) {
    public static RarityDefinition fromSection(String id, ConfigurationSection section) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalizedId = id.trim().toLowerCase(java.util.Locale.ROOT);
        if (section == null) {
            return new RarityDefinition(normalizedId, normalizedId, 0.0, "");
        }
        String displayName = section.getString("display-name", normalizedId);
        if (displayName == null || displayName.isBlank()) {
            displayName = normalizedId;
        }
        double chance = section.getDouble("chance", 0.0);
        String path = section.getString("path", "");
        return new RarityDefinition(normalizedId, displayName, chance, path == null ? "" : path);
    }
}
