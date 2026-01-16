package com.extracrates.command;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.api.OpenMode;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.gui.CrateGui;
import com.extracrates.gui.editor.EditorMenu;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CutscenePath;
import com.extracrates.runtime.SessionManager;
import com.extracrates.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CrateCommand implements CommandExecutor, TabCompleter {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final SessionManager sessionManager;
    private final CrateGui crateGui;
    private final EditorMenu editorMenu;

    public CrateCommand(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            SessionManager sessionManager,
            CrateGui crateGui,
            EditorMenu editorMenu
    ) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.sessionManager = sessionManager;
        this.crateGui = crateGui;
        this.editorMenu = editorMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usa /crate gui|open|preview|cutscene|reload|givekey"));
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
                if (sub.equals("open") && !sender.hasPermission("extracrates.open")) {
                    sender.sendMessage(Component.text("Sin permiso."));
                    return true;
                }
                if (sub.equals("preview") && !sender.hasPermission("extracrates.preview")) {
                    sender.sendMessage(Component.text("Sin permiso."));
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
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Uso: /crate " + sub + " <id>"));
                    return true;
                }
                CrateDefinition crate = configLoader.getCrates().get(args[1]);
                if (crate == null) {
                    sender.sendMessage(languageManager.getMessage("command.crate-not-found"));
                    return true;
                }
                if (sub.equals("preview")) {
                    sessionManager.previewCutscene(player, configLoader.getPaths().get(crate.getAnimation().getPath()));
                } else {
                    sessionManager.openCrate(player, crate);
                }
                return true;
            }
            case "cutscene" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Solo jugadores."));
                    return true;
                }
                if (!sender.hasPermission("extracrates.preview")) {
                    sender.sendMessage(Component.text("Sin permiso."));
                    return true;
                }
                if (args.length < 3 || !args[1].equalsIgnoreCase("test")) {
                    sender.sendMessage(Component.text("Uso: /crate cutscene test <id>"));
                    return true;
                }
                CutscenePath path = configLoader.getPaths().get(args[2]);
                if (path == null) {
                    sender.sendMessage(Component.text("Ruta de cutscene no encontrada."));
                    return true;
                }
                sessionManager.previewCutscene(player, path);
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
                ItemStack key = new ItemStack(crate.getKeyMaterial());
                ItemMeta meta = key.getItemMeta();
                if (meta != null) {
                    String keyName = languageManager.getRaw("command.key-item-name", java.util.Map.of("crate_name", crate.getDisplayName()));
                    meta.displayName(TextUtil.color(keyName));
                    if (crate.getKeyModel() != null && !crate.getKeyModel().isEmpty()) {
                        configLoader.getResourcePackRegistry().resolveCustomModelData(crate.getKeyModel())
                                .ifPresent(meta::setCustomModelData);
                    }
                    key.setItemMeta(meta);
                }
                player.getInventory().addItem(key);
                sender.sendMessage(languageManager.getMessage("command.givekey-success"));
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
            default -> {
                sender.sendMessage(languageManager.getMessage("command.unknown-subcommand"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
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
}
