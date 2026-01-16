package com.extracrates.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record Reward(
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
    public Reward {
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
        commands = commands == null ? List.of() : List.copyOf(commands);
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
        String hologram = section.getString("hologram", "");
        String mapImage = section.getString("map-image", "");
        return new Reward(id, chance, displayName, item, amount, customModel, glow, enchantments, commands, message, effects, hologram, mapImage);
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
}
