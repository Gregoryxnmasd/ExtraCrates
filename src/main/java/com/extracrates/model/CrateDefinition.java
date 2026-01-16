package com.extracrates.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public class CrateDefinition {
    private final String id;
    private final String displayName;
    private final CrateType type;
    private final String openMode;
    private final String keyModel;
    private final Material keyMaterial;
    private final int cooldownSeconds;
    private final double cost;
    private final String permission;
    private final Location cameraStart;
    private final Location rewardAnchor;
    private final AnimationSettings animation;
    private final String rewardsPool;

    public CrateDefinition(
            String id,
            String displayName,
            CrateType type,
            String openMode,
            String keyModel,
            Material keyMaterial,
            int cooldownSeconds,
            double cost,
            String permission,
            Location cameraStart,
            Location rewardAnchor,
            AnimationSettings animation,
            String rewardsPool
    ) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.openMode = openMode;
        this.keyModel = keyModel;
        this.keyMaterial = keyMaterial;
        this.cooldownSeconds = cooldownSeconds;
        this.cost = cost;
        this.permission = permission;
        this.cameraStart = cameraStart;
        this.rewardAnchor = rewardAnchor;
        this.animation = animation;
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

    public Material getKeyMaterial() {
        return keyMaterial;
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

    public String getRewardsPool() {
        return rewardsPool;
    }

    public static CrateDefinition fromSection(String id, ConfigurationSection section) {
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
        String rewardsPool = section.getString("rewards-pool", "");
        return new CrateDefinition(id, displayName, type, openMode, keyModel, keyMaterial, cooldown, cost, permission, cameraStart, rewardAnchor, animation, rewardsPool);
    }

    public static class AnimationSettings {
        private final String path;
        private final String rewardModel;
        private final String hologramFormat;
        private final RewardFloatSettings rewardFloatSettings;
        private final RewardDisplaySettings rewardDisplaySettings;

        public AnimationSettings(
                String path,
                String rewardModel,
                String hologramFormat,
                RewardFloatSettings rewardFloatSettings,
                RewardDisplaySettings rewardDisplaySettings
        ) {
            this.path = path;
            this.rewardModel = rewardModel;
            this.hologramFormat = hologramFormat;
            this.rewardFloatSettings = rewardFloatSettings;
            this.rewardDisplaySettings = rewardDisplaySettings;
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

        public RewardDisplaySettings getRewardDisplaySettings() {
            return rewardDisplaySettings;
        }

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

    public static class RewardDisplaySettings {
        private final int itemCount;
        private final double textLineSpacing;
        private final double orbitRadius;
        private final double orbitSpeed;
        private final double pulseScale;
        private final double pulseSpeed;
        private final double wobbleAmplitude;
        private final double wobbleSpeed;
        private final double glowScale;
        private final ParticleSettings particles;
        private final TrailSettings trail;

        public RewardDisplaySettings(
                int itemCount,
                double textLineSpacing,
                double orbitRadius,
                double orbitSpeed,
                double pulseScale,
                double pulseSpeed,
                double wobbleAmplitude,
                double wobbleSpeed,
                double glowScale,
                ParticleSettings particles,
                TrailSettings trail
        ) {
            this.itemCount = Math.max(1, itemCount);
            this.textLineSpacing = textLineSpacing;
            this.orbitRadius = orbitRadius;
            this.orbitSpeed = orbitSpeed;
            this.pulseScale = pulseScale;
            this.pulseSpeed = pulseSpeed;
            this.wobbleAmplitude = wobbleAmplitude;
            this.wobbleSpeed = wobbleSpeed;
            this.glowScale = glowScale;
            this.particles = particles;
            this.trail = trail;
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

        public double getPulseScale() {
            return pulseScale;
        }

        public double getPulseSpeed() {
            return pulseSpeed;
        }

        public double getWobbleAmplitude() {
            return wobbleAmplitude;
        }

        public double getWobbleSpeed() {
            return wobbleSpeed;
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

        public static RewardDisplaySettings defaultSettings() {
            return new RewardDisplaySettings(
                    3,
                    0.22,
                    0.45,
                    0.12,
                    0.15,
                    0.2,
                    0.08,
                    0.2,
                    0.2,
                    ParticleSettings.defaultSettings(),
                    TrailSettings.defaultSettings()
            );
        }

        public static RewardDisplaySettings fromSection(ConfigurationSection section) {
            if (section == null) {
                return defaultSettings();
            }
            return new RewardDisplaySettings(
                    section.getInt("item-count", 3),
                    section.getDouble("text-line-spacing", 0.22),
                    section.getDouble("orbit-radius", 0.45),
                    section.getDouble("orbit-speed", 0.12),
                    section.getDouble("pulse-scale", 0.15),
                    section.getDouble("pulse-speed", 0.2),
                    section.getDouble("wobble-amplitude", 0.08),
                    section.getDouble("wobble-speed", 0.2),
                    section.getDouble("glow-scale", 0.2),
                    ParticleSettings.fromSection(section.getConfigurationSection("particles")),
                    TrailSettings.fromSection(section.getConfigurationSection("trail"))
            );
        }
    }

    public static class ParticleSettings {
        private final String type;
        private final int count;
        private final double radius;
        private final double yOffset;
        private final double spread;
        private final double speed;
        private final int interval;

        public ParticleSettings(String type, int count, double radius, double yOffset, double spread, double speed, int interval) {
            this.type = type;
            this.count = count;
            this.radius = radius;
            this.yOffset = yOffset;
            this.spread = spread;
            this.speed = speed;
            this.interval = interval;
        }

        public String getType() {
            return type;
        }

        public int getCount() {
            return count;
        }

        public double getRadius() {
            return radius;
        }

        public double getYOffset() {
            return yOffset;
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

        public boolean isEnabled() {
            return type != null && !type.isEmpty() && count > 0;
        }

        public static ParticleSettings defaultSettings() {
            return new ParticleSettings("", 0, 0.35, 0.12, 0.02, 0.01, 2);
        }

        public static ParticleSettings fromSection(ConfigurationSection section) {
            if (section == null) {
                return defaultSettings();
            }
            return new ParticleSettings(
                    section.getString("type", ""),
                    section.getInt("count", 2),
                    section.getDouble("radius", 0.35),
                    section.getDouble("y-offset", 0.12),
                    section.getDouble("spread", 0.02),
                    section.getDouble("speed", 0.01),
                    section.getInt("interval", 2)
            );
        }
    }

    public static class TrailSettings {
        private final String type;
        private final int count;
        private final double spacing;
        private final int length;
        private final double spread;
        private final double speed;
        private final int interval;

        public TrailSettings(String type, int count, double spacing, int length, double spread, double speed, int interval) {
            this.type = type;
            this.count = count;
            this.spacing = spacing;
            this.length = length;
            this.spread = spread;
            this.speed = speed;
            this.interval = interval;
        }

        public String getType() {
            return type;
        }

        public int getCount() {
            return count;
        }

        public double getSpacing() {
            return spacing;
        }

        public int getLength() {
            return length;
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

        public boolean isEnabled() {
            return type != null && !type.isEmpty() && count > 0 && length > 0;
        }

        public static TrailSettings defaultSettings() {
            return new TrailSettings("", 0, 0.15, 8, 0.01, 0.01, 1);
        }

        public static TrailSettings fromSection(ConfigurationSection section) {
            if (section == null) {
                return defaultSettings();
            }
            return new TrailSettings(
                    section.getString("type", ""),
                    section.getInt("count", 1),
                    section.getDouble("spacing", 0.15),
                    section.getInt("length", 8),
                    section.getDouble("spread", 0.01),
                    section.getDouble("speed", 0.01),
                    section.getInt("interval", 1)
            );
        }
    }
}
