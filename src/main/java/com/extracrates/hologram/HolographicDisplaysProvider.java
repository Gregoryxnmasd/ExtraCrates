package com.extracrates.hologram;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class HolographicDisplaysProvider implements HologramProvider {
    private final ExtraCratesPlugin plugin;
    private final boolean available;
    private final Method createHologram;
    private final Method appendTextLine;
    private final Method clearLines;
    private final Method teleport;
    private final Method delete;

    public HolographicDisplaysProvider(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
        boolean resolved = false;
        Method createMethod = null;
        Method appendMethod = null;
        Method clearMethod = null;
        Method teleportMethod = null;
        Method deleteMethod = null;

        if (Bukkit.getPluginManager().getPlugin("HolographicDisplays") != null) {
            try {
                Class<?> apiClass = Class.forName("com.gmail.filoghost.holographicdisplays.api.HologramsAPI");
                Class<?> hologramClass = Class.forName("com.gmail.filoghost.holographicdisplays.api.Hologram");
                createMethod = apiClass.getMethod("createHologram", org.bukkit.plugin.Plugin.class, Location.class);
                appendMethod = hologramClass.getMethod("appendTextLine", String.class);
                clearMethod = hologramClass.getMethod("clearLines");
                teleportMethod = resolveTeleportMethod(hologramClass);
                deleteMethod = hologramClass.getMethod("delete");
                resolved = true;
            } catch (ReflectiveOperationException ignored) {
                resolved = false;
            }
        }

        this.available = resolved;
        this.createHologram = createMethod;
        this.appendTextLine = appendMethod;
        this.clearLines = clearMethod;
        this.teleport = teleportMethod;
        this.delete = deleteMethod;
    }

    @Override
    public String getName() {
        return "holographicdisplays";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public HologramInstance spawnHologram(Location location, Component text, Player viewer) {
        if (!available || location == null) {
            return null;
        }
        try {
            Object hologram = createHologram.invoke(null, plugin, location);
            appendTextLine.invoke(hologram, TextUtil.serializeLegacy(text));
            return new HolographicDisplaysInstance(hologram);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static Method resolveTeleportMethod(Class<?> hologramClass) throws ReflectiveOperationException {
        try {
            return hologramClass.getMethod("teleport", Location.class);
        } catch (NoSuchMethodException ex) {
            return hologramClass.getMethod("setLocation", Location.class);
        }
    }

    private class HolographicDisplaysInstance implements HologramInstance {
        private final Object hologram;

        private HolographicDisplaysInstance(Object hologram) {
            this.hologram = hologram;
        }

        @Override
        public void setText(Component text) {
            try {
                clearLines.invoke(hologram);
                appendTextLine.invoke(hologram, TextUtil.serializeLegacy(text));
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public void teleport(Location location) {
            try {
                teleport.invoke(hologram, location);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public void remove() {
            try {
                delete.invoke(hologram);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public Entity getEntity() {
            return null;
        }
    }
}
