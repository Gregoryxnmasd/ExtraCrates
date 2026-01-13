package com.extracrates.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public class CrateDefinition {
    private final String id;
    private final String displayName;
    private final CrateType type;
    private final String openMode;
    private final String keyModel;
    private final int cooldownSeconds;
    private final double cost;
    private final String permission;
    private final Location cameraStart;
    private final Location rewardAnchor;
    private final AnimationSettings animation;
    private final CutsceneSettings cutsceneSettings;
    private final String rewardsPool;

    public CrateDefinition(
            String id,
            String displayName,
            CrateType type,
            String openMode,
            String keyModel,
            int cooldownSeconds,
            double cost,
            String permission,
            Location cameraStart,
            Location rewardAnchor,
            AnimationSettings animation,
            CutsceneSettings cutsceneSettings,
            String rewardsPool
    ) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.openMode = openMode;
        this.keyModel = keyModel;
        this.cooldownSeconds = cooldownSeconds;
        this.cost = cost;
        this.permission = permission;
        this.cameraStart = cameraStart;
        this.rewardAnchor = rewardAnchor;
        this.animation = animation;
        this.cutsceneSettings = cutsceneSettings;
        this.rewardsPool = rewardsPool;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CrateType getType() {
        return type;
    }

    public String getOpenMode() {
        return openMode;
    }

    public String getKeyModel() {
        return keyModel;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public double getCost() {
        return cost;
    }

    public String getPermission() {
        return permission;
    }

    public Location getCameraStart() {
        return cameraStart;
    }

    public Location getRewardAnchor() {
        return rewardAnchor;
    }

    public AnimationSettings getAnimation() {
        return animation;
    }

    public CutsceneSettings getCutsceneSettings() {
        return cutsceneSettings;
    }

    public String getRewardsPool() {
        return rewardsPool;
    }

    public static CrateDefinition fromSection(String id, ConfigurationSection section, ConfigurationSection defaults) {
        if (section == null) {
            return null;
        }
        String displayName = section.getString("display-name", id);
        CrateType type = CrateType.fromString(section.getString("type", "normal"));
        String openMode = section.getString("open-mode", "reward-only");
        String keyModel = section.getString("key-model", "");
        int cooldown = section.getInt("cooldown-seconds", 0);
        double cost = section.getDouble("cost", 0);
        String permission = section.getString("permission", "extracrates.open");

        ConfigurationSection locations = section.getConfigurationSection("locations");
        Location cameraStart = null;
        Location rewardAnchor = null;
        if (locations != null) {
            String worldName = locations.getString("world", "world");
            World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                world = org.bukkit.Bukkit.getWorlds().isEmpty() ? null : org.bukkit.Bukkit.getWorlds().get(0);
            }
            if (world != null) {
                ConfigurationSection camera = locations.getConfigurationSection("camera-start");
                if (camera != null) {
                    cameraStart = new Location(
                            world,
                            camera.getDouble("x"),
                            camera.getDouble("y"),
                            camera.getDouble("z"),
                            (float) camera.getDouble("yaw", 0),
                            (float) camera.getDouble("pitch", 0)
                    );
                }
                ConfigurationSection reward = locations.getConfigurationSection("reward-anchor");
                if (reward != null) {
                    rewardAnchor = new Location(
                            world,
                            reward.getDouble("x"),
                            reward.getDouble("y"),
                            reward.getDouble("z")
                    );
                }
            }
        }

        AnimationSettings animation = AnimationSettings.fromSection(section.getConfigurationSection("animation"));
        CutsceneSettings cutsceneSettings = CutsceneSettings.fromSections(section.getConfigurationSection("cutscene"), defaults == null ? null : defaults.getConfigurationSection("cutscene"));
        String rewardsPool = section.getString("rewards-pool", "");
        return new CrateDefinition(id, displayName, type, openMode, keyModel, cooldown, cost, permission, cameraStart, rewardAnchor, animation, cutsceneSettings, rewardsPool);
    }

    public static class AnimationSettings {
        private final String path;
        private final String rewardModel;
        private final String hologramFormat;
        private final RewardFloatSettings rewardFloatSettings;

        public AnimationSettings(String path, String rewardModel, String hologramFormat, RewardFloatSettings rewardFloatSettings) {
            this.path = path;
            this.rewardModel = rewardModel;
            this.hologramFormat = hologramFormat;
            this.rewardFloatSettings = rewardFloatSettings;
        }

        public String getPath() {
            return path;
        }

        public String getRewardModel() {
            return rewardModel;
        }

        public String getHologramFormat() {
            return hologramFormat;
        }

        public RewardFloatSettings getRewardFloatSettings() {
            return rewardFloatSettings;
        }

        public static AnimationSettings fromSection(ConfigurationSection section) {
            if (section == null) {
                return new AnimationSettings("", "", "&e%reward_name%", new RewardFloatSettings(0.8, 2.0, true));
            }
            String path = section.getString("path", "");
            String rewardModel = section.getString("reward-model", "");
            String hologram = section.getString("hologram-format", "&e%reward_name%");
            RewardFloatSettings rewardFloatSettings = RewardFloatSettings.fromSection(section.getConfigurationSection("reward-float"));
            return new AnimationSettings(path, rewardModel, hologram, rewardFloatSettings);
        }
    }

    public static class RewardFloatSettings {
        private final double height;
        private final double spinSpeed;
        private final boolean bobbing;

        public RewardFloatSettings(double height, double spinSpeed, boolean bobbing) {
            this.height = height;
            this.spinSpeed = spinSpeed;
            this.bobbing = bobbing;
        }

        public double getHeight() {
            return height;
        }

        public double getSpinSpeed() {
            return spinSpeed;
        }

        public boolean isBobbing() {
            return bobbing;
        }

        public static RewardFloatSettings fromSection(ConfigurationSection section) {
            if (section == null) {
                return new RewardFloatSettings(0.8, 2.0, true);
            }
            return new RewardFloatSettings(
                    section.getDouble("height", 0.8),
                    section.getDouble("spin-speed", 2.0),
                    section.getBoolean("bobbing", true)
            );
        }
    }

    public static class CutsceneSettings {
        private final String overlayModel;
        private final boolean lockMovement;
        private final boolean hideHud;
        private final MusicSettings musicSettings;

        public CutsceneSettings(String overlayModel, boolean lockMovement, boolean hideHud, MusicSettings musicSettings) {
            this.overlayModel = overlayModel;
            this.lockMovement = lockMovement;
            this.hideHud = hideHud;
            this.musicSettings = musicSettings;
        }

        public String getOverlayModel() {
            return overlayModel;
        }

        public boolean isLockMovement() {
            return lockMovement;
        }

        public boolean isHideHud() {
            return hideHud;
        }

        public MusicSettings getMusicSettings() {
            return musicSettings;
        }

        public static CutsceneSettings fromSections(ConfigurationSection section, ConfigurationSection defaults) {
            String overlayModel = readString(section, "overlay-model", defaults, "overlay-model", "pumpkin-model", "");
            boolean lockMovement = readBoolean(section, "locks.movement", defaults, "locks.movement", true);
            boolean hideHud = readBoolean(section, "locks.hud", defaults, "locks.hud", true);
            MusicSettings musicSettings = MusicSettings.fromSections(
                    section == null ? null : section.getConfigurationSection("music"),
                    defaults == null ? null : defaults.getConfigurationSection("music")
            );
            return new CutsceneSettings(overlayModel, lockMovement, hideHud, musicSettings);
        }

        private static String readString(ConfigurationSection section, String key, ConfigurationSection defaults, String defaultKey, String fallbackKey, String fallback) {
            if (section != null && section.isString(key)) {
                return section.getString(key, fallback);
            }
            if (defaults != null) {
                if (defaults.isString(defaultKey)) {
                    return defaults.getString(defaultKey, fallback);
                }
                if (fallbackKey != null && defaults.isString(fallbackKey)) {
                    return defaults.getString(fallbackKey, fallback);
                }
            }
            return fallback;
        }

        private static boolean readBoolean(ConfigurationSection section, String key, ConfigurationSection defaults, String defaultKey, boolean fallback) {
            if (section != null && section.isBoolean(key)) {
                return section.getBoolean(key);
            }
            if (defaults != null && defaults.isBoolean(defaultKey)) {
                return defaults.getBoolean(defaultKey);
            }
            return fallback;
        }
    }

    public static class MusicSettings {
        private final String sound;
        private final float volume;
        private final float pitch;
        private final int fadeInTicks;
        private final int fadeOutTicks;
        private final String category;

        public MusicSettings(String sound, float volume, float pitch, int fadeInTicks, int fadeOutTicks, String category) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.fadeInTicks = fadeInTicks;
            this.fadeOutTicks = fadeOutTicks;
            this.category = category;
        }

        public String getSound() {
            return sound;
        }

        public float getVolume() {
            return volume;
        }

        public float getPitch() {
            return pitch;
        }

        public int getFadeInTicks() {
            return fadeInTicks;
        }

        public int getFadeOutTicks() {
            return fadeOutTicks;
        }

        public String getCategory() {
            return category;
        }

        public static MusicSettings fromSections(ConfigurationSection section, ConfigurationSection defaults) {
            String sound = readString(section, "sound", defaults, "sound", "");
            if (sound == null || sound.isEmpty()) {
                return null;
            }
            float volume = (float) readDouble(section, "volume", defaults, "volume", 1.0);
            float pitch = (float) readDouble(section, "pitch", defaults, "pitch", 1.0);
            int fadeIn = readInt(section, "fade-in-ticks", defaults, "fade-in-ticks", 0);
            int fadeOut = readInt(section, "fade-out-ticks", defaults, "fade-out-ticks", 0);
            String category = readString(section, "category", defaults, "category", "music");
            return new MusicSettings(sound, volume, pitch, fadeIn, fadeOut, category);
        }

        private static String readString(ConfigurationSection section, String key, ConfigurationSection defaults, String defaultKey, String fallback) {
            if (section != null && section.isString(key)) {
                return section.getString(key, fallback);
            }
            if (defaults != null && defaults.isString(defaultKey)) {
                return defaults.getString(defaultKey, fallback);
            }
            return fallback;
        }

        private static double readDouble(ConfigurationSection section, String key, ConfigurationSection defaults, String defaultKey, double fallback) {
            if (section != null && section.isDouble(key)) {
                return section.getDouble(key);
            }
            if (defaults != null && defaults.isDouble(defaultKey)) {
                return defaults.getDouble(defaultKey);
            }
            return fallback;
        }

        private static int readInt(ConfigurationSection section, String key, ConfigurationSection defaults, String defaultKey, int fallback) {
            if (section != null && section.isInt(key)) {
                return section.getInt(key);
            }
            if (defaults != null && defaults.isInt(defaultKey)) {
                return defaults.getInt(defaultKey);
            }
            return fallback;
        }
    }
}
