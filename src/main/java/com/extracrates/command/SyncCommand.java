package com.extracrates.command;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.runtime.core.CrateSession;
import com.extracrates.sync.SyncBridge;
import com.extracrates.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;

public class SyncCommand {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final SyncBridge syncBridge;
    private final LanguageManager languageManager;

    public SyncCommand(ExtraCratesPlugin plugin, ConfigLoader configLoader, SyncBridge syncBridge, LanguageManager languageManager) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.syncBridge = syncBridge;
        this.languageManager = languageManager;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extracrates.sync")) {
            sender.sendMessage(languageManager.getMessage("command.no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(languageManager.getMessage("command.sync-usage"));
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "status" -> {
                for (String line : syncBridge.getStatusLines()) {
                    sender.sendMessage(languageManager.getMessage("command.sync-status-line", java.util.Map.of("line", line)));
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                configLoader.loadAll();
                syncBridge.reload();
                ItemUtil.clearItemCache();
                CrateSession.clearRewardDisplayCache();
                sender.sendMessage(Component.text("Sync recargado."));
            }
            case "flush" -> {
                syncBridge.flush();
                sender.sendMessage(languageManager.getMessage("command.sync-flushed"));
            }
            default -> {
                sender.sendMessage(languageManager.getMessage("command.unknown-subcommand"));
            }
        }
        return true;
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return List.of("status", "reload", "flush");
        }
        return List.of();
    }
}
