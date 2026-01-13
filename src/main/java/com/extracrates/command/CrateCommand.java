package com.extracrates.command;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.gui.CrateGui;
import com.extracrates.model.CrateDefinition;
import com.extracrates.runtime.SessionManager;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
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

    public CrateCommand(ExtraCratesPlugin plugin, ConfigLoader configLoader, SessionManager sessionManager, CrateGui crateGui) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.sessionManager = sessionManager;
        this.crateGui = crateGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usa /crate gui|open|preview|reload|givekey"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Solo jugadores."));
                    return true;
                }
                if (!sender.hasPermission("extracrates.gui")) {
                    sender.sendMessage(Component.text("Sin permiso."));
                    return true;
                }
                crateGui.open(player);
                return true;
            }
            case "open", "preview" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Solo jugadores."));
                    return true;
                }
                if (!sender.hasPermission("extracrates.open")) {
                    sender.sendMessage(Component.text("Sin permiso."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Uso: /crate open <id>"));
                    return true;
                }
                CrateDefinition crate = configLoader.getCrates().get(args[1]);
                if (crate == null) {
                    sender.sendMessage(Component.text("Crate no encontrada."));
                    return true;
                }
                sessionManager.openCrate(player, crate);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("extracrates.reload")) {
                    sender.sendMessage(Component.text("Sin permiso."));
                    return true;
                }
                plugin.reloadConfig();
                configLoader.loadAll();
                sender.sendMessage(Component.text("Configuraciones recargadas."));
                return true;
            }
            case "givekey" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Solo jugadores."));
                    return true;
                }
                if (!sender.hasPermission("extracrates.givekey")) {
                    sender.sendMessage(Component.text("Sin permiso."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Uso: /crate givekey <id>"));
                    return true;
                }
                CrateDefinition crate = configLoader.getCrates().get(args[1]);
                if (crate == null) {
                    sender.sendMessage(Component.text("Crate no encontrada."));
                    return true;
                }
                ItemStack key = new ItemStack(crate.getKeyMaterial());
                ItemMeta meta = key.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.color(crate.getDisplayName() + " &7(llave)"));
                    if (crate.getKeyModel() != null && !crate.getKeyModel().isEmpty()) {
                        try {
                            meta.setCustomModelData(Integer.parseInt(crate.getKeyModel()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    key.setItemMeta(meta);
                }
                player.getInventory().addItem(key);
                sender.sendMessage(Component.text("Llave entregada."));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Subcomando desconocido."));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> results = new ArrayList<>();
        if (args.length == 1) {
            results.add("gui");
            results.add("open");
            results.add("preview");
            results.add("reload");
            results.add("givekey");
            return results;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("open") || args[0].equalsIgnoreCase("preview") || args[0].equalsIgnoreCase("givekey"))) {
            results.addAll(configLoader.getCrates().keySet());
        }
        return results;
    }
}
