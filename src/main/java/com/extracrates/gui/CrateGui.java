package com.extracrates.gui;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.model.CrateDefinition;
import com.extracrates.config.ConfigLoader;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CrateGui implements Listener {
    private static final int PAGE_SIZE = 27;
    private static final int INVENTORY_SIZE = 36;
    private static final int PREVIOUS_PAGE_SLOT = 30;
    private static final int NEXT_PAGE_SLOT = 32;
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final SessionManager sessionManager;
    private final NamespacedKey crateKey;

    public CrateGui(ExtraCratesPlugin plugin, ConfigLoader configLoader, SessionManager sessionManager) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.sessionManager = sessionManager;
        this.crateKey = new NamespacedKey(plugin, "crate-id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int pageIndex) {
        String title = configLoader.getMainConfig().getString("gui.title", "&8ExtraCrates");
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        int totalPages = Math.max(1, (int) Math.ceil(crates.size() / (double) PAGE_SIZE));
        int safePageIndex = Math.max(0, Math.min(pageIndex, totalPages - 1));
        Inventory inventory = Bukkit.createInventory(new CrateGuiHolder(safePageIndex), INVENTORY_SIZE, TextUtil.color(title));
        int startIndex = safePageIndex * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, crates.size());
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            CrateDefinition crate = crates.get(i);
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.color(crate.displayName()));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("ID: ").append(Component.text(crate.id())));
                lore.add(Component.text("Tipo: ").append(Component.text(crate.type().name())));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot++, item);
        }
        if (safePageIndex > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, buildNavItem(Material.ARROW, "&ePágina anterior"));
        }
        if (safePageIndex < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, buildNavItem(Material.ARROW, "&ePágina siguiente"));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof CrateGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        int slot = event.getSlot();
        if (slot == PREVIOUS_PAGE_SLOT) {
            open(player, holder.pageIndex() - 1);
            return;
        }
        CrateDefinition crate = crates.get(slot);
        sessionManager.openCrate(player, crate, false);
        player.closeInventory();
    }

    private ItemStack buildNavItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private record CrateGuiHolder(int pageIndex) implements org.bukkit.inventory.InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
