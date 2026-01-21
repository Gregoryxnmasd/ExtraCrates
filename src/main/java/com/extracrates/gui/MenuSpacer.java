package com.extracrates.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MenuSpacer {
    private MenuSpacer() {
    }

    public static void applyTopRow(Inventory inventory, ItemStack spacer) {
        if (inventory == null || spacer == null) {
            return;
        }
        int size = Math.min(9, inventory.getSize());
        for (int slot = 0; slot < size; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, spacer);
            }
        }
    }

    public static void apply(Inventory inventory, ItemStack spacer) {
        apply(inventory, spacer, true);
    }

    public static void apply(Inventory inventory, ItemStack spacer, boolean fillTopRow) {
        if (inventory == null || spacer == null) {
            return;
        }
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            if (!fillTopRow && slot < 9) {
                continue;
            }
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, spacer);
            }
        }
    }
}
