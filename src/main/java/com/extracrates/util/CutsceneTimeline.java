package com.extracrates.util;

import com.extracrates.model.CutscenePath;
import com.extracrates.model.CutscenePoint;
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
        for (int i = 0; i < points.size() - 1; i++) {
            CutscenePoint start = points.get(i);
            CutscenePoint end = points.get(i + 1);
            Location startLoc = new Location(world, start.getX(), start.getY(), start.getZ(), start.getYaw(), start.getPitch());
            Location endLoc = new Location(world, end.getX(), end.getY(), end.getZ(), end.getYaw(), end.getPitch());
            double distance = startLoc.distance(endLoc);
            int steps = Math.max(2, (int) Math.ceil(distance / path.getStepResolution()));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x = lerp(startLoc.getX(), endLoc.getX(), t);
                double y = lerp(startLoc.getY(), endLoc.getY(), t);
                double z = lerp(startLoc.getZ(), endLoc.getZ(), t);
                float yaw = (float) lerp(startLoc.getYaw(), endLoc.getYaw(), t);
                float pitch = (float) lerp(startLoc.getPitch(), endLoc.getPitch(), t);
                timeline.add(new Location(world, x, y, z, yaw, pitch));
            }
        }
        return timeline;
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }
}
