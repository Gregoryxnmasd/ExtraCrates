package com.extracrates.gui;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.model.CrateDefinition;
import com.extracrates.runtime.SessionManager;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CrateGui implements Listener {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final SessionManager sessionManager;

    public CrateGui(ExtraCratesPlugin plugin, ConfigLoader configLoader, SessionManager sessionManager) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.sessionManager = sessionManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        String title = configLoader.getMainConfig().getString("gui.title", "&8ExtraCrates");
        Inventory inventory = Bukkit.createInventory(player, 27, TextUtil.color(title));
        int slot = 0;
        for (CrateDefinition crate : configLoader.getCrates().values()) {
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.color(crate.getDisplayName()));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("ID: ").append(Component.text(crate.getId())));
                lore.add(Component.text("Tipo: ").append(Component.text(crate.getType().name())));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot++, item);
            if (slot >= inventory.getSize()) {
                break;
            }
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = configLoader.getMainConfig().getString("gui.title", "&8ExtraCrates");
        if (!event.getView().title().equals(TextUtil.color(title))) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        int slot = event.getSlot();
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        if (slot >= crates.size()) {
            return;
        }
        CrateDefinition crate = crates.get(slot);
        sessionManager.openCrate(player, crate, false);
        player.closeInventory();
    }
}
