package com.extracrates.config;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Optional;

public final class SettingsSnapshot {
    private final String particlesDefault;
    private final String hologramFont;
    private final ResourcepackSettings resourcepack;

    private SettingsSnapshot(String particlesDefault, String hologramFont, ResourcepackSettings resourcepack) {
        this.particlesDefault = particlesDefault;
        this.hologramFont = hologramFont;
        this.resourcepack = resourcepack;
    }

    public static SettingsSnapshot fromConfig(FileConfiguration config) {
        String particlesDefault = config.getString("particles-default", "");
        String hologramFont = config.getString("hologram-font", "");
        boolean useCustomModelData = config.getBoolean("resourcepack.use-custom-model-data", true);
        boolean allowAnimatedItems = config.getBoolean("resourcepack.allow-animated-items", true);
        boolean allowMapImages = config.getBoolean("resourcepack.allow-map-images", true);
        return new SettingsSnapshot(particlesDefault, hologramFont, new ResourcepackSettings(useCustomModelData, allowAnimatedItems, allowMapImages));
    }

    public String getParticlesDefault() {
        return particlesDefault;
    }

    public ResourcepackSettings getResourcepack() {
        return resourcepack;
    }

    public Component applyHologramFont(Component component) {
        if (hologramFont == null || hologramFont.isEmpty()) {
            return component;
        }
        Optional<Key> fontKey = parseKey(hologramFont);
        return fontKey.map(component::font).orElse(component);
    }

    private Optional<Key> parseKey(String value) {
        try {
            return Optional.of(Key.key(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record ResourcepackSettings(boolean useCustomModelData, boolean allowAnimatedItems, boolean allowMapImages) {
    }
}
