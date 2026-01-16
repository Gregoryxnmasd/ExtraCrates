package com.extracrates.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * SPI for hologram integrations; implementers may live in external plugins.
 */
@SuppressWarnings("unused")
public interface HologramProvider {
    String getName();

    boolean isAvailable();

    HologramInstance spawnHologram(Location location, Component text, Player viewer);
}
