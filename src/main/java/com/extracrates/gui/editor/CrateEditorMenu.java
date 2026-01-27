package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CrateType;
import com.extracrates.model.RewardPool;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.util.TextUtil;
import com.extracrates.gui.MenuSpacer;
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
    // Layout: fila superior vacía, acciones en el centro, separación y footer.
    private static final int SLOT_LIST_CREATE = 45;
    private static final int SLOT_LIST_BACK = 49;
    private static final int SLOT_DETAIL_BACK = 40;
    private static final int SLOT_DETAIL_DELETE = 44;
    private static final int[] LIST_NAV_FILLER_SLOTS = {46, 47, 48, 50, 51, 52, 53};
    private static final int[] DETAIL_NAV_FILLER_SLOTS = {36, 37, 38, 39, 41, 42, 43};

    private static final int SLOT_DETAIL_DISPLAY_NAME = 9;
    private static final int SLOT_DETAIL_REWARDS_POOL = 10;
    private static final int SLOT_DETAIL_TYPE = 11;
    private static final int SLOT_DETAIL_OPEN_MODE = 12;
    private static final int SLOT_DETAIL_PATH = 13;
    private static final int SLOT_DETAIL_REWARD_LOCATION = 14;
    private static final int SLOT_DETAIL_LOCK_MOVEMENT = 15;
    private static final int SLOT_DETAIL_LOCK_HUD = 16;
    private static final int SLOT_DETAIL_MUSIC = 17;
    private static final int SLOT_DETAIL_MAX_REROLLS = 18;
    private static final int SLOT_SELECTOR_START = 10;
    private static final int SLOT_SELECTOR_BACK = 31;
    private static final int[] SELECTOR_NAV_FILLER_SLOTS = {27, 28, 29, 30, 32, 33, 34, 35};

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
        this.title = TextUtil.colorNoItalic(text("editor.crates.list.title"));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, title);
        List<CrateDefinition> crates = getSortedCrates();
        int slot = 9;
        for (CrateDefinition crate : crates) {
            if (slot > 35) {
                break;
            }
            inventory.setItem(slot++, buildCrateItem(crate));
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_CREATE, buildItem(Material.LIME_CONCRETE,
                text("editor.crates.list.create.name"),
                List.of(text("editor.crates.list.create.lore"))));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW,
                text("editor.crates.list.back.name"),
                List.of(text("editor.crates.list.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openDetail(Player player, String crateId) {
        activeCrate.put(player.getUniqueId(), crateId);
        Inventory inventory = Bukkit.createInventory(player, 45, detailTitle(crateId));
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        inventory.setItem(SLOT_DETAIL_DISPLAY_NAME, buildItem(Material.NAME_TAG, text("editor.crates.detail.display-name.name"), List.of(
                text("editor.common.current", Map.of("value", crate != null ? crate.displayName() : crateId)),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_REWARDS_POOL, buildItem(Material.CHEST_MINECART, text("editor.crates.detail.rewards-pool.name"), List.of(
                text("editor.common.current", Map.of("value", crate != null ? crate.rewardsPool() : "")),
                text("editor.common.click-select")
        )));
        CrateType crateType = crate != null ? crate.type() : CrateType.NORMAL;
        inventory.setItem(SLOT_DETAIL_TYPE, buildItem(Material.COMPARATOR, text("editor.crates.detail.type.name"), List.of(
                text("editor.common.current", Map.of("value", crateType.name())),
                text("editor.crates.detail.type.description", Map.of("description", describeType(crateType))),
                text("editor.common.click-toggle")
        )));
        inventory.setItem(SLOT_DETAIL_OPEN_MODE, buildItem(Material.PAPER, text("editor.crates.detail.open-mode.name"), List.of(
                text("editor.common.current", Map.of("value", crate != null ? crate.openMode() : "reward-only")),
                text("editor.common.click-select")
        )));
        inventory.setItem(SLOT_DETAIL_PATH, buildItem(Material.ENDER_EYE, text("editor.crates.detail.path.name"), List.of(
                text("editor.common.current", Map.of("value", crate != null ? crate.animation().path() : "")),
                text("editor.common.click-select")
        )));
        inventory.setItem(SLOT_DETAIL_REWARD_LOCATION, buildItem(Material.ENDER_PEARL, textOrFallback("editor.crates.detail.reward-location.name", "&eReward location"), List.of(
                textOrFallback("editor.crates.detail.reward-location.desc", "&7Set where the reward item appears."),
                textOrFallback("editor.common.click-set", "&fClick to set.")
        )));
        inventory.setItem(SLOT_DETAIL_LOCK_MOVEMENT, buildItem(Material.IRON_BOOTS, text("editor.crates.detail.lock-movement.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(crate != null && crate.cutsceneSettings().lockMovement()))),
                text("editor.crates.detail.lock-movement.desc"),
                text("editor.common.click-toggle")
        )));
        inventory.setItem(SLOT_DETAIL_LOCK_HUD, buildItem(Material.PAPER, text("editor.crates.detail.lock-hud.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(crate != null && crate.cutsceneSettings().hideHud()))),
                text("editor.crates.detail.lock-hud.desc"),
                text("editor.common.click-toggle")
        )));
        String musicSound = "";
        if (crate != null && crate.cutsceneSettings().musicSettings() != null) {
            musicSound = crate.cutsceneSettings().musicSettings().sound();
        }
        inventory.setItem(SLOT_DETAIL_MUSIC, buildItem(Material.MUSIC_DISC_11, text("editor.crates.detail.music.name"), List.of(
                text("editor.common.current", Map.of("value", (musicSound == null || musicSound.isEmpty())
                        ? text("editor.common.none")
                        : musicSound)),
                text("editor.crates.detail.music.desc"),
                text("editor.common.click-edit")
        )));
        String maxRerolls = crate != null && crate.maxRerolls() != null ? crate.maxRerolls().toString() : text("editor.common.none");
        inventory.setItem(SLOT_DETAIL_MAX_REROLLS, buildItem(Material.ANVIL, text("editor.crates.detail.max-rerolls.name"), List.of(
                text("editor.common.current", Map.of("value", maxRerolls)),
                text("editor.crates.detail.max-rerolls.desc"),
                text("editor.common.click-edit")
        )));
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE,
                text("editor.crates.detail.delete.name"),
                List.of(text("editor.crates.detail.delete.lore"))));
        inventory.setItem(SLOT_DETAIL_BACK, buildItem(Material.ARROW,
                text("editor.crates.detail.back.name"),
                List.of(text("editor.crates.detail.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
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
        if (crateId != null && viewTitle.equals(openModeTitle(crateId))) {
            event.setCancelled(true);
            handleOpenModeSelection(player, crateId, event.getSlot());
            return;
        }
        if (crateId != null && viewTitle.equals(pathSelectorTitle(crateId))) {
            event.setCancelled(true);
            handlePathSelection(player, crateId, event.getSlot());
            return;
        }
        if (crateId != null && viewTitle.equals(detailTitle(crateId))) {
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
        List<CrateDefinition> crates = getSortedCrates();
        int index = slot - 9;
        if (slot < 9 || slot > 35 || index < 0 || index >= crates.size()) {
            return;
        }
        CrateDefinition crate = crates.get(index);
        if (rightClick && shiftClick) {
            confirmationMenu.open(
                    player,
                    languageManager.getRaw("editor.confirmation.title.delete", java.util.Collections.emptyMap()),
                    languageManager.getRaw("editor.crate.confirm.delete", Map.of("id", crate.id())),
                    () -> {
                        deleteCrate(crate.id());
                        player.sendMessage(languageManager.getMessage("editor.crate.success.deleted"));
                        open(player);
                    },
                    () -> open(player)
            );
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
            case SLOT_DETAIL_DISPLAY_NAME -> promptField(player, crateId, "display-name", "editor.crate.prompt.display-name");
            case SLOT_DETAIL_REWARDS_POOL -> openRewardsPoolSelector(player, crateId);
            case SLOT_DETAIL_TYPE -> toggleType(player, crateId);
            case SLOT_DETAIL_OPEN_MODE -> openOpenModeSelector(player, crateId);
            case SLOT_DETAIL_PATH -> openPathSelector(player, crateId);
            case SLOT_DETAIL_REWARD_LOCATION -> setRewardLocation(player, crateId);
            case SLOT_DETAIL_LOCK_MOVEMENT -> toggleCutsceneLock(player, crateId, "movement",
                    languageManager.getRaw("editor.crate.label.lock-movement", java.util.Collections.emptyMap()));
            case SLOT_DETAIL_LOCK_HUD -> toggleCutsceneLock(player, crateId, "hud",
                    languageManager.getRaw("editor.crate.label.lock-hud", java.util.Collections.emptyMap()));
            case SLOT_DETAIL_MUSIC -> promptField(player, crateId, "cutscene.music.sound", "editor.crate.prompt.music-sound");
            case SLOT_DETAIL_MAX_REROLLS -> promptField(player, crateId, "cutscene.max-rerolls", "editor.crate.prompt.max-rerolls");
            case SLOT_DETAIL_DELETE -> confirmDelete(player, crateId);
            case SLOT_DETAIL_BACK -> open(player);
            default -> {
            }
        }
    }

    private void confirmDelete(Player player, String crateId) {
        confirmationMenu.open(
                player,
                languageManager.getRaw("editor.confirmation.title.delete", java.util.Collections.emptyMap()),
                languageManager.getRaw("editor.crate.confirm.delete", Map.of("id", crateId)),
                () -> {
            deleteCrate(crateId);
            player.sendMessage(languageManager.getMessage("editor.crate.success.deleted"));
            open(player);
        }, () -> openDetail(player, crateId));
    }

    private void promptCreate(Player player) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "editor.crate.prompt.new-id", input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.input.invalid-id"));
                return;
            }
            if (configLoader.getCrates().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("command.crate-already-exists"));
                return;
            }
            createCrate(input);
            player.sendMessage(languageManager.getMessage("editor.crate.success.created"));
        }, () -> open(player));
    }

    private void promptClone(Player player, String sourceId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "editor.crate.prompt.clone-id", Map.of("id", sourceId), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.input.invalid-id"));
                return;
            }
            if (configLoader.getCrates().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("command.crate-already-exists"));
                return;
            }
            cloneCrate(sourceId, input);
            player.sendMessage(languageManager.getMessage("editor.crate.success.cloned"));
        }, () -> open(player));
    }

    private void promptField(Player player, String crateId, String field, String promptKey) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, promptKey, input -> {
            Object value = input;
            if ("cutscene.max-rerolls".equals(field)) {
                int parsed = parseInt(input);
                value = parsed <= 0 ? null : parsed;
            }
            updateCrateField(crateId, field, value);
            player.sendMessage(languageManager.getMessage("editor.crate.success.updated"));
        }, () -> openDetail(player, crateId));
    }

    private void toggleType(Player player, String crateId) {
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        CrateType current = crate != null ? crate.type() : CrateType.NORMAL;
        CrateType[] values = CrateType.values();
        CrateType next = values[(current.ordinal() + 1) % values.length];
        updateCrateField(crateId, "type", next.name().toLowerCase());
        player.sendMessage(languageManager.getMessage("editor.crate.success.type-updated"));
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
        player.sendMessage(languageManager.getMessage("editor.crate.success.lock-updated"));
        openDetail(player, crateId);
    }

    private void setRewardLocation(Player player, String crateId) {
        updateCrateLocation(crateId, player.getLocation());
        player.sendMessage(languageManager.getMessage("editor.crate.success.reward-location-updated"));
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
        config.set(path + ".cutscene.locks.movement", true);
        config.set(path + ".cutscene.locks.hud", true);
        config.set(path + ".cutscene.music.sound", "");
        config.set(path + ".rewards-pool", "");
        config.set(path + ".created-at", System.currentTimeMillis());
        saveConfig(config);
    }

    private void openRewardsPoolSelector(Player player, String crateId) {
        Inventory inventory = Bukkit.createInventory(player, 54, rewardsPoolTitle(crateId));
        List<RewardPool> pools = new ArrayList<>(configLoader.getRewardPools().values());
        pools.sort(Comparator.comparing((RewardPool pool) -> resolvePoolCreatedAt(pool.id()))
                .thenComparing(RewardPool::id, String.CASE_INSENSITIVE_ORDER));
        int slot = 9;
        for (RewardPool pool : pools) {
            if (slot > 35) {
                break;
            }
            inventory.setItem(slot++, buildItem(Material.EMERALD, "&a" + pool.id(), List.of(
                    text("editor.common.click-select")
            )));
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW,
                text("editor.crates.paths.back.name"),
                List.of(text("editor.crates.paths.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openOpenModeSelector(Player player, String crateId) {
        Inventory inventory = Bukkit.createInventory(player, 36, openModeTitle(crateId));
        int slot = SLOT_SELECTOR_START;
        for (String mode : List.of("reward-only", "preview-only", "economy-required", "full")) {
            inventory.setItem(slot++, buildItem(Material.BOOK, text("editor.crates.open-mode.option.name", Map.of("mode", mode)), List.of(
                    text("editor.crates.open-mode.option.desc." + mode),
                    text("editor.common.click-select")
            )));
        }
        fillSelectorNavigation(inventory);
        inventory.setItem(SLOT_SELECTOR_BACK, buildItem(Material.ARROW,
                text("editor.crates.detail.back.name"),
                List.of(text("editor.crates.detail.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openPathSelector(Player player, String crateId) {
        Inventory inventory = Bukkit.createInventory(player, 54, pathSelectorTitle(crateId));
        List<CutscenePath> paths = new ArrayList<>(configLoader.getPaths().values());
        paths.sort(Comparator.comparing((CutscenePath path) -> resolvePathCreatedAt(path.getId()))
                .thenComparing(CutscenePath::getId, String.CASE_INSENSITIVE_ORDER));
        int slot = 9;
        inventory.setItem(slot++, buildItem(Material.BARRIER, text("editor.common.none"), List.of(
                text("editor.common.click-select")
        )));
        for (CutscenePath path : paths) {
            if (slot > 35) {
                break;
            }
            inventory.setItem(slot++, buildItem(Material.ENDER_EYE, "&b" + path.getId(), List.of(
                    text("editor.common.click-select")
            )));
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW,
                text("editor.crates.paths.back.name"),
                List.of(text("editor.crates.paths.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void handleRewardsPoolSelection(Player player, String crateId, int slot) {
        if (slot == SLOT_LIST_BACK) {
            openDetail(player, crateId);
            return;
        }
        List<RewardPool> pools = new ArrayList<>(configLoader.getRewardPools().values());
        pools.sort(Comparator.comparing((RewardPool pool) -> resolvePoolCreatedAt(pool.id()))
                .thenComparing(RewardPool::id, String.CASE_INSENSITIVE_ORDER));
        int index = slot - 9;
        if (slot < 9 || slot > 35 || index < 0 || index >= pools.size()) {
            return;
        }
        RewardPool pool = pools.get(index);
        updateCrateField(crateId, "rewards-pool", pool.id());
        player.sendMessage(languageManager.getMessage("editor.crate.success.updated"));
        openDetail(player, crateId);
    }

    private void handleOpenModeSelection(Player player, String crateId, int slot) {
        if (slot == SLOT_SELECTOR_BACK) {
            openDetail(player, crateId);
            return;
        }
        int index = slot - SLOT_SELECTOR_START;
        List<String> modes = List.of("reward-only", "preview-only", "economy-required", "full");
        if (index < 0 || index >= modes.size()) {
            return;
        }
        updateCrateField(crateId, "open-mode", modes.get(index));
        player.sendMessage(languageManager.getMessage("editor.crate.success.updated"));
        openDetail(player, crateId);
    }

    private void handlePathSelection(Player player, String crateId, int slot) {
        if (slot == SLOT_LIST_BACK) {
            openDetail(player, crateId);
            return;
        }
        if (slot == 9) {
            updateCrateField(crateId, "animation.path", "");
            player.sendMessage(languageManager.getMessage("editor.crate.success.updated"));
            openDetail(player, crateId);
            return;
        }
        List<CutscenePath> paths = new ArrayList<>(configLoader.getPaths().values());
        paths.sort(Comparator.comparing((CutscenePath path) -> resolvePathCreatedAt(path.getId()))
                .thenComparing(CutscenePath::getId, String.CASE_INSENSITIVE_ORDER));
        int adjustedSlot = slot - 10;
        if (slot < 10 || slot > 35 || adjustedSlot < 0 || adjustedSlot >= paths.size()) {
            return;
        }
        CutscenePath path = paths.get(adjustedSlot);
        updateCrateField(crateId, "animation.path", path.getId());
        player.sendMessage(languageManager.getMessage("editor.crate.success.updated"));
        openDetail(player, crateId);
    }

    private void cloneCrate(String sourceId, String targetId) {
        FileConfiguration config = loadConfig();
        String sourcePath = "crates." + sourceId;
        String targetPath = "crates." + targetId;
        Object data = config.get(sourcePath);
        config.set(targetPath, data);
        config.set(targetPath + ".created-at", System.currentTimeMillis());
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

    private void updateCrateLocation(String crateId, org.bukkit.Location location) {
        FileConfiguration config = loadConfig();
        String basePath = "crates." + crateId + ".locations";
        config.set(basePath + ".world", location.getWorld().getName());
        config.set(basePath + ".reward-anchor.x", location.getX());
        config.set(basePath + ".reward-anchor.y", location.getY());
        config.set(basePath + ".reward-anchor.z", location.getZ());
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
        lore.add(text("editor.common.action.left-edit"));
        lore.add(text("editor.common.action.right-clone"));
        lore.add(text("editor.common.action.shift-right-delete"));
        return buildItem(Material.CHEST, crate.displayName(), lore);
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

    private void fillSelectorNavigation(Inventory inventory) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : SELECTOR_NAV_FILLER_SLOTS) {
            inventory.setItem(slot, filler);
        }
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

    private Component rewardsPoolTitle(String crateId) {
        return TextUtil.colorNoItalic(text("editor.crates.pools.title", Map.of("crate", crateId)));
    }

    private Component openModeTitle(String crateId) {
        return TextUtil.colorNoItalic(text("editor.crates.open-mode.title", Map.of("crate", crateId)));
    }

    private Component pathSelectorTitle(String crateId) {
        return TextUtil.colorNoItalic(text("editor.crates.paths.title", Map.of("crate", crateId)));
    }

    private Component detailTitle(String crateId) {
        return TextUtil.colorNoItalic(text("editor.crates.detail.title", Map.of("crate", crateId)));
    }

    private String text(String key) {
        return languageManager.getRaw(key, java.util.Collections.emptyMap());
    }

    private String text(String key, Map<String, String> placeholders) {
        return languageManager.getRaw(key, placeholders);
    }

    private String textOrFallback(String key, String fallback) {
        String value = text(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String describeType(CrateType type) {
        return languageManager.getRaw("editor.crates.detail.type.desc." + type.name().toLowerCase(), java.util.Collections.emptyMap());
    }

    private String formatLocation(org.bukkit.Location location) {
        return String.format("%.2f, %.2f, %.2f", location.getX(), location.getY(), location.getZ());
    }

    private int parseInt(String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<CrateDefinition> getSortedCrates() {
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        crates.sort(Comparator.comparing((CrateDefinition crate) -> resolveCreatedAt(crate.id()))
                .thenComparing(CrateDefinition::id, String.CASE_INSENSITIVE_ORDER));
        return crates;
    }

    private long resolveCreatedAt(String crateId) {
        FileConfiguration config = loadConfig();
        return config.getLong("crates." + crateId + ".created-at", 0L);
    }

    private long resolvePathCreatedAt(String pathId) {
        File file = new File(plugin.getDataFolder(), "paths.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getLong("paths." + pathId + ".created-at", 0L);
    }

    private long resolvePoolCreatedAt(String poolId) {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getLong("pools." + poolId + ".created-at", 0L);
    }
}
