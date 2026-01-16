package com.extracrates.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface HologramProvider {
    String getName();

    boolean isAvailable();

    HologramInstance spawnHologram(Location location, Component text, Player viewer);
}
