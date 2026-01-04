package com.extracrates.util;

import com.extracrates.model.Reward;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Map;

public final class ItemUtil {
    private ItemUtil() {
    }

    public static ItemStack buildItem(Reward reward) {
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
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
