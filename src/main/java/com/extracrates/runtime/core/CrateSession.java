package com.extracrates.runtime.core;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.event.CrateRewardEvent;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.runtime.CameraEntityFactory;
import com.extracrates.runtime.ProtocolEntityHider;
import com.extracrates.config.LanguageManager;
import com.extracrates.util.ItemUtil;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.SoundUtil;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CrateSession {
    private static final ConcurrentMap<RewardDisplayCacheKey, ItemStack> REWARD_DISPLAY_CACHE = new ConcurrentHashMap<>();

    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final Player player;
    private final CrateDefinition crate;
    private List<Reward> rewards;
    private final CutscenePath path;
    private final SessionManager sessionManager;
    private final boolean preview;
    private final int maxRerolls;

    private Entity cameraEntity;
    private ItemDisplay rewardDisplay;
    private Entity hologram;
    private BukkitRunnable task;
    private BukkitRunnable musicTask;
    private BukkitRunnable timeoutTask;

    private int rewardIndex;
    private int rerollsUsed;
    private int rewardSwitchTicks;
    private int nextRewardSwitchTick;
    private int elapsedTicks;
    private int rerollEnabledAtTick;
    private Location rewardBaseLocation;
    private Location hologramBaseLocation;
    private Transformation rewardBaseTransform;
    private int rerollEnabledAtTick;
    private int selectedRewardIndex;
    private boolean rerollLocked;
    private BossBar rerollBossBar;

    private GameMode previousGameMode;
    private Entity previousSpectatorTarget;
    private NamespacedKey speedModifierKey;
    private ItemStack previousHelmet;
    private float previousWalkSpeed;
    private float previousFlySpeed;
    private boolean rewardDelivered;

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
        this.maxRerolls = Math.max(0, crate.maxRerolls());
    }

    public void start() {
        capturePlayerState();
        if (path == null) {
            player.sendMessage(languageManager.getMessage("session.error.missing-path"));
            finish();
            return;
        }
        if (preview) {
            player.sendMessage(Component.text("Modo vista previa: solo vista previa."));
        }
        rewardIndex = 0;
        selectedRewardIndex = -1;
        rerollLocked = false;
        elapsedTicks = 0;
        lastInputTick = -1;
        rewardSwitchTicks = Math.max(1, configLoader.getMainConfig().getInt("cutscene.reward-delay-ticks", 20));
        nextRewardSwitchTick = rewardSwitchTicks;
        rerollEnabledAtTick = Math.max(0, configLoader.getMainConfig().getInt("cutscene.reroll-enabled-tick", 0));
        Location start = crate.cameraStart() != null ? crate.cameraStart() : player.getLocation();
        previousGameMode = player.getGameMode();
        gamemodeSnapshotTaken = true;
        previousWalkSpeed = player.getWalkSpeed();
        previousFlySpeed = player.getFlySpeed();
        speedSnapshotTaken = true;
        spawnCamera(start);
        applySpectatorMode();
        spawnRewardDisplay();
        setupRerollHud();
        startMusic();
        scheduleTimeout();
        startCutscene();
    }

    public CrateDefinition getCrate() {
        return crate;
    }

    private void spawnCamera(Location start) {
        FileConfiguration config = configLoader.getMainConfig();
        String cameraEntityType = config.getString("cutscene.camera-entity", "armorstand");
        boolean armorStandInvisible = config.getBoolean("cutscene.armorstand-invisible", true);
        cameraEntity = CameraEntityFactory.spawn(start, cameraEntityType, armorStandInvisible);
        trackEntity(cameraEntity);
        hideFromOthers(cameraEntity);
        logVerbose("Camara creada: tipo=%s invisible=%s", cameraEntityType, armorStandInvisible);
    }

    private void applySpectatorMode() {
        FileConfiguration config = configLoader.getMainConfig();
        String keyText = config.getString("cutscene.speed-modifier-key");
        if (keyText == null || keyText.isBlank()) {
            keyText = config.getString("cutscene.speed-modifier-uuid", "crate-cutscene");
        }
        NamespacedKey parsedKey = NamespacedKey.fromString(keyText.toLowerCase(Locale.ROOT), plugin);
        speedModifierKey = parsedKey != null ? parsedKey : new NamespacedKey(plugin, "crate-cutscene");
        double modifierValue = config.getDouble("cutscene.slowdown-modifier", -10.0);
        sessionManager.applySpectator(player, speedModifierKey, modifierValue);
        player.setSpectatorTarget(cameraEntity);

        previousHelmet = player.getInventory().getHelmet();
        helmetSnapshotTaken = true;
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
            toggleHud(true);
        }
        if (crate.cutsceneSettings().lockMovement()) {
            player.setWalkSpeed(0.0f);
            player.setFlySpeed(0.0f);
        }
        logVerbose("Spectator aplicado: key=%s hud=%s lockMovement=%s", speedModifierKey.getKey(), crate.cutsceneSettings().hideHud(), crate.cutsceneSettings().lockMovement());
    }

    private void spawnRewardDisplay() {
        if (rewards.isEmpty()) {
            return;
        }
        Location anchor = crate.rewardAnchor() != null ? crate.rewardAnchor() : player.getLocation().add(0, 1.5, 0);
        Reward reward = getCurrentReward();
        if (reward == null) {
            return;
        }
        rewardAnchorLocation = anchor.clone();
        CrateDefinition.RewardFloatSettings floatSettings = resolveFloatSettings(reward);
        Location displayLocation = anchor.clone().add(0, floatSettings.height(), 0);

        rewardDisplay = anchor.getWorld().spawn(displayLocation, ItemDisplay.class, display -> {
            display.setItemStack(buildRewardDisplayItem(reward, anchor.getWorld()));
        });
        hologram = anchor.getWorld().spawn(displayLocation.clone().add(0, 0.4, 0), TextDisplay.class, display -> {
            display.text(configLoader.getSettings().applyHologramFont(TextUtil.color(buildHologramText(reward))));
            display.setBillboard(Display.Billboard.CENTER);
        });

        hideFromOthers(rewardDisplay);
        hideFromOthers(hologram);

        rewardBaseLocation = rewardDisplay.getLocation().clone();
        hologramBaseLocation = hologram.getLocation().clone();
        rewardBaseTransform = rewardDisplay.getTransformation();
        logVerbose("Reward display creado: reward=%s", reward.id());
    }

    private Entity spawnHologramEntity(Location location, Reward reward) {
        Component textComponent = buildHologramComponent(reward);
        Class<? extends Entity> textDisplayClass = resolveTextDisplayClass();
        if (textDisplayClass != null) {
            return location.getWorld().spawn(location, textDisplayClass, display -> {
                setTextDisplayText(display, textComponent);
                setTextDisplayBillboard(display, Display.Billboard.CENTER);
            });
        }
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setCustomName(TextUtil.serializeLegacy(textComponent));
            stand.setCustomNameVisible(true);
        });
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Entity> resolveTextDisplayClass() {
        try {
            return (Class<? extends Entity>) Class.forName("org.bukkit.entity.TextDisplay");
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private Component buildHologramComponent(Reward reward) {
        String name = resolveHologramText(reward);
        return configLoader.getSettings().applyHologramFont(TextUtil.color(name));
    }

    private String resolveHologramText(Reward reward) {
        String format = reward.hologram();
        if (format == null || format.isEmpty()) {
            format = crate.animation().hologramFormat();
        }
        if (format == null || format.isEmpty()) {
            format = "%reward_name%";
        }
        return format.replace("%reward_name%", reward.displayName());
    }

    private void setTextDisplayText(Entity entity, Component component) {
        try {
            java.lang.reflect.Method method = entity.getClass().getMethod("text", Component.class);
            method.invoke(entity, component);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void setTextDisplayBillboard(Entity entity, Display.Billboard billboard) {
        try {
            java.lang.reflect.Method method = entity.getClass().getMethod("setBillboard", Display.Billboard.class);
            method.invoke(entity, billboard);
        } catch (ReflectiveOperationException ignored) {
        }
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

    public void hideEntitiesFrom(Player viewer) {
        if (!configLoader.getMainConfig().getBoolean("cutscene.hide-others", true)) {
            return;
        }
        if (viewer.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        if (cameraEntity != null) {
            viewer.hideEntity(plugin, cameraEntity);
        }
        if (rewardDisplay != null) {
            viewer.hideEntity(plugin, rewardDisplay);
        }
        if (hologram != null) {
            viewer.hideEntity(plugin, hologram);
        }
    }

    private void startCutscene() {
        if (path == null || path.getPoints().isEmpty()) {
            logVerbose("Cutscene finalizada: ruta vacia para crate=%s", crate.id());
            finish();
            return;
        }
        List<Location> timeline = buildTimeline(cameraEntity.getWorld(), path);
        if (timeline.isEmpty()) {
            logVerbose("Cutscene finalizada: timeline vacio para crate=%s", crate.id());
            finish();
            return;
        }
        double minTeleportDistance = Math.max(0.0, configLoader.getMainConfig().getDouble("cutscene.min-teleport-distance", 0.0));
        double minTeleportDistanceSquared = minTeleportDistance * minTeleportDistance;
        task = new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = Math.max(0, timeline.size() - 1);
            Location lastTeleportLocation = cameraEntity.getLocation();

            @Override
            public void run() {
                if (tick > totalTicks) {
                    logVerbose("Cutscene completa: tick=%d total=%d", tick, totalTicks);
                    cancel();
                    finish();
                    return;
                }
                int currentTick = tick;
                Location point = timeline.get(tick++);
                if (lastTeleportLocation == null || lastTeleportLocation.distanceSquared(point) >= minTeleportDistanceSquared) {
                    cameraEntity.teleport(point);
                    lastTeleportLocation = point;
                }
                player.setSpectatorTarget(cameraEntity);
                elapsedTicks++;
                if (!rerollLocked && rewards.size() > 1 && rewardSwitchTicks > 0) {
                    while (elapsedTicks >= nextRewardSwitchTick && rewardIndex < rewards.size() - 1) {
                        rewardIndex++;
                        nextRewardSwitchTick += rewardSwitchTicks;
                        refreshRewardDisplay();
                        if (!preview) {
                            Reward reward = getCurrentReward();
                            if (reward != null) {
                                sessionManager.recordPendingReward(player.getUniqueId(), crate.id(), reward.id());
                            }
                        }
                    }
                }
                updateRerollHud();
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
    }

    private void scheduleTimeout() {
        int maxDurationTicks = configLoader.getMainConfig().getInt("sessions.max-duration-ticks", 0);
        if (maxDurationTicks <= 0) {
            return;
        }
        timeoutTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxDurationTicks) {
                    cancel();
                    end();
                }
            }
        };
        timeoutTask.runTaskTimer(plugin, 1L, 1L);
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
        end();
        if (!preview) {
            executeReward();
        }
    }

    private void executeReward() {
        if (preview) {
            return;
        }
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
        if (rerollLocked || rewardIndex >= rewards.size() - 1) {
            return;
        }
        while (elapsedTicks >= nextRewardSwitchTick && rewardIndex < rewards.size() - 1) {
            rewardIndex++;
            nextRewardSwitchTick += rewardSwitchTicks;
            refreshRewardDisplay();
            logVerbose("Cambio de reward post-entrega: tick=%d rewardIndex=%d", elapsedTicks, rewardIndex);
        }
    }

    public void reroll(List<Reward> newRewards) {
        if (newRewards == null || newRewards.isEmpty()) {
            return;
        }
        this.rewards = new ArrayList<>(newRewards);
        rewardIndex = 0;
        elapsedTicks = 0;
        nextRewardSwitchTick = rewardSwitchTicks;
        refreshRewardDisplay();
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

    private void showRewardMessage(Reward reward) {
        if (reward == null || reward.message() == null) {
            return;
        }
        String title = sanitizeMessage(reward.message().title());
        String subtitle = sanitizeMessage(reward.message().subtitle());
        if (title.isEmpty() && subtitle.isEmpty()) {
            return;
        }
        UiMode mode = UiMode.fromConfig(configLoader.getMainConfig());
        if ((mode == UiMode.BOSSBAR || mode == UiMode.BOTH) && !isBossBarSupported()) {
            mode = UiMode.ACTIONBAR;
        }
        switch (mode) {
            case NONE -> {
                return;
            }
            case ACTIONBAR -> sendActionBarMessage(title, subtitle);
            case BOSSBAR -> showBossBarMessage(title, subtitle);
            case BOTH -> {
                showBossBarMessage(title, subtitle);
                sendActionBarMessage(title, subtitle);
            }
        }
    }

    private void showBossBarMessage(String title, String subtitle) {
        String text = !title.isEmpty() ? title : subtitle;
        if (text.isEmpty()) {
            return;
        }
        BossBar bar = BossBar.bossBar(TextUtil.color(text), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        try {
            player.showBossBar(bar);
        } catch (RuntimeException | NoSuchMethodError ex) {
            player.sendActionBar(TextUtil.color(text));
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                player.hideBossBar(bar);
            }
        }.runTaskLater(plugin, 60L);
    }

    private void sendActionBarMessage(String title, String subtitle) {
        String text = joinMessage(title, subtitle);
        if (text.isEmpty()) {
            return;
        }
        player.sendActionBar(TextUtil.color(text));
    }

    private boolean isBossBarSupported() {
        if (bossBarSupported != null) {
            return bossBarSupported;
        }
        try {
            player.getClass().getMethod("showBossBar", BossBar.class);
            player.getClass().getMethod("hideBossBar", BossBar.class);
            bossBarSupported = true;
        } catch (NoSuchMethodException | SecurityException | NoClassDefFoundError ex) {
            bossBarSupported = false;
        }
        return bossBarSupported;
    }

    private String sanitizeMessage(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private String joinMessage(String title, String subtitle) {
        if (title.isEmpty()) {
            return subtitle;
        }
        if (subtitle.isEmpty()) {
            return title;
        }
        return title + " " + subtitle;
    }

    private void refreshRewardDisplay() {
        Reward reward = getCurrentReward();
        if (reward == null) {
            return;
        }
        if (rewardDisplay != null) {
            rewardDisplay.setItemStack(buildRewardDisplayItem(reward, rewardDisplay.getWorld()));
        }
        if (hologram != null) {
            hologram.text(configLoader.getSettings().applyHologramFont(TextUtil.color(buildHologramText(reward))));
        }
    }

    private void executeCutsceneCommands(String key, Reward reward) {
        if (!crate.cutsceneSettings().commandsEnabled()) {
            return;
        }
        List<String> commands = configLoader.getMainConfig().getStringList("cutscene." + key);
        if (commands == null || commands.isEmpty()) {
            return;
        }
        String rewardId = reward != null ? reward.id() : "";
        String rewardName = reward != null ? reward.displayName() : "";
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String parsed = command
                    .replace("%player%", player.getName())
                    .replace("%player_uuid%", player.getUniqueId().toString())
                    .replace("%crate_id%", crate.id())
                    .replace("%crate_name%", crate.displayName())
                    .replace("%reward_id%", rewardId)
                    .replace("%reward_name%", rewardName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private ItemStack buildRewardDisplayItem(Reward reward, World world) {
        boolean debugTimings = configLoader.getMainConfig().getBoolean("debug.timings", false);
        long start = debugTimings ? System.nanoTime() : 0L;
        RewardDisplayCacheKey cacheKey = new RewardDisplayCacheKey(
                reward.id(),
                world != null ? world.getName() : "unknown",
                crate.animation().rewardModel()
        );
        ItemStack cached = REWARD_DISPLAY_CACHE.get(cacheKey);
        if (cached != null) {
            if (debugTimings) {
                logTiming(cacheKey, true, start);
            }
            return cached.clone();
        }
        ItemStack item = ItemUtil.buildItem(reward, world, configLoader, plugin.getMapImageCache());
        String rewardModel = crate.animation().rewardModel();
        if (rewardModel == null || rewardModel.isEmpty()) {
            REWARD_DISPLAY_CACHE.put(cacheKey, item.clone());
            if (debugTimings) {
                logTiming(cacheKey, false, start);
            }
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            REWARD_DISPLAY_CACHE.put(cacheKey, item.clone());
            if (debugTimings) {
                logTiming(cacheKey, false, start);
            }
            return item;
        }
        int modelData = ResourcepackModelResolver.resolveCustomModelData(configLoader, rewardModel);
        if (modelData >= 0) {
            meta.setCustomModelData(modelData);
            item.setItemMeta(meta);
        }
        REWARD_DISPLAY_CACHE.put(cacheKey, item.clone());
        if (debugTimings) {
            logTiming(cacheKey, false, start);
        }
        return item;
    }

    private String buildHologramText(Reward reward) {
        String format = reward.hologram();
        if (format == null || format.isEmpty()) {
            format = crate.animation().hologramFormat();
        }
        if (format == null || format.isEmpty()) {
            format = "%reward_name%";
        }
        String name = format.replace("%reward_name%", reward.displayName());
        if (preview) {
            name = name + "\n&7(solo vista previa)";
        }
        return name;
    }

    public void end() {
        if (task != null) {
            task.cancel();
        }
        if (musicTask != null) {
            musicTask.cancel();
        }
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        stopMusic();
        untrackEntity(cameraEntity);
        untrackEntity(rewardDisplay);
        untrackEntity(hologram);
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
        if (gamemodeSnapshotTaken && previousGameMode != null) {
            player.setGameMode(previousGameMode);
        }
        player.setSpectatorTarget(null);
        if (speedModifierKey != null) {
            sessionManager.removeSpectatorModifier(player, speedModifierKey);
        }
        if (speedSnapshotTaken) {
            player.setWalkSpeed(previousWalkSpeed);
            player.setFlySpeed(previousFlySpeed);
        }
        if (hudHiddenApplied) {
            toggleHud(false);
        }
        if (helmetSnapshotTaken) {
            if (previousHelmet != null) {
                player.sendEquipmentChange(player, EquipmentSlot.HEAD, previousHelmet);
            } else {
                player.sendEquipmentChange(player, EquipmentSlot.HEAD, new ItemStack(Material.AIR));
            }
        }
        if (rerollBossBar != null) {
            player.hideBossBar(rerollBossBar);
            rerollBossBar = null;
        }
        sessionManager.removeSession(player.getUniqueId());
        logVerbose("Sesion limpiada: jugador=%s crate=%s", player.getName(), crate.id());
    }

    public void handleRerollInput() {
        if (maxRerolls > 0 && rerollsUsed >= maxRerolls) {
            player.sendMessage(languageManager.getMessage("session.reroll-limit-reached"));
            finish();
            return;
        }
        if (!advanceReward()) {
            finish();
            return;
        }
        rerollsUsed++;
    }

    private boolean advanceReward() {
        if (rewards == null || rewards.isEmpty()) {
            return false;
        }
        if (rewardIndex >= rewards.size() - 1) {
            return false;
        }
        rewardIndex++;
        nextRewardSwitchTick = elapsedTicks + rewardSwitchTicks;
        refreshRewardDisplay();
        return true;
    }

    private void capturePlayerState() {
        previousGameMode = player.getGameMode();
        previousSpectatorTarget = player.getSpectatorTarget();
        previousWalkSpeed = player.getWalkSpeed();
        previousFlySpeed = player.getFlySpeed();
        previousHelmet = cloneItemStack(player.getInventory().getHelmet());
        previousInventoryContents = cloneItemStackArray(player.getInventory().getContents());
        previousArmorContents = cloneItemStackArray(player.getInventory().getArmorContents());
        previousOffHand = cloneItemStack(player.getInventory().getItemInOffHand());
    }

    private void restorePlayerState() {
        GameMode restoreMode = previousGameMode != null ? previousGameMode : GameMode.SURVIVAL;
        if (restoreMode == GameMode.SPECTATOR) {
            restoreMode = GameMode.SURVIVAL;
        }
        player.setGameMode(restoreMode);
        player.setSpectatorTarget(previousSpectatorTarget);
    }

    private void restoreInventory() {
        if (previousInventoryContents == null) {
            return;
        }
        player.getInventory().setContents(cloneItemStackArray(previousInventoryContents));
        if (previousArmorContents != null) {
            player.getInventory().setArmorContents(cloneItemStackArray(previousArmorContents));
        }
        if (previousOffHand != null) {
            player.getInventory().setItemInOffHand(previousOffHand.clone());
        } else {
            player.getInventory().setItemInOffHand(null);
        }
        player.updateInventory();
    }

    private ItemStack cloneItemStack(ItemStack item) {
        return item != null ? item.clone() : null;
    }

    private ItemStack[] cloneItemStackArray(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        ItemStack[] clone = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            clone[i] = cloneItemStack(items[i]);
        }
        return clone;
    }

    private void trackEntity(Entity entity) {
        ProtocolEntityHider hider = plugin.getProtocolEntityHider();
        if (hider != null && entity != null) {
            hider.trackEntity(player, entity);
        }
    }

    private void untrackEntity(Entity entity) {
        ProtocolEntityHider hider = plugin.getProtocolEntityHider();
        if (hider != null && entity != null) {
            hider.untrackEntity(entity);
        }
    }

    public boolean isMovementLocked() {
        return crate.cutsceneSettings().lockMovement();
    }

    public boolean isPreview() {
        return preview;
    }

    public void handleRerollInput(boolean shift) {
        if (rerollLocked || rewards == null || rewards.isEmpty()) {
            return;
        }
        if (elapsedTicks < rerollEnabledAtTick) {
            player.sendActionBar(languageManager.getMessage("session.reroll.blocked"));
            return;
        }
        if (shift) {
            rerollLocked = true;
            selectedRewardIndex = rewardIndex;
            Reward reward = getCurrentReward();
            Map<String, String> placeholders = new HashMap<>();
            if (reward != null) {
                placeholders.put("reward", reward.displayName());
            }
            player.sendActionBar(languageManager.getMessage("session.reroll.confirmed", placeholders));
            updateRerollHud();
            finish();
            return;
        }
        rewardIndex = (rewardIndex + 1) % rewards.size();
        selectedRewardIndex = rewardIndex;
        refreshRewardDisplay();
        Reward reward = getCurrentReward();
        Map<String, String> placeholders = new HashMap<>();
        if (reward != null) {
            placeholders.put("reward", reward.displayName());
        }
        player.sendActionBar(languageManager.getMessage("session.reroll.advance", placeholders));
        updateRerollHud();
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
            logVerbose("Musica iniciada: sonido=%s fadeIn=0", music.sound());
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
            logVerbose("Musica detenida: sonido=%s fadeOut=0", music.sound());
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

    private void setupRerollHud() {
        if (rewards == null || rewards.isEmpty()) {
            return;
        }
        if (rerollBossBar == null) {
            rerollBossBar = BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            player.showBossBar(rerollBossBar);
        }
        updateRerollHud();
    }

    private void updateRerollHud() {
        if (rerollBossBar == null) {
            return;
        }
        String key = "session.reroll.ready";
        float progress = 1.0f;
        if (rerollLocked) {
            key = "session.reroll.confirmed-bar";
        } else if (rerollEnabledAtTick > 0 && elapsedTicks < rerollEnabledAtTick) {
            key = "session.reroll.waiting";
            progress = Math.min(1.0f, Math.max(0.0f, elapsedTicks / (float) rerollEnabledAtTick));
        }
        rerollBossBar.name(languageManager.getMessage(key));
        rerollBossBar.progress(progress);
    }
}
