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
        boolean[] usedSlots = new boolean[inventory.getSize()];
        int nextSlot = 0;
        for (CrateDefinition crate : configLoader.getCrates().values()) {
            ConfigurationSection crateSection = configLoader.getMainConfig().getConfigurationSection("gui.crates." + crate.getId());
            int preferredSlot = crateSection != null ? crateSection.getInt("slot", -1) : -1;
            int slot = resolveSlot(preferredSlot, usedSlots, nextSlot, inventory.getSize());
            if (slot < 0) {
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
        CrateDefinition crate = resolveCrate(clicked, event.getSlot());
        if (crate == null) {
            return;
        }
        sessionManager.openCrate(player, crate);
        player.closeInventory();
    }

    private ItemStack buildCrateItem(CrateDefinition crate, ConfigurationSection crateSection) {
        Material material = Material.CHEST;
        if (crateSection != null) {
            String materialName = crateSection.getString("material", "CHEST");
            Material configuredMaterial = Material.matchMaterial(materialName);
            if (configuredMaterial != null) {
                material = configuredMaterial;
            }
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String displayName = crate.getDisplayName();
        if (crateSection != null && crateSection.contains("name")) {
            displayName = applyPlaceholders(crateSection.getString("name", displayName), crate);
        }
        meta.displayName(TextUtil.color(displayName));
        int customModelData = crateSection != null ? resolveCustomModelData(crateSection) : 0;
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        List<Component> lore = buildLore(crate, crateSection);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, crate.getId());
        item.setItemMeta(meta);
        return item;
    }

    private int resolveCustomModelData(ConfigurationSection crateSection) {
        if (crateSection == null) {
            return 0;
        }
        if (crateSection.isInt("icon")) {
            return crateSection.getInt("icon", 0);
        }
        if (crateSection.isString("icon")) {
            try {
                return Integer.parseInt(crateSection.getString("icon", "0"));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return crateSection.getInt("custom-model-data", 0);
    }

    private List<Component> buildLore(CrateDefinition crate, ConfigurationSection crateSection) {
        List<Component> lore = new ArrayList<>();
        if (crateSection != null && crateSection.contains("lore")) {
            for (String line : crateSection.getStringList("lore")) {
                lore.add(TextUtil.color(applyPlaceholders(line, crate)));
            }
            return lore;
        }
        lore.add(Component.text("ID: ").append(Component.text(crate.getId())));
        lore.add(Component.text("Tipo: ").append(Component.text(crate.getType().name())));
        return lore;
    }

    private String applyPlaceholders(String text, CrateDefinition crate) {
        if (text == null) {
            return "";
        }
        return text
                .replace("%crate_name%", crate.getDisplayName())
                .replace("%cooldown%", String.valueOf(crate.getCooldownSeconds()))
                .replace("%permission%", crate.getPermission());
    }

    private int resolveSlot(int preferredSlot, boolean[] usedSlots, int nextSlot, int size) {
        if (preferredSlot >= 0 && preferredSlot < size && !usedSlots[preferredSlot]) {
            return preferredSlot;
        }
        int slot = nextSlot;
        while (slot < size && usedSlots[slot]) {
            slot++;
        }
        return slot < size ? slot : -1;
    }

    private CrateDefinition resolveCrate(ItemStack clicked, int slot) {
        ItemMeta meta = clicked.getItemMeta();
        if (meta != null) {
            String crateId = meta.getPersistentDataContainer().get(crateKey, PersistentDataType.STRING);
            if (crateId != null) {
                return configLoader.getCrates().get(crateId);
            }
        }
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        if (slot >= crates.size()) {
            return null;
        }
        return crates.get(slot);
    }
}
