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
import com.extracrates.runtime.core.SessionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usa /crate gui|editor|open|preview|cutscene|reload|sync|givekey|route|migrate"));
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
                if (sub.equals("preview") && !configLoader.getMainConfig().getBoolean("gui.preview-enabled", true)) {
                    sender.sendMessage(Component.text("Las previews están deshabilitadas en la configuración."));
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
            case "migrate" -> {
                if (!sender.hasPermission("extracrates.migrate")) {
                    sender.sendMessage(languageManager.getMessage("command.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Uso: /crate migrate <sql|local>"));
                    return true;
                }
                StorageTarget target = StorageTarget.fromString(args[1]).orElse(null);
                if (target == null) {
                    sender.sendMessage(Component.text("Destino inválido. Usa /crate migrate <sql|local>"));
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
                sessionManager.grantKey(player, crate, 1);
                sender.sendMessage(languageManager.getMessage("command.givekey-success"));
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
            results.add("migrate");
            return results;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("crates")) {
            results.addAll(List.of("create", "edit", "delete"));
            return results;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pools")) {
            results.addAll(List.of("create", "edit", "delete"));
            return results;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rewards")) {
            results.addAll(List.of("create", "edit", "delete", "give", "claim"));
            return results;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("crates") && (args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("delete"))) {
            results.addAll(configLoader.getCrates().keySet());
            return results;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pools") && (args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("delete"))) {
            results.addAll(configLoader.getRewardPools().keySet());
            return results;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rewards")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("create") || action.equals("edit") || action.equals("delete")) {
                results.addAll(configLoader.getRewardPools().keySet());
                return results;
            }
            if (action.equals("give") || action.equals("claim")) {
                Bukkit.getOnlinePlayers().forEach(player -> results.add(player.getName()));
                return results;
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("rewards")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("edit") || action.equals("delete")) {
                RewardPool pool = configLoader.getRewardPools().get(args[2]);
                if (pool != null) {
                    pool.rewards().forEach(reward -> results.add(reward.id()));
                }
                return results;
            }
            if (action.equals("give")) {
                results.addAll(configLoader.getRewardPools().keySet());
                return results;
            }
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("rewards") && args[1].equalsIgnoreCase("give")) {
            RewardPool pool = configLoader.getRewardPools().get(args[3]);
            if (pool != null) {
                pool.rewards().forEach(reward -> results.add(reward.id()));
            }
            return results;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("crates") && args[1].equalsIgnoreCase("edit")) {
            results.addAll(List.of("display-name", "type", "open-mode", "key-model", "cooldown-seconds", "cost", "permission", "rewards-pool"));
            return results;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("pools") && args[1].equalsIgnoreCase("edit")) {
            results.add("roll-count");
            return results;
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("rewards") && args[1].equalsIgnoreCase("edit")) {
            results.addAll(List.of("display-name", "chance", "item", "amount", "custom-model", "glow", "commands", "hologram", "map-image"));
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
        if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
            results.add("sql");
            results.add("local");
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
        config.set(path + ".item", "STONE");
        config.set(path + ".amount", 1);
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
        List<PendingReward> pending = pendingRewardStore.getPending(target.getUniqueId());
        if (pending.isEmpty()) {
            sender.sendMessage(languageManager.getMessage("command.reward-claim-none"));
            return true;
        }
        int delivered = 0;
        List<PendingReward> remaining = new ArrayList<>();
        for (PendingReward entry : pending) {
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
        player.getInventory().addItem(item);
        for (String command : reward.commands()) {
            String parsed = command.replace("%player%", player.getName());
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
}
