package com.extracrates.hologram;

import com.extracrates.ExtraCratesPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HologramProviderFactory {
    private HologramProviderFactory() {
    }

    public static HologramProvider create(ExtraCratesPlugin plugin, FileConfiguration config, HologramSettings settings) {
        String providerName = config.getString("holograms.provider", "display")
                .toLowerCase(Locale.ROOT);
        List<String> order = new ArrayList<>();
        if ("auto".equals(providerName)) {
            order.add("decentholograms");
            order.add("holographicdisplays");
            order.add("display");
        } else {
            order.add(providerName);
            order.add("decentholograms");
            order.add("holographicdisplays");
            order.add("display");
        }

        for (String candidate : order) {
            HologramProvider provider = buildProvider(candidate, plugin, settings);
            if (provider != null && provider.isAvailable()) {
                if (!candidate.equals(providerName)) {
                    plugin.getLogger().info("Hologram provider fallback activado: " + provider.getName());
                }
                return provider;
            }
        }
        HologramProvider fallback = new TextDisplayProvider(settings);
        plugin.getLogger().warning("Hologram provider no disponible, usando TextDisplay por defecto.");
        return fallback;
    }

    private static HologramProvider buildProvider(String name, ExtraCratesPlugin plugin, HologramSettings settings) {
        if (name == null) {
            return null;
        }
        return switch (name) {
            case "display", "textdisplay", "text-display" -> new TextDisplayProvider(settings);
            case "holographicdisplays", "holographic-displays" -> new HolographicDisplaysProvider(plugin);
            case "decentholograms", "decent-holograms" -> new DecentHologramsProvider();
            default -> null;
        };
    }
}
