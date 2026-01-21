package com.extracrates.util;

import com.extracrates.config.ConfigLoader;
import com.extracrates.model.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ItemUtil {
    private static final ConcurrentMap<CacheKey, ItemStack> ITEM_CACHE = new ConcurrentHashMap<>();

    private ItemUtil() {
    }

    public static ItemStack buildItem(Reward reward, World world, ConfigLoader configLoader, MapImageCache mapImageCache) {
        boolean debugTimings = configLoader != null && configLoader.getMainConfig().getBoolean("debug.timings", false);
        long start = debugTimings ? System.nanoTime() : 0L;
        CacheKey cacheKey = new CacheKey(reward.id(), worldKey(world), reward.customModel());
        ItemStack cached = ITEM_CACHE.get(cacheKey);
        if (cached != null) {
            if (debugTimings) {
                logTiming("ItemUtil.buildItem", cacheKey, true, start);
            }
            return cached.clone();
        }
        if (reward.itemStack() != null) {
            ItemStack item = reward.itemStack().clone();
            ITEM_CACHE.put(cacheKey, item.clone());
            if (debugTimings) {
                logTiming("ItemUtil.buildItem", cacheKey, false, start);
            }
            return item;
        }
        Bukkit.getLogger().warning(String.format(
                "Reward %s has no reward-item configured. Skipping delivery to keep NBT intact.",
                reward.id()
        ));
        ItemStack fallback = new ItemStack(Material.AIR);
        ITEM_CACHE.put(cacheKey, fallback.clone());
        if (debugTimings) {
            logTiming("ItemUtil.buildItem", cacheKey, false, start);
        }
        return fallback;
    }

    public static void clearItemCache() {
        ITEM_CACHE.clear();
    }

    private static String worldKey(World world) {
        return world != null ? world.getName() : "unknown";
    }

    private static void logTiming(String context, CacheKey cacheKey, boolean cacheHit, long start) {
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        Bukkit.getLogger().info(String.format(
                "%s reward=%s world=%s model=%s cache=%s timeMs=%.3f",
                context,
                cacheKey.rewardId(),
                cacheKey.world(),
                cacheKey.model(),
                cacheHit ? "hit" : "miss",
                ms
        ));
    }

    private record CacheKey(String rewardId, String world, String model) {
        private CacheKey {
            rewardId = rewardId != null ? rewardId : "unknown";
            world = world != null ? world : "unknown";
            model = model != null ? model : "";
        }
    }
}
