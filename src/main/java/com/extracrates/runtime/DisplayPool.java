package com.extracrates.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DisplayPool {
    private static final int MAX_POOL_SIZE = 64;

    private final Map<UUID, Deque<ArmorStand>> armorStands = new HashMap<>();
    private final Map<UUID, Deque<ItemDisplay>> itemDisplays = new HashMap<>();
    private final Map<UUID, Deque<TextDisplay>> textDisplays = new HashMap<>();

    public ArmorStand acquireArmorStand(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        ArmorStand stand = pollEntity(armorStands, world);
        if (stand == null) {
            stand = world.spawn(location, ArmorStand.class);
        } else {
            stand.teleport(location);
        }
        return stand;
    }

    public ItemDisplay acquireItemDisplay(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        ItemDisplay display = pollEntity(itemDisplays, world);
        if (display == null) {
            display = world.spawn(location, ItemDisplay.class);
        } else {
            display.teleport(location);
        }
        display.setViewRange(1.0f);
        return display;
    }

    public TextDisplay acquireTextDisplay(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        TextDisplay display = pollEntity(textDisplays, world);
        if (display == null) {
            display = world.spawn(location, TextDisplay.class);
        } else {
            display.teleport(location);
        }
        display.setViewRange(1.0f);
        return display;
    }

    public void releaseArmorStand(ArmorStand stand) {
        if (stand == null || stand.isDead()) {
            return;
        }
        stand.setCustomName(null);
        stand.setCustomNameVisible(false);
        stand.setSilent(true);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setMarker(true);
        offerEntity(armorStands, stand);
    }

    public void releaseItemDisplay(ItemDisplay display) {
        if (display == null || display.isDead()) {
            return;
        }
        display.setItemStack(new ItemStack(Material.AIR));
        display.setRotation(0f, 0f);
        display.setViewRange(0.0f);
        offerEntity(itemDisplays, display);
    }

    public void releaseTextDisplay(TextDisplay display) {
        if (display == null || display.isDead()) {
            return;
        }
        display.text(Component.empty());
        display.setViewRange(0.0f);
        display.setBillboard(Display.Billboard.FIXED);
        offerEntity(textDisplays, display);
    }

    public void clear() {
        removeEntities(armorStands);
        removeEntities(itemDisplays);
        removeEntities(textDisplays);
    }

    private <T extends Entity> T pollEntity(Map<UUID, Deque<T>> poolMap, World world) {
        Deque<T> pool = poolMap.get(world.getUID());
        if (pool == null) {
            return null;
        }
        while (!pool.isEmpty()) {
            T entity = pool.pollFirst();
            if (entity != null && !entity.isDead()) {
                return entity;
            }
        }
        return null;
    }

    private <T extends Entity> void offerEntity(Map<UUID, Deque<T>> poolMap, T entity) {
        UUID worldId = entity.getWorld().getUID();
        Deque<T> pool = poolMap.computeIfAbsent(worldId, key -> new ArrayDeque<>());
        if (pool.size() >= MAX_POOL_SIZE) {
            entity.remove();
            return;
        }
        pool.addLast(entity);
    }

    private <T extends Entity> void removeEntities(Map<UUID, Deque<T>> poolMap) {
        for (Deque<T> pool : poolMap.values()) {
            for (T entity : pool) {
                if (entity != null && !entity.isDead()) {
                    entity.remove();
                }
            }
        }
        poolMap.clear();
    }
}
