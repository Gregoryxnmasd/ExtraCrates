package com.extracrates.api;

import org.bukkit.entity.Player;

/**
 * Public API for interacting with ExtraCrates.
 */
public interface ExtraCratesApi {
    /**
     * Opens a crate for the given player using the provided mode.
     *
     * @param player   target player
     * @param crateId  crate identifier from crates.yml
     * @param openMode mode override for how the crate is opened
     * @return true when the crate was opened successfully
     */
    boolean openCrate(Player player, String crateId, OpenMode openMode);

    /**
     * Plays a crate preview for the given player without granting rewards.
     *
     * @param player  target player
     * @param crateId crate identifier from crates.yml
     * @return true when the preview started successfully
     */
    boolean previewCrate(Player player, String crateId);
}
