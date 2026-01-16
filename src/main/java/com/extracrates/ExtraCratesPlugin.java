package com.extracrates;

import com.extracrates.command.CrateCommand;
import com.extracrates.command.SyncCommand;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.ConfigValidator;
import com.extracrates.gui.CrateGui;
import com.extracrates.runtime.SessionManager;
import com.extracrates.runtime.SessionListener;
import com.extracrates.sync.SyncBridge;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraCratesPlugin extends JavaPlugin {
    private ConfigLoader configLoader;
    private SessionManager sessionManager;
    private CrateGui crateGui;
    private SyncBridge syncBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("crates.yml", false);
        saveResource("rewards.yml", false);
        saveResource("paths.yml", false);

        configLoader = new ConfigLoader(this);
        configLoader.loadAll();
        ConfigValidator validator = new ConfigValidator(this, configLoader);
        validator.report(validator.validate());

        sessionManager = new SessionManager(this, configLoader, null);
        syncBridge = new SyncBridge(this, configLoader, sessionManager);
        sessionManager.setSyncBridge(syncBridge);
        new SessionListener(this, sessionManager);
        crateGui = new CrateGui(this, configLoader, sessionManager);

        PluginCommand crateCommand = getCommand("crate");
        if (crateCommand != null) {
            SyncCommand syncCommand = new SyncCommand(this, configLoader, syncBridge);
            CrateCommand executor = new CrateCommand(this, configLoader, sessionManager, crateGui, syncCommand);
            crateCommand.setExecutor(executor);
            crateCommand.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (syncBridge != null) {
            syncBridge.shutdown();
        }
    }
}
