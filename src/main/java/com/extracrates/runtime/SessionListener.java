package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
        sessionManager.endSession(event.getPlayer().getUniqueId());
        sessionManager.endPreview(event.getPlayer().getUniqueId());
    }
}
