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
    private BukkitRunnable musicTask;

    private GameMode previousGameMode;
    private UUID speedModifierUuid;
    private ItemStack previousHelmet;
    private boolean hudHiddenApplied;
    private float previousWalkSpeed;
    private float previousFlySpeed;

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
        previousGameMode = player.getGameMode();
        previousWalkSpeed = player.getWalkSpeed();
        previousFlySpeed = player.getFlySpeed();
        spawnCamera(start);
        applySpectatorMode();
        spawnRewardDisplay();
        startMusic();
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
        String overlayModel = crate.getCutsceneSettings().getOverlayModel();
        if (overlayModel != null && !overlayModel.isEmpty()) {
            ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
            ItemMeta meta = pumpkin.getItemMeta();
            if (meta != null) {
                Integer modelData = configLoader.resolveModelData(overlayModel);
                if (modelData != null) {
                    meta.setCustomModelData(modelData);
                }
                pumpkin.setItemMeta(meta);
            }
            player.sendEquipmentChange(player, EquipmentSlot.HEAD, pumpkin);
        }

        if (crate.getCutsceneSettings().isHideHud()) {
            hudHiddenApplied = toggleHud(true);
        }
        if (crate.getCutsceneSettings().isLockMovement()) {
            player.setWalkSpeed(0.0f);
            player.setFlySpeed(0.0f);
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
        if (isQaMode()) {
            player.sendMessage(Component.text("Modo QA activo: no se entregan items ni se ejecutan comandos."));
        } else {
            player.sendMessage(Component.text("Has recibido: ").append(TextUtil.color(reward.getDisplayName())));
            ItemStack item = ItemUtil.buildItem(reward);
            player.getInventory().addItem(item);

            for (String command : reward.getCommands()) {
                String parsed = command.replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
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

    private boolean isQaMode() {
        return configLoader.getMainConfig().getBoolean("qa-mode", false);
    }

    public void end() {
        if (task != null) {
            task.cancel();
        }
        if (musicTask != null) {
            musicTask.cancel();
        }
        stopMusic();
        if (cameraStand != null && !cameraStand.isDead()) {
            sessionManager.getDisplayPool().releaseArmorStand(cameraStand);
        }
        if (previousGameMode != null) {
            player.setGameMode(previousGameMode);
        }
        player.setSpectatorTarget(null);
        if (speedModifierUuid != null) {
            sessionManager.removeSpectatorModifier(player, speedModifierUuid);
        }
        if (previousGameMode != null) {
            player.setGameMode(previousGameMode);
        }
        player.setSpectatorTarget(null);
        if (speedModifierUuid != null) {
            sessionManager.removeSpectatorModifier(player, speedModifierUuid);
        }
        if (crate.getCutsceneSettings().isLockMovement()) {
            player.setWalkSpeed(previousWalkSpeed);
            player.setFlySpeed(previousFlySpeed);
        }
        if (hudHiddenApplied) {
            toggleHud(false);
        }
        if (previousHelmet != null) {
            player.sendEquipmentChange(player, EquipmentSlot.HEAD, previousHelmet);
        } else {
            player.sendEquipmentChange(player, EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        }
        sessionManager.removeSession(player.getUniqueId());
    }

    public boolean isMovementLocked() {
        return crate.getCutsceneSettings().isLockMovement();
    }

    private boolean toggleHud(boolean hidden) {
        try {
            String methodName = hidden ? "hideHud" : "showHud";
            java.lang.reflect.Method method = player.getClass().getMethod(methodName);
            method.invoke(player);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void startMusic() {
        CrateDefinition.MusicSettings music = crate.getCutsceneSettings().getMusicSettings();
        if (music == null || music.getSound().isEmpty()) {
            return;
        }
        SoundCategory category = parseCategory(music.getCategory());
        if (music.getFadeInTicks() <= 0) {
            player.playSound(player.getLocation(), music.getSound(), category, music.getVolume(), music.getPitch());
            return;
        }
        scheduleMusicFade(music, category, true);
    }

    private void stopMusic() {
        CrateDefinition.MusicSettings music = crate.getCutsceneSettings().getMusicSettings();
        if (music == null || music.getSound().isEmpty()) {
            return;
        }
        SoundCategory category = parseCategory(music.getCategory());
        if (music.getFadeOutTicks() <= 0) {
            player.stopSound(music.getSound(), category);
            return;
        }
        scheduleMusicFade(music, category, false);
    }

    private void scheduleMusicFade(CrateDefinition.MusicSettings music, SoundCategory category, boolean fadeIn) {
        if (musicTask != null) {
            musicTask.cancel();
        }
        int totalTicks = Math.max(1, fadeIn ? music.getFadeInTicks() : music.getFadeOutTicks());
        int steps = Math.max(1, totalTicks / 4);
        float startVolume = fadeIn ? 0.0f : music.getVolume();
        float endVolume = fadeIn ? music.getVolume() : 0.0f;
        float step = (endVolume - startVolume) / steps;
        musicTask = new BukkitRunnable() {
            int stepIndex = 0;
            float current = startVolume;

            @Override
            public void run() {
                if (stepIndex > steps) {
                    if (!fadeIn) {
                        player.stopSound(music.getSound(), category);
                    }
                    cancel();
                    return;
                }
                current = Math.max(0.0f, Math.min(music.getVolume(), current));
                if (current <= 0.0f && !fadeIn) {
                    player.stopSound(music.getSound(), category);
                } else {
                    player.stopSound(music.getSound(), category);
                    float playVolume = Math.max(0.01f, current);
                    player.playSound(player.getLocation(), music.getSound(), category, playVolume, music.getPitch());
                }
                current += step;
                stepIndex++;
            }
        };
        musicTask.runTaskTimer(plugin, 0L, 4L);
    }

    private SoundCategory parseCategory(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) {
            return SoundCategory.MUSIC;
        }
        try {
            return SoundCategory.valueOf(categoryName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SoundCategory.MUSIC;
        }
    }
}
