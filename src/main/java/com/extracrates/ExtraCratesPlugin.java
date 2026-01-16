package com.extracrates;

import com.extracrates.api.ExtraCratesApi;
import com.extracrates.api.ExtraCratesApiImpl;
import com.extracrates.command.CrateCommand;
import com.extracrates.command.SyncCommand;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.gui.CrateGui;
import com.extracrates.runtime.ProtocolEntityHider;
import com.extracrates.runtime.SessionManager;
import com.extracrates.runtime.SessionListener;
import com.extracrates.sync.SyncBridge;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraCratesPlugin extends JavaPlugin {
    private ConfigLoader configLoader;
    private LanguageManager languageManager;
    private SessionManager sessionManager;
    private CrateGui crateGui;
    private ProtocolEntityHider protocolEntityHider;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("crates.yml", false);
        saveResource("rewards.yml", false);
        saveResource("paths.yml", false);
        saveResource("lang/es_es.yml", false);
        saveResource("lang/en_us.yml", false);

        configLoader = new ConfigLoader(this);
        configLoader.loadAll();
        ConfigValidator validator = new ConfigValidator(this, configLoader);
        validator.report(validator.validate());

        protocolEntityHider = ProtocolEntityHider.createIfPresent(this);

        sessionManager = new SessionManager(this, configLoader);
        new SessionListener(this, sessionManager);
        routeEditorManager = new RouteEditorManager(this, configLoader);
        new RouteEditorListener(this, routeEditorManager);
        crateGui = new CrateGui(this, configLoader, sessionManager);
        api = new ExtraCratesApiImpl(configLoader, sessionManager);

        PluginCommand crateCommand = getCommand("crate");
        if (crateCommand != null) {
            CrateCommand executor = new CrateCommand(this, configLoader, sessionManager, crateGui, routeEditorManager);
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
