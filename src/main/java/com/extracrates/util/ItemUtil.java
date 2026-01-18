package com.extracrates.util;

import com.extracrates.config.ConfigLoader;
import com.extracrates.config.SettingsSnapshot;
import com.extracrates.model.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
        Material material = Material.matchMaterial(reward.item().toUpperCase(Locale.ROOT));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material, Math.max(1, reward.amount()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            SettingsSnapshot settings = configLoader != null ? configLoader.getSettings() : null;
            meta.displayName(TextUtil.color(reward.displayName()));
            applyResourcepackModel(reward, meta, settings, configLoader);
            for (Map.Entry<String, Integer> entry : reward.enchantments().entrySet()) {
                Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(entry.getKey().toLowerCase(Locale.ROOT)));
                if (enchantment != null) {
                    meta.addEnchant(enchantment, entry.getValue(), true);
                }
            }
            if (reward.glow()) {
                Enchantment glowEnchant = Enchantment.getByKey(NamespacedKey.minecraft("luck_of_the_sea"));
                if (glowEnchant != null) {
                    meta.addEnchant(glowEnchant, 1, true);
                }
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            applyMapImage(reward, meta, material, settings, world, mapImageCache);
            item.setItemMeta(meta);
        }
        ITEM_CACHE.put(cacheKey, item.clone());
        if (debugTimings) {
            logTiming("ItemUtil.buildItem", cacheKey, false, start);
        }
        return item;
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

    private static void applyMapImage(
            Reward reward,
            ItemMeta meta,
            Material material,
            SettingsSnapshot settings,
            World world,
            MapImageCache mapImageCache
    ) {
        if (!(meta instanceof MapMeta mapMeta)) {
            return;
        }
        if (material != Material.FILLED_MAP) {
            return;
        }
        String mapImage = reward.mapImage();
        if (mapImage == null || mapImage.isBlank()) {
            return;
        }
        if (settings == null || !settings.getResourcepack().allowMapImages()) {
            return;
        }
        World resolvedWorld = world != null ? world : Bukkit.getWorlds().stream().findFirst().orElse(null);
        if (resolvedWorld == null) {
            return;
        }
        Optional<java.awt.image.BufferedImage> image = mapImageCache.getImage(mapImage);
        if (image.isEmpty()) {
            return;
        }
        MapView mapView = Bukkit.createMap(resolvedWorld);
        mapView.getRenderers().forEach(mapView::removeRenderer);
        mapView.addRenderer(new MapImageRenderer(image.get()));
        mapMeta.setMapView(mapView);
    }

    private static void applyResourcepackModel(
            Reward reward,
            ItemMeta meta,
            SettingsSnapshot settings,
            ConfigLoader configLoader
    ) {
        if (settings == null || !settings.getResourcepack().useCustomModelData()) {
            return;
        }
        String customModel = reward.customModel();
        if (customModel == null || customModel.isBlank()) {
            return;
        }
        Integer modelData = resolveModelData(customModel, settings, configLoader);
        if (modelData != null) {
            meta.setCustomModelData(modelData);
        }
    }

    private static Integer resolveModelData(
            String customModel,
            SettingsSnapshot settings,
            ConfigLoader configLoader
    ) {
        String trimmedModel = customModel.trim();
        if (settings.getResourcepack().allowAnimatedItems() && configLoader != null) {
            return configLoader.resolveModelData(trimmedModel);
        }
        try {
            return Integer.parseInt(trimmedModel);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record CacheKey(String rewardId, String world, String model) {
        private CacheKey {
            rewardId = rewardId != null ? rewardId : "unknown";
            world = world != null ? world : "unknown";
            model = model != null ? model : "";
        }
    }
}
