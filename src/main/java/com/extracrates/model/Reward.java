package com.extracrates.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record Reward(
        String id,
        double chance,
        String displayName,
        String item,
        int amount,
        ItemStack itemStack,
        String customModel,
        boolean glow,
        Map<String, Integer> enchantments,
        List<String> commands,
        RewardMessage message,
        RewardEffects effects,
        RewardDisplayOverrides rewardDisplayOverrides,
        String hologram,
        String mapImage
) {
    public Reward {
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
        commands = commands == null ? List.of() : List.copyOf(commands);
        itemStack = itemStack == null ? null : itemStack.clone();
    }

    @Override
    public ItemStack itemStack() {
        return itemStack == null ? null : itemStack.clone();
    }

    public static Reward fromSection(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        double chance = section.getDouble("chance", 0);
        String displayName = section.getString("display-name", id);
        String item = section.getString("item", "STONE");
        int amount = section.getInt("amount", 1);
        ItemStack itemStack = section.getItemStack("item-stack");
        String customModel = section.getString("custom-model", "");
        boolean glow = section.getBoolean("glow", false);
        ConfigurationSection enchantmentsSection = section.getConfigurationSection("enchantments");
        Map<String, Integer> enchantments = Collections.emptyMap();
        if (enchantmentsSection != null) {
            Map<String, Object> values = enchantmentsSection.getValues(false);
            if (values != null && !values.isEmpty()) {
                enchantments = values.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> toInt(e.getValue())));
            }
        }
        List<String> commands = section.getStringList("commands");
        RewardMessage message = RewardMessage.fromSection(section.getConfigurationSection("messages"));
        RewardEffects effects = RewardEffects.fromSection(section.getConfigurationSection("effects"));
        RewardDisplayOverrides rewardDisplayOverrides = RewardDisplayOverrides.fromSection(section.getConfigurationSection("reward-display"));
        String hologram = section.getString("hologram", "");
        String mapImage = section.getString("map-image", "");
        return new Reward(id, chance, displayName, item, amount, itemStack, customModel, glow, enchantments, commands, message, effects, rewardDisplayOverrides, hologram, mapImage);
    }

    public record RewardMessage(String title, String subtitle) {
        public static RewardMessage fromSection(ConfigurationSection section) {
            if (section == null) {
                return new RewardMessage("", "");
            }
            return new RewardMessage(section.getString("title", ""), section.getString("subtitle", ""));
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public record RewardEffects(String particles, String sound, String animation) {
        public static RewardEffects fromSection(ConfigurationSection section) {
            if (section == null) {
                return new RewardEffects("", "", "");
            }
            return new RewardEffects(
                    section.getString("particles", ""),
                    section.getString("sound", ""),
                    section.getString("animation", "")
            );
        }
    }

    public record RewardDisplayOverrides(
            Integer itemCount,
            Double textLineSpacing,
            Double orbitRadius,
            Double orbitSpeed,
            Double wobbleSpeed,
            Double wobbleAmplitude,
            Double pulseSpeed,
            Double pulseScale,
            Double glowScale,
            ParticleOverrides particles,
            TrailOverrides trail
    ) {
        public static RewardDisplayOverrides fromSection(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            return new RewardDisplayOverrides(
                    readInt(section, "item-count"),
                    readDouble(section, "text-line-spacing"),
                    readDouble(section, "orbit-radius"),
                    readDouble(section, "orbit-speed"),
                    readDouble(section, "wobble-speed"),
                    readDouble(section, "wobble-amplitude"),
                    readDouble(section, "pulse-speed"),
                    readDouble(section, "pulse-scale"),
                    readDouble(section, "glow-scale"),
                    ParticleOverrides.fromSection(section.getConfigurationSection("particles")),
                    TrailOverrides.fromSection(section.getConfigurationSection("trail"))
            );
        }

        public CrateDefinition.RewardDisplaySettings applyTo(CrateDefinition.RewardDisplaySettings base) {
            if (base == null) {
                return null;
            }
            CrateDefinition.ParticleSettings baseParticles = base.getParticles();
            CrateDefinition.TrailSettings baseTrail = base.getTrail();
            CrateDefinition.ParticleSettings mergedParticles = baseParticles;
            if (particles != null) {
                mergedParticles = new CrateDefinition.ParticleSettings(
                        particles.type() != null ? particles.type() : baseParticles.getType(),
                        particles.count() != null ? particles.count() : baseParticles.getCount(),
                        particles.spread() != null ? particles.spread() : baseParticles.getSpread(),
                        particles.speed() != null ? particles.speed() : baseParticles.getSpeed(),
                        particles.radius() != null ? particles.radius() : baseParticles.getRadius(),
                        particles.yOffset() != null ? particles.yOffset() : baseParticles.getYOffset(),
                        particles.interval() != null ? particles.interval() : baseParticles.getInterval(),
                        particles.enabled() != null ? particles.enabled() : baseParticles.isEnabled()
                );
            }
            CrateDefinition.TrailSettings mergedTrail = baseTrail;
            if (trail != null) {
                mergedTrail = new CrateDefinition.TrailSettings(
                        trail.type() != null ? trail.type() : baseTrail.getType(),
                        trail.count() != null ? trail.count() : baseTrail.getCount(),
                        trail.spread() != null ? trail.spread() : baseTrail.getSpread(),
                        trail.speed() != null ? trail.speed() : baseTrail.getSpeed(),
                        trail.interval() != null ? trail.interval() : baseTrail.getInterval(),
                        trail.spacing() != null ? trail.spacing() : baseTrail.getSpacing(),
                        trail.length() != null ? trail.length() : baseTrail.getLength(),
                        trail.enabled() != null ? trail.enabled() : baseTrail.isEnabled()
                );
            }
            return new CrateDefinition.RewardDisplaySettings(
                    itemCount != null ? itemCount : base.getItemCount(),
                    textLineSpacing != null ? textLineSpacing : base.getTextLineSpacing(),
                    orbitRadius != null ? orbitRadius : base.getOrbitRadius(),
                    orbitSpeed != null ? orbitSpeed : base.getOrbitSpeed(),
                    wobbleSpeed != null ? wobbleSpeed : base.getWobbleSpeed(),
                    wobbleAmplitude != null ? wobbleAmplitude : base.getWobbleAmplitude(),
                    pulseSpeed != null ? pulseSpeed : base.getPulseSpeed(),
                    pulseScale != null ? pulseScale : base.getPulseScale(),
                    glowScale != null ? glowScale : base.getGlowScale(),
                    mergedParticles,
                    mergedTrail
            );
        }

        private static Integer readInt(ConfigurationSection section, String key) {
            return section.contains(key) ? section.getInt(key) : null;
        }

        private static Double readDouble(ConfigurationSection section, String key) {
            return section.contains(key) ? section.getDouble(key) : null;
        }
    }

    public record ParticleOverrides(
            String type,
            Integer count,
            Double spread,
            Double speed,
            Double radius,
            Double yOffset,
            Integer interval,
            Boolean enabled
    ) {
        public static ParticleOverrides fromSection(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            return new ParticleOverrides(
                    readString(section, "type"),
                    readInt(section, "count"),
                    readDouble(section, "spread"),
                    readDouble(section, "speed"),
                    readDouble(section, "radius"),
                    readDouble(section, "y-offset"),
                    readInt(section, "interval"),
                    readBoolean(section, "enabled")
            );
        }
    }

    public record TrailOverrides(
            String type,
            Integer count,
            Double spread,
            Double speed,
            Integer interval,
            Double spacing,
            Integer length,
            Boolean enabled
    ) {
        public static TrailOverrides fromSection(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            return new TrailOverrides(
                    readString(section, "type"),
                    readInt(section, "count"),
                    readDouble(section, "spread"),
                    readDouble(section, "speed"),
                    readInt(section, "interval"),
                    readDouble(section, "spacing"),
                    readInt(section, "length"),
                    readBoolean(section, "enabled")
            );
        }
    }

    private static Integer readInt(ConfigurationSection section, String key) {
        return section.contains(key) ? section.getInt(key) : null;
    }

    private static Double readDouble(ConfigurationSection section, String key) {
        return section.contains(key) ? section.getDouble(key) : null;
    }

    private static Boolean readBoolean(ConfigurationSection section, String key) {
        return section.contains(key) ? section.getBoolean(key) : null;
    }

    private static String readString(ConfigurationSection section, String key) {
        return section.contains(key) ? section.getString(key, "") : null;
    }
}
