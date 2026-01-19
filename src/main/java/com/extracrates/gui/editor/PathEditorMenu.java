package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
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
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
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
        this.inputManager = inputManager;
        this.confirmationMenu = confirmationMenu;
        this.parent = parent;
        this.title = TextUtil.color("&8Editor de Paths");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, title);
        refreshPathCache();
        List<CutscenePath> paths = new ArrayList<>(configLoader.getPaths().values());
        paths.sort(Comparator.comparing(CutscenePath::getId));
        int slot = 0;
        for (CutscenePath path : paths) {
            inventory.setItem(slot++, buildPathItem(path));
            if (slot >= 45) {
                break;
            }
        }
        inventory.setItem(45, buildItem(Material.LIME_CONCRETE, "&aCrear path", List.of("&7Nueva ruta de cámara.")));
        inventory.setItem(49, buildItem(Material.ARROW, "&eVolver", List.of("&7Regresar al menú principal.")));
        inventory.setItem(53, buildItem(Material.BOOK, "&bRefrescar", List.of("&7Recargar lista.")));
        player.openInventory(inventory);
    }

    private void openDetail(Player player, String pathId) {
        activePath.put(player.getUniqueId(), pathId);
        refreshPathCache();
        CutscenePath path = configLoader.getPaths().get(pathId);
        Inventory inventory = Bukkit.createInventory(player, 27, TextUtil.color("&8Path: " + pathId));
        inventory.setItem(10, buildItem(Material.MAP, "&eEditar puntos", List.of(
                "&7Click para iniciar el editor.",
                "&7Guarda con &f/crate route editor stop&7."
        )));
        inventory.setItem(12, buildItem(Material.CLOCK, "&eDuración", List.of(
                "&7Actual: &f" + (path != null ? path.getDurationSeconds() : 4.0),
                "&7Click para editar."
        )));
        inventory.setItem(14, buildItem(Material.PAPER, "&eSmoothing", List.of(
                "&7Actual: &f" + (path != null ? path.getSmoothing() : "linear"),
                "&7Click para editar."
        )));
        inventory.setItem(16, buildItem(Material.FIREWORK_STAR, "&ePartículas", List.of(
                "&7Actual: &f" + (path != null ? path.getParticlePreview() : ""),
                "&7Click para editar."
        )));
        inventory.setItem(20, buildItem(Material.REPEATER, "&eConstant Speed", List.of(
                "&7Actual: &f" + (path != null && path.isConstantSpeed()),
                "&7Click para alternar."
        )));
        inventory.setItem(22, buildItem(Material.ENDER_EYE, "&ePreview", List.of(
                "&7Click para previsualizar.",
                "&7Muestra partículas sobre la ruta."
        )));
        inventory.setItem(24, buildItem(Material.COMPARATOR, "&eStep Resolution", List.of(
                "&7Actual: &f" + (path != null ? path.getStepResolution() : 0.15),
                "&7Click para editar."
        )));
        inventory.setItem(26, buildItem(Material.ARROW, "&eVolver", List.of("&7Regresar al listado.")));
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
        if (pathId != null && viewTitle.equals(TextUtil.color("&8Path: " + pathId))) {
            event.setCancelled(true);
            handleDetailClick(player, pathId, event.getSlot());
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
            refreshPathCache();
            open(player);
            return;
        }
        List<CutscenePath> paths = new ArrayList<>(configLoader.getPaths().values());
        paths.sort(Comparator.comparing(CutscenePath::getId));
        if (slot < 0 || slot >= paths.size() || slot >= 45) {
            return;
        }
        CutscenePath path = paths.get(slot);
        if (rightClick && shiftClick) {
            confirmationMenu.open(player, "&8Confirmar borrado", "Eliminar path " + path.getId(), () -> {
                deletePath(path.getId());
                player.sendMessage(Component.text("Path eliminada y guardada en YAML."));
                open(player);
            }, () -> open(player));
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
            case 10 -> startPointEditing(player, pathId);
            case 12 -> promptField(player, pathId, "duration-seconds", "Duración en segundos");
            case 14 -> promptField(player, pathId, "smoothing", "Smoothing (linear, catmull-rom, etc)");
            case 16 -> promptField(player, pathId, "particle-preview", "Particle preview");
            case 20 -> toggleConstantSpeed(player, pathId);
            case 22 -> togglePreview(player, pathId);
            case 24 -> promptField(player, pathId, "step-resolution", "Step resolution");
            case 26 -> open(player);
            default -> {
            }
        }
    }

    private void promptCreate(Player player) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(Component.text("Ya tienes una edición pendiente."));
            return;
        }
        inputManager.requestInput(player, "ID del nuevo path", input -> {
            if (input.isEmpty()) {
                player.sendMessage(Component.text("ID inválido."));
                return;
            }
            if (configLoader.getPaths().containsKey(input)) {
                player.sendMessage(Component.text("Ya existe un path con ese ID."));
                return;
            }
            createPath(input);
            player.sendMessage(Component.text("Path creada y guardada en YAML."));
            open(player);
        });
    }

    private void promptClone(Player player, String sourceId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(Component.text("Ya tienes una edición pendiente."));
            return;
        }
        inputManager.requestInput(player, "Nuevo ID para clonar " + sourceId, input -> {
            if (input.isEmpty()) {
                player.sendMessage(Component.text("ID inválido."));
                return;
            }
            if (configLoader.getPaths().containsKey(input)) {
                player.sendMessage(Component.text("Ya existe un path con ese ID."));
                return;
            }
            confirmationMenu.open(player, "&8Confirmar clonación", "Clonar path " + sourceId + " -> " + input, () -> {
                clonePath(sourceId, input);
                player.sendMessage(Component.text("Path clonada y guardada en YAML."));
                open(player);
            }, () -> open(player));
        });
    }

    private void promptField(Player player, String pathId, String field, String prompt) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(Component.text("Ya tienes una edición pendiente."));
            return;
        }
        inputManager.requestInput(player, prompt, input -> {
            Object value = input;
            if (field.equals("duration-seconds") || field.equals("step-resolution")) {
                value = parseDouble(input);
            }
            updatePathField(pathId, field, value);
            player.sendMessage(Component.text("Path actualizada y guardada en YAML."));
            openDetail(player, pathId);
        });
    }

    private void toggleConstantSpeed(Player player, String pathId) {
        CutscenePath path = configLoader.getPaths().get(pathId);
        boolean next = path == null || !path.isConstantSpeed();
        updatePathField(pathId, "constant-speed", next);
        player.sendMessage(Component.text("Constant speed actualizado y guardado en YAML."));
        openDetail(player, pathId);
    }

    private void startPointEditing(Player player, String pathId) {
        boolean started = plugin.getRouteEditorManager().startSession(player, pathId);
        if (!started) {
            player.sendMessage(Component.text("Ya tienes una sesión de rutas activa."));
            return;
        }
        player.closeInventory();
        player.sendMessage(Component.text("Editor iniciado. Haz clic en bloques para marcar puntos."));
        player.sendMessage(Component.text("Usa /crate route editor stop para guardar o /crate route editor cancel para salir."));
    }

    private void togglePreview(Player player, String pathId) {
        CutscenePreviewSession running = previewSessions.remove(player.getUniqueId());
        if (running != null) {
            running.end();
            player.sendMessage(Component.text("Preview detenida."));
            return;
        }
        CutscenePath path = configLoader.getPaths().get(pathId);
        if (path == null) {
            player.sendMessage(Component.text("No existe la ruta seleccionada."));
            return;
        }
        Particle particle = resolvePreviewParticle(path);
        CutscenePreviewSession preview = new CutscenePreviewSession(plugin, player, path, particle, () -> {
            previewSessions.remove(player.getUniqueId());
            player.sendMessage(Component.text("Preview finalizada."));
        });
        previewSessions.put(player.getUniqueId(), preview);
        preview.start();
        player.sendMessage(Component.text("Preview iniciada."));
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
        saveConfig(config);
    }

    private void clonePath(String sourceId, String targetId) {
        FileConfiguration config = loadConfig();
        String sourcePath = "paths." + sourceId;
        String targetPath = "paths." + targetId;
        Object data = config.get(sourcePath);
        config.set(targetPath, data);
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
        lore.add("&7ID: &f" + path.getId());
        lore.add("&7Duración: &f" + path.getDurationSeconds());
        lore.add("&7Puntos: &f" + path.getPoints().size());
        lore.add("&8Click: editar | Click der: clonar | Shift+der: borrar");
        return buildItem(Material.ENDER_EYE, "&b" + path.getId(), lore);
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

    private double parseDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException ex) {
            return DEFAULT_DOUBLE_FALLBACK;
        }
    }
}
