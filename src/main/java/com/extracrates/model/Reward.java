package com.extracrates.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Reward {
    private final String id;
    private final double chance;
    private final String displayName;
    private final String item;
    private final int amount;
    private final String customModel;
    private final boolean glow;
    private final Map<String, Integer> enchantments;
    private final List<String> commands;
    private final RewardMessage message;
    private final RewardEffects effects;
    private final String hologram;
    private final String mapImage;

    public Reward(
            String id,
            double chance,
            String displayName,
            String item,
            int amount,
            String customModel,
            boolean glow,
            Map<String, Integer> enchantments,
            List<String> commands,
            RewardMessage message,
            RewardEffects effects,
            String hologram,
            String mapImage
    ) {
        this.id = id;
        this.chance = chance;
        this.displayName = displayName;
        this.item = item;
        this.amount = amount;
        this.customModel = customModel;
        this.glow = glow;
        this.enchantments = enchantments;
        this.commands = commands;
        this.message = message;
        this.effects = effects;
        this.hologram = hologram;
        this.mapImage = mapImage;
    }

    public String getId() {
        return id;
    }

    public double getChance() {
        return chance;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getItem() {
        return item;
    }

    public int getAmount() {
        return amount;
    }

    public String getCustomModel() {
        return customModel;
    }

    public boolean isGlow() {
        return glow;
    }

    public Map<String, Integer> getEnchantments() {
        return enchantments;
    }

    public List<String> getCommands() {
        return commands;
    }

    public RewardMessage getMessage() {
        return message;
    }

    public RewardEffects getEffects() {
        return effects;
    }

    public String getHologram() {
        return hologram;
    }

    public String getMapImage() {
        return mapImage;
    }

    public static Reward fromSection(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        double chance = section.getDouble("chance", 0);
        String displayName = section.getString("display-name", id);
        String item = section.getString("item", "STONE");
        int amount = section.getInt("amount", 1);
        String customModel = section.getString("custom-model", "");
        boolean glow = section.getBoolean("glow", false);
        Map<String, Integer> enchantments = section.getConfigurationSection("enchantments") != null
                ? section.getConfigurationSection("enchantments").getValues(false).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> toInt(e.getValue())))
                : Collections.emptyMap();
        List<String> commands = section.getStringList("commands");
        RewardMessage message = RewardMessage.fromSection(section.getConfigurationSection("messages"));
        RewardEffects effects = RewardEffects.fromSection(section.getConfigurationSection("effects"));
        String hologram = section.getString("hologram", "");
        String mapImage = section.getString("map-image", "");
        return new Reward(id, chance, displayName, item, amount, customModel, glow, enchantments, commands, message, effects, hologram, mapImage);
    }

    public static class RewardMessage {
        private final String title;
        private final String subtitle;

        public RewardMessage(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

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

    public static class RewardEffects {
        private final String particles;
        private final String sound;
        private final String animation;

        public RewardEffects(String particles, String sound, String animation) {
            this.particles = particles;
            this.sound = sound;
            this.animation = animation;
        }

        public String getParticles() {
            return particles;
        }

        public String getSound() {
            return sound;
        }

        public String getAnimation() {
            return animation;
        }

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
}
