package com.extracrates.gui;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.model.CrateDefinition;
import com.extracrates.runtime.SessionManager;
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
        String title = configLoader.getMainConfig().getString("gui.title", "&8ExtraCrates");
        Inventory inventory = Bukkit.createInventory(player, 27, TextUtil.color(title));
        int slot = 0;
        for (CrateDefinition crate : getAccessibleCrates(player)) {
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
            if (slot == nextSlot) {
                nextSlot = slot + 1;
            }
            usedSlots[slot] = true;
            ItemStack item = buildCrateItem(crate, crateSection);
            inventory.setItem(slot, item);
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
        List<CrateDefinition> crates = getAccessibleCrates(player);
        if (slot >= crates.size()) {
            return;
        }
        CrateDefinition crate = crates.get(slot);
        playClickSound(player);
        sessionManager.openCrate(player, crate);
        player.closeInventory();
    }

    private void playClickSound(Player player) {
        if (!configLoader.getMainConfig().getBoolean("gui.click-sounds", true)) {
            return;
        }
        String soundName = configLoader.getMainConfig().getString("gui.click-sound", "UI_BUTTON_CLICK");
        Sound sound = Sound.UI_BUTTON_CLICK;
        if (soundName != null) {
            try {
                sound = Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                sound = Sound.UI_BUTTON_CLICK;
            }
        }
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }
}
