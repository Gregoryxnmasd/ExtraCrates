package com.extracrates.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

public class TextDisplayProvider implements HologramProvider {
    private final HologramSettings settings;

    public TextDisplayProvider(HologramSettings settings) {
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "textdisplay";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public HologramInstance spawnHologram(Location location, Component text, Player viewer) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            NamespacedKey fontKey = NamespacedKey.fromString(settings.getFontKey());
            if (fontKey != null) {
                entity.setFont(fontKey);
            }
            HologramSettings.TextAnimationSettings animations = settings.getTextAnimationSettings();
            if (animations.isEnabled()) {
                entity.setInterpolationDelay(Math.max(0, animations.getInterpolationDelay()));
                entity.setInterpolationDuration(Math.max(0, animations.getInterpolationDuration()));
                entity.setTeleportDuration(Math.max(0, animations.getTeleportDuration()));
            }
        });
        return new TextDisplayInstance(display);
    }

    private static class TextDisplayInstance implements HologramInstance {
        private final TextDisplay display;

        private TextDisplayInstance(TextDisplay display) {
            this.display = display;
        }

        @Override
        public void setText(Component text) {
            if (display != null && !display.isDead()) {
                display.text(text);
            }
        }

        @Override
        public void teleport(Location location) {
            if (display != null && !display.isDead()) {
                display.teleport(location);
            }
        }

        @Override
        public void remove() {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }

        @Override
        public TextDisplay getEntity() {
            return display;
        }
    }
}
