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
        FileConfiguration config = configLoader.getMainConfig();
        String cameraEntityType = config.getString("cutscene.camera-entity", "armorstand");
        boolean armorStandInvisible = config.getBoolean("cutscene.armorstand-invisible", true);
        cameraEntity = CameraEntityFactory.spawn(start, cameraEntityType, armorStandInvisible);
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
        List<Location> timeline = buildTimeline(cameraEntity.getWorld(), path);
        task = new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (tick > totalTicks) {
                    cancel();
                    finish();
                    return;
                }
                Location point = timeline.get(index++);
                cameraEntity.teleport(point);
                player.setSpectatorTarget(cameraEntity);

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

    private List<Location> buildTimeline(World world, CutscenePath path) {
        List<Location> timeline = new ArrayList<>();
        List<com.extracrates.model.CutscenePoint> points = path.getPoints();
        String smoothing = resolveSmoothing(path);
        for (int i = 0; i < points.size() - 1; i++) {
            com.extracrates.model.CutscenePoint start = points.get(i);
            com.extracrates.model.CutscenePoint end = points.get(i + 1);
            Location startLoc = new Location(world, start.getX(), start.getY(), start.getZ(), start.getYaw(), start.getPitch());
            Location endLoc = new Location(world, end.getX(), end.getY(), end.getZ(), end.getYaw(), end.getPitch());
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
