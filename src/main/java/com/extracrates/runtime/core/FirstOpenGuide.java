package com.extracrates.runtime.core;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public final class FirstOpenGuide {
    private static final List<GuideStep> STEPS = List.of(
            new GuideStep("guide.step-1.message", "guide.step-1.actionbar", "guide.step-1.bossbar"),
            new GuideStep("guide.step-2.message", "guide.step-2.actionbar", "guide.step-2.bossbar"),
            new GuideStep("guide.step-3.message", "guide.step-3.actionbar", "guide.step-3.bossbar")
    );

    private FirstOpenGuide() {
    }

    public static void start(ExtraCratesPlugin plugin, ConfigLoader configLoader, LanguageManager languageManager, Player player) {
        if (STEPS.isEmpty()) {
            return;
        }
        int stepDurationTicks = Math.max(20, configLoader.getMainConfig().getInt("guide.step-duration-ticks", 60));
        BossBar bossBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
        bossBar.addPlayer(player);
        bossBar.setVisible(true);

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup();
                    cancel();
                    return;
                }
                if (index >= STEPS.size()) {
                    cleanup();
                    cancel();
                    return;
                }
                GuideStep step = STEPS.get(index);
                String bossbarText = resolveText(languageManager, step.bossbarKey());
                if (!bossbarText.isBlank()) {
                    bossBar.setTitle(TextUtil.colorString(bossbarText));
                }
                bossBar.setProgress(Math.min(1.0, (index + 1.0) / STEPS.size()));
                String actionBarText = resolveText(languageManager, step.actionbarKey());
                if (!actionBarText.isBlank()) {
                    player.sendActionBar(TextUtil.color(actionBarText));
                }
                String messageText = resolveText(languageManager, step.messageKey());
                if (!messageText.isBlank()) {
                    player.sendMessage(TextUtil.color(messageText));
                }
                index++;
            }

            private void cleanup() {
                bossBar.removeAll();
                bossBar.setVisible(false);
            }
        }.runTaskTimer(plugin, 0L, stepDurationTicks);
    }

    private static String resolveText(LanguageManager languageManager, String key) {
        String text = languageManager.getRaw(key, java.util.Collections.emptyMap());
        if (text == null || text.isBlank() || text.equals(key)) {
            return "";
        }
        return text;
    }

    private record GuideStep(String messageKey, String actionbarKey, String bossbarKey) {
    }
}
