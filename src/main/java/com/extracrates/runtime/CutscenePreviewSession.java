package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.model.CutscenePath;
import com.extracrates.util.CutsceneTimeline;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Player;

import java.util.List;

public class CutscenePreviewSession {
    private final ExtraCratesPlugin plugin;
    private final Player player;
    private final CutscenePath path;
    private final Particle particle;
    private final Runnable onFinish;
    private BukkitRunnable task;
    private boolean finished;

    public CutscenePreviewSession(
            ExtraCratesPlugin plugin,
            Player player,
            CutscenePath path,
            Particle particle,
            Runnable onFinish
    ) {
        this.plugin = plugin;
        this.player = player;
        this.path = path;
        this.particle = particle;
        this.onFinish = onFinish;
    }

    public void start() {
        List<Location> timeline = CutsceneTimeline.build(player.getWorld(), path);
        if (timeline.isEmpty()) {
            finish();
            return;
        }
        int totalTicks = (int) Math.max(1, path.getDurationSeconds() * 20);
        long period = Math.max(1L, totalTicks / Math.max(1, timeline.size()));
        task = new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= timeline.size()) {
                    cancel();
                    finish();
                    return;
                }
                Location point = timeline.get(index++);
                player.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
            }
        };
        task.runTaskTimer(plugin, 0L, period);
    }

    public void end() {
        if (task != null) {
            task.cancel();
        }
        finish();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (onFinish != null) {
            onFinish.run();
        }
    }
}
