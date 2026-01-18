package com.extracrates.runtime.core;

import com.extracrates.api.ExtraCratesApi;
import com.extracrates.config.ConfigLoader;
import com.extracrates.model.CrateDefinition;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Primary implementation of the public ExtraCrates API exposed by the plugin,
 * replacing the legacy {@code ExtraCratesApiImpl} wrapper.
 */
public class ExtraCratesApiService implements ExtraCratesApi {
    private final ConfigLoader configLoader;
    private final SessionManager sessionManager;

    public ExtraCratesApiService(ConfigLoader configLoader, SessionManager sessionManager) {
        this.configLoader = configLoader;
        this.sessionManager = sessionManager;
    }

    @Override
    public Map<String, CrateDefinition> getCrates() {
        return configLoader.getCrates();
    }

    @Override
    public CrateDefinition getCrate(String id) {
        return configLoader.getCrates().get(id);
    }

    @Override
    public boolean openCrate(Player player, String crateId) {
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        if (crate == null) {
            return false;
        }
        return sessionManager.openCrate(player, crate);
    }

    @Override
    public boolean openCrate(Player player, CrateDefinition crate) {
        if (crate == null) {
            return false;
        }
        return sessionManager.openCrate(player, crate);
    }
}
