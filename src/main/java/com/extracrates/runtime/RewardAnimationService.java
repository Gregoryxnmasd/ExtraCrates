package com.extracrates.runtime;

import com.extracrates.model.CrateDefinition;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.Locale;

public class RewardAnimationService {
    public void applyAnimation(
            String animationName,
            ItemDisplay rewardDisplay,
            TextDisplay hologram,
            Location baseLocation,
            Location baseHologramLocation,
            Transformation baseTransform,
            int tick,
            CrateDefinition.RewardFloatSettings floatSettings
    ) {
        if (rewardDisplay == null || baseLocation == null || baseHologramLocation == null || floatSettings == null) {
            return;
        }
        String normalized = animationName == null ? "" : animationName.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "spin" -> applySpin(rewardDisplay, baseLocation, baseHologramLocation, hologram, tick, floatSettings);
            case "pulse" -> applyPulse(rewardDisplay, baseLocation, baseHologramLocation, hologram, baseTransform, tick);
            case "float" -> applyFloat(rewardDisplay, baseLocation, baseHologramLocation, hologram, tick, floatSettings);
            default -> applyFloat(rewardDisplay, baseLocation, baseHologramLocation, hologram, tick, floatSettings);
        }
    }

    private void applySpin(
            ItemDisplay rewardDisplay,
            Location baseLocation,
            Location baseHologramLocation,
            TextDisplay hologram,
            int tick,
            CrateDefinition.RewardFloatSettings floatSettings
    ) {
        float rotation = (float) (tick * floatSettings.spinSpeed());
        rewardDisplay.setRotation(rotation, 0);
        rewardDisplay.teleport(baseLocation);
        if (hologram != null) {
            hologram.teleport(baseHologramLocation);
        }
    }

    private void applyFloat(
            ItemDisplay rewardDisplay,
            Location baseLocation,
            Location baseHologramLocation,
            TextDisplay hologram,
            int tick,
            CrateDefinition.RewardFloatSettings floatSettings
    ) {
        float rotation = (float) (tick * floatSettings.spinSpeed());
        rewardDisplay.setRotation(rotation, 0);
        double bob = floatSettings.bobbing() ? Math.sin(tick / 6.0) * 0.05 : 0.0;
        rewardDisplay.teleport(baseLocation.clone().add(0, bob, 0));
        if (hologram != null) {
            hologram.teleport(baseHologramLocation.clone().add(0, bob, 0));
        }
    }

    private void applyPulse(
            ItemDisplay rewardDisplay,
            Location baseLocation,
            Location baseHologramLocation,
            TextDisplay hologram,
            Transformation baseTransform,
            int tick
    ) {
        rewardDisplay.teleport(baseLocation);
        if (hologram != null) {
            hologram.teleport(baseHologramLocation);
        }
        if (baseTransform == null) {
            return;
        }
        float scaleFactor = 1.0f + 0.1f * (float) Math.sin(tick / 6.0);
        Vector3f baseScale = new Vector3f(baseTransform.getScale());
        Vector3f scaled = baseScale.mul(scaleFactor);
        rewardDisplay.setTransformation(new Transformation(
                baseTransform.getTranslation(),
                baseTransform.getLeftRotation(),
                scaled,
                baseTransform.getRightRotation()
        ));
    }
}
