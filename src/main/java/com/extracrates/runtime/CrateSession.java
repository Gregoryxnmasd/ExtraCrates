package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.hologram.HologramInstance;
import com.extracrates.hologram.HologramSettings;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CutscenePath;
import com.extracrates.model.Reward;
import com.extracrates.util.CutsceneTimeline;
import com.extracrates.util.ItemUtil;
import com.extracrates.util.TextUtil;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class CrateSession {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final Player player;
    private final CrateDefinition crate;
    private final List<Reward> rewards;
    private final CutscenePath path;
    private final boolean grantReward;
    private final SessionManager sessionManager;
    private final boolean preview;

    private Entity cameraEntity;
    private ItemDisplay rewardDisplay;
    private TextDisplay hologram;
    private BukkitRunnable task;
    private Location rewardBaseLocation;
    private Location hologramBaseLocation;
    private org.bukkit.util.Transformation rewardBaseTransform;

    private GameMode previousGameMode;
    private UUID speedModifierUuid;
    private ItemStack previousHelmet;
    private boolean spectatorApplied;

    public CrateSession(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            LanguageManager languageManager,
            Player player,
            CrateDefinition crate,
            List<Reward> rewards,
            CutscenePath path,
            SessionManager sessionManager,
            boolean preview
    ) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.languageManager = languageManager;
        this.player = player;
        this.crate = crate;
        this.rewards = rewards;
        this.path = path;
        this.grantReward = grantReward;
        this.sessionManager = sessionManager;
        this.preview = preview;
    }

    public void start() {
        if (path == null) {
            player.sendMessage(Component.text("No se encontró la ruta de la cutscene."));
            finish();
            return;
        }
        Location start = crate.getCameraStart() != null ? crate.getCameraStart() : player.getLocation();
        spawnCamera(start);
        applySpectatorMode();
        spawnRewardDisplay();
        scheduleTimeout();
        startCutscene();
    }

    private void spawnCamera(Location start) {
        CameraEntityFactory factory = new CameraEntityFactory(configLoader);
        cameraEntity = factory.spawn(start);
        hideFromOthers(cameraEntity);
    }

    private void applySpectatorMode() {
        FileConfiguration config = configLoader.getMainConfig();
        String uuidText = config.getString("cutscene.speed-modifier-uuid", UUID.randomUUID().toString());
        try {
            speedModifierUuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException ex) {
            speedModifierUuid = UUID.randomUUID();
        }
        previousGameMode = player.getGameMode();
        double modifierValue = config.getDouble("cutscene.slowdown-modifier", -10.0);
        sessionManager.applySpectator(player, speedModifierUuid, modifierValue);
        player.setSpectatorTarget(cameraEntity);

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
        if (config.getBoolean("cutscene.fake-equip", true)) {
            player.sendEquipmentChange(player, EquipmentSlot.HEAD, pumpkin);
        }
    }

    private void spawnRewardDisplay() {
        if (rewards.isEmpty()) {
            return;
        }
        Location anchor = crate.getRewardAnchor() != null ? crate.getRewardAnchor() : player.getLocation().add(0, 1.5, 0);
        CrateDefinition.RewardFloatSettings floatSettings = crate.getAnimation().getRewardFloatSettings();
        Location displayLocation = anchor.clone().add(0, floatSettings.getHeight(), 0);
        Reward reward = getCurrentReward();
        if (reward == null) {
            return;
        }

        rewardDisplay = anchor.getWorld().spawn(displayLocation, ItemDisplay.class, display -> {
            ItemStack displayItem = ItemUtil.buildItem(reward);
            String rewardModel = crate.getAnimation().getRewardModel();
            if (rewardModel != null && !rewardModel.isEmpty()) {
                // Animation reward-model takes priority over reward custom-model for display only.
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    try {
                        meta.setCustomModelData(Integer.parseInt(rewardModel));
                    } catch (NumberFormatException ignored) {
                    }
                    displayItem.setItemMeta(meta);
                }
            }
            display.setItemStack(displayItem);
        });
        hologram = anchor.getWorld().spawn(displayLocation.clone().add(0, 0.4, 0), TextDisplay.class, display -> {
            String format = reward.getHologram();
            if (format == null || format.isEmpty()) {
                format = crate.getAnimation().getHologramFormat();
            }
            if (format == null || format.isEmpty()) {
                format = "%reward_name%";
            }
            String name = format.replace("%reward_name%", reward.getDisplayName());
            display.text(configLoader.getSettings().applyHologramFont(TextUtil.color(name)));
            display.setBillboard(Display.Billboard.CENTER);
        });

        hideFromOthers(rewardDisplay);
        hideFromOthers(hologram);

        rewardBaseLocation = rewardDisplay.getLocation().clone();
        hologramBaseLocation = hologram.getLocation().clone();
        rewardBaseTransform = rewardDisplay.getTransformation();
    }

    private void hideFromOthers(Entity entity) {
        ProtocolEntityHider protocolEntityHider = plugin.getProtocolEntityHider();
        if (protocolEntityHider != null) {
            protocolEntityHider.trackEntity(player, entity);
            return;
        }
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
                String animationName = reward.getEffects() != null ? reward.getEffects().getAnimation() : "";
                rewardAnimationService.applyAnimation(
                        animationName,
                        rewardDisplay,
                        hologram,
                        rewardBaseLocation,
                        hologramBaseLocation,
                        rewardBaseTransform,
                        index,
                        floatSettings
                );
            }
        };
        assignTicks(timeline, timelineData.totalDistance(), (int) Math.max(1, path.getDurationSeconds() * 20));
        task.runTaskTimer(plugin, 0L, 1L);
    }

    private TimelineData buildTimeline(World world, CutscenePath path) {
        List<TimelinePoint> timeline = new ArrayList<>();
        List<com.extracrates.model.CutscenePoint> points = path.getPoints();
        String smoothing = path.getSmoothing() == null ? "linear" : path.getSmoothing().trim().toLowerCase(Locale.ROOT);
        boolean useCatmullRom = smoothing.equals("catmull-rom") || smoothing.equals("catmullrom");
        for (int i = 0; i < points.size() - 1; i++) {
            com.extracrates.model.CutscenePoint start = points.get(i);
            com.extracrates.model.CutscenePoint end = points.get(i + 1);
            Location startLoc = new Location(world, start.getX(), start.getY(), start.getZ(), start.getYaw(), start.getPitch());
            Location endLoc = new Location(world, end.getX(), end.getY(), end.getZ(), end.getYaw(), end.getPitch());
            double distance = startLoc.distance(endLoc);
            int steps = Math.max(2, (int) Math.ceil(distance / path.getStepResolution()));
            com.extracrates.model.CutscenePoint prev = i > 0 ? points.get(i - 1) : start;
            com.extracrates.model.CutscenePoint next = (i + 2) < points.size() ? points.get(i + 2) : end;
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x;
                double y;
                double z;
                float yaw;
                float pitch;
                if (useCatmullRom) {
                    x = catmullRom(prev.getX(), start.getX(), end.getX(), next.getX(), t);
                    y = catmullRom(prev.getY(), start.getY(), end.getY(), next.getY(), t);
                    z = catmullRom(prev.getZ(), start.getZ(), end.getZ(), next.getZ(), t);
                    yaw = (float) catmullRom(prev.getYaw(), start.getYaw(), end.getYaw(), next.getYaw(), t);
                    pitch = (float) catmullRom(prev.getPitch(), start.getPitch(), end.getPitch(), next.getPitch(), t);
                } else {
                    x = lerp(startLoc.getX(), endLoc.getX(), t);
                    y = lerp(startLoc.getY(), endLoc.getY(), t);
                    z = lerp(startLoc.getZ(), endLoc.getZ(), t);
                    yaw = (float) lerp(startLoc.getYaw(), endLoc.getYaw(), t);
                    pitch = (float) lerp(startLoc.getPitch(), endLoc.getPitch(), t);
                }
                timeline.add(new Location(world, x, y, z, yaw, pitch));
            }
            distanceSoFar += distance;
        }
        return new TimelineData(timeline, totalDistance);
    }

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1)
                + (-p0 + p2) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    private void finish() {
        if (!preview) {
            executeReward();
        }
        end();
    }

    private void executeReward() {
        for (Reward reward : rewards) {
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
    }

    private Reward getCurrentReward() {
        if (rewards == null || rewards.isEmpty()) {
            return null;
        }
        int clamped = Math.min(rewardIndex, rewards.size() - 1);
        return rewards.get(clamped);
    }

    private void configureRewardSequence(int totalTicks) {
        rewardIndex = 0;
        if (rewards == null || rewards.size() <= 1) {
            rewardSwitchTicks = 0;
            nextRewardSwitchTick = 0;
            return;
        }
        rewardSwitchTicks = Math.max(1L, totalTicks / rewards.size());
        nextRewardSwitchTick = rewardSwitchTicks;
    }

    private void updateRewardSequence(long elapsedTicks) {
        if (rewardSwitchTicks <= 0 || rewards == null) {
            return;
        }
        if (rewardIndex >= rewards.size() - 1) {
            return;
        }
        while (elapsedTicks >= nextRewardSwitchTick && rewardIndex < rewards.size() - 1) {
            rewardIndex++;
            nextRewardSwitchTick += rewardSwitchTicks;
            refreshRewardDisplay();
        }
    }

    private void refreshRewardDisplay() {
        Reward reward = getCurrentReward();
        if (reward == null) {
            return;
        }
        if (rewardDisplay != null) {
            rewardDisplay.setItemStack(ItemUtil.buildItem(reward));
        }
        if (hologram != null) {
            String format = crate.getAnimation().getHologramFormat();
            String name = format.replace("%reward_name%", reward.getDisplayName());
            hologram.text(TextUtil.color(name));
        }
    }

    public void end() {
        if (task != null) {
            task.cancel();
        }
        if (cameraEntity != null && !cameraEntity.isDead()) {
            cameraEntity.remove();
        }
        if (rewardRenderer != null) {
            rewardRenderer.remove();
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

    private Component buildHologramText(HologramSettings settings) {
        String format = reward.getHologram();
        if (format == null || format.isEmpty()) {
            format = crate.getAnimation().getHologramFormat();
        }
        if (format == null || format.isEmpty()) {
            format = settings.getNameFormat();
        }
        String name = format.replace("%reward_name%", reward.getDisplayName());
        return TextUtil.color(name);
    }
}
