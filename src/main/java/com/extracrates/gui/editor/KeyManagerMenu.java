package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.model.CrateDefinition;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.gui.MenuSpacer;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KeyManagerMenu implements Listener {
    private static final int LIST_SIZE = 54;
    // Layout: fila superior vacía, acciones en el centro, separación y footer.
    private static final int SLOT_LIST_DELETE = 53;
    private static final int SLOT_LIST_BACK = 49;
    private static final int SLOT_DETAIL_BACK = 31;
    private static final int SLOT_DETAIL_DELETE = 35;
    private static final int[] LIST_NAV_FILLER_SLOTS = {45, 46, 47, 48, 50, 51, 52};
    private static final int[] DETAIL_NAV_FILLER_SLOTS = {27, 28, 29, 30, 32, 33, 34};

    private static final int SLOT_SEARCH_FIND = 10;
    private static final int SLOT_SEARCH_PLAYER = 12;
    private static final int SLOT_SEARCH_MANAGE = 14;
    private static final int SLOT_DETAIL_ADD = 10;
    private static final int SLOT_DETAIL_STATUS = 12;
    private static final int SLOT_DETAIL_REMOVE = 14;

    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final SessionManager sessionManager;
    private final EditorInputManager inputManager;
    private final EditorMenu parent;
    private final Component searchTitle;
    private final Map<UUID, TargetSelection> activeTargets = new HashMap<>();
    private final Map<UUID, String> activeCrates = new HashMap<>();

    public KeyManagerMenu(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            SessionManager sessionManager,
            EditorInputManager inputManager,
            EditorMenu parent
    ) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.languageManager = plugin.getLanguageManager();
        this.sessionManager = sessionManager;
        this.inputManager = inputManager;
        this.parent = parent;
        this.searchTitle = TextUtil.colorNoItalic(text("editor.keys.title.search"));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        openSearch(player);
    }

    private void openSearch(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 36, searchTitle);
        inventory.setItem(SLOT_SEARCH_FIND, buildItem(
                Material.COMPASS,
                text("editor.keys.search.find.name"),
                List.of(text("editor.keys.search.find.lore"))
        ));
        TargetSelection target = activeTargets.get(player.getUniqueId());
        if (target != null) {
            inventory.setItem(SLOT_SEARCH_PLAYER, buildItem(
                    Material.NAME_TAG,
                    text("editor.keys.search.player.name"),
                    List.of(text("editor.keys.search.player.lore", Map.of("player", target.name())))
            ));
            inventory.setItem(SLOT_SEARCH_MANAGE, buildItem(
                    Material.TRIPWIRE_HOOK,
                    text("editor.keys.search.manage.name"),
                    List.of(text("editor.keys.search.manage.lore"))
            ));
        } else {
            inventory.setItem(SLOT_SEARCH_PLAYER, buildItem(
                    Material.BARRIER,
                    text("editor.keys.search.none.name"),
                    List.of(text("editor.keys.search.none.lore"))
            ));
            inventory.setItem(SLOT_SEARCH_MANAGE, buildItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    text("editor.keys.search.manage-disabled.name"),
                    List.of(text("editor.keys.search.manage-disabled.lore"))
            ));
        }
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE,
                text("editor.keys.nav.clear.name"),
                List.of(text("editor.keys.nav.clear.lore"))));
        inventory.setItem(SLOT_DETAIL_BACK, buildItem(Material.ARROW,
                text("editor.keys.nav.back-main.name"),
                List.of(text("editor.keys.nav.back-main.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openCrateList(Player player, TargetSelection target) {
        Inventory inventory = Bukkit.createInventory(player, LIST_SIZE, listTitle(target));
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        crates.sort(Comparator.comparing(CrateDefinition::id));
        int slot = 9;
        for (CrateDefinition crate : crates) {
            if (slot > 35) {
                break;
            }
            inventory.setItem(slot++, buildCrateItem(target, crate));
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_DELETE, buildItem(Material.RED_CONCRETE,
                text("editor.keys.nav.clear.name"),
                List.of(text("editor.keys.nav.clear.lore"))));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW,
                text("editor.keys.nav.back-search.name"),
                List.of(text("editor.keys.nav.back-search.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openCrateDetail(Player player, TargetSelection target, String crateId) {
        activeCrates.put(player.getUniqueId(), crateId);
        Inventory inventory = Bukkit.createInventory(player, 36, detailTitle(target, crateId));
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        int count = crate != null ? getKeyCount(target, crate) : 0;
        inventory.setItem(SLOT_DETAIL_ADD, buildItem(Material.LIME_CONCRETE,
                text("editor.keys.detail.add.name"),
                List.of(text("editor.keys.detail.add.lore"))));
        inventory.setItem(SLOT_DETAIL_STATUS, buildItem(Material.TRIPWIRE_HOOK, text("editor.keys.detail.status.name"), List.of(
                text("editor.keys.detail.status.crate", Map.of("crate", crateId)),
                text("editor.keys.detail.status.count", Map.of("count", String.valueOf(count)))
        )));
        inventory.setItem(SLOT_DETAIL_REMOVE, buildItem(Material.RED_CONCRETE,
                text("editor.keys.detail.remove.name"),
                List.of(text("editor.keys.detail.remove.lore"))));
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE,
                text("editor.keys.nav.clear.name"),
                List.of(text("editor.keys.nav.clear.lore"))));
        inventory.setItem(SLOT_DETAIL_BACK, buildItem(Material.ARROW,
                text("editor.keys.nav.back-list.name"),
                List.of(text("editor.keys.nav.back-list.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Component viewTitle = event.getView().title();
        if (viewTitle.equals(searchTitle)) {
            event.setCancelled(true);
            handleSearchClick(player, event.getSlot());
            return;
        }
        TargetSelection target = activeTargets.get(player.getUniqueId());
        if (target == null) {
            return;
        }
        if (viewTitle.equals(listTitle(target))) {
            event.setCancelled(true);
            handleListClick(player, target, event.getSlot());
            return;
        }
        String crateId = activeCrates.get(player.getUniqueId());
        if (crateId != null && viewTitle.equals(detailTitle(target, crateId))) {
            event.setCancelled(true);
            handleDetailClick(player, target, crateId, event.getSlot());
        }
    }

    private void handleSearchClick(Player player, int slot) {
        if (slot == SLOT_SEARCH_FIND) {
            promptTarget(player);
            return;
        }
        if (slot == SLOT_SEARCH_MANAGE) {
            TargetSelection target = activeTargets.get(player.getUniqueId());
            if (target != null) {
                openCrateList(player, target);
            }
            return;
        }
        if (slot == SLOT_DETAIL_DELETE) {
            clearTarget(player);
            return;
        }
        if (slot == SLOT_DETAIL_BACK) {
            parent.open(player);
            return;
        }
    }

    private void handleListClick(Player player, TargetSelection target, int slot) {
        if (slot == SLOT_LIST_DELETE) {
            clearTarget(player);
            return;
        }
        if (slot == SLOT_LIST_BACK) {
            openSearch(player);
            return;
        }
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        crates.sort(Comparator.comparing(CrateDefinition::id));
        int index = slot - 9;
        if (slot < 9 || slot > 35 || index < 0 || index >= crates.size()) {
            return;
        }
        openCrateDetail(player, target, crates.get(index).id());
    }

    private void handleDetailClick(Player player, TargetSelection target, String crateId, int slot) {
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        if (crate == null) {
            openCrateList(player, target);
            return;
        }
        switch (slot) {
            case SLOT_DETAIL_ADD -> adjustKeys(player, target, crate, 1);
            case SLOT_DETAIL_REMOVE -> adjustKeys(player, target, crate, -1);
            case SLOT_DETAIL_DELETE -> clearTarget(player);
            case SLOT_DETAIL_BACK -> openCrateList(player, target);
            default -> {
            }
        }
    }

    private void clearTarget(Player player) {
        activeTargets.remove(player.getUniqueId());
        activeCrates.remove(player.getUniqueId());
        openSearch(player);
    }

    private void promptTarget(Player player) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "editor.key.prompt.player-name", input -> {
            String value = input.trim();
            if (value.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.key.error.invalid-player"));
                return;
            }
            Player target = Bukkit.getPlayerExact(value);
            if (target == null) {
                player.sendMessage(languageManager.getMessage("editor.key.error.player-online-required"));
                return;
            }
            activeTargets.put(player.getUniqueId(), new TargetSelection(target.getUniqueId(), target.getName()));
        }, () -> {
            TargetSelection target = activeTargets.get(player.getUniqueId());
            if (target != null) {
                openCrateList(player, target);
            } else {
                openSearch(player);
            }
        });
    }

    private void adjustKeys(Player editor, TargetSelection target, CrateDefinition crate, int delta) {
        if (delta == 0) {
            return;
        }
        Player onlineTarget = Bukkit.getPlayer(target.id());
        if (onlineTarget == null) {
            editor.sendMessage(languageManager.getMessage("editor.key.error.player-online-required"));
            openSearch(editor);
            return;
        }
        if (delta > 0) {
            giveInventoryKey(editor, onlineTarget, crate);
        } else {
            removeInventoryKey(editor, onlineTarget, crate);
        }
        openCrateDetail(editor, target, crate.id());
    }

    private int getKeyCount(TargetSelection target, CrateDefinition crate) {
        Player onlineTarget = Bukkit.getPlayer(target.id());
        if (onlineTarget == null) {
            return 0;
        }
        return countInventoryKeys(onlineTarget, crate);
    }

    private ItemStack buildCrateItem(TargetSelection target, CrateDefinition crate) {
        List<String> lore = new ArrayList<>();
        lore.add(text("editor.keys.list.item.lore.id", Map.of("id", crate.id())));
        lore.add(text("editor.keys.list.item.lore.keys", Map.of("keys", String.valueOf(getKeyCount(target, crate)))));
        lore.add(text("editor.keys.list.item.lore.hint"));
        return buildItem(crate.keyMaterial(), crate.displayName(), lore);
    }

    private int countInventoryKeys(Player player, CrateDefinition crate) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (sessionManager.isKeyItem(item, crate)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void giveInventoryKey(Player editor, Player target, CrateDefinition crate) {
        int before = target.getInventory().firstEmpty();
        sessionManager.grantKey(target, crate, 1);
        if (before == -1) {
            editor.sendMessage(languageManager.getMessage("editor.key.error.inventory-full"));
        }
    }

    private void removeInventoryKey(Player editor, Player target, CrateDefinition crate) {
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!sessionManager.isKeyItem(item, crate)) {
                continue;
            }
            int amount = item.getAmount();
            if (amount <= 1) {
                contents[i] = null;
            } else {
                item.setAmount(amount - 1);
            }
            target.getInventory().setContents(contents);
            return;
        }
        editor.sendMessage(languageManager.getMessage("editor.key.error.no-keys"));
    }

    private Component listTitle(TargetSelection target) {
        return TextUtil.colorNoItalic(text("editor.keys.title.list", Map.of("player", target.name())));
    }

    private Component detailTitle(TargetSelection target, String crateId) {
        return TextUtil.colorNoItalic(text("editor.keys.title.detail", Map.of("crate", crateId, "player", target.name())));
    }

    private void fillListNavigation(Inventory inventory) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : LIST_NAV_FILLER_SLOTS) {
            inventory.setItem(slot, filler);
        }
    }

    private void fillDetailNavigation(Inventory inventory) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : DETAIL_NAV_FILLER_SLOTS) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack buildSpacerItem() {
        return buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private ItemStack buildItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorNoItalic(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream().map(TextUtil::colorNoItalic).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String text(String key) {
        return languageManager.getRaw(key, java.util.Collections.emptyMap());
    }

    private String text(String key, Map<String, String> placeholders) {
        return languageManager.getRaw(key, placeholders);
    }

    private record TargetSelection(UUID id, String name) {
    }
}
