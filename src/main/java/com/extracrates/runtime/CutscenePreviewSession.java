package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.util.CutsceneTimeline;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class CutscenePreviewSession {
    private final ExtraCratesPlugin plugin;
    private final Player player;
    private final CutscenePath path;
    private final Particle particle;
    private final Runnable onFinish;
    private BukkitRunnable task;
    private BukkitRunnable finishTask;
    private Entity cameraEntity;
    private GameMode originalGameMode;
    private Location originalLocation;
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
        originalGameMode = player.getGameMode();
        originalLocation = player.getLocation();
        player.setGameMode(GameMode.SPECTATOR);
        Location start = timeline.getFirst();
        boolean armorStandInvisible = plugin.getConfig().getBoolean("cutscene.armorstand-invisible", true);
        String cameraEntityType = plugin.getConfig().getString("cutscene.camera-entity", "armor_stand");
        cameraEntity = CameraEntityFactory.spawn(start, cameraEntityType, armorStandInvisible);
        task = new BukkitRunnable() {
            int index = 0;
            int elapsedTicks = 0;
            final int lastIndex = timeline.size() - 1;

            @Override
            public void run() {
                if (elapsedTicks >= totalTicks) {
                    cancel();
                    scheduleFinishDelay();
                    return;
                }
                double progress = totalTicks <= 1 ? 1.0 : elapsedTicks / (double) (totalTicks - 1);
                int targetIndex = lastIndex <= 0 ? 0 : (int) Math.round(progress * lastIndex);
                Location point = timeline.get(Math.min(lastIndex, Math.max(0, targetIndex)));
                cameraEntity.teleport(point);
                player.setSpectatorTarget(cameraEntity);
                if (particle != null) {
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
        if (finishTask != null) {
            finishTask.cancel();
        }
        finish();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (cameraEntity != null && cameraEntity.isValid()) {
            cameraEntity.remove();
        }
        player.setSpectatorTarget(null);
        if (originalGameMode != null) {
            player.setGameMode(originalGameMode);
        }
        if (originalLocation != null) {
            player.teleport(originalLocation);
        }
        if (onFinish != null) {
            onFinish.run();
        }
    }

    private void scheduleFinishDelay() {
        finishTask = new BukkitRunnable() {
            @Override
            public void run() {
                finish();
            }
        };
        finishTask.runTaskLater(plugin, 40L);
    }
}
