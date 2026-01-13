package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CutscenePath;
import com.extracrates.model.Reward;
import com.extracrates.util.ItemUtil;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class CrateSession {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final Player player;
    private final CrateDefinition crate;
    private final Reward reward;
    private final CutscenePath path;
    private final SessionManager sessionManager;

    private ArmorStand cameraStand;
    private ItemDisplay rewardDisplay;
    private TextDisplay hologram;
    private BukkitRunnable task;

    private GameMode previousGameMode;
    private UUID speedModifierUuid;
    private ItemStack previousHelmet;

    public CrateSession(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            Player player,
            CrateDefinition crate,
            Reward reward,
            CutscenePath path,
            SessionManager sessionManager
    ) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.player = player;
        this.crate = crate;
        this.reward = reward;
        this.path = path;
        this.sessionManager = sessionManager;
    }

    public void start() {
        Location start = crate.getCameraStart() != null ? crate.getCameraStart() : player.getLocation();
        previousGameMode = player.getGameMode();
        spawnCamera(start);
        applySpectatorMode();
        spawnRewardDisplay();
        startCutscene();
    }

    private void spawnCamera(Location start) {
        cameraStand = start.getWorld().spawn(start, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSilent(true);
        });
        hideFromOthers(cameraStand);
    }

    private void applySpectatorMode() {
        FileConfiguration config = configLoader.getMainConfig();
        String uuidText = config.getString("cutscene.speed-modifier-uuid", UUID.randomUUID().toString());
        try {
            speedModifierUuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException ex) {
            speedModifierUuid = UUID.randomUUID();
        }
        double modifierValue = config.getDouble("cutscene.slowdown-modifier", -10.0);
        sessionManager.applySpectator(player, speedModifierUuid, modifierValue);
        player.setSpectatorTarget(cameraStand);

        previousHelmet = player.getInventory().getHelmet();
        ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = pumpkin.getItemMeta();
        String pumpkinModel = config.getString("cutscene.pumpkin-model", "");
        if (meta != null && pumpkinModel != null && !pumpkinModel.isEmpty()) {
            try {
                meta.setCustomModelData(Integer.parseInt(pumpkinModel));
            } catch (NumberFormatException ignored) {
            }
            pumpkin.setItemMeta(meta);
        }
        player.sendEquipmentChange(player, EquipmentSlot.HEAD, pumpkin);
    }

    private void spawnRewardDisplay() {
        Location anchor = crate.getRewardAnchor() != null ? crate.getRewardAnchor() : player.getLocation().add(0, 1.5, 0);
        CrateDefinition.RewardFloatSettings floatSettings = crate.getAnimation().getRewardFloatSettings();
        Location displayLocation = anchor.clone().add(0, floatSettings.getHeight(), 0);

        rewardDisplay = anchor.getWorld().spawn(displayLocation, ItemDisplay.class, display -> {
            display.setItemStack(ItemUtil.buildItem(reward));
        });
        hologram = anchor.getWorld().spawn(displayLocation.clone().add(0, 0.4, 0), TextDisplay.class, display -> {
            String format = crate.getAnimation().getHologramFormat();
            String name = format.replace("%reward_name%", reward.getDisplayName());
            display.text(TextUtil.color(name));
            display.setBillboard(Display.Billboard.CENTER);
        });

        hideFromOthers(rewardDisplay);
        hideFromOthers(hologram);
    }

    private void hideFromOthers(Entity entity) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                online.hideEntity(plugin, entity);
            }
        }
    }

    private void startCutscene() {
        if (path == null || path.getPoints().isEmpty()) {
            finish();
            return;
        }
        List<Location> timeline = buildTimeline(cameraStand.getWorld(), path);
        spawnPathPreview(timeline, path);
        task = new BukkitRunnable() {
            int index = 0;
            double rotation = 0;

            @Override
            public void run() {
                if (index >= timeline.size()) {
                    cancel();
                    finish();
                    return;
                }
                Location point = timeline.get(index++);
                cameraStand.teleport(point);
                player.setSpectatorTarget(cameraStand);

                CrateDefinition.RewardFloatSettings floatSettings = crate.getAnimation().getRewardFloatSettings();
                rotation += floatSettings.getSpinSpeed();
                if (rewardDisplay != null) {
                    rewardDisplay.setRotation((float) rotation, 0);
                }
                if (floatSettings.isBobbing() && rewardDisplay != null) {
                    double bob = Math.sin(index / 6.0) * 0.05;
                    Location rewardLocation = rewardDisplay.getLocation();
                    rewardDisplay.teleport(rewardLocation.clone().add(0, bob, 0));
                    if (hologram != null) {
                        hologram.teleport(rewardLocation.clone().add(0, 0.4 + bob, 0));
                    }
                }
            }
        };
        int totalTicks = (int) Math.max(1, path.getDurationSeconds() * 20);
        long period = Math.max(1L, totalTicks / Math.max(1, timeline.size()));
        task.runTaskTimer(plugin, 0L, period);
    }

    private List<Location> buildTimeline(World world, CutscenePath path) {
        List<Location> timeline = new ArrayList<>();
        List<com.extracrates.model.CutscenePoint> points = path.getPoints();
        if (points.size() < 2) {
            return timeline;
        }

        boolean useCatmullRom = "catmull-rom".equalsIgnoreCase(path.getSmoothing());
        List<Double> segmentDistances = new ArrayList<>();
        double totalDistance = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            double distance = estimateSegmentDistance(points, i, useCatmullRom);
            segmentDistances.add(distance);
            totalDistance += distance;
        }

        int totalIntervals = 0;
        if (path.isConstantSpeed() && totalDistance > 0) {
            totalIntervals = Math.max(1, (int) Math.ceil(totalDistance / path.getStepResolution()));
        }

        int remainingIntervals = totalIntervals;
        double remainingDistance = totalDistance;
        for (int i = 0; i < points.size() - 1; i++) {
            int intervals;
            double segmentDistance = segmentDistances.get(i);
            if (path.isConstantSpeed() && totalDistance > 0) {
                int remainingSegments = (points.size() - 1) - i;
                int maxAlloc = remainingIntervals - (remainingSegments - 1);
                double ratio = remainingDistance > 0 ? (segmentDistance / remainingDistance) : 0.0;
                intervals = Math.max(1, (int) Math.round(remainingIntervals * ratio));
                intervals = Math.min(maxAlloc, intervals);
                remainingIntervals -= intervals;
                remainingDistance -= segmentDistance;
            } else {
                intervals = Math.max(1, (int) Math.ceil(segmentDistance / path.getStepResolution()));
            }

            for (int s = 0; s <= intervals; s++) {
                if (i > 0 && s == 0) {
                    continue;
                }
                double t = s / (double) intervals;
                com.extracrates.model.CutscenePoint p1 = points.get(i);
                com.extracrates.model.CutscenePoint p2 = points.get(i + 1);
                if (useCatmullRom) {
                    com.extracrates.model.CutscenePoint p0 = points.get(Math.max(0, i - 1));
                    com.extracrates.model.CutscenePoint p3 = points.get(Math.min(points.size() - 1, i + 2));
                    double x = catmullRom(p0.getX(), p1.getX(), p2.getX(), p3.getX(), t);
                    double y = catmullRom(p0.getY(), p1.getY(), p2.getY(), p3.getY(), t);
                    double z = catmullRom(p0.getZ(), p1.getZ(), p2.getZ(), p3.getZ(), t);
                    float yaw = (float) catmullRom(p0.getYaw(), p1.getYaw(), p2.getYaw(), p3.getYaw(), t);
                    float pitch = (float) catmullRom(p0.getPitch(), p1.getPitch(), p2.getPitch(), p3.getPitch(), t);
                    timeline.add(new Location(world, x, y, z, yaw, pitch));
                } else {
                    double x = lerp(p1.getX(), p2.getX(), t);
                    double y = lerp(p1.getY(), p2.getY(), t);
                    double z = lerp(p1.getZ(), p2.getZ(), t);
                    float yaw = (float) lerp(p1.getYaw(), p2.getYaw(), t);
                    float pitch = (float) lerp(p1.getPitch(), p2.getPitch(), t);
                    timeline.add(new Location(world, x, y, z, yaw, pitch));
                }
            }
        }
        return timeline;
    }

    private double estimateSegmentDistance(List<com.extracrates.model.CutscenePoint> points, int index, boolean useCatmullRom) {
        com.extracrates.model.CutscenePoint p1 = points.get(index);
        com.extracrates.model.CutscenePoint p2 = points.get(index + 1);
        if (!useCatmullRom) {
            double dx = p2.getX() - p1.getX();
            double dy = p2.getY() - p1.getY();
            double dz = p2.getZ() - p1.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        com.extracrates.model.CutscenePoint p0 = points.get(Math.max(0, index - 1));
        com.extracrates.model.CutscenePoint p3 = points.get(Math.min(points.size() - 1, index + 2));
        int samples = 12;
        double distance = 0.0;
        double lastX = p1.getX();
        double lastY = p1.getY();
        double lastZ = p1.getZ();
        for (int s = 1; s <= samples; s++) {
            double t = s / (double) samples;
            double x = catmullRom(p0.getX(), p1.getX(), p2.getX(), p3.getX(), t);
            double y = catmullRom(p0.getY(), p1.getY(), p2.getY(), p3.getY(), t);
            double z = catmullRom(p0.getZ(), p1.getZ(), p2.getZ(), p3.getZ(), t);
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            distance += Math.sqrt(dx * dx + dy * dy + dz * dz);
            lastX = x;
            lastY = y;
            lastZ = z;
        }
        return distance;
    }

    private double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1)
                + (-p0 + p2) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    private void spawnPathPreview(List<Location> timeline, CutscenePath path) {
        if (timeline.isEmpty()) {
            return;
        }
        String preview = path.getParticlePreview();
        if (preview == null || preview.isEmpty()) {
            return;
        }
        try {
            Particle particle = Particle.valueOf(preview.toUpperCase(Locale.ROOT));
            for (Location point : timeline) {
                player.spawnParticle(particle, point, 1, 0.0, 0.0, 0.0, 0.0);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private void finish() {
        executeReward();
        end();
    }

    private void executeReward() {
        player.sendMessage(Component.text("Has recibido: ").append(TextUtil.color(reward.getDisplayName())));
        ItemStack item = ItemUtil.buildItem(reward);
        player.getInventory().addItem(item);

        for (String command : reward.getCommands()) {
            String parsed = command.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
        if (reward.getMessage() != null && (!reward.getMessage().getTitle().isEmpty() || !reward.getMessage().getSubtitle().isEmpty())) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                    TextUtil.color(reward.getMessage().getTitle()),
                    TextUtil.color(reward.getMessage().getSubtitle())
            ));
        }
        if (reward.getEffects() != null) {
            if (!reward.getEffects().getSound().isEmpty()) {
                try {
                    Sound sound = Sound.valueOf(reward.getEffects().getSound().toUpperCase(Locale.ROOT));
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (!reward.getEffects().getParticles().isEmpty()) {
                try {
                    Particle particle = Particle.valueOf(reward.getEffects().getParticles().toUpperCase(Locale.ROOT));
                    player.getWorld().spawnParticle(particle, player.getLocation(), 20, 0.2, 0.2, 0.2, 0.01);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void end() {
        if (task != null) {
            task.cancel();
        }
        if (cameraStand != null && !cameraStand.isDead()) {
            cameraStand.remove();
        }
        if (rewardDisplay != null && !rewardDisplay.isDead()) {
            rewardDisplay.remove();
        }
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
        }
        if (previousGameMode != null) {
            player.setGameMode(previousGameMode);
        }
        player.setSpectatorTarget(null);
        if (speedModifierUuid != null) {
            sessionManager.removeSpectatorModifier(player, speedModifierUuid);
        }
        if (previousHelmet != null) {
            player.sendEquipmentChange(player, EquipmentSlot.HEAD, previousHelmet);
        } else {
            player.sendEquipmentChange(player, EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        }
        sessionManager.removeSession(player.getUniqueId());
    }
}
