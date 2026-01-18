package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.runtime.core.CrateSession;
import com.extracrates.runtime.core.SessionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@SuppressWarnings("unused")
public class SessionListener implements Listener {
    private final ExtraCratesPlugin plugin;
    private final SessionManager sessionManager;

    public SessionListener(ExtraCratesPlugin plugin, SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Preview sessions share the same storage as normal sessions.
        sessionManager.endSession(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getProtocolEntityHider() != null) {
            return;
        }
        for (CrateSession session : sessionManager.getSessions()) {
            session.hideEntitiesFrom(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        CrateSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null || !session.isMovementLocked()) {
            return;
        }
        if (event.getFrom().getWorld() != null && event.getTo().getWorld() != null
                && !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        if (event.getFrom().distanceSquared(event.getTo()) > 0.0001) {
            event.setTo(event.getFrom());
        }
    }
}
