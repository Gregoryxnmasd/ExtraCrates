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
        TimelineData timelineData = buildTimeline(cameraStand.getWorld(), path);
        List<TimelinePoint> timeline = timelineData.points();
        if (timeline.isEmpty()) {
            finish();
            return;
        }
        task = new BukkitRunnable() {
            int index = 0;
            double rotation = 0;
            int tick = 0;
            final int totalTicks = (int) Math.max(1, path.getDurationSeconds() * 20);

            @Override
            public void run() {
                if (tick > totalTicks) {
                    cancel();
                    finish();
                    return;
                }
                while (index + 1 < timeline.size() && timeline.get(index + 1).tick() <= tick) {
                    index++;
                }
                Location point = timeline.get(index).location();
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
                tick++;
            }
        };
        assignTicks(timeline, timelineData.totalDistance(), (int) Math.max(1, path.getDurationSeconds() * 20));
        task.runTaskTimer(plugin, 0L, 1L);
    }

    private TimelineData buildTimeline(World world, CutscenePath path) {
        List<TimelinePoint> timeline = new ArrayList<>();
        List<com.extracrates.model.CutscenePoint> points = path.getPoints();
        if (points.size() < 2) {
            return new TimelineData(timeline, 0.0);
        }
        double totalDistance = 0.0;
        List<Double> segmentDistances = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            com.extracrates.model.CutscenePoint start = points.get(i);
            com.extracrates.model.CutscenePoint end = points.get(i + 1);
            Location startLoc = new Location(world, start.getX(), start.getY(), start.getZ());
            Location endLoc = new Location(world, end.getX(), end.getY(), end.getZ());
            double distance = startLoc.distance(endLoc);
            segmentDistances.add(distance);
            totalDistance += distance;
        }
        int segments = segmentDistances.size();
        int uniformSteps = segments > 0
                ? Math.max(2, (int) Math.ceil((totalDistance / Math.max(0.0001, path.getStepResolution())) / segments))
                : 2;
        double distanceSoFar = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            com.extracrates.model.CutscenePoint start = points.get(i);
            com.extracrates.model.CutscenePoint end = points.get(i + 1);
            Location startLoc = new Location(world, start.getX(), start.getY(), start.getZ(), start.getYaw(), start.getPitch());
            Location endLoc = new Location(world, end.getX(), end.getY(), end.getZ(), end.getYaw(), end.getPitch());
            double distance = segmentDistances.get(i);
            int steps = path.isConstantSpeed()
                    ? Math.max(2, (int) Math.ceil(distance / Math.max(0.0001, path.getStepResolution())))
                    : uniformSteps;
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x = lerp(startLoc.getX(), endLoc.getX(), t);
                double y = lerp(startLoc.getY(), endLoc.getY(), t);
                double z = lerp(startLoc.getZ(), endLoc.getZ(), t);
                float yaw = (float) lerp(startLoc.getYaw(), endLoc.getYaw(), t);
                float pitch = (float) lerp(startLoc.getPitch(), endLoc.getPitch(), t);
                timeline.add(new TimelinePoint(new Location(world, x, y, z, yaw, pitch), distanceSoFar + (distance * t), 0));
            }
            distanceSoFar += distance;
        }
        return new TimelineData(timeline, totalDistance);
    }

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private void assignTicks(List<TimelinePoint> timeline, double totalDistance, int totalTicks) {
        if (timeline.isEmpty()) {
            return;
        }
        if (totalDistance <= 0.0) {
            for (int i = 0; i < timeline.size(); i++) {
                TimelinePoint point = timeline.get(i);
                timeline.set(i, new TimelinePoint(point.location(), point.distance(), 0));
            }
            return;
        }
        int lastTick = 0;
        for (int i = 0; i < timeline.size(); i++) {
            TimelinePoint point = timeline.get(i);
            int tick = (int) Math.round((point.distance() / totalDistance) * totalTicks);
            tick = Math.min(totalTicks, Math.max(lastTick, tick));
            timeline.set(i, new TimelinePoint(point.location(), point.distance(), tick));
            lastTick = tick;
        }
    }

    private record TimelinePoint(Location location, double distance, int tick) {
    }

    private record TimelineData(List<TimelinePoint> points, double totalDistance) {
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
