package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CrateType;
import com.extracrates.model.CutscenePath;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.storage.CrateStorage;
import com.extracrates.storage.LocalStorage;
import com.extracrates.storage.SqlStorage;
import com.extracrates.storage.StorageFallback;
import com.extracrates.storage.StorageSettings;
import com.extracrates.util.RewardSelector;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private SyncBridge syncBridge;
    private final Map<UUID, CrateSession> sessions = new HashMap<>();
    private final CrateStorage storage;
    private final boolean storageEnabled;

    public SessionManager(ExtraCratesPlugin plugin, ConfigLoader configLoader, SyncBridge syncBridge) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        StorageSettings settings = StorageSettings.fromConfig(configLoader.getMainConfig());
        this.storageEnabled = settings.enabled();
        this.storage = initializeStorage(settings);
    }

    public void shutdown() {
        sessions.values().forEach(CrateSession::end);
        sessions.clear();
        storage.close();
    }

    public boolean openCrate(Player player, CrateDefinition crate) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Ya tienes una cutscene en progreso."));
            return false;
        }
        if (!player.hasPermission(crate.getPermission())) {
            player.sendMessage(Component.text("No tienes permiso para abrir esta crate."));
            return false;
        }
        String openMode = normalizeOpenMode(crate.getOpenMode());
        boolean previewOnly = openMode.equals("preview-only");
        boolean requiresKey = openMode.equals("key-required")
                || openMode.equals("keyed")
                || openMode.equals("key-only");
        boolean requiresEconomy = openMode.equals("economy-required")
                || openMode.equals("economy")
                || openMode.equals("economy-only");
        if (!previewOnly && !requiresKey && !requiresEconomy && crate.getType() == CrateType.KEYED) {
            requiresKey = true;
        }
        if (!previewOnly && requiresKey && !hasKey(player, crate)) {
            player.sendMessage(Component.text("Necesitas una llave para esta crate."));
            return false;
        }
        if (!previewOnly && requiresEconomy && !canAffordEconomy(player, crate)) {
            player.sendMessage(Component.text("Necesitas saldo de economía para abrir esta crate."));
            return false;
        }
        if (isOnCooldown(player, crate)) {
            player.sendMessage(Component.text("Esta crate está en cooldown."));
            return false;
        }
        RewardPool pool = configLoader.getRewardPools().get(crate.getRewardsPool());
        List<Reward> rewards = RewardSelector.roll(pool);
        if (rewards.isEmpty()) {
            player.sendMessage(Component.text("No hay recompensas configuradas."));
            return false;
        }
        Reward reward = rewards.get(0);
        CutscenePath path = configLoader.getPaths().get(crate.getAnimation().getPath());
        boolean grantReward = !previewOnly;
        CrateSession session = new CrateSession(plugin, configLoader, player, crate, reward, path, grantReward, this);
        sessions.put(player.getUniqueId(), session);
        if (!previewOnly && requiresKey) {
            consumeKey(player, crate);
        }
        session.start();
        recordCrateOpen(player, crate);
        applyCooldown(player, crate);
        storage.logOpen(player.getUniqueId(), crate.getId(), reward.getId(), Bukkit.getServerName(), Instant.now());
        return true;
    }

    public void endSession(UUID playerId) {
        CrateSession session = sessions.remove(playerId);
        if (session != null) {
            session.end();
        }
    }

    public CrateSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    private boolean isOnCooldown(Player player, CrateDefinition crate) {
        if (crate.getCooldownSeconds() <= 0) {
            return false;
        }
        Instant last = storage.getCooldown(player.getUniqueId(), crate.getId()).orElse(null);
        if (last == null) {
            return false;
        }
        Duration elapsed = Duration.between(last, Instant.now());
        return elapsed.getSeconds() < crate.getCooldownSeconds();
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
        int modelData = parseModel(crate.getKeyModel());
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

    private int parseModel(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void consumeKey(Player player, CrateDefinition crate) {
        if (storageEnabled) {
            storage.consumeKey(player.getUniqueId(), crate.getId());
            return;
        }
        int modelData = parseModel(crate.getKeyModel());
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
