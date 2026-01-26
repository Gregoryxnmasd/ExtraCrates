package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.util.ItemUtil;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RewardDisplayRenderer {
    private static final double BASE_TEXT_OFFSET = 0.4;
    private static final double BASE_BOB_AMPLITUDE = 0.05;

    private final ExtraCratesPlugin plugin;
    private final Player player;
    private final CrateDefinition crate;
    private final Reward reward;
    private final CrateDefinition.RewardFloatSettings floatSettings;
    private final CrateDefinition.RewardDisplaySettings displaySettings;
    private final List<ItemDisplay> itemDisplays = new ArrayList<>();
    private final List<TextDisplay> textDisplays = new ArrayList<>();
    private final Deque<Location> trailPoints = new ArrayDeque<>();
    private final Set<String> animations;

    private Location baseLocation;
    private Particle particleType;
    private Particle trailParticleType;
    private int tick;
    private double rotation;

    public RewardDisplayRenderer(
            ExtraCratesPlugin plugin,
            Player player,
            CrateDefinition crate,
            Reward reward
    ) {
        this.plugin = plugin;
        this.player = player;
        this.crate = crate;
        this.reward = reward;
        this.floatSettings = crate.animation().rewardFloatSettings();
        this.displaySettings = resolveDisplaySettings(crate, reward);
        this.animations = parseAnimations(reward.effects() != null ? reward.effects().animation() : "");
    }

    public void spawn(Location anchor) {
        baseLocation = anchor.clone().add(0, floatSettings.height(), 0);
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        particleType = parseParticle(displaySettings.getParticles().getType());
        trailParticleType = parseParticle(displaySettings.getTrail().getType());

        int itemCount = displaySettings.getItemCount();
        for (int i = 0; i < itemCount; i++) {
            ItemDisplay display = world.spawn(baseLocation, ItemDisplay.class, entity -> {
                ItemStack item = ItemUtil.buildItem(
                        reward,
                        world,
                        plugin.getConfigLoader(),
                        plugin.getMapImageCache()
                );
                entity.setItemStack(applyRewardModel(item));
                if (animations.contains("glow")) {
                    entity.setGlowing(true);
                }
            });
            itemDisplays.add(display);
            hideFromOthers(display);
        }

        List<String> lines = new ArrayList<>();
        String format = resolveHologramFormat(crate.animation().hologramFormat(), reward);
        lines.add(format);
        if (reward.hologram() != null && !reward.hologram().isEmpty()) {
            lines.add(applyRewardName(reward.hologram(), reward));
        }

        for (int i = 0; i < lines.size(); i++) {
            double offset = BASE_TEXT_OFFSET + (displaySettings.getTextLineSpacing() * i);
            Location textLocation = baseLocation.clone().add(0, offset, 0);
            String line = lines.get(i);
            TextDisplay display = world.spawn(textLocation, TextDisplay.class, entity -> {
                entity.text(plugin.getConfigLoader().getSettings().applyHologramFont(TextUtil.color(line)));
                entity.setBillboard(Display.Billboard.CENTER);
                if (animations.contains("glow")) {
                    entity.setGlowing(true);
                }
            });
            textDisplays.add(display);
            hideFromOthers(display);
        }
    }

    public void tick() {
        if (baseLocation == null || itemDisplays.isEmpty()) {
            return;
        }
        tick++;
        double bob = floatSettings.bobbing() ? Math.sin(tick / 6.0) * BASE_BOB_AMPLITUDE : 0;
        double wobbleX = animations.contains("wobble")
                ? Math.sin(tick * displaySettings.getWobbleSpeed()) * displaySettings.getWobbleAmplitude()
                : 0;
        double wobbleZ = animations.contains("wobble")
                ? Math.cos(tick * displaySettings.getWobbleSpeed()) * displaySettings.getWobbleAmplitude()
                : 0;
        double pulse = animations.contains("pulse")
                ? Math.sin(tick * displaySettings.getPulseSpeed()) * displaySettings.getPulseScale()
                : 0;
        double glow = animations.contains("glow")
                ? (0.5 + 0.5 * Math.sin(tick * displaySettings.getPulseSpeed() * 0.8)) * displaySettings.getGlowScale()
                : 0;
        double scale = Math.max(0.1, 1.0 + pulse + glow);

        Location center = baseLocation.clone().add(wobbleX, bob, wobbleZ);
        int itemCount = itemDisplays.size();
        double orbitRadius = displaySettings.getOrbitRadius();
        double orbitStep = (Math.PI * 2.0) / Math.max(1, itemCount);
        double orbitOffset = animations.contains("orbit") ? tick * displaySettings.getOrbitSpeed() : 0;

        if (animations.contains("spin")) {
            rotation += floatSettings.spinSpeed();
        }

        for (int i = 0; i < itemCount; i++) {
            ItemDisplay display = itemDisplays.get(i);
            double x = Math.cos((orbitStep * i) + orbitOffset) * orbitRadius;
            double z = Math.sin((orbitStep * i) + orbitOffset) * orbitRadius;
            Location location = center.clone().add(x, 0, z);
            display.teleport(location);
            if (animations.contains("spin")) {
                display.setRotation((float) rotation, 0);
            }
            applyScale(display, scale);
        }

        for (int i = 0; i < textDisplays.size(); i++) {
            TextDisplay display = textDisplays.get(i);
            double offset = BASE_TEXT_OFFSET + (displaySettings.getTextLineSpacing() * i);
            display.teleport(center.clone().add(0, offset, 0));
            applyScale(display, scale);
        }

        spawnParticles(center, orbitOffset);
        spawnTrail(center);
    }

    public void remove() {
        for (ItemDisplay display : itemDisplays) {
            if (!display.isDead()) {
                display.remove();
            }
        }
        for (TextDisplay display : textDisplays) {
            if (!display.isDead()) {
                display.remove();
            }
        }
        itemDisplays.clear();
        textDisplays.clear();
        trailPoints.clear();
    }

    private ItemStack applyRewardModel(ItemStack item) {
        String rewardModel = crate.animation().rewardModel();
        if (rewardModel == null || rewardModel.isEmpty()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        int modelData = ResourcepackModelResolver.resolveCustomModelData(plugin.getConfigLoader(), rewardModel);
        if (modelData >= 0) {
            meta.setCustomModelData(modelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void spawnParticles(Location center, double orbitOffset) {
        CrateDefinition.ParticleSettings particles = displaySettings.getParticles();
        if (particleType == null || !particles.isEnabled()) {
            return;
        }
        if (tick % Math.max(1, particles.getInterval()) != 0) {
            return;
        }
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double angle = orbitOffset;
        double radius = particles.getRadius();
        Location location = center.clone().add(
                Math.cos(angle) * radius,
                particles.getYOffset(),
                Math.sin(angle) * radius
        );
        world.spawnParticle(
                particleType,
                location,
                particles.getCount(),
                particles.getSpread(),
                particles.getSpread(),
                particles.getSpread(),
                particles.getSpeed()
        );
    }

    private String resolveHologramFormat(String format, Reward reward) {
        String resolved = format;
        if (resolved == null || resolved.isBlank() || resolved.equalsIgnoreCase("none")) {
            resolved = "%reward_name%";
        }
        if (!resolved.contains("%reward_name%")) {
            resolved = resolved + " %reward_name%";
        }
        return applyRewardName(resolved, reward);
    }

    private String applyRewardName(String input, Reward reward) {
        if (input == null || reward == null) {
            return input;
        }
        return input.replace("%reward_name%", reward.displayName());
    }

    private void spawnTrail(Location center) {
        CrateDefinition.TrailSettings trail = displaySettings.getTrail();
        if (trailParticleType == null || !trail.isEnabled()) {
            return;
        }
        if (tick % Math.max(1, trail.getInterval()) != 0) {
            return;
        }
        Location target = itemDisplays.isEmpty() ? center : itemDisplays.getFirst().getLocation();
        if (trailPoints.isEmpty() || trailPoints.getLast().distanceSquared(target) >= trail.getSpacing() * trail.getSpacing()) {
            trailPoints.addLast(target.clone());
        }
        while (trailPoints.size() > trail.getLength()) {
            trailPoints.removeFirst();
        }
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        for (Location point : trailPoints) {
            world.spawnParticle(
                    trailParticleType,
                    point,
                    trail.getCount(),
                    trail.getSpread(),
                    trail.getSpread(),
                    trail.getSpread(),
                    trail.getSpeed()
            );
        }
    }

    private void applyScale(Display display, double scale) {
        Transformation transformation = new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f((float) scale, (float) scale, (float) scale),
                new Quaternionf()
        );
        display.setTransformation(transformation);
    }

    private void hideFromOthers(Entity entity) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                online.hideEntity(plugin, entity);
            }
        }
    }

    private static CrateDefinition.RewardFloatSettings resolveFloatSettings(CrateDefinition crate, Reward reward) {
        return crate.animation().rewardFloatSettings();
    }

    private Set<String> parseAnimations(String animationString) {
        Set<String> result = new HashSet<>();
        if (animationString == null || animationString.isEmpty()) {
            result.add("spin");
            return result;
        }
        String normalized = animationString.toLowerCase(Locale.ROOT).replace('_', '-');
        String[] parts = normalized.split("[,\\s]+");
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (part.equals("reward-spin")) {
                result.add("spin");
            } else {
                result.add(part);
            }
        }
        if (result.isEmpty()) {
            result.add("spin");
        }
        return result;
    }

    private Particle parseParticle(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private CrateDefinition.RewardDisplaySettings resolveDisplaySettings(CrateDefinition crate, Reward reward) {
        CrateDefinition.RewardDisplaySettings base = crate.animation().rewardDisplaySettings();
        if (reward == null || reward.rewardDisplayOverrides() == null) {
            return base;
        }
        CrateDefinition.RewardDisplaySettings merged = reward.rewardDisplayOverrides().applyTo(base);
        return merged != null ? merged : base;
    }
}
