package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.model.CrateDefinition;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.storage.CrateStorage;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
    // Layout: acciones principales al centro, navegación en fila inferior.
    private static final int SLOT_LIST_DELETE = 47;
    private static final int SLOT_LIST_BACK = 49;
    private static final int SLOT_LIST_REFRESH = 53;
    private static final int SLOT_DETAIL_DELETE = 18;
    private static final int SLOT_DETAIL_BACK = 22;
    private static final int SLOT_DETAIL_REFRESH = 26;
    private static final int[] LIST_NAV_FILLER_SLOTS = {45, 46, 48, 50, 51, 52};
    private static final int[] DETAIL_NAV_FILLER_SLOTS = {19, 20, 21, 23, 24, 25};

    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
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
        this.sessionManager = sessionManager;
        this.inputManager = inputManager;
        this.parent = parent;
        this.searchTitle = TextUtil.color("&8Llaves - Buscar");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        openSearch(player);
    }

    private void openSearch(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, searchTitle);
        inventory.setItem(11, buildItem(Material.COMPASS, "&eBuscar jugador", List.of("&7Escribe el nombre en el chat.")));
        TargetSelection target = activeTargets.get(player.getUniqueId());
        if (target != null) {
            inventory.setItem(13, buildItem(Material.NAME_TAG, "&aJugador", List.of("&7Actual: &f" + target.name())));
            inventory.setItem(15, buildItem(Material.TRIPWIRE_HOOK, "&dGestionar llaves", List.of("&7Ver crates del jugador.")));
        } else {
            inventory.setItem(13, buildItem(Material.BARRIER, "&7Sin jugador", List.of("&7Busca un jugador primero.")));
            inventory.setItem(15, buildItem(Material.GRAY_STAINED_GLASS_PANE, "&7Gestionar", List.of("&7Selecciona un jugador.")));
        }
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE, "&cBorrar selección", List.of("&7Limpiar jugador seleccionado.")));
        inventory.setItem(SLOT_DETAIL_BACK, buildItem(Material.ARROW, "&eVolver", List.of("&7Regresar al menú principal.")));
        inventory.setItem(SLOT_DETAIL_REFRESH, buildItem(Material.BOOK, "&bRefrescar", List.of("&7Recargar vista.")));
        player.openInventory(inventory);
    }

    private void openCrateList(Player player, TargetSelection target) {
        Inventory inventory = Bukkit.createInventory(player, LIST_SIZE, listTitle(target));
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        crates.sort(Comparator.comparing(CrateDefinition::id));
        int slot = 0;
        for (CrateDefinition crate : crates) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, buildCrateItem(target, crate));
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_DELETE, buildItem(Material.RED_CONCRETE, "&cBorrar selección", List.of("&7Limpiar jugador seleccionado.")));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW, "&eVolver", List.of("&7Regresar a búsqueda.")));
        inventory.setItem(SLOT_LIST_REFRESH, buildItem(Material.BOOK, "&bRefrescar", List.of("&7Recargar lista.")));
        player.openInventory(inventory);
    }

    private void openCrateDetail(Player player, TargetSelection target, String crateId) {
        activeCrates.put(player.getUniqueId(), crateId);
        Inventory inventory = Bukkit.createInventory(player, 27, detailTitle(target, crateId));
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        int count = crate != null ? getKeyCount(target, crate) : 0;
        inventory.setItem(11, buildItem(Material.LIME_CONCRETE, "&aSumar", List.of("&7Agregar 1 llave.")));
        inventory.setItem(13, buildItem(Material.TRIPWIRE_HOOK, "&eLlaves", List.of(
                "&7Crate: &f" + crateId,
                "&7Cantidad: &f" + count
        )));
        inventory.setItem(15, buildItem(Material.RED_CONCRETE, "&cQuitar", List.of("&7Remover 1 llave.")));
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE, "&cBorrar selección", List.of("&7Limpiar jugador seleccionado.")));
        inventory.setItem(SLOT_DETAIL_BACK, buildItem(Material.ARROW, "&eVolver", List.of("&7Regresar al listado.")));
        inventory.setItem(SLOT_DETAIL_REFRESH, buildItem(Material.BOOK, "&bRefrescar", List.of("&7Recargar datos.")));
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
        if (slot == 11) {
            promptTarget(player);
            return;
        }
        if (slot == 15) {
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
        if (slot == SLOT_DETAIL_REFRESH) {
            openSearch(player);
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
        if (slot == SLOT_LIST_REFRESH) {
            openCrateList(player, target);
            return;
        }
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        crates.sort(Comparator.comparing(CrateDefinition::id));
        if (slot < 0 || slot >= crates.size() || slot >= 45) {
            return;
        }
        openCrateDetail(player, target, crates.get(slot).id());
    }

    private void handleDetailClick(Player player, TargetSelection target, String crateId, int slot) {
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        if (crate == null) {
            openCrateList(player, target);
            return;
        }
        switch (slot) {
            case 11 -> adjustKeys(player, target, crate, 1);
            case 15 -> adjustKeys(player, target, crate, -1);
            case SLOT_DETAIL_DELETE -> clearTarget(player);
            case SLOT_DETAIL_BACK -> openCrateList(player, target);
            case SLOT_DETAIL_REFRESH -> openCrateDetail(player, target, crateId);
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
            player.sendMessage(Component.text("Ya tienes una edición pendiente."));
            return;
        }
        inputManager.requestInput(player, "Nombre del jugador", input -> {
            String value = input.trim();
            if (value.isEmpty()) {
                player.sendMessage(Component.text("Jugador inválido."));
                openSearch(player);
                return;
            }
            if (sessionManager.isStorageEnabled()) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(value);
                if (!offline.isOnline() && !offline.hasPlayedBefore()) {
                    player.sendMessage(Component.text("Jugador no encontrado."));
                    openSearch(player);
                    return;
                }
                String displayName = offline.getName() != null ? offline.getName() : value;
                activeTargets.put(player.getUniqueId(), new TargetSelection(offline.getUniqueId(), displayName));
                openCrateList(player, activeTargets.get(player.getUniqueId()));
                return;
            }
            Player target = Bukkit.getPlayerExact(value);
            if (target == null) {
                player.sendMessage(Component.text("El jugador debe estar conectado."));
                openSearch(player);
                return;
            }
            activeTargets.put(player.getUniqueId(), new TargetSelection(target.getUniqueId(), target.getName()));
            openCrateList(player, activeTargets.get(player.getUniqueId()));
        });
    }

    private void adjustKeys(Player editor, TargetSelection target, CrateDefinition crate, int delta) {
        if (delta == 0) {
            return;
        }
        if (sessionManager.isStorageEnabled()) {
            CrateStorage storage = sessionManager.getStorage();
            if (delta > 0) {
                storage.addKey(target.id(), crate.id(), delta);
            } else {
                boolean removed = storage.consumeKey(target.id(), crate.id());
                if (!removed) {
                    editor.sendMessage(Component.text("El jugador no tiene llaves para esa crate."));
                }
            }
            openCrateDetail(editor, target, crate.id());
            return;
        }
        Player onlineTarget = Bukkit.getPlayer(target.id());
        if (onlineTarget == null) {
            editor.sendMessage(Component.text("El jugador debe estar conectado."));
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
        if (sessionManager.isStorageEnabled()) {
            return sessionManager.getStorage().getKeyCount(target.id(), crate.id());
        }
        Player onlineTarget = Bukkit.getPlayer(target.id());
        if (onlineTarget == null) {
            return 0;
        }
        return countInventoryKeys(onlineTarget, crate);
    }

    private ItemStack buildCrateItem(TargetSelection target, CrateDefinition crate) {
        List<String> lore = new ArrayList<>();
        lore.add("&7ID: &f" + crate.id());
        lore.add("&7Llaves: &f" + getKeyCount(target, crate));
        lore.add("&8Click: gestionar");
        return buildItem(crate.keyMaterial(), crate.displayName(), lore);
    }

    private int countInventoryKeys(Player player, CrateDefinition crate) {
        int modelData = resolveKeyModelData(crate);
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != crate.keyMaterial()) {
                continue;
            }
            if (modelData >= 0) {
                if (item.getItemMeta() == null || !item.getItemMeta().hasCustomModelData()) {
                    continue;
                }
                if (item.getItemMeta().getCustomModelData() != modelData) {
                    continue;
                }
            }
            count += item.getAmount();
        }
        return count;
    }

    private void giveInventoryKey(Player editor, Player target, CrateDefinition crate) {
        ItemStack item = new ItemStack(crate.keyMaterial());
        ItemMeta meta = item.getItemMeta();
        int modelData = resolveKeyModelData(crate);
        if (meta != null) {
            meta.displayName(TextUtil.color("&eLlave " + crate.displayName()));
            if (modelData >= 0) {
                meta.setCustomModelData(modelData);
            }
            item.setItemMeta(meta);
        }
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            editor.sendMessage(Component.text("El inventario del jugador está lleno."));
        }
    }

    private void removeInventoryKey(Player editor, Player target, CrateDefinition crate) {
        int modelData = resolveKeyModelData(crate);
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != crate.keyMaterial()) {
                continue;
            }
            if (modelData >= 0) {
                if (item.getItemMeta() == null || !item.getItemMeta().hasCustomModelData()) {
                    continue;
                }
                if (item.getItemMeta().getCustomModelData() != modelData) {
                    continue;
                }
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
        editor.sendMessage(Component.text("El jugador no tiene llaves para esa crate."));
    }

    private int resolveKeyModelData(CrateDefinition crate) {
        if (crate.keyModel() == null || crate.keyModel().isEmpty()) {
            return -1;
        }
        return ResourcepackModelResolver.resolveCustomModelData(configLoader, crate.keyModel());
    }

    private Component listTitle(TargetSelection target) {
        return TextUtil.color("&8Llaves: " + target.name());
    }

    private Component detailTitle(TargetSelection target, String crateId) {
        return TextUtil.color("&8" + crateId + " -> " + target.name());
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

    private record TargetSelection(UUID id, String name) {
    }
}
