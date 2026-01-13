package com.extracrates.api;

import com.extracrates.model.CrateDefinition;
import org.bukkit.entity.Player;

import java.util.Map;

public interface ExtraCratesApi {
    Map<String, CrateDefinition> getCrates();

    CrateDefinition getCrate(String id);

    boolean openCrate(Player player, String crateId);

    boolean openCrate(Player player, CrateDefinition crate);
}
