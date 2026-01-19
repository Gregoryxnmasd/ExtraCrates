package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CrateType;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CrateEditorMenu implements Listener {
    // Layout: acciones principales al centro, navegación en fila inferior.
    private static final int SLOT_LIST_CREATE = 45;
    private static final int SLOT_LIST_DELETE = 47;
    private static final int SLOT_LIST_BACK = 49;
    private static final int SLOT_LIST_REFRESH = 53;
    private static final int SLOT_DETAIL_DELETE = 18;
    private static final int SLOT_DETAIL_BACK = 22;
    private static final int SLOT_DETAIL_REFRESH = 26;
    private static final int[] LIST_NAV_FILLER_SLOTS = {46, 48, 50, 51, 52};
    private static final int[] DETAIL_NAV_FILLER_SLOTS = {19, 20, 21, 23, 24, 25};

    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final EditorInputManager inputManager;
    private final ConfirmationMenu confirmationMenu;
    private final EditorMenu parent;
    private final Component title;
    private final Map<UUID, String> activeCrate = new HashMap<>();

    public CrateEditorMenu(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            EditorInputManager inputManager,
            ConfirmationMenu confirmationMenu,
            EditorMenu parent
    ) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.languageManager = plugin.getLanguageManager();
        this.inputManager = inputManager;
        this.confirmationMenu = confirmationMenu;
        this.parent = parent;
        this.title = TextUtil.color(text("editor.crates.title"));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, title);
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        crates.sort(Comparator.comparing(CrateDefinition::id));
        int slot = 0;
        for (CrateDefinition crate : crates) {
            inventory.setItem(slot++, buildCrateItem(crate));
            if (slot >= 45) {
                break;
            }
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_CREATE, buildItem(Material.LIME_CONCRETE, "&aCrear crate", List.of("&7Nuevo crate desde cero.")));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW, "&eVolver", List.of("&7Regresar al menú principal.")));
        inventory.setItem(SLOT_LIST_REFRESH, buildItem(Material.BOOK, "&bRefrescar", List.of("&7Recargar lista.")));
        player.openInventory(inventory);
    }

    private void openDetail(Player player, String crateId) {
        activeCrate.put(player.getUniqueId(), crateId);
        Inventory inventory = Bukkit.createInventory(player, 27, TextUtil.color(text("editor.crates.detail.title", Map.of("crate", crateId))));
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        inventory.setItem(9, buildItem(Material.NAME_TAG, "&eDisplay Name", List.of(
                "&7Actual: &f" + (crate != null ? crate.displayName() : crateId),
                "&7Click para editar."
        )));
        inventory.setItem(10, buildItem(Material.CHEST_MINECART, "&eRewards Pool", List.of(
                "&7Actual: &f" + (crate != null ? crate.rewardsPool() : ""),
                "&7Click para seleccionar."
        )));
        inventory.setItem(11, buildItem(Material.COMPARATOR, "&eTipo", List.of(
                "&7Actual: &f" + (crate != null ? crate.type().name() : "NORMAL"),
                "&7Click para alternar."
        )));
        inventory.setItem(12, buildItem(Material.PAPER, "&eOpen Mode", List.of(
                "&7Actual: &f" + (crate != null ? crate.openMode() : "reward-only"),
                "&7Click para editar."
        )));
        inventory.setItem(13, buildItem(Material.CARVED_PUMPKIN, "&eCutscene overlay", List.of(
                "&7Actual: &f" + (crate != null ? crate.cutsceneSettings().overlayModel() : ""),
                "&7Click para editar."
        )));
        inventory.setItem(14, buildItem(Material.IRON_BOOTS, "&eLock movimiento", List.of(
                "&7Actual: &f" + (crate != null && crate.cutsceneSettings().lockMovement()),
                "&7Click para alternar."
        )));
        inventory.setItem(15, buildItem(Material.PAPER, "&eLock HUD", List.of(
                "&7Actual: &f" + (crate != null && crate.cutsceneSettings().hideHud()),
                "&7Click para alternar."
        )));
        String musicSound = "";
        if (crate != null && crate.cutsceneSettings().musicSettings() != null) {
            musicSound = crate.cutsceneSettings().musicSettings().sound();
        }
        inventory.setItem(16, buildItem(Material.MUSIC_DISC_11, "&eMúsica", List.of(
                "&7Actual: &f" + (musicSound == null || musicSound.isEmpty() ? "ninguna" : musicSound),
                "&7Click para editar."
        )));
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE, "&cBorrar crate", List.of("&7Eliminar crate actual.")));
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
        if (viewTitle.equals(title)) {
            event.setCancelled(true);
            handleListClick(player, event.getSlot(), event.isRightClick(), event.isShiftClick());
            return;
        }
        String crateId = activeCrate.get(player.getUniqueId());
        if (crateId != null && viewTitle.equals(rewardsPoolTitle(crateId))) {
            event.setCancelled(true);
            handleRewardsPoolSelection(player, crateId, event.getSlot());
            return;
        }
        if (crateId != null && viewTitle.equals(pathSelectorTitle(crateId))) {
            event.setCancelled(true);
            handlePathSelection(player, crateId, event.getSlot());
            return;
        }
        if (crateId != null && viewTitle.equals(TextUtil.color("&8Crate: " + crateId))) {
            event.setCancelled(true);
            handleDetailClick(player, crateId, event.getSlot());
        }
    }

    private void handleListClick(Player player, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == SLOT_LIST_CREATE) {
            promptCreate(player);
            return;
        }
        if (slot == SLOT_LIST_BACK) {
            parent.open(player);
            return;
        }
        if (slot == SLOT_LIST_REFRESH) {
            open(player);
            return;
        }
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        crates.sort(Comparator.comparing(CrateDefinition::id));
        if (slot < 0 || slot >= crates.size() || slot >= 45) {
            return;
        }
        CrateDefinition crate = crates.get(slot);
        if (rightClick && shiftClick) {
            confirmationMenu.open(
                    player,
                    text("editor.crates.confirm.delete-title"),
                    text("editor.crates.confirm.delete-description", Map.of("crate", crate.id())),
                    () -> {
                deleteCrate(crate.id());
                player.sendMessage(languageManager.getMessage("editor.crates.messages.deleted"));
                open(player);
            }, () -> open(player));
            return;
        }
        if (rightClick) {
            promptClone(player, crate.id());
            return;
        }
        openDetail(player, crate.id());
    }

    private void handleDetailClick(Player player, String crateId, int slot) {
        switch (slot) {
            case 9 -> promptField(player, crateId, "display-name", "Display name nuevo");
            case 10 -> promptField(player, crateId, "rewards-pool", "ID del rewards pool");
            case 11 -> toggleType(player, crateId);
            case 12 -> promptField(player, crateId, "open-mode", "Open mode (reward-only, cinematic, etc)");
            case 13 -> promptField(player, crateId, "cutscene.overlay-model", "Overlay model de la cutscene");
            case 14 -> toggleCutsceneLock(player, crateId, "movement", "movimiento");
            case 15 -> toggleCutsceneLock(player, crateId, "hud", "HUD");
            case 16 -> promptField(player, crateId, "cutscene.music.sound", "Música (ID de sonido)");
            case SLOT_DETAIL_DELETE -> confirmDelete(player, crateId);
            case SLOT_DETAIL_BACK -> open(player);
            case SLOT_DETAIL_REFRESH -> openDetail(player, crateId);
            default -> {
            }
        }
    }

    private void confirmDelete(Player player, String crateId) {
        confirmationMenu.open(player, "&8Confirmar borrado", "Eliminar crate " + crateId, () -> {
            deleteCrate(crateId);
            player.sendMessage(Component.text("Crate eliminada y guardada en YAML."));
            open(player);
        }, () -> openDetail(player, crateId));
    }

    private void promptCreate(Player player) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(Component.text("Ya tienes una edición pendiente."));
            return;
        }
        if (slot == 49) {
            confirmSelection(player, crateId, "rewards-pool", "limpiar rewards pool", "");
            return;
        }
        if (slot == 53) {
            openRewardsPoolSelector(player, crateId);
            return;
        }
        List<String> poolIds = new ArrayList<>(configLoader.getRewardPools().keySet());
        poolIds.sort(String.CASE_INSENSITIVE_ORDER);
        if (slot < 0 || slot >= poolIds.size() || slot >= 45) {
            return;
        }
        String poolId = poolIds.get(slot);
        confirmSelection(player, crateId, "rewards-pool", "cambiar rewards pool a " + poolId, poolId);
    }

    private void handlePathSelection(Player player, String crateId, int slot) {
        if (slot == 45) {
            openDetail(player, crateId);
            return;
        }
        if (slot == 49) {
            confirmSelection(player, crateId, "animation.path", "limpiar cutscene path", "");
            return;
        }
        if (slot == 53) {
            openPathSelector(player, crateId);
            return;
        }
        List<String> pathIds = new ArrayList<>(configLoader.getPaths().keySet());
        pathIds.sort(String.CASE_INSENSITIVE_ORDER);
        if (slot < 0 || slot >= pathIds.size() || slot >= 45) {
            return;
        }
        String pathId = pathIds.get(slot);
        confirmSelection(player, crateId, "animation.path", "cambiar cutscene path a " + pathId, pathId);
    }

    private void confirmSelection(Player player, String crateId, String field, String label, Object value) {
        confirmationMenu.open(
                player,
                "&8Confirmar cambio",
                "Actualizar " + label + " de " + crateId,
                () -> {
                    updateCrateField(crateId, field, value);
                    player.sendMessage(Component.text("Crate actualizada y guardada en YAML."));
                    openDetail(player, crateId);
                },
                () -> openDetail(player, crateId)
        );
    }

    private void promptCreate(Player player) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, text("editor.crates.prompts.new-id"), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.crates.messages.invalid-id"));
                return;
            }
            if (configLoader.getCrates().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("editor.crates.messages.exists"));
                return;
            }
            confirmationMenu.open(player, "&8Confirmar creación", "Crear crate " + input, () -> {
                createCrate(input);
                player.sendMessage(Component.text("Crate creada y guardada en YAML."));
                open(player);
            }, () -> open(player));
        }, () -> open(player));
    }

    private void promptClone(Player player, String sourceId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, text("editor.crates.prompts.clone-id", Map.of("source", sourceId)), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.crates.messages.invalid-id"));
                return;
            }
            if (configLoader.getCrates().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("editor.crates.messages.exists"));
                return;
            }
            confirmationMenu.open(
                    player,
                    text("editor.crates.confirm.clone-title"),
                    text("editor.crates.confirm.clone-description", Map.of("source", sourceId, "target", input)),
                    () -> {
                cloneCrate(sourceId, input);
                player.sendMessage(languageManager.getMessage("editor.crates.messages.cloned"));
                open(player);
            }, () -> open(player));
        }, () -> open(player));
    }

    private void promptField(Player player, String crateId, String field, String promptKey) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, prompt, input -> confirmationMenu.open(
                player,
                "&8Confirmar cambio",
                "Actualizar " + field + " de " + crateId,
                () -> {
                    updateCrateField(crateId, field, input);
                    player.sendMessage(Component.text("Crate actualizada y guardada en YAML."));
                    openDetail(player, crateId);
                },
                () -> openDetail(player, crateId)
        ), () -> openDetail(player, crateId));
    }

    private void toggleType(Player player, String crateId) {
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        CrateType current = crate != null ? crate.type() : CrateType.NORMAL;
        CrateType[] values = CrateType.values();
        CrateType next = values[(current.ordinal() + 1) % values.length];
        updateCrateField(crateId, "type", next.name().toLowerCase());
        player.sendMessage(Component.text("Tipo actualizado y guardado en YAML."));
        openDetail(player, crateId);
    }

    private void toggleCutsceneLock(Player player, String crateId, String lockKey, String label) {
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        boolean current = false;
        if (crate != null) {
            current = lockKey.equals("movement")
                    ? crate.cutsceneSettings().lockMovement()
                    : crate.cutsceneSettings().hideHud();
        }
        boolean next = !current;
        updateCrateField(crateId, "cutscene.locks." + lockKey, next);
        player.sendMessage(Component.text("Lock actualizado y guardado en YAML."));
        openDetail(player, crateId);
    }

    private void createCrate(String id) {
        FileConfiguration config = loadConfig();
        String path = "crates." + id;
        config.set(path + ".display-name", id);
        config.set(path + ".type", "normal");
        config.set(path + ".open-mode", "reward-only");
        config.set(path + ".key-model", "");
        config.set(path + ".cooldown-seconds", 0);
        config.set(path + ".cost", 0);
        config.set(path + ".reroll-cost", 0);
        config.set(path + ".permission", "extracrates.open");
        config.set(path + ".cutscene.overlay-model", "");
        config.set(path + ".cutscene.locks.movement", true);
        config.set(path + ".cutscene.locks.hud", true);
        config.set(path + ".cutscene.music.sound", "");
        config.set(path + ".rewards-pool", "");
        saveConfig(config);
    }

    private void cloneCrate(String sourceId, String targetId) {
        FileConfiguration config = loadConfig();
        String sourcePath = "crates." + sourceId;
        String targetPath = "crates." + targetId;
        Object data = config.get(sourcePath);
        config.set(targetPath, data);
        saveConfig(config);
    }

    private void deleteCrate(String id) {
        FileConfiguration config = loadConfig();
        config.set("crates." + id, null);
        saveConfig(config);
    }

    private void updateCrateField(String id, String field, Object value) {
        FileConfiguration config = loadConfig();
        config.set("crates." + id + "." + field, value);
        saveConfig(config);
    }

    private FileConfiguration loadConfig() {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfig(FileConfiguration config) {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        try {
            config.save(file);
            configLoader.loadAll();
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar crates.yml: " + ex.getMessage());
        }
    }

    private ItemStack buildCrateItem(CrateDefinition crate) {
        List<String> lore = new ArrayList<>();
        lore.add(text("editor.crates.list.item.lore.id", Map.of("id", crate.id())));
        lore.add(text("editor.crates.list.item.lore.type", Map.of("type", crate.type().name())));
        lore.add(text("editor.crates.list.item.lore.rewards", Map.of("pool", String.valueOf(crate.rewardsPool()))));
        lore.add(text("editor.crates.list.item.lore.hint"));
        return buildItem(Material.CHEST, crate.displayName(), lore);
    }

    private void fillListNavigation(Inventory inventory) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        inventory.setItem(SLOT_LIST_DELETE, buildItem(Material.RED_CONCRETE, "&cBorrar crate", List.of("&7Usa el detalle para borrar.")));
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

    private Component rewardsPoolTitle(String crateId) {
        return TextUtil.color("&8Rewards pool: " + crateId);
    }

    private Component pathSelectorTitle(String crateId) {
        return TextUtil.color("&8Cutscene path: " + crateId);
    }
}
