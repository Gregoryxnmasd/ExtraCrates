package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.runtime.CutscenePreviewSession;
import com.extracrates.util.TextUtil;
import com.extracrates.gui.MenuSpacer;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PathEditorMenu implements Listener {
    private static final double DEFAULT_DOUBLE_FALLBACK = 0;
    // Layout: fila superior vacía, acciones en el centro, separación y footer.
    private static final int SLOT_LIST_CREATE = 45;
    private static final int SLOT_LIST_BACK = 49;
    private static final int SLOT_DETAIL_BACK = 31;
    private static final int SLOT_DETAIL_DELETE = 35;
    private static final int[] LIST_NAV_FILLER_SLOTS = {46, 47, 48, 50, 51, 52, 53};
    private static final int[] DETAIL_NAV_FILLER_SLOTS = {27, 28, 29, 30, 32, 33, 34};

    private static final int SLOT_DETAIL_EDIT_POINTS = 9;
    private static final int SLOT_DETAIL_DURATION = 10;
    private static final int SLOT_DETAIL_SMOOTHING = 11;
    private static final int SLOT_DETAIL_PARTICLES = 12;
    private static final int SLOT_DETAIL_CONSTANT_SPEED = 13;
    private static final int SLOT_DETAIL_PREVIEW = 14;
    private static final int SLOT_DETAIL_STEP_RESOLUTION = 15;
    private static final int SLOT_SELECTOR_START = 10;
    private static final int SLOT_SELECTOR_BACK = 31;
    private static final int[] SELECTOR_NAV_FILLER_SLOTS = {27, 28, 29, 30, 32, 33, 34, 35};
    private static final List<String> SMOOTHING_OPTIONS = List.of("linear", "ease-in", "ease-out", "ease-in-out", "smoothstep");
    private static final int SLOT_PARTICLE_PREV = 47;
    private static final int SLOT_PARTICLE_BACK = 49;
    private static final int SLOT_PARTICLE_NEXT = 51;
    private static final int PARTICLES_PER_PAGE = 27;

    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final EditorInputManager inputManager;
    private final ConfirmationMenu confirmationMenu;
    private final EditorMenu parent;
    private final Component title;
    private final Map<UUID, String> activePath = new HashMap<>();
    private final Map<UUID, CutscenePreviewSession> previewSessions = new HashMap<>();
    private final Map<UUID, Integer> particlePages = new HashMap<>();

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
        int slot = 9;
        for (CutscenePath path : paths) {
            if (slot > 35) {
                break;
            }
            inventory.setItem(slot++, buildPathItem(path));
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_CREATE, buildItem(Material.LIME_CONCRETE,
                text("editor.paths.list.create.name"),
                List.of(text("editor.paths.list.create.lore"))));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW,
                text("editor.paths.list.back.name"),
                List.of(text("editor.paths.list.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openDetail(Player player, String pathId) {
        activePath.put(player.getUniqueId(), pathId);
        refreshPathCache();
        CutscenePath path = configLoader.getPaths().get(pathId);
        Inventory inventory = Bukkit.createInventory(player, 36, detailTitle(pathId));
        inventory.setItem(SLOT_DETAIL_EDIT_POINTS, buildItem(Material.MAP, text("editor.paths.detail.edit-points.name"), List.of(
                text("editor.common.click-start-editor"),
                text("editor.paths.detail.edit-points.free-mode"),
                text("editor.paths.detail.edit-points.save")
        )));
        inventory.setItem(SLOT_DETAIL_DURATION, buildItem(Material.CLOCK, text("editor.paths.detail.duration.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(path != null ? path.getDurationSeconds() : 4.0))),
                text("editor.paths.detail.duration.desc"),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_SMOOTHING, buildItem(Material.PAPER, text("editor.paths.detail.smoothing.name"), List.of(
                text("editor.common.current", Map.of("value", path != null ? path.getSmoothing() : "linear")),
                text("editor.paths.detail.smoothing.desc"),
                text("editor.common.click-select")
        )));
        inventory.setItem(SLOT_DETAIL_PARTICLES, buildItem(Material.FIREWORK_STAR, text("editor.paths.detail.particles.name"), List.of(
                text("editor.common.current", Map.of("value", path != null ? path.getParticlePreview() : "")),
                text("editor.paths.detail.particles.desc"),
                text("editor.common.click-select")
        )));
        inventory.setItem(SLOT_DETAIL_CONSTANT_SPEED, buildItem(Material.REPEATER, text("editor.paths.detail.constant-speed.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(path != null && path.isConstantSpeed()))),
                text("editor.paths.detail.constant-speed.desc"),
                text("editor.common.click-toggle")
        )));
        inventory.setItem(SLOT_DETAIL_PREVIEW, buildItem(Material.ENDER_EYE, text("editor.paths.detail.preview.name"), List.of(
                text("editor.common.click-preview"),
                text("editor.paths.detail.preview.lore")
        )));
        inventory.setItem(SLOT_DETAIL_STEP_RESOLUTION, buildItem(Material.COMPARATOR, text("editor.paths.detail.step-resolution.name"), List.of(
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
        String pathId = activePath.get(player.getUniqueId());
        if (pathId != null && viewTitle.equals(detailTitle(pathId))) {
            event.setCancelled(true);
            handleDetailClick(player, pathId, event.getSlot());
            return;
        }
        if (pathId != null && viewTitle.equals(smoothingTitle(pathId))) {
            event.setCancelled(true);
            handleSmoothingSelection(player, pathId, event.getSlot());
            return;
        }
        if (pathId != null && viewTitle.equals(particleTitle(pathId))) {
            event.setCancelled(true);
            handleParticleSelection(player, pathId, event.getSlot());
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
        int index = slot - 9;
        if (slot < 9 || slot > 35 || index < 0 || index >= paths.size()) {
            return;
        }
        CutscenePath path = paths.get(index);
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
            case SLOT_DETAIL_EDIT_POINTS -> startPointEditing(player, pathId);
            case SLOT_DETAIL_DURATION -> promptField(player, pathId, "duration-seconds", "editor.path.prompt.duration");
            case SLOT_DETAIL_SMOOTHING -> promptField(player, pathId, "smoothing", "editor.path.prompt.smoothing");
            case SLOT_DETAIL_PARTICLES -> promptField(player, pathId, "particle-preview", "editor.path.prompt.particle-preview");
            case SLOT_DETAIL_CONSTANT_SPEED -> toggleConstantSpeed(player, pathId);
            case SLOT_DETAIL_PREVIEW -> togglePreview(player, pathId);
            case SLOT_DETAIL_STEP_RESOLUTION -> promptField(player, pathId, "step-resolution", "editor.path.prompt.step-resolution");
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

    private void openSmoothingSelector(Player player, String pathId) {
        Inventory inventory = Bukkit.createInventory(player, 36, smoothingTitle(pathId));
        int slot = SLOT_SELECTOR_START;
        for (String option : SMOOTHING_OPTIONS) {
            inventory.setItem(slot++, buildItem(Material.PAPER, text("editor.paths.smoothing.option.name", Map.of("mode", option)), List.of(
                    text("editor.paths.smoothing.option.desc." + option),
                    text("editor.common.click-select")
            )));
        }
        fillSelectorNavigation(inventory);
        inventory.setItem(SLOT_SELECTOR_BACK, buildItem(Material.ARROW,
                text("editor.paths.detail.back.name"),
                List.of(text("editor.paths.detail.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openParticleSelector(Player player, String pathId) {
        openParticleSelector(player, pathId, particlePages.getOrDefault(player.getUniqueId(), 0));
    }

    private void openParticleSelector(Player player, String pathId, int page) {
        Particle[] particles = Particle.values();
        Arrays.sort(particles, Comparator.comparing(particle -> particle.name().toLowerCase(Locale.ROOT)));
        int totalPages = Math.max(1, (int) Math.ceil(particles.length / (double) PARTICLES_PER_PAGE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        particlePages.put(player.getUniqueId(), safePage);

        Inventory inventory = Bukkit.createInventory(player, 54, particleTitle(pathId));
        int startIndex = safePage * PARTICLES_PER_PAGE;
        int endIndex = Math.min(startIndex + PARTICLES_PER_PAGE, particles.length);
        int slot = 9;
        for (int i = startIndex; i < endIndex; i++) {
            String particleName = particles[i].name().toLowerCase(Locale.ROOT);
            inventory.setItem(slot++, buildItem(Material.FIREWORK_STAR, "&f" + particleName, List.of(
                    text("editor.common.click-select")
            )));
        }
        fillParticleNavigation(inventory, safePage, totalPages);
        inventory.setItem(SLOT_PARTICLE_BACK, buildItem(Material.ARROW,
                text("editor.paths.detail.back.name"),
                List.of(text("editor.paths.detail.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void handleSmoothingSelection(Player player, String pathId, int slot) {
        if (slot == SLOT_SELECTOR_BACK) {
            openDetail(player, pathId);
            return;
        }
        int optionIndex = slot - SLOT_SELECTOR_START;
        if (optionIndex < 0 || optionIndex >= SMOOTHING_OPTIONS.size()) {
            return;
        }
        updatePathField(pathId, "smoothing", SMOOTHING_OPTIONS.get(optionIndex));
        player.sendMessage(languageManager.getMessage("editor.path.success.updated"));
        openDetail(player, pathId);
    }

    private void handleParticleSelection(Player player, String pathId, int slot) {
        if (slot == SLOT_PARTICLE_BACK) {
            openDetail(player, pathId);
            return;
        }
        if (slot == SLOT_PARTICLE_PREV || slot == SLOT_PARTICLE_NEXT) {
            int currentPage = particlePages.getOrDefault(player.getUniqueId(), 0);
            int nextPage = slot == SLOT_PARTICLE_NEXT ? currentPage + 1 : currentPage - 1;
            openParticleSelector(player, pathId, nextPage);
            return;
        }
        Particle[] particles = Particle.values();
        Arrays.sort(particles, Comparator.comparing(particle -> particle.name().toLowerCase(Locale.ROOT)));
        int currentPage = particlePages.getOrDefault(player.getUniqueId(), 0);
        int index = currentPage * PARTICLES_PER_PAGE + (slot - 9);
        if (slot < 9 || slot > 35 || index < 0 || index >= particles.length) {
            return;
        }
        Particle selected = particles[index];
        updatePathField(pathId, "particle-preview", selected.name().toLowerCase(Locale.ROOT));
        player.sendMessage(languageManager.getMessage("editor.path.success.updated"));
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

    private ItemStack buildSpacerItem() {
        return buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private void fillParticleNavigation(Inventory inventory, int page, int totalPages) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : LIST_NAV_FILLER_SLOTS) {
            inventory.setItem(slot, filler);
        }
        if (totalPages > 1) {
            if (page > 0) {
                inventory.setItem(SLOT_PARTICLE_PREV, buildItem(Material.ARROW,
                        text("editor.common.prev-page"),
                        List.of(text("editor.common.click-select"))));
            }
            if (page < totalPages - 1) {
                inventory.setItem(SLOT_PARTICLE_NEXT, buildItem(Material.ARROW,
                        text("editor.common.next-page"),
                        List.of(text("editor.common.click-select"))));
            }
        }
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

    private Component smoothingTitle(String pathId) {
        return TextUtil.colorNoItalic(text("editor.paths.smoothing.title", Map.of("path", pathId)));
    }

    private Component particleTitle(String pathId) {
        return TextUtil.colorNoItalic(text("editor.paths.particles.title", Map.of("path", pathId)));
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
