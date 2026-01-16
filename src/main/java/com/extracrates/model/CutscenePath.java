package com.extracrates.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CutscenePath {
    private final String id;
    private final double durationSeconds;
    private final boolean constantSpeed;
    private final double stepResolution;
    private final String smoothing;
    private final String particlePreview;
    private final List<CutscenePoint> points;
    private volatile List<CutscenePoint> timelineCache;

    public CutscenePath(
            String id,
            double durationSeconds,
            boolean constantSpeed,
            double stepResolution,
            String smoothing,
            String particlePreview,
            List<CutscenePoint> points
    ) {
        this.id = id;
        this.durationSeconds = durationSeconds;
        this.constantSpeed = constantSpeed;
        this.stepResolution = stepResolution;
        this.smoothing = smoothing;
        this.particlePreview = particlePreview;
        this.points = points;
    }

    public String getId() {
        return id;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isConstantSpeed() {
        return constantSpeed;
    }

    public double getStepResolution() {
        return stepResolution;
    }

    public String getSmoothing() {
        return smoothing;
    }

    public String getParticlePreview() {
        return particlePreview;
    }

    public List<CutscenePoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public List<CutscenePoint> getTimelinePoints() {
        List<CutscenePoint> cached = timelineCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (timelineCache == null) {
                timelineCache = Collections.unmodifiableList(buildTimelinePoints());
            }
            return timelineCache;
        }
    }

    public static CutscenePath fromSection(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        double duration = section.getDouble("duration-seconds", 4.0);
        boolean constantSpeed = section.getBoolean("constant-speed", true);
        double stepResolution = section.getDouble("step-resolution", 0.15);
        String smoothing = section.getString("smoothing", "linear");
        String particlePreview = section.getString("particle-preview", "");
        List<CutscenePoint> points = new ArrayList<>();
        for (Object raw : section.getList("points", new ArrayList<>())) {
            if (raw instanceof java.util.Map<?, ?> map) {
                double x = getNumber(map, "x");
                double y = getNumber(map, "y");
                double z = getNumber(map, "z");
                double yaw = getNumber(map, "yaw");
                double pitch = getNumber(map, "pitch");
                points.add(new CutscenePoint(x, y, z, (float) yaw, (float) pitch));
            }
        }
        return new CutscenePath(id, duration, constantSpeed, stepResolution, smoothing, particlePreview, points);
    }

    private List<CutscenePoint> buildTimelinePoints() {
        if (points.isEmpty()) {
            return List.of();
        }
        if (points.size() == 1) {
            return new ArrayList<>(points);
        }
        List<CutscenePoint> timeline = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            CutscenePoint start = points.get(i);
            CutscenePoint end = points.get(i + 1);
            double distance = Math.sqrt(
                    Math.pow(start.getX() - end.getX(), 2)
                            + Math.pow(start.getY() - end.getY(), 2)
                            + Math.pow(start.getZ() - end.getZ(), 2)
            );
            int steps = Math.max(2, (int) Math.ceil(distance / stepResolution));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x = lerp(start.getX(), end.getX(), t);
                double y = lerp(start.getY(), end.getY(), t);
                double z = lerp(start.getZ(), end.getZ(), t);
                float yaw = (float) lerp(start.getYaw(), end.getYaw(), t);
                float pitch = (float) lerp(start.getPitch(), end.getPitch(), t);
                timeline.add(new CutscenePoint(x, y, z, yaw, pitch));
            }
        }
        return timeline;
    }

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static double getNumber(java.util.Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
