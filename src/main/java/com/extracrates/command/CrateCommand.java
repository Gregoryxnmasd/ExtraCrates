package com.extracrates.command;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.LanguageManager;
import com.extracrates.gui.CrateGui;
import com.extracrates.gui.editor.EditorMenu;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.route.RouteEditorManager;
import com.extracrates.runtime.CutscenePreviewSession;
import com.extracrates.config.ConfigLoader;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.storage.PendingRewardStore;
import com.extracrates.storage.PendingRewardStore.PendingReward;
import com.extracrates.util.ItemUtil;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.TextUtil;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final SessionManager sessionManager;
    private final CrateGui crateGui;
    private final EditorMenu editorMenu;
    private final SyncCommand syncCommand;
    private final RouteEditorManager routeEditorManager;
    private final ResourcepackModelResolver resourcepackModelResolver;
    private final PendingRewardStore pendingRewardStore;

    public CrateCommand(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            LanguageManager languageManager,
            SessionManager sessionManager,
            CrateGui crateGui,
            EditorMenu editorMenu,
            SyncCommand syncCommand,
            RouteEditorManager routeEditorManager,
            ResourcepackModelResolver resourcepackModelResolver,
            PendingRewardStore pendingRewardStore
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
        this.pendingRewardStore = pendingRewardStore;
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
            results.add("editor");
            results.add("open");
            results.add("preview");
            results.add("cutscene");
            results.add("reload");
            results.add("sync");
            results.add("givekey");
            results.add("route");
            results.add("crates");
            results.add("pools");
            results.add("rewards");
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
