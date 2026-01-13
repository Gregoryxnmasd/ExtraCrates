package com.extracrates;

import com.extracrates.command.CrateCommand;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.gui.CrateGui;
import com.extracrates.runtime.SessionManager;
import com.extracrates.runtime.SessionListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraCratesPlugin extends JavaPlugin {
    private ConfigLoader configLoader;
    private LanguageManager languageManager;
    private SessionManager sessionManager;
    private CrateGui crateGui;

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

        languageManager = new LanguageManager(this);
        languageManager.load();

        sessionManager = new SessionManager(this, configLoader, languageManager);
        new SessionListener(this, sessionManager);
        crateGui = new CrateGui(this, configLoader, sessionManager);

        PluginCommand crateCommand = getCommand("crate");
        if (crateCommand != null) {
            CrateCommand executor = new CrateCommand(this, configLoader, sessionManager, crateGui, languageManager);
            crateCommand.setExecutor(executor);
            crateCommand.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }
}
