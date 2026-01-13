package com.extracrates.util;

import com.extracrates.config.ConfigLoader;
import com.extracrates.model.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

public final class ItemUtil {
    private ItemUtil() {
    }

    public static ItemStack buildItem(Reward reward, World world, ConfigLoader configLoader, MapImageCache mapImageCache) {
        Material material = Material.matchMaterial(reward.getItem().toUpperCase(Locale.ROOT));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material, Math.max(1, reward.getAmount()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.color(reward.getDisplayName()));
            if (reward.getCustomModel() != null && !reward.getCustomModel().isEmpty()) {
                try {
                    int modelData = Integer.parseInt(reward.getCustomModel());
                    meta.setCustomModelData(modelData);
                } catch (NumberFormatException ignored) {
                    // Allow string keys for resourcepack mapping handled elsewhere.
                }
            }
            for (Map.Entry<String, Integer> entry : reward.getEnchantments().entrySet()) {
                Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(entry.getKey().toLowerCase(Locale.ROOT)));
                if (enchantment != null) {
                    meta.addEnchant(enchantment, entry.getValue(), true);
                }
            }
            if (reward.isGlow()) {
                Enchantment glowEnchant = Enchantment.getByKey(NamespacedKey.minecraft("luck_of_the_sea"));
                if (glowEnchant != null) {
                    meta.addEnchant(glowEnchant, 1, true);
                }
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            applyMapImage(reward, meta, world, configLoader, mapImageCache);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void applyMapImage(
            Reward reward,
            ItemMeta meta,
            World world,
            ConfigLoader configLoader,
            MapImageCache mapImageCache
    ) {
        if (!(meta instanceof MapMeta mapMeta)) {
            return;
        }
        if (Material.matchMaterial(reward.getItem().toUpperCase(Locale.ROOT)) != Material.FILLED_MAP) {
            return;
        }
        String mapImage = reward.getMapImage();
        if (mapImage == null || mapImage.isBlank()) {
            return;
        }
        boolean allowMapImages = configLoader.getMainConfig().getBoolean("resourcepack.allow-map-images", true);
        if (!allowMapImages) {
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
}
