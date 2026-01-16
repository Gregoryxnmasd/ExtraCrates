package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.hologram.HologramProvider;
import com.extracrates.hologram.HologramProviderFactory;
import com.extracrates.hologram.HologramSettings;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CrateType;
import com.extracrates.model.CutscenePath;
import com.extracrates.model.CutscenePoint;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.storage.CrateStorage;
import com.extracrates.storage.LocalStorage;
import com.extracrates.storage.SqlStorage;
import com.extracrates.storage.StorageFallback;
import com.extracrates.storage.StorageSettings;
import com.extracrates.util.RewardSelector;
import com.extracrates.util.ResourcepackModelResolver;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SessionManager {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final HologramProvider hologramProvider;
    private final HologramSettings hologramSettings;
    private final Map<UUID, CrateSession> sessions = new HashMap<>();
    private final Map<UUID, CutscenePreviewSession> previews = new HashMap<>();
    private final Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();

    public SessionManager(ExtraCratesPlugin plugin, ConfigLoader configLoader, Economy economy) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.hologramSettings = HologramSettings.fromConfig(configLoader.getMainConfig());
        this.hologramProvider = HologramProviderFactory.create(plugin, configLoader.getMainConfig(), hologramSettings);
    }

    public void shutdown() {
        sessions.values().forEach(CrateSession::end);
        sessions.clear();
        previews.values().forEach(CutscenePreviewSession::end);
        previews.clear();
    }

    public boolean openCrate(Player player, CrateDefinition crate) {
        return openCrate(player, crate, false);
    }

    public boolean openCrate(Player player, CrateDefinition crate, boolean preview) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(languageManager.getMessage("session.already-in-progress"));
            return false;
        }
        if (!player.hasPermission(crate.getPermission())) {
            player.sendMessage(languageManager.getMessage("session.no-permission"));
            return false;
        }
        boolean preview = openMode == OpenMode.PREVIEW;
        if (!preview && crate.getType() == com.extracrates.model.CrateType.KEYED && !hasKey(player, crate)) {
            player.sendMessage(Component.text("Necesitas una llave para esta crate."));
            return false;
        }
        long remainingSeconds = isOnCooldown(player, crate);
        if (remainingSeconds > 0) {
            player.sendMessage(Component.text("Esta crate está en cooldown. Tiempo restante: " + remainingSeconds + "s."));
            return false;
        }
        RewardPool pool = configLoader.getRewardPools().get(crate.getRewardsPool());
        long seed = ThreadLocalRandom.current().nextLong();
        List<Reward> rewards = RewardSelector.roll(pool, seed);
        if (rewards.isEmpty()) {
            player.sendMessage(languageManager.getMessage("session.no-rewards"));
            return false;
        }
        if (!chargePlayer(player, crate)) {
            return false;
        }
        CutscenePath path = configLoader.getPaths().get(crate.getAnimation().getPath());
        CrateSession session = new CrateSession(plugin, configLoader, player, crate, rewards, path, this);
        sessions.put(player.getUniqueId(), session);
        if (!preview && crate.getType() == com.extracrates.model.CrateType.KEYED) {
            consumeKey(player, crate);
        }
        session.start();
        if (!preview) {
            applyCooldown(player, crate);
        }
        return true;
    }

    private boolean chargePlayer(Player player, CrateDefinition crate) {
        double cost = crate.getCost();
        if (cost <= 0 || economy == null) {
            return true;
        }
        if (!economy.has(player, cost)) {
            player.sendMessage(Component.text("No tienes saldo suficiente para abrir esta crate."));
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, cost);
        if (!response.transactionSuccess()) {
            player.sendMessage(Component.text("No se pudo descontar el costo de la crate."));
            return false;
        }
        return true;
    }

    public boolean previewCutscene(Player player, CutscenePath path) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Ya tienes una cutscene en progreso."));
            return false;
        }
        if (path == null) {
            player.sendMessage(Component.text("Ruta de cutscene no encontrada."));
            return false;
        }
        if (path.getPoints().size() < 2) {
            player.sendMessage(Component.text("La ruta necesita al menos 2 puntos para el preview."));
            return false;
        }
        String previewName = path.getParticlePreview();
        if (previewName == null || previewName.isBlank()) {
            player.sendMessage(Component.text("La ruta no tiene particle-preview configurado."));
            return false;
        }
        Particle particle;
        try {
            particle = Particle.valueOf(previewName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Component.text("Particle-preview inválido: " + previewName));
            return false;
        }
        endPreview(player.getUniqueId());
        CutscenePreviewSession preview = new CutscenePreviewSession(
                plugin,
                player,
                path,
                particle,
                () -> previews.remove(player.getUniqueId())
        );
        previews.put(player.getUniqueId(), preview);
        preview.start();
        player.sendMessage(Component.text("Preview de cutscene iniciada."));
        return true;
    }

    public void endSession(UUID playerId) {
        CrateSession session = sessions.remove(playerId);
        if (session != null) {
            session.end();
        }
    }

    public void endPreview(UUID playerId) {
        CutscenePreviewSession preview = previews.remove(playerId);
        if (preview != null) {
            preview.end();
        }
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    private CutscenePath buildDefaultPath(Player player) {
        Location location = player.getLocation();
        List<CutscenePoint> points = List.of(
                new CutscenePoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()),
                new CutscenePoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch())
        );
        return new CutscenePath("default", 3.0, true, 0.15, "linear", "", points);
    }

    private boolean isOnCooldown(Player player, CrateDefinition crate) {
        if (crate.getCooldownSeconds() <= 0) {
            return 0;
        }
        Map<String, Instant> userCooldowns = cooldowns.get(player.getUniqueId());
        if (userCooldowns == null) {
            return 0;
        }
        Instant last = userCooldowns.get(crate.getId());
        if (last == null) {
            return 0;
        }
        Duration elapsed = Duration.between(last, Instant.now());
        long remaining = crate.getCooldownSeconds() - elapsed.getSeconds();
        return Math.max(remaining, 0);
    }

    private void applyCooldown(Player player, CrateDefinition crate) {
        applyCooldown(player, crate, Instant.now(), true);
    }

    private void applyCooldown(Player player, CrateDefinition crate, Instant timestamp, boolean record) {
        if (crate.getCooldownSeconds() <= 0) {
            return;
        }
        storage.setCooldown(player.getUniqueId(), crate.getId(), Instant.now());
    }

    private boolean hasKey(Player player, CrateDefinition crate) {
        if (storageEnabled) {
            return storage.getKeyCount(player.getUniqueId(), crate.getId()) > 0;
        }
        if (crate.getKeyModel() == null || crate.getKeyModel().isEmpty()) {
            return true;
        }
        int modelData = ResourcepackModelResolver.resolveCustomModelData(configLoader, crate.getKeyModel());
        if (modelData < 0) {
            return false;
        }
        return Arrays.stream(player.getInventory().getContents()).anyMatch(item -> {
            if (item == null || item.getItemMeta() == null) {
                return false;
            }
            if (!item.getItemMeta().hasCustomModelData()) {
                return false;
            }
            return item.getItemMeta().getCustomModelData() == modelData;
        });
    }

    private void consumeKey(Player player, CrateDefinition crate) {
        int modelData = ResourcepackModelResolver.resolveCustomModelData(configLoader, crate.getKeyModel());
        if (modelData < 0) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getItemMeta() == null || !item.getItemMeta().hasCustomModelData()) {
                continue;
            }
            if (item.getItemMeta().getCustomModelData() == modelData) {
                int amount = item.getAmount();
                if (amount <= 1) {
                    contents[i] = null;
                } else {
                    item.setAmount(amount - 1);
                }
                player.getInventory().setContents(contents);
                if (record && syncBridge != null) {
                    syncBridge.recordKeyConsumed(player.getUniqueId(), crate.getId());
                }
                return;
            }
        }
    }

    private CrateStorage initializeStorage(StorageSettings settings) {
        if (!settings.enabled()) {
            return new LocalStorage();
        }
        try {
            SqlStorage sqlStorage = new SqlStorage(settings, plugin.getLogger());
            return new StorageFallback(sqlStorage, new LocalStorage(), plugin.getLogger());
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo inicializar SQL storage, usando modo local: " + ex.getMessage());
            return new LocalStorage();
        }
    }

    public void applySpectator(Player player, UUID modifierUuid, double modifierValue) {
        player.setGameMode(GameMode.SPECTATOR);
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute != null) {
            AttributeModifier modifier = new AttributeModifier(modifierUuid, "crate-cutscene", modifierValue, AttributeModifier.Operation.ADD_NUMBER);
            attribute.addModifier(modifier);
        }
    }

    public void removeSpectatorModifier(Player player, UUID modifierUuid) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.getModifiers().stream()
                    .filter(mod -> mod.getUniqueId().equals(modifierUuid))
                    .forEach(attribute::removeModifier);
        }
    }

    public void recordRewardGranted(Player player, CrateDefinition crate, Reward reward) {
        if (syncBridge != null) {
            syncBridge.recordRewardGranted(player.getUniqueId(), crate.getId(), reward.getId());
        }
    }

    public void applyRemoteCooldown(UUID playerId, String crateId, Instant timestamp) {
        cooldowns.computeIfAbsent(playerId, key -> new HashMap<>()).put(crateId, timestamp);
    }

    public void applyRemoteKeyConsumed(UUID playerId, String crateId) {
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        if (crate == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            consumeKey(player, crate, false);
        }
    }

    public void applyRemoteOpen(UUID playerId, String crateId) {
        plugin.getLogger().info(() -> "[Sync] Apertura remota registrada " + playerId + " en crate " + crateId);
    }

    public void applyRemoteReward(UUID playerId, String crateId, String rewardId) {
        plugin.getLogger().info(() -> "[Sync] Recompensa remota registrada " + rewardId + " para " + playerId);
    }

    public void flushSyncCaches() {
        cooldowns.clear();
    }
}
