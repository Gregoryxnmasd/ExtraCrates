package com.extracrates.runtime;

import com.extracrates.config.ConfigLoader;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class CameraEntityFactory {
    private final ConfigLoader configLoader;

    public CameraEntityFactory(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public Entity spawn(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("World is required to spawn camera entity.");
        }
        EntityType type = parseEntityType();
        Entity entity = world.spawnEntity(location, type);
        configure(entity);
        return entity;
    }

    private EntityType parseEntityType() {
        String raw = configLoader.getMainConfig().getString("camera-entity", "armorstand");
        if (raw == null || raw.isBlank()) {
            return EntityType.ARMOR_STAND;
        }
        String normalized = raw.trim().replace("-", "_").toUpperCase(Locale.ROOT);
        try {
            return EntityType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return EntityType.ARMOR_STAND;
        }
    }

    private void configure(Entity entity) {
        entity.setGravity(false);
        entity.setInvulnerable(true);
        if (entity instanceof ArmorStand stand) {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setSilent(true);
        } else if (entity instanceof ItemDisplay display) {
            display.setItemStack(new ItemStack(Material.AIR));
        } else if (entity instanceof LivingEntity living) {
            living.setInvisible(true);
            living.setSilent(true);
            living.setAI(false);
        }
    }
}
