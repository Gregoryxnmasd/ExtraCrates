package com.extracrates.resourcepack;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

public class ResourcePackRegistry {
    private final Map<String, Integer> modelData;

    public ResourcePackRegistry(Map<String, Integer> modelData) {
        this.modelData = new HashMap<>(modelData);
    }

    public static ResourcePackRegistry fromConfig(FileConfiguration config) {
        if (config == null) {
            return new ResourcePackRegistry(Collections.emptyMap());
        }
        ConfigurationSection section = config.getConfigurationSection("resourcepack.models");
        if (section == null) {
            return new ResourcePackRegistry(Collections.emptyMap());
        }
        Map<String, Integer> models = new HashMap<>();
        for (String key : section.getKeys(false)) {
            OptionalInt value = toModelData(section.get(key));
            value.ifPresent(modelData -> models.put(normalize(key), modelData));
        }
        return new ResourcePackRegistry(models);
    }

    public OptionalInt resolveCustomModelData(String customModel) {
        if (customModel == null) {
            return OptionalInt.empty();
        }
        String trimmed = customModel.trim();
        if (trimmed.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
        }
        Integer mapped = modelData.get(normalize(trimmed));
        if (mapped == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(mapped);
    }

    public Map<String, Integer> getModelData() {
        return Collections.unmodifiableMap(modelData);
    }

    private static OptionalInt toModelData(Object value) {
        if (value instanceof Number number) {
            return OptionalInt.of(number.intValue());
        }
        if (value == null) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT);
    }
}
