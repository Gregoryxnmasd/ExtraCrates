package com.extracrates;

import com.extracrates.command.CrateCommand;
import com.extracrates.config.ConfigLoader;
import com.extracrates.gui.CrateGui;
import com.extracrates.runtime.SessionManager;
import com.extracrates.runtime.SessionListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraCratesPlugin extends JavaPlugin {
    private ConfigLoader configLoader;
    private SessionManager sessionManager;
    private CrateGui crateGui;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("crates.yml", false);
        saveResource("rewards.yml", false);
        saveResource("paths.yml", false);

        configLoader = new ConfigLoader(this);
        configLoader.loadAll();

        setupEconomy();
        sessionManager = new SessionManager(this, configLoader, economy);
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
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return;
        }
        economy = registration.getProvider();
    }
}
