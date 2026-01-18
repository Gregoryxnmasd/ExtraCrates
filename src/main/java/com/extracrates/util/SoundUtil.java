package com.extracrates.util;

import com.extracrates.config.SettingsSnapshot;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public final class SoundUtil {
    private SoundUtil() {
    }

    public static void play(Player player, SettingsSnapshot.SoundEffect effect) {
        if (player == null || effect == null || effect.sound() == null) {
            return;
        }
        player.playSound(player.getLocation(), effect.sound(), SoundCategory.PLAYERS, effect.volume(), effect.pitch());
    }
}
