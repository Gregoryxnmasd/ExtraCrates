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
    private final boolean grantReward;
    private final SessionManager sessionManager;
    private final String openMode;

    private ArmorStand cameraStand;
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
            Player player,
            CrateDefinition crate,
            Reward reward,
            CutscenePath path,
            boolean grantReward,
            SessionManager sessionManager
    ) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.player = player;
        this.crate = crate;
        this.reward = reward;
        this.path = path;
        this.grantReward = grantReward;
        this.sessionManager = sessionManager;
        this.openMode = normalizeOpenMode(crate.getOpenMode());
    }

    public void start() {
        if (isRewardOnly()) {
            executeReward();
            sessionManager.removeSession(player.getUniqueId());
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
        cameraStand = sessionManager.getDisplayPool().acquireArmorStand(start);
        if (cameraStand == null) {
            return;
        }
        cameraStand.setInvisible(true);
        cameraStand.setGravity(false);
        cameraStand.setMarker(true);
        cameraStand.setSilent(true);
        resetVisibility(cameraStand);
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
        player.setSpectatorTarget(cameraStand);

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
        Location anchor = crate.getRewardAnchor() != null ? crate.getRewardAnchor() : player.getLocation().add(0, 1.5, 0);
        CrateDefinition.RewardFloatSettings floatSettings = crate.getAnimation().getRewardFloatSettings();
        Location displayLocation = anchor.clone().add(0, floatSettings.getHeight(), 0);

        rewardDisplay = sessionManager.getDisplayPool().acquireItemDisplay(displayLocation);
        if (rewardDisplay != null) {
            rewardDisplay.setItemStack(ItemUtil.buildItem(reward));
        }
        hologram = sessionManager.getDisplayPool().acquireTextDisplay(displayLocation.clone().add(0, 0.4, 0));
        if (hologram != null) {
            String format = crate.getAnimation().getHologramFormat();
            String name = format.replace("%reward_name%", reward.getDisplayName());
            hologram.text(TextUtil.color(name));
            hologram.setBillboard(Display.Billboard.CENTER);
        }

        resetVisibility(rewardDisplay);
        resetVisibility(hologram);
    }

    private void resetVisibility(Entity entity) {
        if (entity == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showEntity(plugin, entity);
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
        List<Location> timeline = buildTimeline(cameraStand.getWorld(), path);
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

    private void scheduleTimeout() {
        long maxDurationTicks = Math.max(0L, configLoader.getMainConfig().getLong("sessions.max-duration-ticks", 600L));
        if (maxDurationTicks == 0L) {
            return;
        }
        timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                end();
            }
        };
        timeoutTask.runTaskLater(plugin, maxDurationTicks);
    }

    private List<Location> buildTimeline(World world, CutscenePath path) {
        List<Location> timeline = new ArrayList<>();
        for (com.extracrates.model.CutscenePoint point : path.getTimelinePoints()) {
            timeline.add(new Location(world, point.getX(), point.getY(), point.getZ(), point.getYaw(), point.getPitch()));
        }
        return timeline;
    }

    private void finish() {
        if (!isPreviewOnly()) {
            executeReward();
        }
        end();
    }

    private void executeReward() {
        if (!grantReward) {
            player.sendMessage(Component.text("Vista previa completada. No se entregó recompensa."));
            return;
        }
        player.sendMessage(Component.text("Has recibido: ").append(TextUtil.color(reward.getDisplayName())));
        ItemStack item = ItemUtil.buildItem(reward);
        player.getInventory().addItem(item);
        sessionManager.recordRewardGranted(player, crate, reward);

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
        if (musicTask != null) {
            musicTask.cancel();
        }
        stopMusic();
        if (cameraStand != null && !cameraStand.isDead()) {
            sessionManager.getDisplayPool().releaseArmorStand(cameraStand);
        }
        if (rewardDisplay != null && !rewardDisplay.isDead()) {
            sessionManager.getDisplayPool().releaseItemDisplay(rewardDisplay);
        }
        if (hologram != null && !hologram.isDead()) {
            sessionManager.getDisplayPool().releaseTextDisplay(hologram);
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
