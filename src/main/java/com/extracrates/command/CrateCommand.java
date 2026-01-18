package com.extracrates.command;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.LanguageManager;
import com.extracrates.gui.CrateGui;
import com.extracrates.gui.editor.EditorMenu;
import com.extracrates.model.CrateDefinition;
import com.extracrates.route.RouteEditorManager;
import com.extracrates.runtime.CutscenePreviewSession;
import com.extracrates.config.ConfigLoader;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.TextUtil;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CrateCommand implements CommandExecutor, TabCompleter {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final SessionManager sessionManager;
    private final CrateGui crateGui;
    private final EditorMenu editorMenu;
    private final SyncCommand syncCommand;
    private final RouteEditorManager routeEditorManager;
    private final ResourcepackModelResolver resourcepackModelResolver;

    public CrateCommand(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            LanguageManager languageManager,
            SessionManager sessionManager,
            CrateGui crateGui,
            EditorMenu editorMenu,
            SyncCommand syncCommand,
            RouteEditorManager routeEditorManager,
            ResourcepackModelResolver resourcepackModelResolver
    ) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.languageManager = languageManager;
        this.sessionManager = sessionManager;
        this.crateGui = crateGui;
        this.editorMenu = editorMenu;
        this.syncCommand = syncCommand;
        this.routeEditorManager = routeEditorManager;
        this.resourcepackModelResolver = resourcepackModelResolver;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage(languageManager.getMessage("command.usage"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                if (!sender.hasPermission("extracrates.gui")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                crateGui.open(player);
                return true;
            }
            case "editor" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                editorMenu.open(player);
                return true;
            }
            case "open", "preview" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                String permission = sub.equals("preview") ? "extracrates.preview" : "extracrates.open";
                if (!sender.hasPermission(permission)) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(languageManager.getMessage("command.open-usage", java.util.Map.of("sub", sub)));
                    return true;
                }
                CrateDefinition crate = configLoader.getCrates().get(args[1]);
                if (crate == null) {
                    sender.sendMessage(languageManager.getMessage("command.crate-not-found"));
                    return true;
                }
                sessionManager.openCrate(player, crate, sub.equals("preview"));
                return true;
            }
            case "reroll" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                if (!sender.hasPermission("extracrates.reroll")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                sessionManager.rerollSession(player);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("extracrates.reload")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                configLoader.loadAll();
                languageManager.load();
                sender.sendMessage(languageManager.getMessage("command.reload-success"));
                return true;
            }
            case "debug" -> {
                if (!sender.hasPermission("extracrates.reload")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (args.length < 3 || !args[1].equalsIgnoreCase("verbose")) {
                    sender.sendMessage(Component.text("Uso: /crate debug verbose <on|off|toggle>"));
                    return true;
                }
                boolean current = plugin.getConfig().getBoolean("debug.verbose", false);
                String action = args[2].toLowerCase(Locale.ROOT);
                boolean next;
                switch (action) {
                    case "on", "true", "enable" -> next = true;
                    case "off", "false", "disable" -> next = false;
                    case "toggle" -> next = !current;
                    default -> {
                        sender.sendMessage(Component.text("Uso: /crate debug verbose <on|off|toggle>"));
                        return true;
                    }
                }
                plugin.getConfig().set("debug.verbose", next);
                plugin.saveConfig();
                sender.sendMessage(Component.text("Debug verbose " + (next ? "activado" : "desactivado") + "."));
                return true;
            }
            case "sync" -> {
                return syncCommand.handle(sender, args);
            }
            case "givekey" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                if (!sender.hasPermission("extracrates.givekey")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(languageManager.getMessage("command.givekey-usage"));
                    return true;
                }
                CrateDefinition crate = configLoader.getCrates().get(args[1]);
                if (crate == null) {
                    sender.sendMessage(languageManager.getMessage("command.crate-not-found"));
                    return true;
                }
                ItemStack key = new ItemStack(crate.keyMaterial());
                ItemMeta meta = key.getItemMeta();
                if (meta != null) {
                    String keyName = languageManager.getRaw("command.key-item-name", java.util.Map.of("crate_name", crate.displayName()));
                    meta.displayName(TextUtil.color(keyName));
                    if (crate.keyModel() != null && !crate.keyModel().isEmpty()) {
                        int modelData = resourcepackModelResolver.resolve(configLoader, crate.keyModel());
                        if (modelData >= 0) {
                            meta.setCustomModelData(modelData);
                        }
                    }
                    key.setItemMeta(meta);
                }
                player.getInventory().addItem(key);
                sender.sendMessage(languageManager.getMessage("command.givekey-success"));
                return true;
            }
            case "status" -> {
                if (!sender.hasPermission("extracrates.status")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                List<String> lines = buildStatusLines();
                lines.forEach(line -> sender.sendMessage(Component.text(line)));
                if (args.length >= 2 && args[1].equalsIgnoreCase("export")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Uso: /crate status export <archivo>"));
                        return true;
                    }
                    String fileName = args[2];
                    File output = new File(plugin.getDataFolder(), fileName);
                    try {
                        if (output.toPath().getParent() != null) {
                            Files.createDirectories(output.toPath().getParent());
                        }
                        Files.write(output.toPath(), lines, StandardCharsets.UTF_8);
                        sender.sendMessage(Component.text("Estado exportado a " + output.getPath()));
                    } catch (IOException ex) {
                        sender.sendMessage(Component.text("No se pudo exportar el estado: " + ex.getMessage()));
                    }
                }
                return true;
            }
            case "cutscene" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                if (args.length < 2 || !args[1].equalsIgnoreCase("test")) {
                    sender.sendMessage(languageManager.getMessage("command.cutscene-usage"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(languageManager.getMessage("command.cutscene-usage"));
                    return true;
                }
                com.extracrates.cutscene.CutscenePath path = configLoader.getPaths().get(args[2]);
                if (path == null) {
                    sender.sendMessage(languageManager.getMessage("command.cutscene-path-not-found"));
                    return true;
                }
                Particle particle = resolveParticle(path.getParticlePreview());
                CutscenePreviewSession preview = new CutscenePreviewSession(plugin, player, path, particle, null);
                preview.start();
                sender.sendMessage(languageManager.getMessage("command.cutscene-preview-started", java.util.Map.of("path", path.getId())));
                return true;
            }
            case "route" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                if (args.length < 2 || !args[1].equalsIgnoreCase("editor")) {
                    sender.sendMessage(languageManager.getMessage("command.route-editor-usage"));
                    return true;
                }
                if (!sender.hasPermission("extracrates.route.editor")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(languageManager.getMessage("command.route-editor-usage"));
                    return true;
                }
                String action = args[2];
                if (action.equalsIgnoreCase("stop")) {
                    if (routeEditorManager.endSession(player, true)) {
                        sender.sendMessage(languageManager.getMessage("command.route-saved"));
                    } else {
                        sender.sendMessage(languageManager.getMessage("command.route-no-active-editor"));
                    }
                    return true;
                }
                if (action.equalsIgnoreCase("cancel")) {
                    if (routeEditorManager.endSession(player, false)) {
                        sender.sendMessage(languageManager.getMessage("command.route-canceled"));
                    } else {
                        sender.sendMessage(languageManager.getMessage("command.route-no-active-editor"));
                    }
                    return true;
                }
                if (!routeEditorManager.startSession(player, action)) {
                    sender.sendMessage(languageManager.getMessage("command.route-already-active"));
                    return true;
                }
                sender.sendMessage(languageManager.getMessage("command.route-started", java.util.Map.of("path", action)));
                sender.sendMessage(languageManager.getMessage("command.route-instructions"));
                return true;
            }
            case "sessions" -> {
                if (!sender.hasPermission("extracrates.sessions")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (args.length < 2 || !args[1].equalsIgnoreCase("check")) {
                    sender.sendMessage(Component.text("Uso: /crate sessions check"));
                    return true;
                }
                int cleaned = sessionManager.cleanupInactiveSessions();
                sender.sendMessage(Component.text("Sesiones inactivas cerradas: " + cleaned));
                return true;
            }
            default -> {
                sender.sendMessage(languageManager.getMessage("command.unknown-subcommand"));
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        List<String> results = new ArrayList<>();
        if (args.length == 1) {
            results.add("gui");
            results.add("editor");
            results.add("open");
            results.add("preview");
            results.add("cutscene");
            results.add("reroll");
            results.add("reload");
            results.add("debug");
            results.add("sync");
            results.add("givekey");
            results.add("route");
            results.add("status");
            return results;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("route")) {
            results.add("editor");
            return results;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
            results.add("export");
            return results;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("sync")) {
            results.addAll(syncCommand.tabComplete(args));
            return results;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("open") || args[0].equalsIgnoreCase("preview") || args[0].equalsIgnoreCase("givekey"))) {
            results.addAll(configLoader.getCrates().keySet());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cutscene")) {
            results.add("test");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cutscene") && args[1].equalsIgnoreCase("test")) {
            results.addAll(configLoader.getPaths().keySet());
        }
        return results;
    }

    private @NotNull Particle resolveParticle(@Nullable String particleName) {
        if (particleName == null || particleName.isEmpty()) {
            return Particle.END_ROD;
        }
        try {
            return Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Particle.END_ROD;
        }
    }

    private List<String> buildStatusLines() {
        List<String> lines = new ArrayList<>();
        int crates = configLoader.getCrates().size();
        int pools = configLoader.getRewardPools().size();
        int paths = configLoader.getPaths().size();
        lines.add("Config: crates=" + crates + ", pools=" + pools + ", paths=" + paths);

        com.extracrates.storage.StorageSettings storageSettings =
                com.extracrates.storage.StorageSettings.fromConfig(configLoader.getMainConfig());
        SessionManager.StorageStatus storageStatus = sessionManager.getStorageStatus();
        lines.add("Storage: enabled=" + storageStatus.enabled()
                + ", type=" + storageSettings.type()
                + ", backend=" + storageStatus.backend()
                + ", fallback=" + (storageStatus.fallbackActive() ? "activo" : "inactivo"));

        lines.addAll(sessionManager.getSyncBridge().getStatusLines());

        boolean protocolLibPresent = plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null;
        lines.add("ProtocolLib: " + (protocolLibPresent ? "detectado" : "no detectado"));

        boolean vaultPresent = plugin.getServer().getPluginManager().getPlugin("Vault") != null;
        String economyStatus = plugin.getEconomy() != null ? "economy=ok" : "economy=no disponible";
        lines.add("Vault: " + (vaultPresent ? "detectado" : "no detectado") + " (" + economyStatus + ")");

        int activeSessions = sessionManager.getActiveSessionCount();
        int previewSessions = sessionManager.getActivePreviewCount();
        int pendingRewards = sessionManager.getPendingRewardCount();
        lines.add("Sesiones activas: " + activeSessions + " (preview=" + previewSessions + ")");
        lines.add("Recompensas pendientes: " + pendingRewards);
        return lines;
    }
}
