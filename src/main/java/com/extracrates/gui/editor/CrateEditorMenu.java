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
        inventory.setItem(45, buildItem(
                Material.LIME_CONCRETE,
                text("editor.crates.list.create.name"),
                List.of(text("editor.crates.list.create.lore"))
        ));
        inventory.setItem(49, buildItem(
                Material.ARROW,
                text("editor.crates.list.back.name"),
                List.of(text("editor.crates.list.back.lore"))
        ));
        inventory.setItem(53, buildItem(
                Material.BOOK,
                text("editor.crates.list.refresh.name"),
                List.of(text("editor.crates.list.refresh.lore"))
        ));
        player.openInventory(inventory);
    }

    private void openDetail(Player player, String crateId) {
        activeCrate.put(player.getUniqueId(), crateId);
        Inventory inventory = Bukkit.createInventory(player, 27, TextUtil.color(text("editor.crates.detail.title", Map.of("crate", crateId))));
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        inventory.setItem(10, buildItem(Material.NAME_TAG, text("editor.crates.detail.display-name"), List.of(
                text("editor.crates.detail.current", Map.of("value", crate != null ? crate.displayName() : crateId)),
                text("editor.crates.detail.click-edit")
        )));
        inventory.setItem(12, buildItem(Material.CHEST_MINECART, text("editor.crates.detail.rewards-pool"), List.of(
                text("editor.crates.detail.current", Map.of("value", crate != null ? String.valueOf(crate.rewardsPool()) : "")),
                text("editor.crates.detail.click-edit")
        )));
        inventory.setItem(14, buildItem(Material.COMPARATOR, text("editor.crates.detail.type"), List.of(
                text("editor.crates.detail.current", Map.of("value", crate != null ? crate.type().name() : "NORMAL")),
                text("editor.crates.detail.click-toggle")
        )));
        inventory.setItem(16, buildItem(Material.PAPER, text("editor.crates.detail.open-mode"), List.of(
                text("editor.crates.detail.current", Map.of("value", crate != null ? crate.openMode() : "reward-only")),
                text("editor.crates.detail.click-edit")
        )));
        String overlayValue = "";
        if (crate != null && crate.cutsceneSettings().overlayModel() != null) {
            overlayValue = crate.cutsceneSettings().overlayModel();
        }
        inventory.setItem(19, buildItem(Material.CARVED_PUMPKIN, text("editor.crates.detail.cutscene-overlay"), List.of(
                text("editor.crates.detail.current", Map.of("value", overlayValue)),
                text("editor.crates.detail.click-edit")
        )));
        inventory.setItem(20, buildItem(Material.IRON_BOOTS, text("editor.crates.detail.lock-movement"), List.of(
                text("editor.crates.detail.current", Map.of("value", String.valueOf(crate != null && crate.cutsceneSettings().lockMovement()))),
                text("editor.crates.detail.click-toggle")
        )));
        inventory.setItem(21, buildItem(Material.PAPER, text("editor.crates.detail.lock-hud"), List.of(
                text("editor.crates.detail.current", Map.of("value", String.valueOf(crate != null && crate.cutsceneSettings().hideHud()))),
                text("editor.crates.detail.click-toggle")
        )));
        String musicSound = "";
        if (crate != null && crate.cutsceneSettings().musicSettings() != null) {
            musicSound = crate.cutsceneSettings().musicSettings().sound();
        }
        String musicValue = musicSound == null || musicSound.isEmpty()
                ? text("editor.common.none")
                : musicSound;
        inventory.setItem(23, buildItem(Material.MUSIC_DISC_11, text("editor.crates.detail.music"), List.of(
                text("editor.crates.detail.current", Map.of("value", musicValue)),
                text("editor.crates.detail.click-edit")
        )));
        inventory.setItem(22, buildItem(Material.ARROW, text("editor.crates.detail.back.name"), List.of(text("editor.crates.detail.back.lore"))));
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
        if (crateId != null && viewTitle.equals(detailTitle(crateId))) {
            event.setCancelled(true);
            handleDetailClick(player, crateId, event.getSlot());
        }
    }

    private void handleListClick(Player player, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == 45) {
            promptCreate(player);
            return;
        }
        if (slot == 49) {
            parent.open(player);
            return;
        }
        if (slot == 53) {
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
            case 10 -> promptField(player, crateId, "display-name", "editor.crates.prompts.display-name");
            case 12 -> promptField(player, crateId, "rewards-pool", "editor.crates.prompts.rewards-pool");
            case 14 -> toggleType(player, crateId);
            case 16 -> promptField(player, crateId, "open-mode", "editor.crates.prompts.open-mode");
            case 19 -> promptField(player, crateId, "cutscene.overlay-model", "editor.crates.prompts.cutscene-overlay");
            case 20 -> toggleCutsceneLock(player, crateId, "movement", "editor.crates.detail.lock-movement");
            case 21 -> toggleCutsceneLock(player, crateId, "hud", "editor.crates.detail.lock-hud");
            case 23 -> promptField(player, crateId, "cutscene.music.sound", "editor.crates.prompts.music");
            case 22 -> open(player);
            default -> {
            }
        }
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
            confirmationMenu.open(
                    player,
                    text("editor.crates.confirm.create-title"),
                    text("editor.crates.confirm.create-description", Map.of("crate", input)),
                    () -> {
                createCrate(input);
                player.sendMessage(languageManager.getMessage("editor.crates.messages.created"));
                open(player);
            }, () -> open(player));
        });
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
        });
    }

    private void promptField(Player player, String crateId, String field, String promptKey) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, text(promptKey), input -> confirmationMenu.open(
                player,
                text("editor.crates.confirm.update-title"),
                text("editor.crates.confirm.update-description", Map.of("field", field, "crate", crateId)),
                () -> {
                    updateCrateField(crateId, field, input);
                    player.sendMessage(languageManager.getMessage("editor.crates.messages.updated"));
                    openDetail(player, crateId);
                },
                () -> openDetail(player, crateId)
        ));
    }

    private void toggleType(Player player, String crateId) {
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        CrateType current = crate != null ? crate.type() : CrateType.NORMAL;
        CrateType[] values = CrateType.values();
        CrateType next = values[(current.ordinal() + 1) % values.length];
        confirmationMenu.open(
                player,
                text("editor.crates.confirm.update-title"),
                text("editor.crates.confirm.type-description", Map.of("type", next.name())),
                () -> {
            updateCrateField(crateId, "type", next.name().toLowerCase());
            player.sendMessage(languageManager.getMessage("editor.crates.messages.type-updated"));
            openDetail(player, crateId);
        }, () -> openDetail(player, crateId));
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
        confirmationMenu.open(
                player,
                text("editor.crates.confirm.update-title"),
                text("editor.crates.confirm.lock-description", Map.of("lock", text(label), "value", String.valueOf(next))),
                () -> {
            updateCrateField(crateId, "cutscene.locks." + lockKey, next);
            player.sendMessage(languageManager.getMessage("editor.crates.messages.lock-updated"));
            openDetail(player, crateId);
        }, () -> openDetail(player, crateId));
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

    private String text(String key) {
        return languageManager.getRaw(key, java.util.Collections.emptyMap());
    }

    private String text(String key, Map<String, String> placeholders) {
        return languageManager.getRaw(key, placeholders);
    }

    private Component detailTitle(String crateId) {
        return TextUtil.color(text("editor.crates.detail.title", Map.of("crate", crateId)));
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
