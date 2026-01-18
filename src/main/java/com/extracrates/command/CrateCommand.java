package com.extracrates.command;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.LanguageManager;
import com.extracrates.gui.CrateGui;
import com.extracrates.gui.OpenHistoryGui;
import com.extracrates.gui.editor.EditorMenu;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CrateType;
import com.extracrates.route.RouteEditorManager;
import com.extracrates.runtime.CutscenePreviewSession;
import com.extracrates.config.ConfigLoader;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.sync.CrateHistoryEntry;
import com.extracrates.sync.SyncEventType;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.ItemUtil;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CrateCommand implements CommandExecutor, TabCompleter {
    private static final Map<String, FieldType> MASS_FIELDS = new LinkedHashMap<>();

    static {
        MASS_FIELDS.put("display-name", FieldType.STRING);
        MASS_FIELDS.put("type", FieldType.CRATE_TYPE);
        MASS_FIELDS.put("open-mode", FieldType.OPEN_MODE);
        MASS_FIELDS.put("key-model", FieldType.STRING);
        MASS_FIELDS.put("key-material", FieldType.MATERIAL);
        MASS_FIELDS.put("cooldown-seconds", FieldType.INTEGER);
        MASS_FIELDS.put("cost", FieldType.DECIMAL);
        MASS_FIELDS.put("permission", FieldType.STRING);
        MASS_FIELDS.put("rewards-pool", FieldType.REWARDS_POOL);
    }

    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final SessionManager sessionManager;
    private final CrateGui crateGui;
    private final OpenHistoryGui openHistoryGui;
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
            OpenHistoryGui openHistoryGui,
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
        this.openHistoryGui = openHistoryGui;
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
            sender.sendMessage(Component.text("Usa /crate gui|editor|open|preview|cutscene|reload|sync|givekey|route|history"));
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
                    sender.sendMessage(languageManager.getMessage("command.no-permission", player, null, null, null));
                    return true;
                }
                crateGui.open(player);
                return true;
            }
            case "history" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                if (!sender.hasPermission("extracrates.history")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                openHistoryGui.open(player);
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
                    sender.sendMessage(languageManager.getMessage("command.no-permission", player, null, null, null));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(languageManager.getMessage("command.open-usage", java.util.Map.of("sub", sub)));
                    return true;
                }
                CrateDefinition crate = configLoader.getCrates().get(args[1]);
                if (crate == null) {
                    sender.sendMessage(languageManager.getMessage("command.crate-not-found", player, null, null, null));
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
                ItemUtil.clearItemCache();
                CrateSession.clearRewardDisplayCache();
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
                    sender.sendMessage(languageManager.getMessage("command.no-permission", player, null, null, null));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(languageManager.getMessage("command.givekey-usage", player, null, null, null));
                    return true;
                }
                CrateDefinition crate = configLoader.getCrates().get(args[1]);
                if (crate == null) {
                    sender.sendMessage(languageManager.getMessage("command.crate-not-found", player, null, null, null));
                    return true;
                }
                ItemStack key = new ItemStack(crate.keyMaterial());
                ItemMeta meta = key.getItemMeta();
                if (meta != null) {
                    String keyName = languageManager.getRaw("command.key-item-name", player, crate, null, null, java.util.Map.of());
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
                sender.sendMessage(languageManager.getMessage("command.givekey-success", player, crate, null, null));
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
            case "claim" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                if (!sender.hasPermission("extracrates.open")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                boolean discard = args.length > 1 && args[1].equalsIgnoreCase("discard");
                sessionManager.claimPendingReward(player, discard);
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
            case "history" -> {
                if (!sender.hasPermission("extracrates.history")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Uso: /crate history <player> [crate] [page]"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                    sender.sendMessage(Component.text("Jugador no encontrado."));
                    return true;
                }
                String crateId = null;
                int page = 1;
                if (args.length >= 3) {
                    if (isInteger(args[2])) {
                        page = parsePage(args[2]);
                    } else {
                        crateId = args[2];
                    }
                }
                if (args.length >= 4) {
                    crateId = args[2];
                    if (!isInteger(args[3])) {
                        sender.sendMessage(Component.text("Página inválida."));
                        return true;
                    }
                    page = parsePage(args[3]);
                }
                if (crateId != null && !configLoader.getCrates().containsKey(crateId)) {
                    sender.sendMessage(languageManager.getMessage("command.crate-not-found"));
                    return true;
                }
                int pageSize = 10;
                int offset = Math.max(0, page - 1) * pageSize;
                List<CrateHistoryEntry> history = sessionManager.getHistory(target.getUniqueId(), crateId, pageSize + 1, offset);
                boolean hasNext = history.size() > pageSize;
                if (hasNext) {
                    history = history.subList(0, pageSize);
                }
                if (history.isEmpty()) {
                    sender.sendMessage(Component.text("Sin historial para este jugador."));
                    return true;
                }
                String header = "Historial de crates de " + target.getName()
                        + (crateId != null ? " (" + crateId + ")" : "")
                        + " - Página " + page;
                sender.sendMessage(Component.text(header));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
                for (CrateHistoryEntry entry : history) {
                    String line = "- [" + formatter.format(entry.timestamp()) + "] "
                            + formatEvent(entry.type(), entry.crateId(), entry.rewardId());
                    sender.sendMessage(Component.text(line));
                }
                if (page > 1) {
                    sender.sendMessage(Component.text("Anterior: " + buildHistoryCommand(args[1], crateId, page - 1)));
                }
                if (hasNext) {
                    sender.sendMessage(Component.text("Siguiente: " + buildHistoryCommand(args[1], crateId, page + 1)));
                }
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
            results.add("history");
            results.add("editor");
            results.add("open");
            results.add("preview");
            results.add("claim");
            results.add("cutscene");
            results.add("reroll");
            results.add("reload");
            results.add("debug");
            results.add("sync");
            results.add("givekey");
            results.add("route");
            results.add("history");
            return results;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("history")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                results.add(player.getName());
            }
            return results;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("history")) {
            results.addAll(configLoader.getCrates().keySet());
            results.add("1");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("history")) {
            results.add("1");
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

    private boolean handleMass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.mass")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("set")) {
            sender.sendMessage(languageManager.getMessage("command.mass-usage"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(languageManager.getMessage("command.mass-usage"));
            return true;
        }
        String field = args[2].toLowerCase(Locale.ROOT);
        FieldType fieldType = MASS_FIELDS.get(field);
        if (fieldType == null) {
            sender.sendMessage(languageManager.getMessage("command.mass-invalid-field", Map.of("field", field)));
            return true;
        }
        String rawValue = args[3];
        ParsedValue parsedValue = parseValue(field, fieldType, rawValue);
        if (parsedValue == null) {
            sender.sendMessage(languageManager.getMessage("command.mass-invalid-value", Map.of("field", field, "value", rawValue)));
            return true;
        }

        FilterSpec filterSpec = null;
        if (args.length >= 5) {
            filterSpec = parseFilter(args[4]);
            if (filterSpec == null) {
                sender.sendMessage(languageManager.getMessage("command.mass-invalid-filter", Map.of("filter", args[4])));
                return true;
            }
        }

        FileConfiguration config = loadCratesConfig();
        ConfigurationSection cratesSection = config.getConfigurationSection("crates");
        if (cratesSection == null) {
            sender.sendMessage(languageManager.getMessage("command.crate-not-found"));
            return true;
        }

        int matched = 0;
        int updated = 0;
        for (CrateDefinition crate : configLoader.getCrates().values()) {
            if (filterSpec != null && !filterSpec.matches(crate)) {
                continue;
            }
            matched++;
            Object currentValue = getCurrentValue(crate, fieldType, field);
            if (valuesEqual(currentValue, parsedValue.value())) {
                continue;
            }
            String path = "crates." + crate.id() + "." + field;
            config.set(path, parsedValue.value());
            updated++;
            plugin.getLogger().info(String.format(
                    "Mass set %s for crate '%s' from '%s' to '%s'",
                    field,
                    crate.id(),
                    formatValue(currentValue),
                    parsedValue.displayValue()
            ));
        }

        if (matched == 0) {
            sender.sendMessage(languageManager.getMessage("command.mass-no-matches"));
            return true;
        }

        if (updated > 0) {
            saveCratesConfig(config);
        }
        sender.sendMessage(languageManager.getMessage(
                "command.mass-updated",
                Map.of("updated", String.valueOf(updated), "matched", String.valueOf(matched))
        ));
        return true;
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

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private int parsePage(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(parsed, 1);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private String formatEvent(SyncEventType type, String crateId, String rewardId) {
        String label = switch (type) {
            case CRATE_OPEN -> "Apertura";
            case REWARD_GRANTED -> "Recompensa";
            case KEY_CONSUMED -> "Llave usada";
            case COOLDOWN_SET -> "Cooldown";
        };
        String detail = "crate=" + crateId;
        if (rewardId != null && !rewardId.isBlank() && type == SyncEventType.REWARD_GRANTED) {
            detail += " reward=" + rewardId;
        }
        return label + " " + detail;
    }

    private String buildHistoryCommand(String playerName, String crateId, int page) {
        StringBuilder command = new StringBuilder("/crate history ").append(playerName);
        if (crateId != null && !crateId.isBlank()) {
            command.append(" ").append(crateId);
        }
        command.append(" ").append(page);
        return command.toString();
    }
}
