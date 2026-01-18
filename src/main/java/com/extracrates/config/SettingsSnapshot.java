package com.extracrates.config;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Optional;

public final class SettingsSnapshot {
    private final String particlesDefault;
    private final String hologramFont;
    private final ResourcepackSettings resourcepack;
    private final SoundSettings sounds;

    private SettingsSnapshot(String particlesDefault, String hologramFont, ResourcepackSettings resourcepack, SoundSettings sounds) {
        this.particlesDefault = particlesDefault;
        this.hologramFont = hologramFont;
        this.resourcepack = resourcepack;
        this.sounds = sounds;
    }

    public static SettingsSnapshot fromConfig(FileConfiguration config) {
        String particlesDefault = config.getString("particles-default", "");
        String hologramFont = config.getString("hologram-font", "");
        boolean useCustomModelData = config.getBoolean("resourcepack.use-custom-model-data", true);
        boolean allowAnimatedItems = config.getBoolean("resourcepack.allow-animated-items", true);
        boolean allowMapImages = config.getBoolean("resourcepack.allow-map-images", true);
        SoundSettings sounds = SoundSettings.fromConfig(config);
        return new SettingsSnapshot(
                particlesDefault,
                hologramFont,
                new ResourcepackSettings(useCustomModelData, allowAnimatedItems, allowMapImages),
                sounds
        );
    }

    public String getParticlesDefault() {
        return particlesDefault;
    }

    public ResourcepackSettings getResourcepack() {
        return resourcepack;
    }

    public SoundSettings getSounds() {
        return sounds;
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

    public record SoundEffect(Sound sound, float volume, float pitch) {
    }

    public record SoundSettings(SoundEffect reroll, SoundEffect claim, SoundEffect error, SoundEffect preview) {
        public static SoundSettings fromConfig(FileConfiguration config) {
            SoundEffect rerollDefault = new SoundEffect(Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
            SoundEffect claimDefault = new SoundEffect(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            SoundEffect errorDefault = new SoundEffect(Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            SoundEffect previewDefault = new SoundEffect(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);

            SoundEffect reroll = parseSoundEffect(config, "sounds.reroll", rerollDefault);
            SoundEffect claim = parseSoundEffect(config, "sounds.claim", claimDefault);
            SoundEffect error = parseSoundEffect(config, "sounds.error", errorDefault);
            SoundEffect preview = parseSoundEffect(config, "sounds.preview", previewDefault);

            return new SoundSettings(reroll, claim, error, preview);
        }
    }

    private static SoundEffect parseSoundEffect(FileConfiguration config, String path, SoundEffect fallback) {
        String soundName = config.getString(path + ".sound", "");
        Sound sound = parseSound(soundName, fallback.sound());
        float volume = (float) config.getDouble(path + ".volume", fallback.volume());
        float pitch = (float) config.getDouble(path + ".pitch", fallback.pitch());
        return new SoundEffect(sound, volume, pitch);
    }

    private static Sound parseSound(String value, Sound fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace(':', '_')
                .replace('.', '_');
        try {
            return Sound.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
