package com.extracrates.api;

import com.extracrates.config.ConfigLoader;
import com.extracrates.model.CrateDefinition;
import com.extracrates.runtime.SessionManager;
import org.bukkit.entity.Player;

import java.util.Objects;

public class ExtraCratesApiImpl implements ExtraCratesApi {
    private final ConfigLoader configLoader;
    private final SessionManager sessionManager;

    public ExtraCratesApiImpl(ConfigLoader configLoader, SessionManager sessionManager) {
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
    }

    @Override
    public boolean openCrate(Player player, String crateId, OpenMode openMode) {
        if (player == null || crateId == null || openMode == null) {
            return false;
        }
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        if (crate == null) {
            return false;
        }
        return sessionManager.openCrate(player, crate, openMode);
    }

    @Override
    public boolean previewCrate(Player player, String crateId) {
        return openCrate(player, crateId, OpenMode.PREVIEW);
    }
}
