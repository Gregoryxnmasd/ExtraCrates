package com.extracrates.util;

import com.extracrates.config.SettingsSnapshot;
import com.extracrates.model.Reward;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Map;

public final class ItemUtil {
    private ItemUtil() {
    }

    public static ItemStack buildItem(Reward reward, SettingsSnapshot settings) {
        Material material = Material.matchMaterial(reward.getItem().toUpperCase(Locale.ROOT));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material, Math.max(1, reward.getAmount()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.color(reward.getDisplayName()));
            applyResourcepackModel(reward, meta, settings);
            applyMapImage(reward, meta, settings, material);
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
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void applyResourcepackModel(Reward reward, ItemMeta meta, SettingsSnapshot settings) {
        SettingsSnapshot.ResourcepackSettings resourcepack = settings.getResourcepack();
        if (!resourcepack.useCustomModelData()) {
            return;
        }
        String customModel = reward.getCustomModel();
        if (customModel == null || customModel.isEmpty()) {
            return;
        }
        try {
            int modelData = Integer.parseInt(customModel);
            meta.setCustomModelData(modelData);
        } catch (NumberFormatException ignored) {
            if (resourcepack.allowAnimatedItems()) {
                NamespacedKey key = NamespacedKey.fromString("extracrates:resourcepack_model");
                if (key != null) {
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, customModel);
                }
            }
        }
    }

    private static void applyMapImage(Reward reward, ItemMeta meta, SettingsSnapshot settings, Material material) {
        if (!(meta instanceof MapMeta) || (material != Material.MAP && material != Material.FILLED_MAP)) {
            return;
        }
        String mapImage = reward.getMapImage();
        if (mapImage == null || mapImage.isEmpty()) {
            return;
        }
        SettingsSnapshot.ResourcepackSettings resourcepack = settings.getResourcepack();
        if (!resourcepack.allowMapImages()) {
            return;
        }
        try {
            int modelData = Integer.parseInt(mapImage);
            if (resourcepack.useCustomModelData()) {
                meta.setCustomModelData(modelData);
            }
        } catch (NumberFormatException ignored) {
            NamespacedKey key = NamespacedKey.fromString("extracrates:map_image");
            if (key != null) {
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, mapImage);
            }
        }
    }
}
