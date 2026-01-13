package com.extracrates;

import com.extracrates.command.CrateCommand;
import com.extracrates.config.ConfigLoader;
import com.extracrates.gui.CrateGui;
import com.extracrates.runtime.ProtocolEntityHider;
import com.extracrates.runtime.SessionManager;
import com.extracrates.runtime.SessionListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraCratesPlugin extends JavaPlugin {
    private ConfigLoader configLoader;
    private SessionManager sessionManager;
    private CrateGui crateGui;
    private ProtocolEntityHider protocolEntityHider;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("crates.yml", false);
        saveResource("rewards.yml", false);
        saveResource("paths.yml", false);

        configLoader = new ConfigLoader(this);
        configLoader.loadAll();

        protocolEntityHider = ProtocolEntityHider.createIfPresent(this);

        sessionManager = new SessionManager(this, configLoader);
        new SessionListener(this, sessionManager);
        crateGui = new CrateGui(this, configLoader, sessionManager);

        PluginCommand crateCommand = getCommand("crate");
        if (crateCommand != null) {
            CrateCommand executor = new CrateCommand(this, configLoader, sessionManager, crateGui);
            crateCommand.setExecutor(executor);
            crateCommand.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (protocolEntityHider != null) {
            protocolEntityHider.shutdown();
        }
    }

    public ProtocolEntityHider getProtocolEntityHider() {
        return protocolEntityHider;
    }
}
