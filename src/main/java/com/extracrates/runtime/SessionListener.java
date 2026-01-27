package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.runtime.core.CrateSession;
import com.extracrates.runtime.core.SessionManager;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

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
        CrateSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        sessionManager.handleSessionQuit(event.getPlayer(), session);
        sessionManager.endSession(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        CrateSession session = sessionManager.getSession(playerId);
        if (session != null && !session.isActive()) {
            sessionManager.clearCrateEffects(player);
            sessionManager.removeSession(playerId);
        }
        if (plugin.getProtocolEntityHider() != null) {
            return;
        }
        for (CrateSession session : sessionManager.getSessions()) {
            session.hideEntitiesFrom(player);
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

    @EventHandler
    public void onRerollInteract(PlayerInteractEvent event) {
        CrateSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            session.handleRerollInput(false);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRerollAnimation(PlayerAnimationEvent event) {
        CrateSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        session.handleRerollInput(false);
    }

    @EventHandler
    public void onRerollSneak(PlayerToggleSneakEvent event) {
        CrateSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null || !event.isSneaking()) {
            return;
        }
        session.handleRerollInput(true);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        CrateSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null || !session.isActive()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        CrateSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null || !session.isActive()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        CrateSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        CrateSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        CrateSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        CrateSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
    }
}
