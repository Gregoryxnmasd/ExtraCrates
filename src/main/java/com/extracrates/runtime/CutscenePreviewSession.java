package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.cutscene.CutscenePath;
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
        int maxParticlesPerTick = Math.max(1, plugin.getConfig().getInt("cutscene.preview-particles-per-tick", 6));
        int baseParticlesPerTick = Math.max(1, (int) Math.ceil(timeline.size() / (double) totalTicks));
        task = new BukkitRunnable() {
            int index = 0;
            int elapsedTicks = 0;

            @Override
            public void run() {
                if (index >= timeline.size()) {
                    cancel();
                    finish();
                    return;
                }
                int ticksRemaining = Math.max(1, totalTicks - elapsedTicks);
                int pointsRemaining = timeline.size() - index;
                int desiredPerTick = (int) Math.ceil(pointsRemaining / (double) ticksRemaining);
                int particlesPerTick = Math.min(maxParticlesPerTick, Math.max(baseParticlesPerTick, desiredPerTick));
                Location nextPoint = timeline.get(index);
                int throttledPerTick = applyDistanceThrottle(particlesPerTick, nextPoint);
                for (int i = 0; i < throttledPerTick && index < timeline.size(); i++) {
                    Location point = timeline.get(index++);
                    player.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
                }
                elapsedTicks++;
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
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

    private int applyDistanceThrottle(int particlesPerTick, Location point) {
        double distance = player.getLocation().distance(point);
        double factor = Math.max(1.0, distance / 16.0);
        return Math.max(1, (int) Math.floor(particlesPerTick / factor));
    }
}
