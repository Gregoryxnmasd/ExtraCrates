package com.extracrates.runtime;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public enum UiMode {
    BOSSBAR,
    ACTIONBAR,
    BOTH,
    NONE;

    public static UiMode fromConfig(FileConfiguration config) {
        if (config == null) {
            return BOSSBAR;
        }
        return fromString(config.getString("ui-mode", "bossbar"));
    }

    public static UiMode fromString(String value) {
        if (value == null) {
            return BOSSBAR;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "actionbar", "action-bar", "action_bar" -> ACTIONBAR;
            case "both" -> BOTH;
            case "none", "off", "disabled" -> NONE;
            default -> BOSSBAR;
        };
    }
}
