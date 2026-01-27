package com.extracrates.util;

import com.extracrates.cutscene.CutscenePath;
import com.extracrates.cutscene.CutscenePoint;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public final class CutsceneTimeline {
    private CutsceneTimeline() {
    }

    public static List<Location> build(World world, CutscenePath path) {
        List<Location> timeline = new ArrayList<>();
        List<CutscenePoint> points = path.getPoints();
        com.extracrates.cutscene.CutsceneSpinSettings spinSettings = path.getSpinSettings();
        double spinOffset = 0.0;
        double spinStep = spinSettings != null ? spinSettings.stepDelta() : 0.0;
        boolean spinStarted = false;
        for (int i = 0; i < points.size() - 1; i++) {
            CutscenePoint start = points.get(i);
            CutscenePoint end = points.get(i + 1);
            if (path.isDirectPoint(i + 1)) {
                if (timeline.isEmpty()) {
                    timeline.add(new Location(world, start.x(), start.y(), start.z(), start.yaw(), start.pitch()));
                }
                timeline.add(new Location(world, end.x(), end.y(), end.z(), end.yaw(), end.pitch()));
                continue;
            }
            Location startLoc = new Location(world, start.x(), start.y(), start.z(), start.yaw(), start.pitch());
            Location endLoc = new Location(world, end.x(), end.y(), end.z(), end.yaw(), end.pitch());
            double distance = startLoc.distance(endLoc);
            int steps = Math.max(2, (int) Math.ceil(distance / path.getStepResolution()));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x = lerp(startLoc.getX(), endLoc.getX(), t);
                double y = lerp(startLoc.getY(), endLoc.getY(), t);
                double z = lerp(startLoc.getZ(), endLoc.getZ(), t);
                float yaw = (float) lerp(startLoc.getYaw(), endLoc.getYaw(), t);
                float pitch = (float) lerp(startLoc.getPitch(), endLoc.getPitch(), t);
                if (spinSettings != null && spinSettings.isActiveForSegment(i)) {
                    yaw = wrapDegrees(yaw + (float) spinOffset);
                    spinOffset += spinStep;
                    spinStarted = true;
                } else if (spinStarted) {
                    yaw = wrapDegrees(yaw + (float) spinOffset);
                }
                timeline.add(new Location(world, x, y, z, yaw, pitch));
            }
        }
        return timeline;
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }
}
