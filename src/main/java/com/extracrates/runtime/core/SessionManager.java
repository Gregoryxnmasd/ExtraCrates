package com.extracrates.runtime.core;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.cutscene.CutscenePoint;
import com.extracrates.economy.EconomyService;
import com.extracrates.event.CrateOpenEvent;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.storage.CrateStorage;
import com.extracrates.storage.CrateOpenEntry;
import com.extracrates.storage.LocalStorage;
import com.extracrates.storage.OpenHistoryFilter;
import com.extracrates.storage.SqlStorage;
import com.extracrates.storage.StorageFallback;
import com.extracrates.storage.StorageSettings;
import com.extracrates.sync.CrateHistoryEntry;
import com.extracrates.sync.SyncBridge;
import com.extracrates.sync.SyncEventType;
import com.extracrates.util.RewardSelector;
import com.extracrates.util.ResourcepackModelResolver;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class SessionManager {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final EconomyService economyService;
    private final LanguageManager languageManager;
    private final CrateStorage storage;
    private final SyncBridge syncBridge;
    private final boolean storageEnabled;
    private final String serverId;
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
        this.serverId = SyncSettings.fromConfig(configLoader.getMainConfig()).getServerId();
    }

    public void shutdown() {
        sessions.values().forEach(CrateSession::end);
        sessions.clear();
        sessionRandoms.clear();
        cooldownTasks.values().forEach(BukkitRunnable::cancel);
        cooldownTasks.clear();
        cooldownBars.clear();
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
        if (!configLoader.isConfigValid()) {
            player.sendMessage(Component.text("La configuración es inválida. Revisa el reporte de validación."));
            return false;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(languageManager.getMessage("session.already-in-progress"));
            return false;
        }
        CutscenePath path = resolveCutscenePath(crate, player);
        RewardPool rewardPool = resolveRewardPool(crate);
        if (rewardPool == null) {
            player.sendMessage(languageManager.getMessage("session.error.missing-reward-pool"));
            return false;
        }
        if (!preview && crate.type() == com.extracrates.model.CrateType.KEYED && !hasKey(player, crate)) {
            player.sendMessage(languageManager.getMessage("session.key-required"));
            return false;
        }
        if (!preview && isOnCooldown(player, crate)) {
            long remainingSeconds = getCooldownRemainingSeconds(player, crate);
            Map<String, String> placeholders = Map.of("seconds", String.valueOf(remainingSeconds));
            player.sendMessage(languageManager.getMessage("session.cooldown", placeholders));
            languageManager.sendActionBar(player, "session.cooldown-actionbar", placeholders);
            showCooldownBossBar(player, crate, remainingSeconds);
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
            logVerbose("Sesion finalizada: jugador=%s", playerId);
        }
        sessionRandoms.remove(playerId);
        clearCooldownDisplay(playerId);
    }

    public void endPreview(UUID playerId) {
        CrateSession session = sessions.get(playerId);
        if (session != null && session.isPreview()) {
            endSession(playerId);
        }
    }

    public boolean rerollSession(Player player) {
        CrateSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(languageManager.getMessage("session.no-active"));
            return false;
        }
        CrateDefinition crate = session.getCrate();
        if (!chargeRerollCost(player, crate)) {
            return false;
        }
        RewardPool rewardPool = resolveRewardPool(crate);
        if (rewardPool == null) {
            player.sendMessage(Component.text("No se encontró el pool de recompensas para esta crate."));
            return false;
        }
        Random random = sessionRandoms.computeIfAbsent(player.getUniqueId(), key -> new Random());
        List<Reward> rewards = RewardSelector.roll(rewardPool, random, buildRollLogger(player));
        if (rewards.isEmpty()) {
            player.sendMessage(languageManager.getMessage("session.no-rewards"));
            return false;
        }
        session.reroll(rewards);
        return true;
    }

    public CrateSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
        sessionRandoms.remove(playerId);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public int getActivePreviewCount() {
        return (int) sessions.values().stream().filter(CrateSession::isPreview).count();
    }

    public int getPendingRewardCount() {
        return sessions.values().stream()
                .filter(session -> !session.isPreview())
                .mapToInt(CrateSession::getPendingRewardCount)
                .sum();
    }

    public StorageStatus getStorageStatus() {
        String backend = "local";
        boolean fallbackActive = false;
        if (storage instanceof StorageFallback fallback) {
            backend = "sql";
            fallbackActive = fallback.isUsingFallback();
        } else if (storage instanceof LocalStorage) {
            backend = "local";
        } else if (storage != null) {
            backend = storage.getClass().getSimpleName();
        }
        return new StorageStatus(storageEnabled, backend, fallbackActive);
    }

    public SyncBridge getSyncBridge() {
        return syncBridge;
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

    private RewardSelector.RewardSelectorSettings buildRewardSelectorSettings() {
        boolean normalizeChances = configLoader.getMainConfig().getBoolean("rewards.normalize-chances", false);
        double warningThreshold = configLoader.getMainConfig().getDouble("rewards.warning-threshold", 0);
        RewardSelector.RewardWarningLogger warningLogger = (pool, reward, threshold) -> plugin.getLogger().warning(
                String.format(
                        "Reward chance exceeds threshold pool=%s rewardId=%s chance=%.4f threshold=%.4f",
                        pool.id(),
                        reward.id(),
                        reward.chance(),
                        threshold
                )
        );
        if (warningThreshold <= 0) {
            warningLogger = null;
        }
        return new RewardSelector.RewardSelectorSettings(normalizeChances, warningThreshold, warningLogger);
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

    private boolean chargeRerollCost(Player player, CrateDefinition crate) {
        double rerollCost = crate.rerollCost();
        if (rerollCost <= 0) {
            return true;
        }
        if (!economyService.isAvailable()) {
            return true;
        }
        if (!economyService.hasBalance(player, rerollCost)) {
            player.sendMessage(languageManager.getMessage("session.reroll-no-balance", Map.of(
                    "amount", economyService.format(rerollCost)
            )));
            return false;
        }
        EconomyResponse response = economyService.withdraw(player, rerollCost);
        return response.type == EconomyResponse.ResponseType.SUCCESS;
    }

    private boolean isOnCooldown(Player player, CrateDefinition crate) {
        return getCooldownRemainingSeconds(player, crate) > 0;
    }

    public long getCooldownRemainingSeconds(Player player, CrateDefinition crate) {
        long crateRemaining = getCooldownRemainingSeconds(player, crate.id(), crate.cooldownSeconds());
        long typeRemaining = getCooldownRemainingSeconds(player, typeCooldownKey(crate.type()), getTypeCooldownSeconds(crate.type()));
        return Math.max(crateRemaining, typeRemaining);
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
        Instant now = Instant.now();
        applyCooldown(player, crate.id(), crate.cooldownSeconds(), now, true);
        applyCooldown(player, typeCooldownKey(crate.type()), getTypeCooldownSeconds(crate.type()), now, true);
    }

    private void applyCooldown(Player player, String cooldownKey, int cooldownSeconds, Instant timestamp, boolean record) {
        if (cooldownSeconds <= 0) {
            return;
        }
        Instant appliedAt = timestamp != null ? timestamp : Instant.now();
        cooldowns.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).put(cooldownKey, appliedAt);
        if (storage != null) {
            storage.setCooldown(player.getUniqueId(), cooldownKey, appliedAt);
        }
        if (record && syncBridge != null) {
            recordHistory(player.getUniqueId(), crate.id(), null, SyncEventType.COOLDOWN_SET, appliedAt);
            syncBridge.recordCooldown(player.getUniqueId(), crate.id(), appliedAt);
        } else if (record) {
            recordHistory(player.getUniqueId(), crate.id(), null, SyncEventType.COOLDOWN_SET, appliedAt);
        }
    }

    private long getCooldownRemainingSeconds(Player player, String cooldownKey, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        Instant last = getCooldownTimestamp(player, cooldownKey);
        if (last == null) {
            return 0;
        }
        Duration elapsed = Duration.between(last, Instant.now());
        long remaining = cooldownSeconds - elapsed.getSeconds();
        return Math.max(remaining, 0);
    }

    private int getTypeCooldownSeconds(com.extracrates.model.CrateType type) {
        String key = "cooldown-global." + type.name().toLowerCase();
        return configLoader.getMainConfig().getInt(key, 0);
    }

    private String typeCooldownKey(com.extracrates.model.CrateType type) {
        return "type:" + type.name().toLowerCase();
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

    public List<CrateOpenEntry> getOpenHistory(UUID playerId, OpenHistoryFilter filter, int limit, int offset) {
        if (storage == null) {
            return List.of();
        }
        return storage.getOpenHistory(playerId, filter, limit, offset);
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

    public void applyRemotePendingReward(UUID playerId, String crateId, String rewardId) {
        if (rewardId == null || rewardId.isBlank()) {
            return;
        }
        pendingRewards.computeIfAbsent(playerId, key -> new HashMap<>()).put(crateId, rewardId);
        plugin.getLogger().info(() -> "[Sync] Recompensa pendiente registrada " + rewardId + " para " + playerId);
    }

    public void flushSyncCaches() {
        cooldowns.clear();
        pendingRewards.clear();
    }

    private boolean hasPendingReward(UUID playerId, String crateId) {
        Map<String, String> playerPending = pendingRewards.get(playerId);
        if (playerPending == null) {
            return false;
        }
        return playerPending.containsKey(crateId);
    }

    private void maybeShowFirstOpenGuide(Player player) {
        if (!configLoader.getMainConfig().getBoolean("guide.enabled", true)) {
            return;
        }
        if (storage == null) {
            return;
        }
        if (storage.markFirstOpen(player.getUniqueId())) {
            FirstOpenGuide.start(plugin, configLoader, languageManager, player);
        }
    }

    public boolean isStorageEnabled() {
        return storageEnabled;
    }

    public CrateStorage getStorage() {
        return storage;
    }

    private void showCooldownBossBar(Player player, CrateDefinition crate, long remainingSeconds) {
        if (remainingSeconds <= 0 || crate.cooldownSeconds() <= 0) {
            return;
        }
        UUID playerId = player.getUniqueId();
        clearCooldownDisplay(playerId);
        float progress = Math.max(0.0f, Math.min(1.0f, remainingSeconds / (float) crate.cooldownSeconds()));
        Map<String, String> placeholders = Map.of("seconds", String.valueOf(remainingSeconds));
        BossBar bossBar = languageManager.createBossBar(
                "session.cooldown-bossbar",
                progress,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS,
                placeholders
        );
        player.showBossBar(bossBar);
        BukkitRunnable task = new BukkitRunnable() {
            long remaining = remainingSeconds;

            @Override
            public void run() {
                if (remaining <= 0 || !player.isOnline()) {
                    player.hideBossBar(bossBar);
                    clearCooldownDisplay(playerId);
                    cancel();
                    return;
                }
                float updatedProgress = Math.max(0.0f, Math.min(1.0f, remaining / (float) crate.cooldownSeconds()));
                Map<String, String> updatedPlaceholders = Map.of("seconds", String.valueOf(remaining));
                bossBar.name(languageManager.getMessage("session.cooldown-bossbar", updatedPlaceholders));
                bossBar.progress(updatedProgress);
                remaining--;
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
        cooldownBars.put(playerId, bossBar);
        cooldownTasks.put(playerId, task);
    }

    private void clearCooldownDisplay(UUID playerId) {
        BossBar bossBar = cooldownBars.remove(playerId);
        if (bossBar != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.hideBossBar(bossBar);
            }
        }
        BukkitRunnable task = cooldownTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
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
