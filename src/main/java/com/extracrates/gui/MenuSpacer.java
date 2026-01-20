package com.extracrates.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MenuSpacer {
    private MenuSpacer() {
    }

    public static void apply(Inventory inventory, ItemStack spacer) {
        if (inventory == null || spacer == null) {
            return;
        }
        int size = inventory.getSize();
        if (size < 9) {
            return;
        }
        int rows = size / 9;
        if (rows < 2) {
            return;
        }
        fillRowIfEmpty(inventory, 0, spacer);
        if (rows >= 3) {
            fillRowIfEmpty(inventory, rows - 2, spacer);
        }
    }

    private static void fillRowIfEmpty(Inventory inventory, int rowIndex, ItemStack spacer) {
        int start = rowIndex * 9;
        int end = Math.min(start + 9, inventory.getSize());
        for (int slot = start; slot < end; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && existing.getType() != Material.AIR) {
                return;
            }
        }
        for (int slot = start; slot < end; slot++) {
            inventory.setItem(slot, spacer.clone());
        }
    }
}
