package com.extracrates.api;

import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

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

    default boolean hasActiveSession(Player player) {
        return false;
    }

    default @Nullable Reward getCurrentReward(Player player) {
        return null;
    }

    default int getRemainingTicks(Player player) {
        return -1;
    }
}
