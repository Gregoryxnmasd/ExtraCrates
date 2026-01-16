package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        boolean cleanupOnQuit = plugin.getConfig().getBoolean("sessions.cleanup-on-quit", true);
        if (cleanupOnQuit) {
            sessionManager.endSession(event.getPlayer().getUniqueId());
            return;
        }
        int fallbackTicks = plugin.getConfig().getInt("sessions.max-duration-ticks", 600);
        long delay = Math.max(1L, fallbackTicks);
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> sessionManager.endSession(event.getPlayer().getUniqueId()),
                delay
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        CrateSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null || !session.isMovementLocked()) {
            return;
        }
        if (event.getTo() == null) {
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
