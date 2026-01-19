package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.runtime.core.SessionManager;
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

import java.util.List;

@SuppressWarnings("unused")
public class EditorMenu implements Listener {
    private final ExtraCratesPlugin plugin;
    private final LanguageManager languageManager;
    private final Component title;
    private final CrateEditorMenu crateEditorMenu;
    private final RewardEditorMenu rewardEditorMenu;
    private final PathEditorMenu pathEditorMenu;
    private final KeyManagerMenu keyManagerMenu;

    public EditorMenu(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            EditorInputManager inputManager,
            ConfirmationMenu confirmationMenu,
            SessionManager sessionManager
    ) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.title = TextUtil.color(languageManager.getRaw("editor.menu.title", java.util.Collections.emptyMap()));
        this.crateEditorMenu = new CrateEditorMenu(plugin, configLoader, inputManager, confirmationMenu, this);
        this.rewardEditorMenu = new RewardEditorMenu(plugin, configLoader, inputManager, confirmationMenu, this);
        this.pathEditorMenu = new PathEditorMenu(plugin, configLoader, inputManager, confirmationMenu, this);
        this.keyManagerMenu = new KeyManagerMenu(plugin, configLoader, sessionManager, inputManager, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, title);
        inventory.setItem(11, buildItem(
                Material.CHEST,
                languageManager.getRaw("editor.menu.crates.name", java.util.Collections.emptyMap()),
                List.of(languageManager.getRaw("editor.menu.crates.lore", java.util.Collections.emptyMap()))
        ));
        inventory.setItem(13, buildItem(
                Material.EMERALD,
                languageManager.getRaw("editor.menu.rewards.name", java.util.Collections.emptyMap()),
                List.of(languageManager.getRaw("editor.menu.rewards.lore", java.util.Collections.emptyMap()))
        ));
        inventory.setItem(15, buildItem(
                Material.ENDER_EYE,
                languageManager.getRaw("editor.menu.paths.name", java.util.Collections.emptyMap()),
                List.of(languageManager.getRaw("editor.menu.paths.lore", java.util.Collections.emptyMap()))
        ));
        inventory.setItem(20, buildItem(
                Material.TRIPWIRE_HOOK,
                languageManager.getRaw("editor.menu.keys.name", java.util.Collections.emptyMap()),
                List.of(languageManager.getRaw("editor.menu.keys.lore", java.util.Collections.emptyMap()))
        ));
        inventory.setItem(22, buildItem(
                Material.BARRIER,
                languageManager.getRaw("editor.menu.close.name", java.util.Collections.emptyMap()),
                List.of(languageManager.getRaw("editor.menu.close.lore", java.util.Collections.emptyMap()))
        ));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().title().equals(title)) {
            return;
        }
        event.setCancelled(true);
        switch (event.getSlot()) {
            case 11 -> crateEditorMenu.open(player);
            case 13 -> rewardEditorMenu.openPools(player);
            case 15 -> pathEditorMenu.open(player);
            case 20 -> keyManagerMenu.open(player);
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    private ItemStack buildItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.color(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream().map(TextUtil::color).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
