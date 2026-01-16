package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
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
    private final Component title;
    private final CrateEditorMenu crateEditorMenu;
    private final RewardEditorMenu rewardEditorMenu;
    private final PathEditorMenu pathEditorMenu;

    public EditorMenu(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            EditorInputManager inputManager,
            ConfirmationMenu confirmationMenu
    ) {
        this.plugin = plugin;
        this.title = TextUtil.color("&8Editor ExtraCrates");
        this.crateEditorMenu = new CrateEditorMenu(plugin, configLoader, inputManager, confirmationMenu, this);
        this.rewardEditorMenu = new RewardEditorMenu(plugin, configLoader, inputManager, confirmationMenu, this);
        this.pathEditorMenu = new PathEditorMenu(plugin, configLoader, inputManager, confirmationMenu, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, title);
        inventory.setItem(11, buildItem(Material.CHEST, "&eCrates", List.of("&7Crear, editar y borrar crates.")));
        inventory.setItem(13, buildItem(Material.EMERALD, "&aRewards", List.of("&7Editar pools y recompensas.")));
        inventory.setItem(15, buildItem(Material.ENDER_EYE, "&bPaths", List.of("&7Gestionar rutas cinemáticas.")));
        inventory.setItem(22, buildItem(Material.BARRIER, "&cCerrar", List.of("&7Salir del editor.")));
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
