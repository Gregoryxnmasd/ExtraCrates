package com.extracrates.command;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.LanguageManager;
import com.extracrates.gui.CrateGui;
import com.extracrates.gui.editor.EditorMenu;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CrateType;
import com.extracrates.route.RouteEditorManager;
import com.extracrates.runtime.CutscenePreviewSession;
import com.extracrates.config.ConfigLoader;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
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

import java.io.File;
import java.io.IOException;
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
            sender.sendMessage(Component.text("Usa /crate gui|editor|open|preview|cutscene|reload|sync|givekey|route|mass"));
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
                    sender.sendMessage(Component.text("Solo jugadores."));
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
                    sender.sendMessage(Component.text("Uso: /crate " + sub + " <id>"));
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
            case "cutscene" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(languageManager.getMessage("command.only-players"));
                    return true;
                }
                if (args.length < 2 || !args[1].equalsIgnoreCase("test")) {
                    sender.sendMessage(Component.text("Uso: /crate cutscene test <id>"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Uso: /crate cutscene test <id>"));
                    return true;
                }
                com.extracrates.cutscene.CutscenePath path = configLoader.getPaths().get(args[2]);
                if (path == null) {
                    sender.sendMessage(Component.text("Ruta de cutscene no encontrada."));
                    return true;
                }
                Particle particle = resolveParticle(path.getParticlePreview());
                CutscenePreviewSession preview = new CutscenePreviewSession(plugin, player, path, particle, null);
                preview.start();
                sender.sendMessage(Component.text("Preview iniciada para ruta '" + path.getId() + "'."));
                return true;
            }
            case "route" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Solo jugadores."));
                    return true;
                }
                if (args.length < 2 || !args[1].equalsIgnoreCase("editor")) {
                    sender.sendMessage(Component.text("Uso: /crate route editor <id|stop|cancel>"));
                    return true;
                }
                if (!sender.hasPermission("extracrates.route.editor")) {
                    sender.sendMessage(Component.text("Sin permiso."));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Uso: /crate route editor <id|stop|cancel>"));
                    return true;
                }
                String action = args[2];
                if (action.equalsIgnoreCase("stop")) {
                    if (routeEditorManager.endSession(player, true)) {
                        sender.sendMessage(Component.text("Ruta guardada."));
                    } else {
                        sender.sendMessage(Component.text("No tienes un editor activo."));
                    }
                    return true;
                }
                if (action.equalsIgnoreCase("cancel")) {
                    if (routeEditorManager.endSession(player, false)) {
                        sender.sendMessage(Component.text("Editor cancelado."));
                    } else {
                        sender.sendMessage(Component.text("No tienes un editor activo."));
                    }
                    return true;
                }
                if (!routeEditorManager.startSession(player, action)) {
                    sender.sendMessage(Component.text("Ya tienes un editor activo."));
                    return true;
                }
                sender.sendMessage(Component.text("Editor iniciado para ruta '" + action + "'."));
                sender.sendMessage(Component.text("Haz clic en bloques para marcar puntos. Usa /crate route editor stop para guardar."));
                return true;
            }
            case "mass" -> {
                return handleMass(sender, args);
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
            results.add("reload");
            results.add("sync");
            results.add("givekey");
            results.add("route");
            results.add("mass");
            return results;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mass")) {
            results.add("set");
            return results;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("mass") && args[1].equalsIgnoreCase("set")) {
            results.addAll(MASS_FIELDS.keySet());
            return results;
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("mass") && args[1].equalsIgnoreCase("set")) {
            results.addAll(List.of("type=", "open-mode=", "rewards-pool="));
            return results;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("route")) {
            results.add("editor");
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

    private FileConfiguration loadCratesConfig() {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveCratesConfig(FileConfiguration config) {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        try {
            config.save(file);
            configLoader.loadAll();
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar crates.yml: " + ex.getMessage());
        }
    }

    private ParsedValue parseValue(String field, FieldType fieldType, String rawValue) {
        return switch (fieldType) {
            case STRING -> new ParsedValue(rawValue, rawValue);
            case INTEGER -> {
                try {
                    int parsed = Integer.parseInt(rawValue);
                    if (parsed < 0) {
                        yield null;
                    }
                    yield new ParsedValue(parsed, String.valueOf(parsed));
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            case DECIMAL -> {
                try {
                    double parsed = Double.parseDouble(rawValue);
                    if (parsed < 0) {
                        yield null;
                    }
                    yield new ParsedValue(parsed, String.valueOf(parsed));
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            case MATERIAL -> {
                Material material = Material.matchMaterial(rawValue);
                if (material == null) {
                    yield null;
                }
                String name = material.name();
                yield new ParsedValue(name, name);
            }
            case CRATE_TYPE -> {
                CrateType type = parseCrateTypeStrict(rawValue);
                if (type == null) {
                    yield null;
                }
                String normalized = type.name().toLowerCase(Locale.ROOT);
                yield new ParsedValue(normalized, normalized);
            }
            case OPEN_MODE -> {
                String normalized = normalizeRequiredString(rawValue);
                if (normalized == null) {
                    yield null;
                }
                yield new ParsedValue(normalized, normalized);
            }
            case REWARDS_POOL -> {
                String normalized = normalizeNullableString(rawValue);
                if (!normalized.isEmpty() && !configLoader.getRewardPools().containsKey(normalized)) {
                    yield null;
                }
                yield new ParsedValue(normalized, normalized);
            }
        };
    }

    private FilterSpec parseFilter(String rawFilter) {
        String[] parts = rawFilter.split("[:=]", 2);
        if (parts.length != 2) {
            return null;
        }
        String key = parts[0].toLowerCase(Locale.ROOT);
        String value = parts[1];
        return switch (key) {
            case "type" -> {
                CrateType type = parseCrateTypeStrict(value);
                yield type == null ? null : FilterSpec.type(type);
            }
            case "open-mode" -> {
                String normalized = normalizeRequiredString(value);
                yield normalized == null ? null : FilterSpec.openMode(normalized);
            }
            case "rewards-pool" -> {
                String normalized = normalizeNullableString(value);
                if (!normalized.isEmpty() && !configLoader.getRewardPools().containsKey(normalized)) {
                    yield null;
                }
                yield FilterSpec.rewardsPool(normalized);
            }
            default -> null;
        };
    }

    private CrateType parseCrateTypeStrict(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(CrateType.values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(null);
    }

    private String normalizeRequiredString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeNullableString(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("null")) {
            return "";
        }
        return trimmed;
    }

    private Object getCurrentValue(CrateDefinition crate, FieldType fieldType, String field) {
        return switch (fieldType) {
            case STRING -> switch (field) {
                case "display-name" -> Optional.ofNullable(crate.displayName()).orElse("");
                case "key-model" -> Optional.ofNullable(crate.keyModel()).orElse("");
                case "permission" -> Optional.ofNullable(crate.permission()).orElse("");
                default -> "";
            };
            case INTEGER -> crate.cooldownSeconds();
            case DECIMAL -> crate.cost();
            case MATERIAL -> crate.keyMaterial().name();
            case CRATE_TYPE -> crate.type().name().toLowerCase(Locale.ROOT);
            case OPEN_MODE -> Optional.ofNullable(crate.openMode()).orElse("");
            case REWARDS_POOL -> Optional.ofNullable(crate.rewardsPool()).orElse("");
        };
    }

    private boolean valuesEqual(Object currentValue, Object newValue) {
        if (currentValue instanceof Double currentDouble && newValue instanceof Double newDouble) {
            return Double.compare(currentDouble, newDouble) == 0;
        }
        return Objects.equals(currentValue, newValue);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double doubleValue) {
            return String.valueOf(doubleValue);
        }
        return String.valueOf(value);
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

    private record ParsedValue(Object value, String displayValue) {
    }

    private record FilterSpec(FilterType type, String stringValue, CrateType crateType) {
        static FilterSpec type(CrateType type) {
            return new FilterSpec(FilterType.CRATE_TYPE, null, type);
        }

        static FilterSpec openMode(String mode) {
            return new FilterSpec(FilterType.OPEN_MODE, mode, null);
        }

        static FilterSpec rewardsPool(String pool) {
            return new FilterSpec(FilterType.REWARDS_POOL, pool, null);
        }

        boolean matches(CrateDefinition crate) {
            return switch (type) {
                case CRATE_TYPE -> crate.type() == crateType;
                case OPEN_MODE -> normalize(crate.openMode()).equalsIgnoreCase(normalize(stringValue));
                case REWARDS_POOL -> normalize(crate.rewardsPool()).equalsIgnoreCase(normalize(stringValue));
            };
        }

        private String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private enum FilterType {
        CRATE_TYPE,
        OPEN_MODE,
        REWARDS_POOL
    }
}
