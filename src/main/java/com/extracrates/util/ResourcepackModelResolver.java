package com.extracrates.util;

import com.extracrates.runtime.core.ConfigLoader;
import org.bukkit.configuration.ConfigurationSection;

public final class ResourcepackModelResolver {
    private static final ResourcepackModelResolver INSTANCE = new ResourcepackModelResolver();

    private ResourcepackModelResolver() {
    }

    public static ResourcepackModelResolver getInstance() {
        return INSTANCE;
    }

    public static int resolveCustomModelData(ConfigLoader configLoader, String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
        ConfigurationSection section = configLoader.getMainConfig().getConfigurationSection("resourcepack.models");
        if (section == null) {
            return -1;
        }
        Object raw = section.get(value);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    public int resolve(ConfigLoader configLoader, String value) {
        return resolveCustomModelData(configLoader, value);
    }
}
