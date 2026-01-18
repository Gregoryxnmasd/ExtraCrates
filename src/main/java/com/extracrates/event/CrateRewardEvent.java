package com.extracrates.event;

import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CrateRewardEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CrateDefinition crate;
    private final boolean preview;
    private Reward reward;
    private boolean cancelled;

    public CrateRewardEvent(Player player, CrateDefinition crate, Reward reward, boolean preview) {
        this.player = player;
        this.crate = crate;
        this.reward = reward;
        this.preview = preview;
    }

    public Player getPlayer() {
        return player;
    }

    public CrateDefinition getCrate() {
        return crate;
    }

    public boolean isPreview() {
        return preview;
    }

    public Reward getReward() {
        return reward;
    }

    public void setReward(Reward reward) {
        this.reward = reward;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
