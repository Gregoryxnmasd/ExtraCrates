package com.extracrates;

import com.extracrates.api.ExtraCratesApi;
import com.extracrates.command.CrateCommand;
import com.extracrates.command.SyncCommand;
import com.extracrates.config.ConfigValidator;
import com.extracrates.config.LanguageManager;
import com.extracrates.economy.EconomyService;
import com.extracrates.gui.CrateGui;
import com.extracrates.gui.OpenHistoryGui;
import com.extracrates.gui.editor.ConfirmationMenu;
import com.extracrates.gui.editor.EditorInputManager;
import com.extracrates.gui.editor.EditorMenu;
import com.extracrates.route.RouteEditorListener;
import com.extracrates.route.RouteEditorManager;
import com.extracrates.runtime.ProtocolEntityHider;
import com.extracrates.runtime.SessionListener;
import com.extracrates.config.ConfigLoader;
import com.extracrates.runtime.core.ExtraCratesApiService;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.storage.PendingRewardStore;
import com.extracrates.sync.SyncBridge;
import com.extracrates.util.MapImageCache;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraCratesPlugin extends JavaPlugin {
    private ConfigLoader configLoader;
    private LanguageManager languageManager;
    private SessionManager sessionManager;
    private CrateGui crateGui;
    private OpenHistoryGui openHistoryGui;
    private ExtraCratesApi apiService;
    private RouteEditorManager routeEditorManager;
    private MapImageCache mapImageCache;
    private ProtocolEntityHider protocolEntityHider;
    private EditorMenu editorMenu;
    private Economy economy;
    private SyncBridge syncBridge;
    private PendingRewardStore pendingRewardStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("crates.yml", false);
        saveResource("rewards.yml", false);
        saveResource("paths.yml", false);
        saveResource("lang/en_us.yml", false);

        configLoader = new ConfigLoader(this);
        configLoader.loadAll();
        ConfigValidator validator = new ConfigValidator(this, configLoader);
        ConfigValidator.ValidationReport report = validator.validate();
        validator.report(report);
        configLoader.setConfigValid(report.isValid());

        languageManager = new LanguageManager(this);
        languageManager.load();

        setupEconomy();
        EconomyService economyService = new EconomyService(this);
        sessionManager = new SessionManager(this, configLoader, economyService);
        syncBridge = new SyncBridge(this, configLoader, sessionManager);
        SyncCommand syncCommand = new SyncCommand(this, configLoader, syncBridge, languageManager);
        apiService = new ExtraCratesApiService(configLoader, sessionManager);
        getServer().getServicesManager().register(ExtraCratesApi.class, apiService, this, ServicePriority.Normal);
        new SessionListener(this, sessionManager);
        routeEditorManager = new RouteEditorManager(this, configLoader);
        new RouteEditorListener(this, routeEditorManager);
        crateGui = new CrateGui(this, configLoader, sessionManager);
        openHistoryGui = new OpenHistoryGui(this, configLoader, sessionManager);
        mapImageCache = new MapImageCache(this);
        protocolEntityHider = ProtocolEntityHider.createIfPresent(this);
        pendingRewardStore = new PendingRewardStore(this);
        EditorInputManager inputManager = new EditorInputManager(this);
        ConfirmationMenu confirmationMenu = new ConfirmationMenu(this, configLoader);
        editorMenu = new EditorMenu(this, configLoader, inputManager, confirmationMenu, sessionManager);

        PluginCommand crateCommand = getCommand("crate");
        if (crateCommand != null) {
            CrateCommand executor = new CrateCommand(
                    this,
                    configLoader,
                    languageManager,
                    sessionManager,
                    crateGui,
                    openHistoryGui,
                    editorMenu,
                    syncCommand,
                    routeEditorManager
            );
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
        if (protocolEntityHider != null) {
            protocolEntityHider.shutdown();
        }
        if (syncBridge != null) {
            syncBridge.shutdown();
        }
    }

    public ProtocolEntityHider getProtocolEntityHider() {
        return protocolEntityHider;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public RouteEditorManager getRouteEditorManager() {
        return routeEditorManager;
    }

    public EditorMenu getEditorMenu() {
        return editorMenu;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
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

    public MapImageCache getMapImageCache() {
        return mapImageCache;
    }

    public PendingRewardStore getPendingRewardStore() {
        return pendingRewardStore;
    }
}
