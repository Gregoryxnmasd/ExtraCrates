package com.extracrates.cutscene;

import org.bukkit.configuration.ConfigurationSection;

public record CutsceneSpinSettings(
        boolean enabled,
        int startPoint,
        int endPoint,
        SpinDirection direction,
        double speed
) {
    public static CutsceneSpinSettings disabled() {
        return new CutsceneSpinSettings(false, 0, 0, SpinDirection.RIGHT, 0.0);
    }

    public boolean isActiveForSegment(int segmentIndex) {
        return enabled && speed > 0.0 && endPoint > startPoint && segmentIndex >= startPoint && segmentIndex < endPoint;
    }

    public double stepDelta() {
        return speed * direction.multiplier();
    }

    public static CutsceneSpinSettings fromSection(ConfigurationSection section, int pointsCount) {
        if (section == null) {
            return disabled();
        }
        boolean enabled = section.getBoolean("enabled", false);
        int lastIndex = Math.max(0, pointsCount - 1);
        int startPoint = Math.max(0, section.getInt("start-point", 0));
        int endPoint = section.getInt("end-point", lastIndex);
        startPoint = Math.min(startPoint, lastIndex);
        endPoint = Math.min(Math.max(endPoint, startPoint), lastIndex);
        SpinDirection direction = SpinDirection.fromString(section.getString("direction", "right"));
        double speed = Math.max(0.0, section.getDouble("speed", 0.0));
        return new CutsceneSpinSettings(enabled, startPoint, endPoint, direction, speed);
    }
}
