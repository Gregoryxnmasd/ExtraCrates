package com.extracrates.api;

import com.extracrates.model.CrateDefinition;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Public API exposed via the Bukkit services manager for external plugins.
 */
@SuppressWarnings("unused")
public interface ExtraCratesApi {
    Map<String, CrateDefinition> getCrates();

    CrateDefinition getCrate(String id);

    boolean openCrate(Player player, String crateId);

    boolean openCrate(Player player, CrateDefinition crate);
}
