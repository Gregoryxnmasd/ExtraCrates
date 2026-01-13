package com.extracrates.cutscene;

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

    private static double getNumber(java.util.Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
