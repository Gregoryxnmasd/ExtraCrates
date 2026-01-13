package com.extracrates.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

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
        return new CrateDefinition(id, displayName, type, openMode, keyModel, cooldown, cost, permission, cameraStart, rewardAnchor, animation, rewardsPool);
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
                return new AnimationSettings("", "", "", new RewardFloatSettings(0.8, 2.0, true));
            }
            String path = section.getString("path", "");
            String rewardModel = section.getString("reward-model", "");
            String hologram = section.getString("hologram-format", "");
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
}
