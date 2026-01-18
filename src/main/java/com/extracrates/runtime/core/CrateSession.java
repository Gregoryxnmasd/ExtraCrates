package com.extracrates.runtime.core;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.runtime.CameraEntityFactory;
import com.extracrates.config.LanguageManager;
import com.extracrates.util.ItemUtil;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.*;

public class CrateSession {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final Player player;
    private final CrateDefinition crate;
    private final List<Reward> rewards;
    private final CutscenePath path;
    private final SessionManager sessionManager;
    private final boolean preview;

    private Entity cameraEntity;
    private ItemDisplay rewardDisplay;
    private TextDisplay hologram;
    private BukkitRunnable task;
    private BukkitRunnable musicTask;

    private int rewardIndex;
    private int rewardSwitchTicks;
    private int nextRewardSwitchTick;
    private int elapsedTicks;
    private Location rewardBaseLocation;
    private Location hologramBaseLocation;
    private Transformation rewardBaseTransform;

    private GameMode previousGameMode;
    private NamespacedKey speedModifierKey;
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
        this.sessionManager = sessionManager;
        this.preview = preview;
    }

    public void start() {
        if (path == null) {
            player.sendMessage(Component.text("No se encontró la ruta de la cutscene."));
            finish();
            return;
        }
        rewardIndex = 0;
        elapsedTicks = 0;
        rewardSwitchTicks = Math.max(1, configLoader.getMainConfig().getInt("cutscene.reward-delay-ticks", 20));
        nextRewardSwitchTick = rewardSwitchTicks;
        Location start = crate.cameraStart() != null ? crate.cameraStart() : player.getLocation();
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
        FileConfiguration config = configLoader.getMainConfig();
        String cameraEntityType = config.getString("cutscene.camera-entity", "armorstand");
        boolean armorStandInvisible = config.getBoolean("cutscene.armorstand-invisible", true);
        cameraEntity = CameraEntityFactory.spawn(start, cameraEntityType, armorStandInvisible);
        hideFromOthers(cameraEntity);
    }

    private void applySpectatorMode() {
        FileConfiguration config = configLoader.getMainConfig();
        String keyText = config.getString("cutscene.speed-modifier-key");
        if (keyText == null || keyText.isBlank()) {
            keyText = config.getString("cutscene.speed-modifier-uuid", "crate-cutscene");
        }
        NamespacedKey parsedKey = NamespacedKey.fromString(keyText.toLowerCase(Locale.ROOT), plugin);
        speedModifierKey = parsedKey != null ? parsedKey : new NamespacedKey(plugin, "crate-cutscene");
        previousGameMode = player.getGameMode();
        double modifierValue = config.getDouble("cutscene.slowdown-modifier", -10.0);
        sessionManager.applySpectator(player, speedModifierKey, modifierValue);
        player.setSpectatorTarget(cameraEntity);

        previousHelmet = player.getInventory().getHelmet();
        String overlayModel = crate.cutsceneSettings().overlayModel();
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

        if (crate.cutsceneSettings().hideHud()) {
            hudHiddenApplied = toggleHud(true);
        }
        if (crate.cutsceneSettings().lockMovement()) {
            player.setWalkSpeed(0.0f);
            player.setFlySpeed(0.0f);
        }
    }

    private void spawnRewardDisplay() {
        if (rewards.isEmpty()) {
            return;
        }
        Location anchor = crate.rewardAnchor() != null ? crate.rewardAnchor() : player.getLocation().add(0, 1.5, 0);
        CrateDefinition.RewardFloatSettings floatSettings = crate.animation().rewardFloatSettings();
        Location displayLocation = anchor.clone().add(0, floatSettings.height(), 0);
        Reward reward = getCurrentReward();
        if (reward == null) {
            return;
        }

        rewardDisplay = createRewardDisplay(displayLocation, reward);
        hologram = createHologram(displayLocation.clone().add(0, 0.4, 0), reward);

        hideFromOthers(rewardDisplay);
        hideFromOthers(hologram);

        rewardBaseLocation = rewardDisplay.getLocation().clone();
        hologramBaseLocation = hologram.getLocation().clone();
        rewardBaseTransform = rewardDisplay.getTransformation();
    }

    private void hideFromOthers(Entity entity) {
        if (!configLoader.getMainConfig().getBoolean("cutscene.hide-others", true)) {
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
        List<Location> timeline = buildTimeline(cameraEntity.getWorld(), path);
        if (timeline.isEmpty()) {
            finish();
            return;
        }
        task = new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = Math.max(0, timeline.size() - 1);

            @Override
            public void run() {
                if (tick > totalTicks) {
                    cancel();
                    finish();
                    return;
                }
                Location point = timeline.get(tick++);
                cameraEntity.teleport(point);
                player.setSpectatorTarget(cameraEntity);
                elapsedTicks++;
                if (rewards.size() > 1 && rewardSwitchTicks > 0) {
                    while (elapsedTicks >= nextRewardSwitchTick && rewardIndex < rewards.size() - 1) {
                        rewardIndex++;
                        nextRewardSwitchTick += rewardSwitchTicks;
                        refreshRewardDisplay();
                    }
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
    }

    private List<Location> buildTimeline(World world, CutscenePath path) {
        List<Location> timeline = new ArrayList<>();
        List<com.extracrates.cutscene.CutscenePoint> points = path.getPoints();
        String smoothing = resolveSmoothing(path);
        for (int i = 0; i < points.size() - 1; i++) {
            com.extracrates.cutscene.CutscenePoint start = points.get(i);
            com.extracrates.cutscene.CutscenePoint end = points.get(i + 1);
            Location startLoc = new Location(world, start.x(), start.y(), start.z(), start.yaw(), start.pitch());
            Location endLoc = new Location(world, end.x(), end.y(), end.z(), end.yaw(), end.pitch());
            double distance = startLoc.distance(endLoc);
            int steps = Math.max(2, (int) Math.ceil(distance / path.getStepResolution()));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double eased = applyEasing(t, smoothing);
                double x = lerp(startLoc.getX(), endLoc.getX(), eased);
                double y = lerp(startLoc.getY(), endLoc.getY(), eased);
                double z = lerp(startLoc.getZ(), endLoc.getZ(), eased);
                float yaw = lerpAngle(startLoc.getYaw(), endLoc.getYaw(), eased);
                float pitch = lerpAngle(startLoc.getPitch(), endLoc.getPitch(), eased);
                timeline.add(new Location(world, x, y, z, yaw, pitch));
            }
        }
        return timeline;
    }

    private String resolveSmoothing(CutscenePath path) {
        String smoothing = path.getSmoothing();
        if (smoothing == null || smoothing.isBlank()) {
            smoothing = "linear";
        }
        return smoothing;
    }

    private double applyEasing(double t, String smoothing) {
        String mode = smoothing == null ? "linear" : smoothing.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "ease-in", "ease_in", "in" -> t * t;
            case "ease-out", "ease_out", "out" -> 1 - Math.pow(1 - t, 2);
            case "ease-in-out", "ease_in_out", "in-out", "smoothstep", "catmull-rom" -> t * t * (3 - 2 * t);
            default -> t;
        };
    }

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private float lerpAngle(float start, float end, double t) {
        float delta = wrapDegrees(end - start);
        return start + (float) (delta * t);
    }

    private float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    private void finish() {
        if (!preview) {
            executeReward();
        }
        end();
    }

    private void executeReward() {
        Reward reward = getCurrentReward();
        if (reward == null) {
            return;
        }
        if (isQaMode()) {
            player.sendMessage(Component.text("Modo QA activo: no se entregan items ni se ejecutan comandos."));
        } else {
            player.sendMessage(Component.text("Has recibido: ").append(TextUtil.color(reward.displayName())));
            ItemStack item = ItemUtil.buildItem(reward, player.getWorld(), configLoader, plugin.getMapImageCache());
            player.getInventory().addItem(item);

            for (String command : reward.commands()) {
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

    private Reward getCurrentReward() {
        if (rewards == null || rewards.isEmpty()) {
            return null;
        }
        if (rewardIndex < 0 || rewardIndex >= rewards.size()) {
            return null;
        }
        return rewards.get(rewardIndex);
    }

    private void refreshRewardDisplay() {
        Reward reward = getCurrentReward();
        if (reward == null) {
            return;
        }
        Location displayLocation = resolveRewardDisplayLocation();
        if (rewardDisplay == null || rewardDisplay.isDead()) {
            rewardDisplay = createRewardDisplay(displayLocation, reward);
            if (rewardBaseLocation == null) {
                rewardBaseLocation = rewardDisplay.getLocation().clone();
            }
            if (rewardBaseTransform == null) {
                rewardBaseTransform = rewardDisplay.getTransformation();
            }
            if (rewardBaseTransform != null) {
                rewardDisplay.setTransformation(rewardBaseTransform);
            }
        } else {
            rewardDisplay.setItemStack(buildRewardDisplayItem(reward, rewardDisplay.getWorld()));
        }
        hideFromOthers(rewardDisplay);

        Location hologramLocation = resolveHologramLocation(displayLocation);
        if (hologram == null || hologram.isDead()) {
            hologram = createHologram(hologramLocation, reward);
            if (hologramBaseLocation == null) {
                hologramBaseLocation = hologram.getLocation().clone();
            }
        } else {
            String format = crate.animation().hologramFormat();
            if (format == null || format.isEmpty()) {
                format = "%reward_name%";
            }
            String name = format.replace("%reward_name%", reward.displayName());
            hologram.text(TextUtil.color(name));
        }
        hideFromOthers(hologram);
    }

    private Location resolveRewardDisplayLocation() {
        if (rewardBaseLocation != null) {
            return rewardBaseLocation.clone();
        }
        Location anchor = crate.rewardAnchor() != null ? crate.rewardAnchor() : player.getLocation().add(0, 1.5, 0);
        CrateDefinition.RewardFloatSettings floatSettings = crate.animation().rewardFloatSettings();
        return anchor.clone().add(0, floatSettings.height(), 0);
    }

    private Location resolveHologramLocation(Location displayLocation) {
        if (hologramBaseLocation != null) {
            return hologramBaseLocation.clone();
        }
        return displayLocation.clone().add(0, 0.4, 0);
    }

    private ItemDisplay createRewardDisplay(Location displayLocation, Reward reward) {
        return displayLocation.getWorld().spawn(displayLocation, ItemDisplay.class, display -> {
            display.setItemStack(buildRewardDisplayItem(reward, displayLocation.getWorld()));
        });
    }

    private TextDisplay createHologram(Location hologramLocation, Reward reward) {
        return hologramLocation.getWorld().spawn(hologramLocation, TextDisplay.class, display -> {
            String format = reward.hologram();
            if (format == null || format.isEmpty()) {
                format = crate.animation().hologramFormat();
            }
            if (format == null || format.isEmpty()) {
                format = "%reward_name%";
            }
            String name = format.replace("%reward_name%", reward.displayName());
            display.text(configLoader.getSettings().applyHologramFont(TextUtil.color(name)));
            display.setBillboard(Display.Billboard.CENTER);
        });
    }

    private boolean isQaMode() {
        return configLoader.getMainConfig().getBoolean("qa-mode", false);
    }

    private ItemStack buildRewardDisplayItem(Reward reward, World world) {
        ItemStack item = ItemUtil.buildItem(reward, world, configLoader, plugin.getMapImageCache());
        String rewardModel = crate.animation().rewardModel();
        if (rewardModel == null || rewardModel.isEmpty()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        int modelData = ResourcepackModelResolver.resolveCustomModelData(configLoader, rewardModel);
        if (modelData >= 0) {
            meta.setCustomModelData(modelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void end() {
        if (task != null) {
            task.cancel();
        }
        if (musicTask != null) {
            musicTask.cancel();
        }
        stopMusic();
        if (cameraEntity != null && !cameraEntity.isDead()) {
            if (cameraEntity instanceof ArmorStand armorStand) {
                armorStand.remove();
            } else {
                cameraEntity.remove();
            }
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
        if (speedModifierKey != null) {
            sessionManager.removeSpectatorModifier(player, speedModifierKey);
        }
        if (crate.cutsceneSettings().lockMovement()) {
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
        return crate.cutsceneSettings().lockMovement();
    }

    public boolean isPreview() {
        return preview;
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
        CrateDefinition.MusicSettings music = crate.cutsceneSettings().musicSettings();
        if (music == null || music.sound().isEmpty()) {
            return;
        }
        SoundCategory category = parseCategory(music.category());
        if (music.fadeInTicks() <= 0) {
            player.playSound(player.getLocation(), music.sound(), category, music.volume(), music.pitch());
            return;
        }
        scheduleMusicFade(music, category, true);
    }

    private void stopMusic() {
        CrateDefinition.MusicSettings music = crate.cutsceneSettings().musicSettings();
        if (music == null || music.sound().isEmpty()) {
            return;
        }
        SoundCategory category = parseCategory(music.category());
        if (music.fadeOutTicks() <= 0) {
            player.stopSound(music.sound(), category);
            return;
        }
        scheduleMusicFade(music, category, false);
    }

    private void scheduleMusicFade(CrateDefinition.MusicSettings music, SoundCategory category, boolean fadeIn) {
        if (musicTask != null) {
            musicTask.cancel();
        }
        int totalTicks = Math.max(1, fadeIn ? music.fadeInTicks() : music.fadeOutTicks());
        int steps = Math.max(1, totalTicks / 4);
        float startVolume = fadeIn ? 0.0f : music.volume();
        float endVolume = fadeIn ? music.volume() : 0.0f;
        float step = (endVolume - startVolume) / steps;
        musicTask = new BukkitRunnable() {
            int stepIndex = 0;
            float current = startVolume;

            @Override
            public void run() {
                if (stepIndex > steps) {
                    if (!fadeIn) {
                        player.stopSound(music.sound(), category);
                    }
                    cancel();
                    return;
                }
                current = Math.max(0.0f, Math.min(music.volume(), current));
                if (current <= 0.0f && !fadeIn) {
                    player.stopSound(music.sound(), category);
                } else {
                    player.stopSound(music.sound(), category);
                    float playVolume = Math.max(0.01f, current);
                    player.playSound(player.getLocation(), music.sound(), category, playVolume, music.pitch());
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
