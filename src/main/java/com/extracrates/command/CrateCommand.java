package com.extracrates.command;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.LanguageManager;
import com.extracrates.gui.CrateGui;
import com.extracrates.gui.OpenHistoryGui;
import com.extracrates.gui.editor.EditorMenu;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.route.RouteEditorManager;
import com.extracrates.runtime.CutscenePreviewSession;
import com.extracrates.config.ConfigLoader;
import com.extracrates.runtime.core.CrateSession;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.storage.PendingRewardStore;
import com.extracrates.storage.StorageMigrationReport;
import com.extracrates.storage.StorageTarget;
import com.extracrates.sync.CrateHistoryEntry;
import com.extracrates.sync.SyncEventType;
import com.extracrates.util.CommandUtil;
import com.extracrates.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final PendingRewardStore pendingRewardStore;

    public CrateCommand(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            LanguageManager languageManager,
            SessionManager sessionManager,
            CrateGui crateGui,
            OpenHistoryGui openHistoryGui,
            EditorMenu editorMenu,
            SyncCommand syncCommand,
            RouteEditorManager routeEditorManager
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
        this.pendingRewardStore = plugin.getPendingRewardStore();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(languageManager.getMessage("command.only-players"));
                return true;
            }
            if (!sender.hasPermission("extracrates.editor")) {
                sender.sendMessage(languageManager.getMessage("command.no-permission"));
                return true;
            }
            editorMenu.open(player);
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
                if (args.length == 1) {
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
                return handleHistory(sender, args);
            }
            case "editor" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                if (!sender.hasPermission("extracrates.editor")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                editorMenu.open(player);
                return true;
            }
            case "open", "preview" -> {
                String permission = sub.equals("preview") ? "extracrates.preview" : "extracrates.open";
                if (!sender.hasPermission(permission)) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (sub.equals("preview") && !configLoader.getMainConfig().getBoolean("gui.preview-enabled", true)) {
                    sender.sendMessage(Component.text("Las previews están deshabilitadas en la configuración."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(languageManager.getMessage("command.open-usage", java.util.Map.of("sub", sub)));
                    return true;
                }
                Player target;
                String crateId;
                if (sender instanceof Player player && args.length == 2) {
                    target = player;
                    crateId = args[1];
                } else {
                    if (args.length < 3) {
                        sender.sendMessage(languageManager.getMessage("command.open-usage", java.util.Map.of("sub", sub)));
                        return true;
                    }
                    target = Bukkit.getPlayerExact(args[1]);
                    crateId = args[2];
                    if (target == null) {
                        sender.sendMessage(languageManager.getMessage("command.player-not-found"));
                        return true;
                    }
                }
                CrateDefinition crate = configLoader.getCrates().get(crateId);
                if (crate == null) {
                    sender.sendMessage(languageManager.getMessage("command.crate-not-found", target, null, null, null));
                    return true;
                }
                sessionManager.openCrate(target, crate, sub.equals("preview"));
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
                    sender.sendMessage(Component.text("Uso: /crates debug verbose <on|off|toggle>"));
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
                        sender.sendMessage(Component.text("Uso: /crates debug verbose <on|off|toggle>"));
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
            case "migrate" -> {
                if (!sender.hasPermission("extracrates.migrate")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Uso: /crates migrate <sql|local>"));
                    return true;
                }
                StorageTarget target = StorageTarget.fromString(args[1]).orElse(null);
                if (target == null) {
                    sender.sendMessage(Component.text("Destino inválido. Usa /crates migrate <sql|local>"));
                    return true;
                }
                StorageMigrationReport report = sessionManager.migrateStorage(target);
                if (report.success()) {
                    sender.sendMessage(Component.text("Migración completada. Revisa la consola para validar integridad."));
                } else {
                    sender.sendMessage(Component.text("Migración fallida: " + report.message()));
                }
                return true;
            }
            case "givekey" -> {
                sender.sendMessage(languageManager.getMessage("command.unknown-subcommand"));
                return true;
            }
            case "clear" -> {
                return handleClear(sender, args);
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
                if (!sender.hasPermission("extracrates.route.editor")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(languageManager.getMessage("command.route-usage"));
                    return true;
                }
                String action = args[1];
                if (action.equalsIgnoreCase("save")) {
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
                if (action.equalsIgnoreCase("marker")) {
                    if (routeEditorManager.hasNoSession(player)) {
                        sender.sendMessage(languageManager.getMessage("command.route-no-active-editor"));
                        return true;
                    }
                    if (args.length < 3 || !args[2].equalsIgnoreCase("move")) {
                        sender.sendMessage(languageManager.getMessage("command.route-usage"));
                        return true;
                    }
                    routeEditorManager.moveMarkerToPlayer(player);
                    return true;
                }
                if (action.equalsIgnoreCase("add") || action.equalsIgnoreCase("capture")) {
                    if (routeEditorManager.hasNoSession(player)) {
                        sender.sendMessage(languageManager.getMessage("command.route-no-active-editor"));
                        return true;
                    }
                    routeEditorManager.capturePoint(player);
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
            case "crates" -> {
                return handleCrates(sender, args);
            }
            case "pools" -> {
                return handlePools(sender, args);
            }
            case "rewards" -> {
                return handleRewards(sender, args);
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
        List<String> options = new ArrayList<>();
        String current = args.length > 0 ? args[args.length - 1] : "";
        if (args.length == 1) {
            options.addAll(List.of("gui", "history", "editor", "open", "preview", "cutscene", "reroll", "reload", "debug", "sync", "route", "migrate", "clear", "crates", "pools", "rewards"));
            return filterByPrefix(options, current);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("crates")) {
            options.addAll(List.of("create", "edit", "delete"));
            return filterByPrefix(options, current);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pools")) {
            options.addAll(List.of("create", "edit", "delete"));
            return filterByPrefix(options, current);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rewards")) {
            options.addAll(List.of("create", "edit", "delete", "give", "claim"));
            return filterByPrefix(options, current);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("crates") && (args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("delete"))) {
            options.addAll(configLoader.getCrates().keySet());
            return filterByPrefix(options, current);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pools") && (args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("delete"))) {
            options.addAll(configLoader.getRewardPools().keySet());
            return filterByPrefix(options, current);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rewards")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("create") || action.equals("edit") || action.equals("delete")) {
                options.addAll(configLoader.getRewardPools().keySet());
                return filterByPrefix(options, current);
            }
            if (action.equals("give") || action.equals("claim")) {
                Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
                return filterByPrefix(options, current);
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("rewards")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("edit") || action.equals("delete")) {
                RewardPool pool = configLoader.getRewardPools().get(args[2]);
                if (pool != null) {
                    pool.rewards().forEach(reward -> options.add(reward.id()));
                }
                return filterByPrefix(options, current);
            }
            if (action.equals("give")) {
                options.addAll(configLoader.getRewardPools().keySet());
                return filterByPrefix(options, current);
            }
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("rewards") && args[1].equalsIgnoreCase("give")) {
            RewardPool pool = configLoader.getRewardPools().get(args[3]);
            if (pool != null) {
                pool.rewards().forEach(reward -> options.add(reward.id()));
            }
            return filterByPrefix(options, current);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("crates") && args[1].equalsIgnoreCase("edit")) {
            options.addAll(List.of("display-name", "type", "open-mode", "key-model", "cooldown-seconds", "cost", "permission", "rewards-pool", "cutscene.max-rerolls", "animation.path"));
            return filterByPrefix(options, current);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("pools") && args[1].equalsIgnoreCase("edit")) {
            options.add("roll-count");
            return filterByPrefix(options, current);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("rewards") && args[1].equalsIgnoreCase("edit")) {
            options.addAll(List.of("display-name", "chance", "commands", "hologram", "map-image"));
            return filterByPrefix(options, current);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("history")) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            return filterByPrefix(options, current);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("history")) {
            options.addAll(configLoader.getCrates().keySet());
            options.add("1");
            return filterByPrefix(options, current);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("history")) {
            options.add("1");
            return filterByPrefix(options, current);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("route")) {
            options.addAll(List.of("save", "cancel", "add", "capture", "marker"));
            options.addAll(configLoader.getPaths().keySet());
            return filterByPrefix(options, current);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("route")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("marker")) {
                options.add("move");
                return filterByPrefix(options, current);
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
            options.addAll(List.of("sql", "local"));
            return filterByPrefix(options, current);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("sync")) {
            options.addAll(syncCommand.tabComplete(args));
            return filterByPrefix(options, current);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("open") || args[0].equalsIgnoreCase("preview"))) {
            if (sender instanceof Player) {
                configLoader.getCrates().keySet().forEach(options::add);
            } else {
                Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            }
            return filterByPrefix(options, current);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("open") || args[0].equalsIgnoreCase("preview"))) {
            options.addAll(configLoader.getCrates().keySet());
            return filterByPrefix(options, current);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cutscene")) {
            options.add("test");
            return filterByPrefix(options, current);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cutscene") && args[1].equalsIgnoreCase("test")) {
            options.addAll(configLoader.getPaths().keySet());
            return filterByPrefix(options, current);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            return filterByPrefix(options, current);
        }
        return List.of();
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.clear")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        Player target;
        if (args.length == 1) {
            if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(languageManager.getMessage("command.clear-usage"));
                return true;
            }
        } else {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(languageManager.getMessage("command.player-not-found"));
                return true;
            }
        }
        sessionManager.clearCrateEffects(target);
        sender.sendMessage(languageManager.getMessage("command.clear-success", java.util.Map.of("player", target.getName())));
        if (!sender.equals(target)) {
            target.sendMessage(languageManager.getMessage("command.clear-target"));
        }
        return true;
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

    private List<String> filterByPrefix(@NotNull Collection<String> options, @Nullable String input) {
        if (options.isEmpty()) {
            return List.of();
        }
        if (input == null || input.isBlank()) {
            return options.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        String lowered = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowered))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.history")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /crates history <player> [crate] [page]"));
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

    private boolean handleCrates(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(languageManager.getMessage("command.crates-usage"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "create" -> handleCrateCreate(sender, args);
            case "edit" -> handleCrateEdit(sender, args);
            case "delete" -> handleCrateDelete(sender, args);
            default -> {
                sender.sendMessage(languageManager.getMessage("command.crates-usage"));
                yield true;
            }
        };
    }

    private boolean handleCrateCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.crate.create")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(languageManager.getMessage("command.crates-create-usage"));
            return true;
        }
        String id = args[2];
        if (configLoader.getCrates().containsKey(id)) {
            sender.sendMessage(languageManager.getMessage("command.crate-already-exists"));
            return true;
        }
        FileConfiguration config = loadConfig("crates.yml");
        String path = "crates." + id;
        config.set(path + ".display-name", id);
        config.set(path + ".type", "normal");
        config.set(path + ".open-mode", "reward-only");
        config.set(path + ".key-model", "");
        config.set(path + ".cooldown-seconds", 0);
        config.set(path + ".cost", 0);
        config.set(path + ".permission", "extracrates.open");
        config.set(path + ".rewards-pool", "");
        config.set(path + ".created-at", System.currentTimeMillis());
        saveConfig(config, "crates.yml");
        sender.sendMessage(languageManager.getMessage("command.crate-created", Map.of("crate", id)));
        return true;
    }

    private boolean handleCrateEdit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.crate.edit")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(languageManager.getMessage("command.crates-edit-usage"));
            return true;
        }
        String id = args[2];
        if (!configLoader.getCrates().containsKey(id)) {
            sender.sendMessage(languageManager.getMessage("command.crate-not-found"));
            return true;
        }
        String field = args[3];
        String value = joinArgs(args, 4);
        FileConfiguration config = loadConfig("crates.yml");
        config.set("crates." + id + "." + field, parseValue(value));
        saveConfig(config, "crates.yml");
        sender.sendMessage(languageManager.getMessage("command.crate-updated", Map.of("crate", id, "field", field)));
        return true;
    }

    private boolean handleCrateDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.crate.delete")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(languageManager.getMessage("command.crates-delete-usage"));
            return true;
        }
        String id = args[2];
        if (!configLoader.getCrates().containsKey(id)) {
            sender.sendMessage(languageManager.getMessage("command.crate-not-found"));
            return true;
        }
        FileConfiguration config = loadConfig("crates.yml");
        config.set("crates." + id, null);
        saveConfig(config, "crates.yml");
        sender.sendMessage(languageManager.getMessage("command.crate-deleted", Map.of("crate", id)));
        return true;
    }

    private boolean handlePools(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(languageManager.getMessage("command.pools-usage"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "create" -> handlePoolCreate(sender, args);
            case "edit" -> handlePoolEdit(sender, args);
            case "delete" -> handlePoolDelete(sender, args);
            default -> {
                sender.sendMessage(languageManager.getMessage("command.pools-usage"));
                yield true;
            }
        };
    }

    private boolean handlePoolCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.pool.create")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(languageManager.getMessage("command.pools-create-usage"));
            return true;
        }
        String id = args[2];
        if (configLoader.getRewardPools().containsKey(id)) {
            sender.sendMessage(languageManager.getMessage("command.pool-already-exists"));
            return true;
        }
        FileConfiguration config = loadConfig("rewards.yml");
        String path = "pools." + id;
        config.set(path + ".roll-count", 1);
        config.set(path + ".rewards", new java.util.HashMap<>());
        config.set(path + ".created-at", System.currentTimeMillis());
        saveConfig(config, "rewards.yml");
        sender.sendMessage(languageManager.getMessage("command.pool-created", Map.of("pool", id)));
        return true;
    }

    private boolean handlePoolEdit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.pool.edit")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(languageManager.getMessage("command.pools-edit-usage"));
            return true;
        }
        String id = args[2];
        if (!configLoader.getRewardPools().containsKey(id)) {
            sender.sendMessage(languageManager.getMessage("command.pool-not-found"));
            return true;
        }
        String field = args[3];
        String value = joinArgs(args, 4);
        FileConfiguration config = loadConfig("rewards.yml");
        config.set("pools." + id + "." + field, parseValue(value));
        saveConfig(config, "rewards.yml");
        sender.sendMessage(languageManager.getMessage("command.pool-updated", Map.of("pool", id, "field", field)));
        return true;
    }

    private boolean handlePoolDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.pool.delete")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(languageManager.getMessage("command.pools-delete-usage"));
            return true;
        }
        String id = args[2];
        if (!configLoader.getRewardPools().containsKey(id)) {
            sender.sendMessage(languageManager.getMessage("command.pool-not-found"));
            return true;
        }
        FileConfiguration config = loadConfig("rewards.yml");
        config.set("pools." + id, null);
        saveConfig(config, "rewards.yml");
        sender.sendMessage(languageManager.getMessage("command.pool-deleted", Map.of("pool", id)));
        return true;
    }

    private boolean handleRewards(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(languageManager.getMessage("command.rewards-usage"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "create" -> handleRewardCreate(sender, args);
            case "edit" -> handleRewardEdit(sender, args);
            case "delete" -> handleRewardDelete(sender, args);
            case "give" -> handleRewardGive(sender, args);
            case "claim" -> handleRewardClaim(sender, args);
            default -> {
                sender.sendMessage(languageManager.getMessage("command.rewards-usage"));
                yield true;
            }
        };
    }

    private boolean handleRewardCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.reward.create")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(languageManager.getMessage("command.rewards-create-usage"));
            return true;
        }
        String poolId = args[2];
        String rewardId = args[3];
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        if (pool == null) {
            sender.sendMessage(languageManager.getMessage("command.pool-not-found"));
            return true;
        }
        if (pool.rewards().stream().anyMatch(reward -> reward.id().equalsIgnoreCase(rewardId))) {
            sender.sendMessage(languageManager.getMessage("command.reward-already-exists"));
            return true;
        }
        FileConfiguration config = loadConfig("rewards.yml");
        String path = "pools." + poolId + ".rewards." + rewardId;
        config.set(path + ".chance", 1.0);
        config.set(path + ".display-name", rewardId);
        saveConfig(config, "rewards.yml");
        sender.sendMessage(languageManager.getMessage("command.reward-created", Map.of("pool", poolId, "reward", rewardId)));
        return true;
    }

    private boolean handleRewardEdit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.reward.edit")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 6) {
            sender.sendMessage(languageManager.getMessage("command.rewards-edit-usage"));
            return true;
        }
        String poolId = args[2];
        String rewardId = args[3];
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        if (pool == null) {
            sender.sendMessage(languageManager.getMessage("command.pool-not-found"));
            return true;
        }
        Reward reward = pool.rewards().stream()
                .filter(entry -> entry.id().equalsIgnoreCase(rewardId))
                .findFirst()
                .orElse(null);
        if (reward == null) {
            sender.sendMessage(languageManager.getMessage("command.reward-not-found"));
            return true;
        }
        String field = args[4];
        if (isRewardFieldBlocked(field)) {
            sender.sendMessage(languageManager.getMessage("command.reward-field-disabled"));
            return true;
        }
        String value = joinArgs(args, 5);
        FileConfiguration config = loadConfig("rewards.yml");
        config.set("pools." + poolId + ".rewards." + rewardId + "." + field, parseValue(value));
        saveConfig(config, "rewards.yml");
        sender.sendMessage(languageManager.getMessage("command.reward-updated", Map.of("pool", poolId, "reward", rewardId, "field", field)));
        return true;
    }

    private boolean handleRewardDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.reward.delete")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(languageManager.getMessage("command.rewards-delete-usage"));
            return true;
        }
        String poolId = args[2];
        String rewardId = args[3];
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        if (pool == null) {
            sender.sendMessage(languageManager.getMessage("command.pool-not-found"));
            return true;
        }
        Reward reward = pool.rewards().stream()
                .filter(entry -> entry.id().equalsIgnoreCase(rewardId))
                .findFirst()
                .orElse(null);
        if (reward == null) {
            sender.sendMessage(languageManager.getMessage("command.reward-not-found"));
            return true;
        }
        FileConfiguration config = loadConfig("rewards.yml");
        config.set("pools." + poolId + ".rewards." + rewardId, null);
        saveConfig(config, "rewards.yml");
        sender.sendMessage(languageManager.getMessage("command.reward-deleted", Map.of("pool", poolId, "reward", rewardId)));
        return true;
    }

    private boolean handleRewardGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.reward.give")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(languageManager.getMessage("command.rewards-give-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        String poolId = args[3];
        String rewardId = args[4];
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        if (pool == null) {
            sender.sendMessage(languageManager.getMessage("command.pool-not-found"));
            return true;
        }
        Reward reward = pool.rewards().stream()
                .filter(entry -> entry.id().equalsIgnoreCase(rewardId))
                .findFirst()
                .orElse(null);
        if (reward == null) {
            sender.sendMessage(languageManager.getMessage("command.reward-not-found"));
            return true;
        }
        if (target == null) {
            pendingRewardStore.addPending(Objects.requireNonNull(Bukkit.getOfflinePlayer(args[2]).getUniqueId()), poolId, rewardId);
            sender.sendMessage(languageManager.getMessage("command.reward-queued", Map.of("player", args[2], "reward", reward.displayName())));
            return true;
        }
        if (grantReward(target, reward)) {
            sender.sendMessage(languageManager.getMessage("command.reward-given", Map.of("player", target.getName(), "reward", reward.displayName())));
        }
        return true;
    }

    private boolean handleRewardClaim(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.reward.claim")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        Player target = null;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(languageManager.getMessage("command.player-not-found"));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(languageManager.getMessage("command.rewards-claim-usage"));
            return true;
        }
        List<PendingRewardStore.PendingReward> pending = pendingRewardStore.getPending(target.getUniqueId());
        if (pending.isEmpty()) {
            sender.sendMessage(languageManager.getMessage("command.reward-claim-none"));
            return true;
        }
        int delivered = 0;
        List<PendingRewardStore.PendingReward> remaining = new ArrayList<>();
        for (PendingRewardStore.PendingReward entry : pending) {
            Reward reward = findReward(entry.poolId(), entry.rewardId());
            if (reward == null) {
                remaining.add(entry);
                continue;
            }
            if (grantReward(target, reward)) {
                delivered++;
            } else {
                remaining.add(entry);
            }
        }
        pendingRewardStore.setPending(target.getUniqueId(), remaining);
        sender.sendMessage(languageManager.getMessage(
                "command.reward-claim-success",
                Map.of("player", target.getName(), "amount", String.valueOf(delivered))
        ));
        if (!remaining.isEmpty()) {
            sender.sendMessage(languageManager.getMessage("command.reward-claim-remaining"));
        }
        return true;
    }

    private Reward findReward(String poolId, String rewardId) {
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        if (pool == null) {
            return null;
        }
        return pool.rewards().stream()
                .filter(reward -> reward.id().equalsIgnoreCase(rewardId))
                .findFirst()
                .orElse(null);
    }

    private boolean grantReward(Player player, Reward reward) {
        if (isQaMode()) {
            player.sendMessage(languageManager.getMessage("command.reward-qa-mode"));
            return false;
        }
        ItemStack item = ItemUtil.buildItem(reward, player.getWorld(), configLoader, plugin.getMapImageCache());
        if (item.getType() != Material.AIR) {
            player.getInventory().addItem(item);
        } else {
            plugin.getLogger().warning("Reward item missing for " + reward.id() + ". Skipping item delivery.");
        }
        for (String command : reward.commands()) {
            String parsed = command.replace("%player%", player.getName());
            if (CommandUtil.isBroadcastMessage(parsed)) {
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
        player.sendMessage(languageManager.getMessage("session.reward-received", Map.of("reward", reward.displayName())));
        return true;
    }

    private boolean isQaMode() {
        return configLoader.getMainConfig().getBoolean("qa-mode", false);
    }

    private FileConfiguration loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfig(FileConfiguration config, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        try {
            config.save(file);
            configLoader.loadAll();
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar " + fileName + ": " + ex.getMessage());
        }
    }

    private String joinArgs(String[] args, int startIndex) {
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length));
    }

    private Object parseValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("null")) {
            return null;
        }
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(trimmed);
        }
        if (trimmed.matches("-?\\d+")) {
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
                return trimmed;
            }
        }
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private boolean isRewardFieldBlocked(String field) {
        return Set.of(
                "item",
                "amount",
                "custom-model",
                "glow",
                "enchantments",
                "item-stack",
                "reward-item",
                "display-item"
        ).contains(field.toLowerCase(Locale.ROOT));
    }

    private ParsedValue parseValue(String field, FieldType fieldType, String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return switch (fieldType) {
            case STRING -> new ParsedValue(trimmed, trimmed);
            case INTEGER -> {
                try {
                    int value = Integer.parseInt(trimmed);
                    yield new ParsedValue(value, Integer.toString(value));
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            case DECIMAL -> {
                try {
                    double value = Double.parseDouble(trimmed);
                    yield new ParsedValue(value, Double.toString(value));
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            case MATERIAL -> {
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(trimmed);
                if (material == null) {
                    yield null;
                }
                yield new ParsedValue(material.name(), material.name());
            }
            case CRATE_TYPE -> {
                com.extracrates.model.CrateType type = com.extracrates.model.CrateType.fromString(trimmed);
                yield new ParsedValue(type.name().toLowerCase(Locale.ROOT), type.name());
            }
            case OPEN_MODE -> new ParsedValue(trimmed.toLowerCase(Locale.ROOT), trimmed);
            case REWARDS_POOL -> new ParsedValue(trimmed, trimmed);
        };
    }

    private FilterSpec parseFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("[:=]", 2);
        if (parts.length != 2) {
            return null;
        }
        String key = parts[0].trim().toLowerCase(Locale.ROOT);
        String value = parts[1].trim();
        if (value.isEmpty()) {
            return null;
        }
        return switch (key) {
            case "id" -> crate -> crate.id().equalsIgnoreCase(value);
            case "type" -> crate -> crate.type().name().equalsIgnoreCase(value);
            case "open-mode", "openmode" -> crate -> crate.openMode().equalsIgnoreCase(value);
            case "permission" -> crate -> crate.permission().equalsIgnoreCase(value);
            case "rewards-pool", "pool" -> crate -> crate.rewardsPool() != null && crate.rewardsPool().equalsIgnoreCase(value);
            default -> null;
        };
    }

    private FileConfiguration loadCratesConfig() {
        return loadConfig("crates.yml");
    }

    private void saveCratesConfig(FileConfiguration config) {
        saveConfig(config, "crates.yml");
    }

    private Object getCurrentValue(CrateDefinition crate, FieldType fieldType, String field) {
        return switch (fieldType) {
            case STRING -> switch (field) {
                case "display-name" -> crate.displayName();
                case "key-model" -> crate.keyModel();
                case "permission" -> crate.permission();
                case "rewards-pool" -> crate.rewardsPool();
                default -> "";
            };
            case INTEGER -> crate.cooldownSeconds();
            case DECIMAL -> crate.cost();
            case MATERIAL -> crate.keyMaterial() != null ? crate.keyMaterial().name() : "";
            case CRATE_TYPE -> crate.type();
            case OPEN_MODE -> crate.openMode();
            case REWARDS_POOL -> crate.rewardsPool();
        };
    }

    private boolean valuesEqual(Object currentValue, Object newValue) {
        if (currentValue == null && newValue == null) {
            return true;
        }
        if (currentValue == null || newValue == null) {
            return false;
        }
        if (currentValue instanceof String currentString && newValue instanceof String newString) {
            return currentString.equalsIgnoreCase(newString);
        }
        return Objects.equals(currentValue, newValue);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof org.bukkit.Material material) {
            return material.name();
        }
        if (value instanceof com.extracrates.model.CrateType crateType) {
            return crateType.name().toLowerCase(Locale.ROOT);
        }
        return value.toString();
    }

    private boolean isInteger(@NotNull String value) {
        return value.matches("-?\\d+");
    }

    private int parsePage(@NotNull String value) {
        try {
            int page = Integer.parseInt(value);
            return Math.max(1, page);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private String buildHistoryCommand(@NotNull String playerName, @Nullable String crateId, int page) {
        if (crateId == null || crateId.isEmpty()) {
            return "/crates history " + playerName + " " + page;
        }
        return "/crates history " + playerName + " " + crateId + " " + page;
    }

    private String formatEvent(SyncEventType type, String crateId, String rewardId) {
        if (type == null) {
            return "Evento desconocido";
        }
        return switch (type) {
            case CRATE_OPEN -> "Apertura de crate " + crateId;
            case REWARD_GRANTED -> "Recompensa " + rewardId;
            case KEY_CONSUMED -> "Llave consumida en " + crateId;
            case COOLDOWN_SET -> "Cooldown aplicado en " + crateId;
            default -> "Evento " + type.name().toLowerCase(Locale.ROOT);
        };
    }

    private record ParsedValue(Object value, String displayValue) {
    }

    private enum FieldType {
        STRING,
        INTEGER,
        DECIMAL,
        MATERIAL,
        CRATE_TYPE,
        OPEN_MODE,
        REWARDS_POOL
    }

    @FunctionalInterface
    private interface FilterSpec {
        boolean matches(CrateDefinition crate);
    }
}
