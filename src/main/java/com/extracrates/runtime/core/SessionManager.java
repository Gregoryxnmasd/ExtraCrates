package com.extracrates.runtime.core;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.cutscene.CutscenePoint;
import com.extracrates.economy.EconomyService;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.storage.CrateStorage;
import com.extracrates.storage.LocalStorage;
import com.extracrates.storage.SqlStorage;
import com.extracrates.storage.StorageFallback;
import com.extracrates.storage.StorageSettings;
import com.extracrates.sync.CrateHistoryEntry;
import com.extracrates.sync.SyncBridge;
import com.extracrates.sync.SyncEventType;
import com.extracrates.util.RewardSelector;
import com.extracrates.util.ResourcepackModelResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SessionManager {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final EconomyService economyService;
    private final LanguageManager languageManager;
    private final CrateStorage storage;
    private final SyncBridge syncBridge;
    private final boolean storageEnabled;
    // Stores both preview and normal crate sessions. Preview sessions are marked in CrateSession.
    private final Map<UUID, CrateSession> sessions = new HashMap<>();
    private final Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();
    private final Map<UUID, Random> sessionRandoms = new HashMap<>();
    private final Map<UUID, Deque<CrateHistoryEntry>> history = new HashMap<>();
    private static final int HISTORY_LIMIT = 200;

    public SessionManager(ExtraCratesPlugin plugin, ConfigLoader configLoader, EconomyService economyService) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.economyService = economyService;
        this.languageManager = plugin.getLanguageManager();
        StorageSettings storageSettings = StorageSettings.fromConfig(configLoader.getMainConfig());
        this.storageEnabled = storageSettings.enabled();
        this.storage = initializeStorage(storageSettings);
        this.syncBridge = new SyncBridge(plugin, configLoader, this);
    }

    public void shutdown() {
        sessions.values().forEach(CrateSession::end);
        sessions.clear();
        sessionRandoms.clear();
        if (syncBridge != null) {
            syncBridge.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
    }

    public boolean openCrate(Player player, CrateDefinition crate) {
        return openCrate(player, crate, false);
    }

    public boolean openCrate(Player player, CrateDefinition crate, boolean preview) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Ya tienes una cutscene en progreso."));
            return false;
        }
        CutscenePath path = resolveCutscenePath(crate, player);
        RewardPool rewardPool = resolveRewardPool(crate);
        if (rewardPool == null) {
            player.sendMessage(Component.text("No se encontró el pool de recompensas para esta crate."));
            return false;
        }
        if (!preview && crate.type() == com.extracrates.model.CrateType.KEYED && !hasKey(player, crate)) {
            player.sendMessage(Component.text("Necesitas una llave para esta crate."));
            return false;
        }
        if (!preview && isOnCooldown(player, crate)) {
            player.sendMessage(Component.text("Esta crate está en cooldown."));
            return false;
        }
        Random random = sessionRandoms.computeIfAbsent(player.getUniqueId(), key -> new Random());
        List<Reward> rewards = RewardSelector.roll(rewardPool, random, buildRollLogger(player));
        if (rewards.isEmpty()) {
            player.sendMessage(languageManager.getMessage("session.no-rewards"));
            return false;
        }
        CrateSession session = new CrateSession(plugin, configLoader, languageManager, player, crate, rewards, path, this, preview);
        sessions.put(player.getUniqueId(), session);
        if (!preview && crate.type() == com.extracrates.model.CrateType.KEYED) {
            consumeKey(player, crate);
        }
        if (!preview) {
            Instant openedAt = Instant.now();
            recordHistory(player.getUniqueId(), crate.id(), null, SyncEventType.CRATE_OPEN, openedAt);
            if (syncBridge != null) {
                syncBridge.recordCrateOpen(player.getUniqueId(), crate.id(), openedAt);
            }
        }
        session.start();
        if (!preview) {
            applyCooldown(player, crate);
        }
        return true;
    }

    public void endSession(UUID playerId) {
        CrateSession session = sessions.remove(playerId);
        if (session != null) {
            session.end();
        }
        sessionRandoms.remove(playerId);
    }

    public void endPreview(UUID playerId) {
        CrateSession session = sessions.get(playerId);
        if (session != null && session.isPreview()) {
            endSession(playerId);
        }
    }

    public CrateSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
        sessionRandoms.remove(playerId);
    }

    private RewardSelector.RewardRollLogger buildRollLogger(Player player) {
        if (!configLoader.getMainConfig().getBoolean("debug.rolls", false)) {
            return null;
        }
        return (reward, roll, total) -> plugin.getLogger().info(String.format(
                "Roll debug player=%s rewardId=%s roll=%.4f chance=%.4f total=%.4f",
                player.getName(),
                reward.id(),
                roll,
                reward.chance(),
                total
        ));
    }

    private CutscenePath buildDefaultPath(Player player) {
        Location location = player.getLocation();
        List<CutscenePoint> points = List.of(
                new CutscenePoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()),
                new CutscenePoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch())
        );
        return new CutscenePath("default", 3.0, true, 0.15, "linear", "", points);
    }

    private CutscenePath resolveCutscenePath(CrateDefinition crate, Player player) {
        String pathId = crate.animation() != null ? crate.animation().path() : null;
        CutscenePath path = pathId != null ? configLoader.getPaths().get(pathId) : null;
        if (path == null) {
            return buildDefaultPath(player);
        }
        return path;
    }

    private RewardPool resolveRewardPool(CrateDefinition crate) {
        if (crate.rewardsPool() == null) {
            return null;
        }
        return configLoader.getRewardPools().get(crate.rewardsPool());
    }

    private boolean isOnCooldown(Player player, CrateDefinition crate) {
        return getCooldownRemainingSeconds(player, crate) > 0;
    }

    public long getCooldownRemainingSeconds(Player player, CrateDefinition crate) {
        if (crate.cooldownSeconds() <= 0) {
            return 0;
        }
        Instant last = getCooldownTimestamp(player, crate.id());
        if (last == null) {
            return 0;
        }
        Duration elapsed = Duration.between(last, Instant.now());
        long remaining = crate.cooldownSeconds() - elapsed.getSeconds();
        return Math.max(remaining, 0);
    }

    private Instant getCooldownTimestamp(Player player, String crateId) {
        Map<String, Instant> userCooldowns = cooldowns.get(player.getUniqueId());
        if (userCooldowns != null) {
            Instant cached = userCooldowns.get(crateId);
            if (cached != null) {
                return cached;
            }
        }
        if (storage == null) {
            return null;
        }
        Instant stored = storage.getCooldown(player.getUniqueId(), crateId).orElse(null);
        if (stored != null) {
            cooldowns.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).put(crateId, stored);
        }
        return stored;
    }

    private void applyCooldown(Player player, CrateDefinition crate) {
        applyCooldown(player, crate, Instant.now(), true);
    }

    private void applyCooldown(Player player, CrateDefinition crate, Instant timestamp, boolean record) {
        if (crate.cooldownSeconds() <= 0) {
            return;
        }
        Instant appliedAt = timestamp != null ? timestamp : Instant.now();
        cooldowns.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).put(crate.id(), appliedAt);
        if (storage != null) {
            storage.setCooldown(player.getUniqueId(), crate.id(), appliedAt);
        }
        if (record && syncBridge != null) {
            recordHistory(player.getUniqueId(), crate.id(), null, SyncEventType.COOLDOWN_SET, appliedAt);
            syncBridge.recordCooldown(player.getUniqueId(), crate.id(), appliedAt);
        } else if (record) {
            recordHistory(player.getUniqueId(), crate.id(), null, SyncEventType.COOLDOWN_SET, appliedAt);
        }
    }

    private boolean hasKey(Player player, CrateDefinition crate) {
        if (storageEnabled) {
            return storage.getKeyCount(player.getUniqueId(), crate.id()) > 0;
        }
        if (crate.keyModel() == null || crate.keyModel().isEmpty()) {
            return true;
        }
        int modelData = ResourcepackModelResolver.resolveCustomModelData(configLoader, crate.keyModel());
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
        consumeKey(player, crate, true);
    }

    private void consumeKey(Player player, CrateDefinition crate, boolean record) {
        if (storageEnabled) {
            boolean consumed = storage.consumeKey(player.getUniqueId(), crate.id());
            if (consumed && record && syncBridge != null) {
                Instant timestamp = Instant.now();
                recordHistory(player.getUniqueId(), crate.id(), null, SyncEventType.KEY_CONSUMED, timestamp);
                syncBridge.recordKeyConsumed(player.getUniqueId(), crate.id(), timestamp);
            } else if (consumed && record) {
                recordHistory(player.getUniqueId(), crate.id(), null, SyncEventType.KEY_CONSUMED, Instant.now());
            }
            return;
        }
        int modelData = ResourcepackModelResolver.resolveCustomModelData(configLoader, crate.keyModel());
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
                    Instant timestamp = Instant.now();
                    recordHistory(player.getUniqueId(), crate.id(), null, SyncEventType.KEY_CONSUMED, timestamp);
                    syncBridge.recordKeyConsumed(player.getUniqueId(), crate.id(), timestamp);
                } else if (record) {
                    recordHistory(player.getUniqueId(), crate.id(), null, SyncEventType.KEY_CONSUMED, Instant.now());
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

    public void applySpectator(Player player, NamespacedKey modifierKey, double modifierValue) {
        player.setGameMode(GameMode.SPECTATOR);
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute != null) {
            AttributeModifier modifier = new AttributeModifier(modifierKey.getKey(), modifierValue, AttributeModifier.Operation.ADD_NUMBER);
            attribute.addModifier(modifier);
        }
    }

    public void removeSpectatorModifier(Player player, NamespacedKey modifierKey) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.getModifiers().stream()
                    .filter(mod -> mod.getName().equals(modifierKey.getKey()))
                    .forEach(attribute::removeModifier);
        }
    }

    public void recordRewardGranted(Player player, CrateDefinition crate, Reward reward) {
        Instant timestamp = Instant.now();
        recordHistory(player.getUniqueId(), crate.id(), reward.id(), SyncEventType.REWARD_GRANTED, timestamp);
        if (syncBridge != null) {
            syncBridge.recordRewardGranted(player.getUniqueId(), crate.id(), reward.id(), timestamp);
        }
    }

    public void applyRemoteCooldown(UUID playerId, String crateId, Instant timestamp) {
        cooldowns.computeIfAbsent(playerId, key -> new HashMap<>()).put(crateId, timestamp);
        recordHistory(playerId, crateId, null, SyncEventType.COOLDOWN_SET, timestamp);
    }

    public void applyRemoteKeyConsumed(UUID playerId, String crateId, Instant timestamp) {
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        if (crate == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            consumeKey(player, crate, false);
        }
        recordHistory(playerId, crateId, null, SyncEventType.KEY_CONSUMED, timestamp);
    }

    public void applyRemoteOpen(UUID playerId, String crateId, Instant timestamp) {
        plugin.getLogger().info(() -> "[Sync] Apertura remota registrada " + playerId + " en crate " + crateId);
        recordHistory(playerId, crateId, null, SyncEventType.CRATE_OPEN, timestamp);
    }

    public void applyRemoteReward(UUID playerId, String crateId, String rewardId, Instant timestamp) {
        plugin.getLogger().info(() -> "[Sync] Recompensa remota registrada " + rewardId + " para " + playerId);
        recordHistory(playerId, crateId, rewardId, SyncEventType.REWARD_GRANTED, timestamp);
    }

    public void flushSyncCaches() {
        cooldowns.clear();
    }

    public List<CrateHistoryEntry> getHistory(UUID playerId, String crateId, int limit, int offset) {
        if (syncBridge != null && syncBridge.isHistoryAvailable()) {
            return syncBridge.getHistory(playerId, crateId, limit, offset);
        }
        return getLocalHistory(playerId, crateId, limit, offset);
    }

    private List<CrateHistoryEntry> getLocalHistory(UUID playerId, String crateId, int limit, int offset) {
        Deque<CrateHistoryEntry> entries = history.get(playerId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        int skipped = 0;
        List<CrateHistoryEntry> result = new java.util.ArrayList<>();
        for (CrateHistoryEntry entry : entries) {
            if (crateId != null && !crateId.isBlank() && !crateId.equalsIgnoreCase(entry.crateId())) {
                continue;
            }
            if (skipped < offset) {
                skipped++;
                continue;
            }
            result.add(entry);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private void recordHistory(UUID playerId, String crateId, String rewardId, SyncEventType type, Instant timestamp) {
        Deque<CrateHistoryEntry> entries = history.computeIfAbsent(playerId, key -> new ArrayDeque<>());
        String serverId = syncBridge != null && syncBridge.getSettings() != null ? syncBridge.getSettings().getServerId() : "local";
        entries.addFirst(new CrateHistoryEntry(type, playerId, crateId, rewardId, timestamp, serverId));
        while (entries.size() > HISTORY_LIMIT) {
            entries.removeLast();
        }
    }
}
