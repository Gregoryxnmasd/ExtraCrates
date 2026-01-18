package com.extracrates.event;

import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CrateOpenEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CrateDefinition crate;
    private final boolean preview;
    private List<Reward> rewards;
    private boolean cancelled;

    public CrateOpenEvent(Player player, CrateDefinition crate, List<Reward> rewards, boolean preview) {
        this.player = player;
        this.crate = crate;
        this.preview = preview;
        this.rewards = rewards == null ? new ArrayList<>() : new ArrayList<>(rewards);
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

    public List<Reward> getRewards() {
        return rewards;
    }

    public void setRewards(List<Reward> rewards) {
        this.rewards = rewards == null ? new ArrayList<>() : new ArrayList<>(rewards);
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
