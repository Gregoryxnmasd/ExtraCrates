package com.extracrates.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public record CrateDefinition(
        String id,
        String displayName,
        CrateType type,
        String openMode,
        String keyModel,
        Material keyMaterial,
        int cooldownSeconds,
        double cost,
        double rerollCost,
        String permission,
        int maxRerolls,
        Location cameraStart,
        Location rewardAnchor,
        AllowedArea allowedArea,
        AnimationSettings animation,
        CutsceneSettings cutsceneSettings,
        String rewardsPool
) {
    public CrateDefinition {
        cameraStart = cameraStart == null ? null : cameraStart.clone();
        rewardAnchor = rewardAnchor == null ? null : rewardAnchor.clone();
    }

    @Override
    public Location cameraStart() {
        return cameraStart == null ? null : cameraStart.clone();
    }

    @Override
    public Location rewardAnchor() {
        return rewardAnchor == null ? null : rewardAnchor.clone();
    }

    public static CrateDefinition fromSection(String id, ConfigurationSection section, ConfigurationSection defaults) {
        if (section == null) {
            return null;
        }
        String displayName = section.getString("display-name", id);
        CrateType type = CrateType.fromString(section.getString("type", "normal"));
        String openMode = section.getString("open-mode", "reward-only");
        String keyModel = section.getString("key-model", "");
        String keyMaterialName = section.getString("key-material", "TRIPWIRE_HOOK");
        Material keyMaterial = Material.matchMaterial(keyMaterialName);
        if (keyMaterial == null) {
            keyMaterial = Material.TRIPWIRE_HOOK;
        }
        int cooldown = section.getInt("cooldown-seconds", 0);
        double cost = section.getDouble("cost", 0);
        double rerollCost = section.getDouble("reroll-cost", 0);
        String permission = section.getString("permission", "extracrates.open");
        int maxRerolls = Math.max(0, section.getInt("max-rerolls", 0));

        ConfigurationSection locations = section.getConfigurationSection("locations");
        Location cameraStart = null;
        Location rewardAnchor = null;
        String worldName = null;
        World world = null;
        if (locations != null) {
            worldName = locations.getString("world", "world");
            world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                java.util.List<World> worlds = org.bukkit.Bukkit.getWorlds();
                world = worlds.isEmpty() ? null : worlds.getFirst();
                worldName = world != null ? world.getName() : worldName;
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

        AllowedArea allowedArea = AllowedArea.fromSection(section.getConfigurationSection("allowed-area"), worldName);
        AnimationSettings animation = AnimationSettings.fromSection(section.getConfigurationSection("animation"));
        CutsceneSettings cutsceneSettings = CutsceneSettings.fromSections(section.getConfigurationSection("cutscene"), defaults == null ? null : defaults.getConfigurationSection("cutscene"));
        String rewardsPool = section.getString("rewards-pool", "");
        return new CrateDefinition(
                id,
                displayName,
                type,
                openMode,
                keyModel,
                keyMaterial,
                cooldown,
                cost,
                rerollCost,
                permission,
                maxRerolls,
                cameraStart,
                rewardAnchor,
                allowedArea,
                animation,
                cutsceneSettings,
                rewardsPool
        );
    }

    public record AnimationSettings(
            String path,
            String rewardModel,
            String hologramFormat,
            RewardFloatSettings rewardFloatSettings,
            RewardDisplaySettings rewardDisplaySettings
    ) {
        public static AnimationSettings fromSection(ConfigurationSection section) {
            if (section == null) {
                return new AnimationSettings(
                        "",
                        "",
                        "&e%reward_name%",
                        new RewardFloatSettings(0.8, 2.0, true),
                        RewardDisplaySettings.defaultSettings()
                );
            }
            String path = section.getString("path", "");
            String rewardModel = section.getString("reward-model", "");
            String hologram = section.getString("hologram-format", "");
            RewardFloatSettings rewardFloatSettings = RewardFloatSettings.fromSection(section.getConfigurationSection("reward-float"));
            RewardDisplaySettings rewardDisplaySettings = RewardDisplaySettings.fromSection(section.getConfigurationSection("reward-display"));
            return new AnimationSettings(path, rewardModel, hologram, rewardFloatSettings, rewardDisplaySettings);
        }
    }

    public record AllowedArea(String worldName, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public static AllowedArea fromSection(ConfigurationSection section, String defaultWorld) {
            if (section == null) {
                return null;
            }
            ConfigurationSection min = section.getConfigurationSection("min");
            ConfigurationSection max = section.getConfigurationSection("max");
            if (min == null || max == null) {
                return null;
            }
            String worldName = section.getString("world", defaultWorld);
            if (worldName == null || worldName.isBlank()) {
                return null;
            }
            double minX = Math.min(min.getDouble("x"), max.getDouble("x"));
            double minY = Math.min(min.getDouble("y"), max.getDouble("y"));
            double minZ = Math.min(min.getDouble("z"), max.getDouble("z"));
            double maxX = Math.max(min.getDouble("x"), max.getDouble("x"));
            double maxY = Math.max(min.getDouble("y"), max.getDouble("y"));
            double maxZ = Math.max(min.getDouble("z"), max.getDouble("z"));
            return new AllowedArea(worldName, minX, minY, minZ, maxX, maxY, maxZ);
        }

        public boolean contains(Location location) {
            if (location == null || location.getWorld() == null) {
                return false;
            }
            if (!location.getWorld().getName().equals(worldName)) {
                return false;
            }
            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    public record RewardFloatSettings(double height, double spinSpeed, boolean bobbing) {
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

    public record RewardDisplaySettings(
            int itemCount,
            double textLineSpacing,
            double orbitRadius,
            double orbitSpeed,
            double wobbleSpeed,
            double wobbleAmplitude,
            double pulseSpeed,
            double pulseScale,
            double glowScale,
            ParticleSettings particles,
            TrailSettings trail
    ) {
        public static RewardDisplaySettings defaultSettings() {
            return new RewardDisplaySettings(
                    1,
                    0.25,
                    0.6,
                    0.08,
                    0.08,
                    0.12,
                    0.12,
                    0.08,
                    0.2,
                    ParticleSettings.defaultSettings(),
                    TrailSettings.defaultSettings()
            );
        }

        public static RewardDisplaySettings fromSection(ConfigurationSection section) {
            RewardDisplaySettings defaults = defaultSettings();
            if (section == null) {
                return defaults;
            }
            return new RewardDisplaySettings(
                    section.getInt("item-count", defaults.itemCount()),
                    section.getDouble("text-line-spacing", defaults.textLineSpacing()),
                    section.getDouble("orbit-radius", defaults.orbitRadius()),
                    section.getDouble("orbit-speed", defaults.orbitSpeed()),
                    section.getDouble("wobble-speed", defaults.wobbleSpeed()),
                    section.getDouble("wobble-amplitude", defaults.wobbleAmplitude()),
                    section.getDouble("pulse-speed", defaults.pulseSpeed()),
                    section.getDouble("pulse-scale", defaults.pulseScale()),
                    section.getDouble("glow-scale", defaults.glowScale()),
                    ParticleSettings.fromSection(section.getConfigurationSection("particles"), defaults.particles()),
                    TrailSettings.fromSection(section.getConfigurationSection("trail"), defaults.trail())
            );
        }

        public int getItemCount() {
            return itemCount;
        }

        public double getTextLineSpacing() {
            return textLineSpacing;
        }

        public double getOrbitRadius() {
            return orbitRadius;
        }

        public double getOrbitSpeed() {
            return orbitSpeed;
        }

        public double getWobbleSpeed() {
            return wobbleSpeed;
        }

        public double getWobbleAmplitude() {
            return wobbleAmplitude;
        }

        public double getPulseSpeed() {
            return pulseSpeed;
        }

        public double getPulseScale() {
            return pulseScale;
        }

        public double getGlowScale() {
            return glowScale;
        }

        public ParticleSettings getParticles() {
            return particles;
        }

        public TrailSettings getTrail() {
            return trail;
        }
    }

    public record ParticleSettings(
            String type,
            int count,
            double spread,
            double speed,
            double radius,
            double yOffset,
            int interval,
            boolean enabled
    ) {
        public static ParticleSettings defaultSettings() {
            return new ParticleSettings(
                    "END_ROD",
                    2,
                    0.05,
                    0.01,
                    0.6,
                    0.1,
                    4,
                    true
            );
        }

        public static ParticleSettings fromSection(ConfigurationSection section) {
            return fromSection(section, defaultSettings());
        }

        public static ParticleSettings fromSection(ConfigurationSection section, ParticleSettings defaults) {
            if (section == null) {
                return defaults;
            }
            return new ParticleSettings(
                    section.getString("type", defaults.type()),
                    section.getInt("count", defaults.count()),
                    section.getDouble("spread", defaults.spread()),
                    section.getDouble("speed", defaults.speed()),
                    section.getDouble("radius", defaults.radius()),
                    section.getDouble("y-offset", defaults.yOffset()),
                    section.getInt("interval", defaults.interval()),
                    section.getBoolean("enabled", defaults.enabled())
            );
        }

        public String getType() {
            return type;
        }

        public int getCount() {
            return count;
        }

        public double getSpread() {
            return spread;
        }

        public double getSpeed() {
            return speed;
        }

        public double getRadius() {
            return radius;
        }

        public double getYOffset() {
            return yOffset;
        }

        public int getInterval() {
            return interval;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    public record TrailSettings(
            String type,
            int count,
            double spread,
            double speed,
            int interval,
            double spacing,
            int length,
            boolean enabled
    ) {
        public static TrailSettings defaultSettings() {
            return new TrailSettings(
                    "END_ROD",
                    1,
                    0.02,
                    0.01,
                    2,
                    0.15,
                    12,
                    false
            );
        }

        public static TrailSettings fromSection(ConfigurationSection section) {
            return fromSection(section, defaultSettings());
        }

        public static TrailSettings fromSection(ConfigurationSection section, TrailSettings defaults) {
            if (section == null) {
                return defaults;
            }
            return new TrailSettings(
                    section.getString("type", defaults.type()),
                    section.getInt("count", defaults.count()),
                    section.getDouble("spread", defaults.spread()),
                    section.getDouble("speed", defaults.speed()),
                    section.getInt("interval", defaults.interval()),
                    section.getDouble("spacing", defaults.spacing()),
                    section.getInt("length", defaults.length()),
                    section.getBoolean("enabled", defaults.enabled())
            );
        }

        public String getType() {
            return type;
        }

        public int getCount() {
            return count;
        }

        public double getSpread() {
            return spread;
        }

        public double getSpeed() {
            return speed;
        }

        public int getInterval() {
            return interval;
        }

        public double getSpacing() {
            return spacing;
        }

        public int getLength() {
            return length;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    public record CutsceneSettings(String overlayModel, boolean lockMovement, boolean hideHud, MusicSettings musicSettings) {
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

    public record MusicSettings(String sound, float volume, float pitch, int fadeInTicks, int fadeOutTicks, String category) {
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
