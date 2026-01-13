package com.extracrates;

import com.extracrates.api.ExtraCratesApi;
import com.extracrates.command.CrateCommand;
import com.extracrates.gui.CrateGui;
import com.extracrates.runtime.SessionListener;
import com.extracrates.runtime.core.ConfigLoader;
import com.extracrates.runtime.core.ExtraCratesApiService;
import com.extracrates.runtime.core.SessionManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraCratesPlugin extends JavaPlugin {
    private ConfigLoader configLoader;
    private SessionManager sessionManager;
    private CrateGui crateGui;
    private ExtraCratesApi apiService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("crates.yml", false);
        saveResource("rewards.yml", false);
        saveResource("paths.yml", false);

        configLoader = new ConfigLoader(this);
        configLoader.loadAll();

        sessionManager = new SessionManager(this, configLoader);
        apiService = new ExtraCratesApiService(configLoader, sessionManager);
        getServer().getServicesManager().register(ExtraCratesApi.class, apiService, this, ServicePriority.Normal);
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
        if (apiService != null) {
            getServer().getServicesManager().unregister(ExtraCratesApi.class, apiService);
        }
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
    }
}
