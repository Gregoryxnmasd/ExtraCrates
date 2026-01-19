package com.extracrates.route;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.LanguageManager;
import com.extracrates.cutscene.CutscenePoint;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RouteEditorSession {
    private final ExtraCratesPlugin plugin;
    private final LanguageManager languageManager;
    private final Player player;
    private final String pathId;
    private final Particle previewParticle;
    private final String particleName;
    private RouteCaptureMode captureMode;
    private RouteCaptureSource captureSource;
    private final List<CutscenePoint> points = new ArrayList<>();
    private BukkitRunnable previewTask;
    private ArmorStand marker;

    public RouteEditorSession(
            ExtraCratesPlugin plugin,
            Player player,
            String pathId,
            Particle previewParticle,
            String particleName,
            RouteCaptureMode captureMode,
            RouteCaptureSource captureSource
    ) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.player = player;
        this.pathId = pathId;
        this.previewParticle = previewParticle;
        this.particleName = particleName;
        this.captureMode = captureMode;
        this.captureSource = captureSource;
    }

    public String getPathId() {
        return pathId;
    }

    public String getParticleName() {
        return particleName;
    }

    public RouteCaptureMode getCaptureMode() {
        return captureMode;
    }

    public RouteCaptureSource getCaptureSource() {
        return captureSource;
    }

    public void setCaptureMode(RouteCaptureMode captureMode) {
        this.captureMode = captureMode;
    }

    public void setCaptureSource(RouteCaptureSource captureSource) {
        this.captureSource = captureSource;
    }

    public void ensureMarker(Location location) {
        if (marker != null && marker.isValid()) {
            marker.teleport(location);
            return;
        }
        marker = location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCustomName("Route Marker");
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
            stand.setGravity(false);
        });
    }

    public void removeMarker() {
        if (marker != null) {
            marker.remove();
            marker = null;
        }
    }

    public List<CutscenePoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public void startPreview() {
        previewTask = new BukkitRunnable() {
            @Override
            public void run() {
                renderPreview();
            }
        };
        previewTask.runTaskTimer(plugin, 0L, 10L);
    }

    public void stopPreview() {
        if (previewTask != null) {
            previewTask.cancel();
            previewTask = null;
        }
    }

    public void cleanup() {
        stopPreview();
        removeMarker();
    }

    public Location getCaptureLocation() {
        if (captureSource == RouteCaptureSource.MARKER && marker != null && marker.isValid()) {
            return marker.getLocation();
        }
        return player.getLocation();
    }

    public void addPoint(Location location, float yaw, float pitch) {
        points.add(new CutscenePoint(location.getX(), location.getY(), location.getZ(), yaw, pitch));
        player.sendMessage(languageManager.getMessage(
                "route.editor.point-added",
                java.util.Map.of(
                        "count", String.valueOf(points.size()),
                        "x", formatNumber(location.getX()),
                        "y", formatNumber(location.getY()),
                        "z", formatNumber(location.getZ()),
                        "yaw", formatNumber(yaw),
                        "pitch", formatNumber(pitch)
                )
        ));
        renderPreview();
    }

    private void renderPreview() {
        if (previewParticle == null || points.isEmpty()) {
            return;
        }
        for (CutscenePoint point : points) {
            player.spawnParticle(previewParticle, point.x(), point.y(), point.z(), 1, 0, 0, 0, 0);
        }
        double step = 0.3;
        for (int i = 0; i < points.size() - 1; i++) {
            CutscenePoint start = points.get(i);
            CutscenePoint end = points.get(i + 1);
            Location startLoc = new Location(player.getWorld(), start.x(), start.y(), start.z());
            Location endLoc = new Location(player.getWorld(), end.x(), end.y(), end.z());
            double distance = startLoc.distance(endLoc);
            int steps = Math.max(1, (int) Math.ceil(distance / step));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x = lerp(startLoc.getX(), endLoc.getX(), t);
                double y = lerp(startLoc.getY(), endLoc.getY(), t);
                double z = lerp(startLoc.getZ(), endLoc.getZ(), t);
                player.spawnParticle(previewParticle, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private String formatNumber(double value) {
        return String.format("%.2f", value);
    }
}
