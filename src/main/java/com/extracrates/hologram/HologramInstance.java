package com.extracrates.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface HologramInstance {
    void setText(Component text);

    void teleport(Location location);

    void remove();

    Entity getEntity();
}
