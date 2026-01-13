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

    private Entity cameraEntity;
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
        List<Location> timeline = buildTimeline(cameraEntity.getWorld(), path);
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
                cameraEntity.teleport(point);
                player.setSpectatorTarget(cameraEntity);

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
        for (int i = 0; i < points.size() - 1; i++) {
            com.extracrates.model.CutscenePoint start = points.get(i);
            com.extracrates.model.CutscenePoint end = points.get(i + 1);
            Location startLoc = new Location(world, start.getX(), start.getY(), start.getZ(), start.getYaw(), start.getPitch());
            Location endLoc = new Location(world, end.getX(), end.getY(), end.getZ(), end.getYaw(), end.getPitch());
            double distance = startLoc.distance(endLoc);
            int steps = Math.max(2, (int) Math.ceil(distance / path.getStepResolution()));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x = lerp(startLoc.getX(), endLoc.getX(), t);
                double y = lerp(startLoc.getY(), endLoc.getY(), t);
                double z = lerp(startLoc.getZ(), endLoc.getZ(), t);
                float yaw = (float) lerp(startLoc.getYaw(), endLoc.getYaw(), t);
                float pitch = (float) lerp(startLoc.getPitch(), endLoc.getPitch(), t);
                timeline.add(new Location(world, x, y, z, yaw, pitch));
            }
        }
        return timeline;
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
        if (cameraEntity != null && !cameraEntity.isDead()) {
            cameraEntity.remove();
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
