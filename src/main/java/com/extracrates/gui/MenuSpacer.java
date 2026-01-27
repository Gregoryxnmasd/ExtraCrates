package com.extracrates.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public static void applyCenteredItems(Inventory inventory, int startSlot, int endSlot, List<ItemStack> items) {
        if (inventory == null || items == null || items.isEmpty()) {
            return;
        }
        List<Integer> slots = centeredSlots(startSlot, endSlot, items.size());
        int itemCount = Math.min(items.size(), slots.size());
        for (int i = 0; i < itemCount; i++) {
            inventory.setItem(slots.get(i), items.get(i));
        }
    }

    public static List<Integer> centeredSlots(int startSlot, int endSlot, int itemCount) {
        if (itemCount <= 0 || endSlot < startSlot) {
            return Collections.emptyList();
        }
        int startRow = startSlot / 9;
        int endRow = endSlot / 9;
        int totalRows = endRow - startRow + 1;
        int maxItems = Math.min(itemCount, totalRows * 9);
        int rowsNeeded = (int) Math.ceil(maxItems / 9.0);
        int startRowOffset = (int) Math.ceil((totalRows - rowsNeeded) / 2.0);
        List<Integer> slots = new ArrayList<>(maxItems);
        int remaining = maxItems;
        for (int rowIndex = 0; rowIndex < rowsNeeded; rowIndex++) {
            int row = startRow + startRowOffset + rowIndex;
            int itemsInRow = Math.min(9, remaining);
            int horizontalOffset = (9 - itemsInRow) / 2;
            int rowStartSlot = row * 9;
            for (int col = 0; col < itemsInRow; col++) {
                int slot = rowStartSlot + horizontalOffset + col;
                if (slot < startSlot || slot > endSlot) {
                    continue;
                }
                slots.add(slot);
            }
            remaining -= itemsInRow;
        }
        return slots;
    }

    public static int centeredIndex(int startSlot, int endSlot, int itemCount, int slot) {
        if (itemCount <= 0) {
            return -1;
        }
        List<Integer> slots = centeredSlots(startSlot, endSlot, itemCount);
        return slots.indexOf(slot);
    }
}
