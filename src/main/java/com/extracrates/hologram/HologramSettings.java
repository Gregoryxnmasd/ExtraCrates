package com.extracrates.hologram;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

public class HologramSettings {
    private final String nameFormat;
    private final String fontKey;
    private final Vector offset;
    private final TextAnimationSettings textAnimationSettings;

    public HologramSettings(String nameFormat, String fontKey, Vector offset, TextAnimationSettings textAnimationSettings) {
        this.nameFormat = nameFormat;
        this.fontKey = fontKey;
        this.offset = offset;
        this.textAnimationSettings = textAnimationSettings;
    }

    public String getNameFormat() {
        return nameFormat;
    }

    public String getFontKey() {
        return fontKey;
    }

    public Vector getOffset() {
        return offset.clone();
    }

    public TextAnimationSettings getTextAnimationSettings() {
        return textAnimationSettings;
    }

    public static HologramSettings fromConfig(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("holograms");
        String nameFormat = "&e%reward_name%";
        double xOffset = 0.0;
        double yOffset = 0.8;
        double zOffset = 0.0;
        TextAnimationSettings animationSettings = new TextAnimationSettings(false, 0, 0, 0);

        if (section != null) {
            nameFormat = section.getString("name-format", nameFormat);
            xOffset = section.getDouble("x-offset", xOffset);
            yOffset = section.getDouble("height-offset", yOffset);
            zOffset = section.getDouble("z-offset", zOffset);
            ConfigurationSection animations = section.getConfigurationSection("text-animations");
            if (animations != null) {
                boolean enabled = animations.getBoolean("enabled", false);
                int interpolationDelay = animations.getInt("interpolation-delay", 0);
                int interpolationDuration = animations.getInt("interpolation-duration", 0);
                int teleportDuration = animations.getInt("teleport-duration", 0);
                animationSettings = new TextAnimationSettings(enabled, interpolationDelay, interpolationDuration, teleportDuration);
            }
        }

        String fontKey = config.getString("hologram-font", "");
        Vector offset = new Vector(xOffset, yOffset, zOffset);
        return new HologramSettings(nameFormat, fontKey, offset, animationSettings);
    }

    public static class TextAnimationSettings {
        private final boolean enabled;
        private final int interpolationDelay;
        private final int interpolationDuration;
        private final int teleportDuration;

        public TextAnimationSettings(boolean enabled, int interpolationDelay, int interpolationDuration, int teleportDuration) {
            this.enabled = enabled;
            this.interpolationDelay = interpolationDelay;
            this.interpolationDuration = interpolationDuration;
            this.teleportDuration = teleportDuration;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getInterpolationDelay() {
            return interpolationDelay;
        }

        public int getInterpolationDuration() {
            return interpolationDuration;
        }

        public int getTeleportDuration() {
            return teleportDuration;
        }
    }
}
