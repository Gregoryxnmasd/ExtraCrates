package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
public class ConfirmationMenu implements Listener {
    private final ExtraCratesPlugin plugin;
    private final LanguageManager languageManager;
    private final ConfigLoader configLoader;
    private final Map<UUID, ConfirmationRequest> confirmations = new HashMap<>();

    public ConfirmationMenu(ExtraCratesPlugin plugin, ConfigLoader configLoader) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.languageManager = plugin.getLanguageManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, String title, String description, Runnable onConfirm, Runnable onCancel) {
        if (!configLoader.getMainConfig().getBoolean("gui.editor-confirmations", true)) {
            onConfirm.run();
            return;
        }
        Inventory inventory = Bukkit.createInventory(player, 9, TextUtil.color(title));
        inventory.setItem(3, buildItem(Material.LIME_WOOL,
                languageManager.getRaw("editor.confirmation.confirm-button", java.util.Collections.emptyMap()),
                description));
        inventory.setItem(5, buildItem(Material.RED_WOOL,
                languageManager.getRaw("editor.confirmation.cancel-button", java.util.Collections.emptyMap()),
                languageManager.getRaw("editor.confirmation.cancel-description", java.util.Collections.emptyMap())));
        confirmations.put(player.getUniqueId(), new ConfirmationRequest(title, onConfirm, onCancel));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ConfirmationRequest request = confirmations.get(player.getUniqueId());
        if (request == null) {
            return;
        }
        if (!event.getView().title().equals(TextUtil.color(request.title()))) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (slot == 3) {
            confirmations.remove(player.getUniqueId());
            player.closeInventory();
            request.onConfirm().run();
        } else if (slot == 5) {
            confirmations.remove(player.getUniqueId());
            player.closeInventory();
            request.onCancel().run();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        ConfirmationRequest request = confirmations.get(event.getPlayer().getUniqueId());
        if (request == null) {
            return;
        }
        if (!event.getView().title().equals(TextUtil.color(request.title()))) {
            return;
        }
        confirmations.remove(event.getPlayer().getUniqueId());
        request.onCancel().run();
    }

    private ItemStack buildItem(Material material, String name, String loreLine) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.color(name));
            if (loreLine != null && !loreLine.isEmpty()) {
                meta.lore(java.util.List.of(TextUtil.color(loreLine)));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String confirmName() {
        return languageManager.getRaw("editor.confirmation.confirm-name", java.util.Collections.emptyMap());
    }

    private String cancelName() {
        return languageManager.getRaw("editor.confirmation.cancel-name", java.util.Collections.emptyMap());
    }

    private String cancelLore() {
        return languageManager.getRaw("editor.confirmation.cancel-lore", java.util.Collections.emptyMap());
    }

    private record ConfirmationRequest(String title, Runnable onConfirm, Runnable onCancel) {
    }
}
