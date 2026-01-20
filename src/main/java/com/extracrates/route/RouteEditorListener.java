package com.extracrates.route;

import com.extracrates.ExtraCratesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

@SuppressWarnings("unused")
public class RouteEditorListener implements Listener {
    private final RouteEditorManager routeEditorManager;

    public RouteEditorListener(ExtraCratesPlugin plugin, RouteEditorManager routeEditorManager) {
        this.routeEditorManager = routeEditorManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        routeEditorManager.endSession(event.getPlayer(), false);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (routeEditorManager.hasNoSession(event.getPlayer())) {
            return;
        }
        routeEditorManager.endSession(event.getPlayer(), false);
    }
}
