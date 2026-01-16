package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
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

public class ConfirmationMenu implements Listener {
    private final ExtraCratesPlugin plugin;
    private final Map<UUID, ConfirmationRequest> confirmations = new HashMap<>();

    public ConfirmationMenu(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, String title, String description, Runnable onConfirm, Runnable onCancel) {
        Inventory inventory = Bukkit.createInventory(player, 9, TextUtil.color(title));
        inventory.setItem(3, buildItem(Material.LIME_WOOL, "&aConfirmar", description));
        inventory.setItem(5, buildItem(Material.RED_WOOL, "&cCancelar", "Volver sin guardar"));
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
                meta.lore(java.util.List.of(TextUtil.color("&7" + loreLine)));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private record ConfirmationRequest(String title, Runnable onConfirm, Runnable onCancel) {
    }
}
