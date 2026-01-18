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
import com.extracrates.storage.DeliveryStatus;
import com.extracrates.storage.LocalStorage;
import com.extracrates.storage.SqlStorage;
import com.extracrates.storage.StorageFallback;
import com.extracrates.storage.StorageSettings;
import com.extracrates.sync.SyncBridge;
import com.extracrates.util.RewardSelector;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.SoundUtil;
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
import java.util.ArrayList;
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
    private final Map<UUID, Map<String, String>> pendingRewards = new HashMap<>();

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
        recentOpens.clear();
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
            SoundUtil.play(player, configLoader.getSettings().getSounds().error());
            return false;
        }
        CutscenePath path = resolveCutscenePath(crate, player);
        RewardPool rewardPool = resolveRewardPool(crate);
        if (rewardPool == null) {
            player.sendMessage(Component.text("No se encontró el pool de recompensas para esta crate."));
            SoundUtil.play(player, configLoader.getSettings().getSounds().error());
            return false;
        }
        if (!preview && crate.type() == com.extracrates.model.CrateType.KEYED && !hasKey(player, crate)) {
            player.sendMessage(Component.text("Necesitas una llave para esta crate."));
            SoundUtil.play(player, configLoader.getSettings().getSounds().error());
            return false;
        }
        if (!preview && isOnCooldown(player, crate)) {
            player.sendMessage(Component.text("Esta crate está en cooldown."));
            SoundUtil.play(player, configLoader.getSettings().getSounds().error());
            return false;
        }
        Random random = sessionRandoms.computeIfAbsent(player.getUniqueId(), key -> new Random());
        List<Reward> rewards = RewardSelector.roll(
                rewardPool,
                random,
                buildRollLogger(player),
                buildRewardSelectorSettings()
        );
        if (rewards.isEmpty()) {
            player.sendMessage(languageManager.getMessage("session.no-rewards"));
            SoundUtil.play(player, configLoader.getSettings().getSounds().error());
            return false;
        }
        CrateSession session = new CrateSession(
                plugin,
                configLoader,
                languageManager,
                player,
                crate,
                new ArrayList<>(sessionRewards),
                path,
                this,
                preview
        );
        sessions.put(player.getUniqueId(), session);
        if (!preview && crate.type() == com.extracrates.model.CrateType.KEYED) {
            consumeKey(player, crate);
        }
        logVerbose("Sesion iniciada: jugador=%s crate=%s preview=%s rewards=%d", player.getName(), crate.id(), preview, rewards.size());
        session.start();
        if (preview) {
            SoundUtil.play(player, configLoader.getSettings().getSounds().preview());
        }
        if (!preview) {
            maybeShowFirstOpenGuide(player);
        }
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
            syncBridge.recordCooldown(player.getUniqueId(), cooldownKey);
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
                syncBridge.recordKeyConsumed(player.getUniqueId(), crate.id());
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
                    syncBridge.recordKeyConsumed(player.getUniqueId(), crate.id());
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
        clearPendingReward(player.getUniqueId(), crate.id());
        if (syncBridge != null) {
            String rewardId = resolveHistoryRewardId(crate, reward);
            syncBridge.recordRewardGranted(player.getUniqueId(), crate.id(), rewardId);
        }
    }

    public void recordPendingReward(UUID playerId, String crateId, String rewardId) {
        if (rewardId == null || rewardId.isBlank()) {
            return;
        }
        pendingRewards.computeIfAbsent(playerId, key -> new HashMap<>()).put(crateId, rewardId);
        if (syncBridge != null) {
            syncBridge.recordPendingReward(playerId, crateId, rewardId);
        }
    }

    public void clearPendingReward(UUID playerId, String crateId) {
        Map<String, String> playerPending = pendingRewards.get(playerId);
        if (playerPending == null) {
            return;
        }
        playerPending.remove(crateId);
        if (playerPending.isEmpty()) {
            pendingRewards.remove(playerId);
        }
    }

    public void recordDeliveryStarted(Player player, CrateDefinition crate, Reward reward, int attempt) {
        recordDeliveryStatus(player, crate, reward, DeliveryStatus.STARTED, attempt);
    }

    public void recordDeliveryCompleted(Player player, CrateDefinition crate, Reward reward, int attempt) {
        recordDeliveryStatus(player, crate, reward, DeliveryStatus.COMPLETED, attempt);
    }

    public void recordDeliveryPending(Player player, CrateDefinition crate, Reward reward, int attempt) {
        recordDeliveryStatus(player, crate, reward, DeliveryStatus.PENDING, attempt);
    }

    private void recordDeliveryStatus(Player player, CrateDefinition crate, Reward reward, DeliveryStatus status, int attempt) {
        if (storage == null || reward == null || crate == null || player == null) {
            return;
        }
        storage.recordDelivery(player.getUniqueId(), crate.id(), reward.id(), status, attempt, Instant.now());
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
        clearPendingReward(playerId, crateId);
        plugin.getLogger().info(() -> "[Sync] Recompensa remota registrada " + rewardId + " para " + playerId);
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
}
