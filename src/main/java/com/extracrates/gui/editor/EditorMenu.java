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
    // Layout: acciones principales al centro (fila media), navegación en la fila inferior.
    private static final int SLOT_ACTION_CRATES = 10;
    private static final int SLOT_ACTION_REWARDS = 12;
    private static final int SLOT_ACTION_PATHS = 14;
    private static final int SLOT_ACTION_KEYS = 16;
    private static final int SLOT_NAV_CLOSE = 22;
    private static final int[] NAV_FILLER_SLOTS = {18, 19, 20, 21, 23, 24, 25, 26};

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
        inventory.setItem(SLOT_ACTION_CRATES, buildItem(Material.CHEST, "&eCrates", List.of("&7Crear, editar y borrar crates.")));
        inventory.setItem(SLOT_ACTION_REWARDS, buildItem(Material.EMERALD, "&aRewards", List.of("&7Editar pools y recompensas.")));
        inventory.setItem(SLOT_ACTION_PATHS, buildItem(Material.ENDER_EYE, "&bPaths", List.of("&7Gestionar rutas cinemáticas.")));
        inventory.setItem(SLOT_ACTION_KEYS, buildItem(Material.TRIPWIRE_HOOK, "&dLlaves", List.of("&7Buscar jugador y gestionar llaves.")));
        fillNavigation(inventory);
        inventory.setItem(SLOT_NAV_CLOSE, buildItem(Material.BARRIER, "&cCerrar", List.of("&7Salir del editor.")));
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
            case SLOT_ACTION_CRATES -> crateEditorMenu.open(player);
            case SLOT_ACTION_REWARDS -> rewardEditorMenu.openPools(player);
            case SLOT_ACTION_PATHS -> pathEditorMenu.open(player);
            case SLOT_ACTION_KEYS -> keyManagerMenu.open(player);
            case SLOT_NAV_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void fillNavigation(Inventory inventory) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : NAV_FILLER_SLOTS) {
            inventory.setItem(slot, filler);
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
