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
    private final CutsceneSpinSettings spinSettings;
    private final List<CutscenePoint> points;
    private final java.util.Set<Integer> directPoints;
    private final List<CutsceneSegmentCommand> segmentCommands;
    private volatile List<CutscenePoint> timelineCache;

    public CutscenePath(
            String id,
            double durationSeconds,
            boolean constantSpeed,
            double stepResolution,
            String smoothing,
            String particlePreview,
            CutsceneSpinSettings spinSettings,
            List<CutscenePoint> points,
            java.util.Set<Integer> directPoints,
            List<CutsceneSegmentCommand> segmentCommands
    ) {
        this.id = id;
        this.durationSeconds = durationSeconds;
        this.constantSpeed = constantSpeed;
        this.stepResolution = stepResolution;
        this.smoothing = smoothing;
        this.particlePreview = particlePreview;
        this.spinSettings = spinSettings;
        this.points = points;
        this.directPoints = directPoints;
        this.segmentCommands = segmentCommands;
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

    public CutsceneSpinSettings getSpinSettings() {
        return spinSettings;
    }

    public boolean isDirectPoint(int index) {
        return directPoints.contains(index);
    }

    public List<CutscenePoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public List<CutsceneSegmentCommand> getSegmentCommands() {
        return Collections.unmodifiableList(segmentCommands);
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
        java.util.Set<Integer> directPoints = parseDirectPoints(section.getList("direct-points", List.of()));
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
        CutsceneSpinSettings spinSettings = CutsceneSpinSettings.fromSection(section.getConfigurationSection("spin"), points.size());
        List<CutsceneSegmentCommand> segmentCommands = parseSegmentCommands(section.getList("segment-commands", List.of()), points.size());
        return new CutscenePath(
                id,
                duration,
                constantSpeed,
                stepResolution,
                smoothing,
                particlePreview,
                spinSettings,
                points,
                directPoints,
                segmentCommands
        );
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
            if (isDirectPoint(i + 1)) {
                if (timeline.isEmpty()) {
                    timeline.add(start);
                }
                timeline.add(end);
                continue;
            }
            double distance = Math.sqrt(
                    Math.pow(start.x() - end.x(), 2)
                            + Math.pow(start.y() - end.y(), 2)
                            + Math.pow(start.z() - end.z(), 2)
            );
            int steps = Math.max(2, (int) Math.ceil(distance / stepResolution));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x = lerp(start.x(), end.x(), t);
                double y = lerp(start.y(), end.y(), t);
                double z = lerp(start.z(), end.z(), t);
                float yaw = (float) lerp(start.yaw(), end.yaw(), t);
                float pitch = (float) lerp(start.pitch(), end.pitch(), t);
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

    private static java.util.Set<Integer> parseDirectPoints(List<?> rawPoints) {
        java.util.Set<Integer> indices = new java.util.HashSet<>();
        for (Object raw : rawPoints) {
            if (raw instanceof Number number) {
                indices.add(number.intValue());
            } else if (raw instanceof String text) {
                try {
                    indices.add(Integer.parseInt(text.trim()));
                } catch (NumberFormatException ignored) {
                    // ignore invalid values
                }
            }
        }
        return indices;
    }

    private static List<CutsceneSegmentCommand> parseSegmentCommands(List<?> rawCommands, int pointsCount) {
        if (rawCommands == null || rawCommands.isEmpty()) {
            return List.of();
        }
        int lastIndex = Math.max(0, pointsCount - 1);
        List<CutsceneSegmentCommand> commands = new ArrayList<>();
        for (Object raw : rawCommands) {
            if (!(raw instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            int startPoint = clampIndex(getInt(map, "start-point", 0), lastIndex);
            int endPoint = clampIndex(getInt(map, "end-point", startPoint + 1), lastIndex);
            if (endPoint <= startPoint) {
                continue;
            }
            List<String> entries = readStringList(map.get("commands"));
            if (entries.isEmpty()) {
                continue;
            }
            commands.add(new CutsceneSegmentCommand(startPoint, endPoint, entries));
        }
        return commands;
    }

    private static int clampIndex(int value, int lastIndex) {
        return Math.min(Math.max(value, 0), lastIndex);
    }

    private static int getInt(java.util.Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object entry : list) {
                if (entry == null) {
                    continue;
                }
                String text = entry.toString().trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            return result;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                return List.of(trimmed);
            }
        }
        return List.of();
    }
}
