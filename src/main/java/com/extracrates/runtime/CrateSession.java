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
    private BukkitRunnable timeoutTask;

    private GameMode previousGameMode;
    private UUID speedModifierUuid;
    private ItemStack previousHelmet;
    private boolean spectatorApplied;

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
        spawnCamera(start);
        applySpectatorMode();
        spawnRewardDisplay();
        scheduleTimeout();
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
        spectatorApplied = true;
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

    private boolean isQaMode() {
        return configLoader.getMainConfig().getBoolean("qa-mode", false);
    }

    public void end() {
        if (task != null) {
            task.cancel();
        }
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        if (cameraStand != null && !cameraStand.isDead()) {
            sessionManager.getDisplayPool().releaseArmorStand(cameraStand);
        }
        if (rewardDisplay != null && !rewardDisplay.isDead()) {
            sessionManager.getDisplayPool().releaseItemDisplay(rewardDisplay);
        }
        if (hologram != null && !hologram.isDead()) {
            sessionManager.getDisplayPool().releaseTextDisplay(hologram);
        }
        if (spectatorApplied) {
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
        }
        sessionManager.removeSession(player.getUniqueId());
    }

    private boolean isRewardOnly() {
        return "reward-only".equals(openMode);
    }

    private boolean isPreviewOnly() {
        return "preview-only".equals(openMode);
    }

    private String normalizeOpenMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "reward-only";
        }
        return mode.toLowerCase(Locale.ROOT);
    }
}
