package com.extracrates.runtime;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.TextDisplay;

import java.util.Locale;

public final class CameraEntityFactory {
    private CameraEntityFactory() {
    }

    public static Entity spawn(Location start, String cameraEntityType, boolean armorStandInvisible) {
        String type = cameraEntityType == null ? "armorstand" : cameraEntityType.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "display", "itemdisplay", "item_display" -> start.getWorld().spawn(start, ItemDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setShadowRadius(0.0f);
                display.setShadowStrength(0.0f);
            });
            case "textdisplay", "text_display" -> start.getWorld().spawn(start, TextDisplay.class, display -> {
                display.text(Component.empty());
                display.setBillboard(Display.Billboard.CENTER);
                display.setShadowRadius(0.0f);
                display.setShadowStrength(0.0f);
            });
            case "armorstand", "armor_stand" -> start.getWorld().spawn(start, ArmorStand.class, stand -> {
                stand.setInvisible(armorStandInvisible);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setSilent(true);
            });
            default -> start.getWorld().spawn(start, ArmorStand.class, stand -> {
                stand.setInvisible(armorStandInvisible);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setSilent(true);
            });
        };
    }
}
