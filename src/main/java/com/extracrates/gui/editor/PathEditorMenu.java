package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.runtime.CutscenePreviewSession;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PathEditorMenu implements Listener {
    private static final double DEFAULT_DOUBLE_FALLBACK = 0;
    // Layout: acciones principales al centro, navegación en fila inferior.
    private static final int SLOT_LIST_CREATE = 45;
    private static final int SLOT_LIST_BACK = 49;
    private static final int SLOT_DETAIL_BACK = 18;
    private static final int SLOT_DETAIL_DELETE = 26;
    private static final int[] LIST_NAV_FILLER_SLOTS = {46, 47, 48, 50, 51, 52};
    private static final int[] DETAIL_NAV_FILLER_SLOTS = {19, 20, 21, 23, 24, 25};

    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final EditorInputManager inputManager;
    private final ConfirmationMenu confirmationMenu;
    private final EditorMenu parent;
    private final Component title;
    private final Map<UUID, String> activePath = new HashMap<>();
    private final Map<UUID, CutscenePreviewSession> previewSessions = new HashMap<>();

    public PathEditorMenu(
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
        this.title = TextUtil.colorNoItalic(text("editor.paths.list.title"));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, title);
        refreshPathCache();
        List<CutscenePath> paths = new ArrayList<>(configLoader.getPaths().values());
        paths.sort(Comparator.comparing((CutscenePath path) -> resolveCreatedAt(path.getId()))
                .thenComparing(CutscenePath::getId, String.CASE_INSENSITIVE_ORDER));
        int slot = 0;
        for (CutscenePath path : paths) {
            inventory.setItem(slot++, buildPathItem(path));
            if (slot >= 45) {
                break;
            }
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_CREATE, buildItem(Material.LIME_CONCRETE,
                text("editor.paths.list.create.name"),
                List.of(text("editor.paths.list.create.lore"))));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW,
                text("editor.paths.list.back.name"),
                List.of(text("editor.paths.list.back.lore"))));
        player.openInventory(inventory);
    }

    private void openDetail(Player player, String pathId) {
        activePath.put(player.getUniqueId(), pathId);
        refreshPathCache();
        CutscenePath path = configLoader.getPaths().get(pathId);
        Inventory inventory = Bukkit.createInventory(player, 27, detailTitle(pathId));
        inventory.setItem(0, buildItem(Material.MAP, text("editor.paths.detail.edit-points.name"), List.of(
                text("editor.common.click-start-editor"),
                text("editor.paths.detail.edit-points.free-mode"),
                text("editor.paths.detail.edit-points.save")
        )));
        inventory.setItem(1, buildItem(Material.CLOCK, text("editor.paths.detail.duration.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(path != null ? path.getDurationSeconds() : 4.0))),
                text("editor.paths.detail.duration.desc"),
                text("editor.common.click-edit")
        )));
        inventory.setItem(2, buildItem(Material.PAPER, text("editor.paths.detail.smoothing.name"), List.of(
                text("editor.common.current", Map.of("value", path != null ? path.getSmoothing() : "linear")),
                text("editor.paths.detail.smoothing.desc"),
                text("editor.common.click-edit")
        )));
        inventory.setItem(3, buildItem(Material.FIREWORK_STAR, text("editor.paths.detail.particles.name"), List.of(
                text("editor.common.current", Map.of("value", path != null ? path.getParticlePreview() : "")),
                text("editor.paths.detail.particles.desc"),
                text("editor.common.click-edit")
        )));
        inventory.setItem(4, buildItem(Material.REPEATER, text("editor.paths.detail.constant-speed.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(path != null && path.isConstantSpeed()))),
                text("editor.paths.detail.constant-speed.desc"),
                text("editor.common.click-toggle")
        )));
        inventory.setItem(5, buildItem(Material.ENDER_EYE, text("editor.paths.detail.preview.name"), List.of(
                text("editor.common.click-preview"),
                text("editor.paths.detail.preview.lore")
        )));
        inventory.setItem(6, buildItem(Material.COMPARATOR, text("editor.paths.detail.step-resolution.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(path != null ? path.getStepResolution() : 0.15))),
                text("editor.paths.detail.step-resolution.desc"),
                text("editor.common.click-edit")
        )));
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE,
                text("editor.paths.detail.delete.name"),
                List.of(text("editor.paths.detail.delete.lore"))));
        inventory.setItem(SLOT_DETAIL_BACK, buildItem(Material.ARROW,
                text("editor.paths.detail.back.name"),
                List.of(text("editor.paths.detail.back.lore"))));
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
        String pathId = activePath.get(player.getUniqueId());
        if (pathId != null && viewTitle.equals(detailTitle(pathId))) {
            event.setCancelled(true);
            handleDetailClick(player, pathId, event.getSlot());
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
        List<CutscenePath> paths = new ArrayList<>(configLoader.getPaths().values());
        paths.sort(Comparator.comparing((CutscenePath path) -> resolveCreatedAt(path.getId()))
                .thenComparing(CutscenePath::getId, String.CASE_INSENSITIVE_ORDER));
        if (slot < 0 || slot >= paths.size() || slot >= 45) {
            return;
        }
        CutscenePath path = paths.get(slot);
        if (rightClick && shiftClick) {
            confirmationMenu.open(
                    player,
                    languageManager.getRaw("editor.confirmation.title.delete", java.util.Collections.emptyMap()),
                    languageManager.getRaw("editor.path.confirm.delete", Map.of("id", path.getId())),
                    () -> {
                        deletePath(path.getId());
                        player.sendMessage(languageManager.getMessage("editor.path.success.deleted"));
                        open(player);
                    },
                    () -> open(player)
            );
            return;
        }
        if (rightClick) {
            promptClone(player, path.getId());
            return;
        }
        openDetail(player, path.getId());
    }

    private void handleDetailClick(Player player, String pathId, int slot) {
        switch (slot) {
            case 0 -> startPointEditing(player, pathId);
            case 1 -> promptField(player, pathId, "duration-seconds", "editor.path.prompt.duration");
            case 2 -> promptField(player, pathId, "smoothing", "editor.path.prompt.smoothing");
            case 3 -> promptField(player, pathId, "particle-preview", "editor.path.prompt.particle-preview");
            case 4 -> toggleConstantSpeed(player, pathId);
            case 5 -> togglePreview(player, pathId);
            case 6 -> promptField(player, pathId, "step-resolution", "editor.path.prompt.step-resolution");
            case SLOT_DETAIL_DELETE -> confirmDelete(player, pathId);
            case SLOT_DETAIL_BACK -> open(player);
            default -> {
            }
        }
    }

    private void confirmDelete(Player player, String pathId) {
        confirmationMenu.open(
                player,
                languageManager.getRaw("editor.confirmation.title.delete", java.util.Collections.emptyMap()),
                languageManager.getRaw("editor.path.confirm.delete", Map.of("id", pathId)),
                () -> {
            deletePath(pathId);
            player.sendMessage(languageManager.getMessage("editor.path.success.deleted"));
            open(player);
        }, () -> openDetail(player, pathId));
    }

    private void promptCreate(Player player) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "editor.path.prompt.new-id", input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.input.invalid-id"));
                return;
            }
            if (configLoader.getPaths().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("editor.path.error.already-exists"));
                return;
            }
            createPath(input);
            player.sendMessage(languageManager.getMessage("editor.path.success.created"));
        }, () -> open(player));
    }

    private void promptClone(Player player, String sourceId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "editor.path.prompt.clone-id", Map.of("id", sourceId), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.input.invalid-id"));
                return;
            }
            if (configLoader.getPaths().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("editor.path.error.already-exists"));
                return;
            }
            clonePath(sourceId, input);
            player.sendMessage(languageManager.getMessage("editor.path.success.cloned"));
        }, () -> open(player));
    }

    private void promptField(Player player, String pathId, String field, String promptKey) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, promptKey, input -> {
            Object value = input;
            if (field.equals("duration-seconds") || field.equals("step-resolution")) {
                value = parseDouble(input);
            }
            updatePathField(pathId, field, value);
            player.sendMessage(languageManager.getMessage("editor.path.success.updated"));
        }, () -> openDetail(player, pathId));
    }

    private void toggleConstantSpeed(Player player, String pathId) {
        CutscenePath path = configLoader.getPaths().get(pathId);
        boolean next = path == null || !path.isConstantSpeed();
        updatePathField(pathId, "constant-speed", next);
        player.sendMessage(languageManager.getMessage("editor.path.success.constant-speed-updated"));
        openDetail(player, pathId);
    }

    private void startPointEditing(Player player, String pathId) {
        boolean started = plugin.getRouteEditorManager().startSession(player, pathId);
        if (!started) {
            player.sendMessage(languageManager.getMessage("editor.path.warning.active-session"));
            return;
        }
        player.closeInventory();
        player.sendMessage(plugin.getLanguageManager().getMessage("command.route-started", java.util.Map.of("path", pathId)));
        player.sendMessage(plugin.getLanguageManager().getMessage("command.route-instructions"));
    }

    private void togglePreview(Player player, String pathId) {
        CutscenePreviewSession running = previewSessions.remove(player.getUniqueId());
        if (running != null) {
            running.end();
            player.sendMessage(languageManager.getMessage("editor.path.info.preview-stopped"));
            return;
        }
        CutscenePath path = configLoader.getPaths().get(pathId);
        if (path == null) {
            player.sendMessage(languageManager.getMessage("editor.path.error.missing-path"));
            return;
        }
        Particle particle = resolvePreviewParticle(path);
        CutscenePreviewSession preview = new CutscenePreviewSession(plugin, player, path, particle, () -> {
            previewSessions.remove(player.getUniqueId());
            player.sendMessage(languageManager.getMessage("editor.path.success.preview-finished"));
        });
        previewSessions.put(player.getUniqueId(), preview);
        preview.start();
        player.sendMessage(languageManager.getMessage("editor.path.success.preview-started"));
    }

    private Particle resolvePreviewParticle(CutscenePath path) {
        String particleName = configLoader.getMainConfig().getString("particles-default", "end_rod");
        if (path != null) {
            String preview = path.getParticlePreview();
            if (preview != null && !preview.isEmpty()) {
                particleName = preview;
            }
        }
        try {
            return Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Particle.END_ROD;
        }
    }

    private void createPath(String id) {
        FileConfiguration config = loadConfig();
        String path = "paths." + id;
        config.set(path + ".duration-seconds", 4.0);
        config.set(path + ".constant-speed", true);
        config.set(path + ".step-resolution", 0.15);
        config.set(path + ".smoothing", "linear");
        config.set(path + ".particle-preview", "");
        config.set(path + ".points", new ArrayList<>());
        config.set(path + ".created-at", System.currentTimeMillis());
        saveConfig(config);
    }

    private void clonePath(String sourceId, String targetId) {
        FileConfiguration config = loadConfig();
        String sourcePath = "paths." + sourceId;
        String targetPath = "paths." + targetId;
        Object data = config.get(sourcePath);
        config.set(targetPath, data);
        config.set(targetPath + ".created-at", System.currentTimeMillis());
        saveConfig(config);
    }

    private void deletePath(String id) {
        FileConfiguration config = loadConfig();
        config.set("paths." + id, null);
        saveConfig(config);
    }

    private void updatePathField(String id, String field, Object value) {
        FileConfiguration config = loadConfig();
        config.set("paths." + id + "." + field, value);
        saveConfig(config);
    }

    private FileConfiguration loadConfig() {
        File file = new File(plugin.getDataFolder(), "paths.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfig(FileConfiguration config) {
        File file = new File(plugin.getDataFolder(), "paths.yml");
        try {
            config.save(file);
            configLoader.loadAll();
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar paths.yml: " + ex.getMessage());
        }
    }

    private void refreshPathCache() {
        configLoader.loadAll();
    }

    private ItemStack buildPathItem(CutscenePath path) {
        List<String> lore = new ArrayList<>();
        lore.add(text("editor.paths.list.item.lore.id", Map.of("id", path.getId())));
        lore.add(text("editor.paths.list.item.lore.duration", Map.of("duration", String.valueOf(path.getDurationSeconds()))));
        lore.add(text("editor.paths.list.item.lore.points", Map.of("points", String.valueOf(path.getPoints().size()))));
        lore.add(text("editor.common.action.left-edit"));
        lore.add(text("editor.common.action.right-clone"));
        lore.add(text("editor.common.action.shift-right-delete"));
        return buildItem(Material.ENDER_EYE, "&b" + path.getId(), lore);
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
            meta.displayName(TextUtil.colorNoItalic(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream().map(TextUtil::colorNoItalic).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private double parseDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException ex) {
            return DEFAULT_DOUBLE_FALLBACK;
        }
    }

    private Component detailTitle(String pathId) {
        return TextUtil.colorNoItalic(text("editor.paths.detail.title", Map.of("path", pathId)));
    }

    private String text(String key) {
        return languageManager.getRaw(key, java.util.Collections.emptyMap());
    }

    private String text(String key, Map<String, String> placeholders) {
        return languageManager.getRaw(key, placeholders);
    }

    private long resolveCreatedAt(String pathId) {
        FileConfiguration config = loadConfig();
        return config.getLong("paths." + pathId + ".created-at", 0L);
    }
}
