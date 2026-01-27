package com.extracrates.runtime.core;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.cutscene.CutscenePath;
import com.extracrates.cutscene.CutscenePoint;
import com.extracrates.economy.EconomyService;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.RarityDefinition;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.storage.CrateOpenEntry;
import com.extracrates.storage.CrateStorage;
import com.extracrates.storage.DeliveryStatus;
import com.extracrates.storage.LocalStorage;
import com.extracrates.storage.PendingReward;
import com.extracrates.storage.OpenHistoryFilter;
import com.extracrates.storage.SqlStorage;
import com.extracrates.storage.StorageFallback;
import com.extracrates.storage.StorageMigrationReport;
import com.extracrates.storage.StorageMigrator;
import com.extracrates.storage.StorageSettings;
import com.extracrates.storage.StorageTarget;
import com.extracrates.sync.SyncBridge;
import com.extracrates.sync.SyncEventType;
import com.extracrates.sync.SyncSettings;
import com.extracrates.sync.CrateHistoryEntry;
import com.extracrates.util.ItemUtil;
import com.extracrates.util.RaritySelector;
import com.extracrates.util.RewardSelector;
import com.extracrates.util.ResourcepackModelResolver;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private final NamespacedKey keyMarker;
    // Stores both preview and normal crate sessions. Preview sessions are marked in CrateSession.
    private final Map<UUID, CrateSession> sessions = new HashMap<>();
    private final Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();
    private final Map<UUID, Random> sessionRandoms = new HashMap<>();
    private final Map<UUID, Deque<CrateHistoryEntry>> history = new HashMap<>();
    private final Map<UUID, BukkitRunnable> cooldownTasks = new HashMap<>();
    private final Map<UUID, BossBar> cooldownBars = new HashMap<>();
    private final Map<UUID, Map<String, String>> pendingRewards = new HashMap<>();
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
        this.keyMarker = new NamespacedKey(plugin, "crate_key_id");
    }

    public void shutdown() {
        sessions.values().forEach(session -> {
            if (!session.isPreview() && !session.isRewardDelivered() && session.isWaitingForClaim()) {
                Reward reward = session.getActiveReward();
                if (reward != null) {
                    setPendingReward(session.getPlayer(), session.getCrate(), reward);
                }
            }
            session.end();
        });
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

    public int cleanupActiveSessions(String reason, boolean localMode) {
        List<CrateSession> activeSessions = new ArrayList<>(sessions.values());
        if (activeSessions.isEmpty()) {
            plugin.getLogger().info(() -> String.format(
                    "Cleanup de sesiones (%s): no hay sesiones activas%s.",
                    reason,
                    localMode ? " en modo local" : ""
            ));
            return 0;
        }
        plugin.getLogger().info(() -> String.format(
                "Cleanup de sesiones (%s): limpiando %d sesiones activas%s.",
                reason,
                activeSessions.size(),
                localMode ? " en modo local" : ""
        ));
        for (CrateSession session : activeSessions) {
            Player player = session.getPlayer();
            if (player != null) {
                plugin.getLogger().info(() -> String.format(
                        "Cleanup sesion: jugador=%s uuid=%s crate=%s",
                        player.getName(),
                        player.getUniqueId(),
                        session.getCrateId()
                ));
                clearCrateEffects(player);
                continue;
            }
            UUID playerId = session.getPlayerId();
            endSession(playerId);
            removeSession(playerId);
        }
        return activeSessions.size();
    }

    public boolean openCrate(Player player, CrateDefinition crate) {
        return openCrate(player, crate, false);
    }

    public boolean openCrate(Player player, CrateDefinition crate, boolean preview) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(languageManager.getMessage("session.already-in-progress"));
            return false;
        }
        if (!hasCratePermission(player, crate)) {
            player.sendMessage(languageManager.getMessage("session.no-permission", player, crate, null, null));
            return false;
        }
        if (isWorldBlocked(player, crate)) {
            player.sendMessage(languageManager.getMessage("session.world-blocked"));
            return false;
        }
        RewardPool rewardPool = resolveRewardPool(crate);
        if (rewardPool == null) {
            player.sendMessage(languageManager.getMessage("session.error.missing-reward-pool"));
            return false;
        }
        Random random = sessionRandoms.computeIfAbsent(player.getUniqueId(), key -> new Random());
        List<Reward> rewards = rollRewards(rewardPool, random, buildRollLogger(player));
        if (rewards.isEmpty()) {
            player.sendMessage(languageManager.getMessage("session.no-rewards"));
            return false;
        }
        CutscenePath path = resolveCutscenePath(crate, rewards.getFirst(), player);
        OpenState openState = null;
        if (!preview) {
            if (storage != null && !storage.acquireLock(player.getUniqueId(), crate.id())) {
                player.sendMessage(Component.text("Ya tienes una apertura en progreso."));
                return false;
            }
            Instant previousCooldown = getCooldownTimestamp(player, crate.id());
            openState = new OpenState(storage != null, false, false, previousCooldown);
        }
        CrateSession session = new CrateSession(plugin, configLoader, languageManager, player, crate, rewards, path, this, preview, openState);
        sessions.put(player.getUniqueId(), session);
        Instant createdAt = Instant.now();
        plugin.getLogger().info(() -> String.format(
                "Sesion creada: jugador=%s crate=%s timestamp=%s",
                player.getName(),
                crate.id(),
                createdAt
        ));
        if (!preview && storage != null) {
            storage.logOpenStarted(player.getUniqueId(), crate.id(), resolveServerId(), createdAt);
        }
        session.start();
        return true;
    }

    public boolean openCrateWithRarity(Player player, CrateDefinition crate, String rarityId) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(languageManager.getMessage("session.already-in-progress"));
            return false;
        }
        if (!hasCratePermission(player, crate)) {
            player.sendMessage(languageManager.getMessage("session.no-permission", player, crate, null, null));
            return false;
        }
        if (isWorldBlocked(player, crate)) {
            player.sendMessage(languageManager.getMessage("session.world-blocked"));
            return false;
        }
        RewardPool rewardPool = resolveRewardPool(crate);
        if (rewardPool == null) {
            player.sendMessage(languageManager.getMessage("session.error.missing-reward-pool"));
            return false;
        }
        Random random = sessionRandoms.computeIfAbsent(player.getUniqueId(), key -> new Random());
        List<Reward> rewards = rollRewardsByRarity(rewardPool, rarityId, random, buildRollLogger(player));
        if (rewards.isEmpty()) {
            player.sendMessage(languageManager.getMessage("session.no-rewards-rarity", java.util.Map.of("rarity", rarityId)));
            return false;
        }
        CutscenePath path = resolveCutscenePath(crate, rewards.getFirst(), player);
        OpenState openState = null;
        if (storage != null && !storage.acquireLock(player.getUniqueId(), crate.id())) {
            player.sendMessage(Component.text("Ya tienes una apertura en progreso."));
            return false;
        }
        Instant previousCooldown = getCooldownTimestamp(player, crate.id());
        openState = new OpenState(storage != null, false, false, previousCooldown);
        CrateSession session = new CrateSession(plugin, configLoader, languageManager, player, crate, rewards, path, this, false, openState);
        sessions.put(player.getUniqueId(), session);
        Instant createdAt = Instant.now();
        plugin.getLogger().info(() -> String.format(
                "Sesion creada (rarity): jugador=%s crate=%s rarity=%s timestamp=%s",
                player.getName(),
                crate.id(),
                rarityId,
                createdAt
        ));
        if (storage != null) {
            storage.logOpenStarted(player.getUniqueId(), crate.id(), resolveServerId(), createdAt);
        }
        session.start();
        return true;
    }

    public boolean hasCratePermission(Player player, CrateDefinition crate) {
        if (player == null || crate == null) {
            return false;
        }
        boolean usePerCrate = configLoader.getMainConfig().getBoolean("permissions.use-per-crate", true);
        if (!usePerCrate) {
            return true;
        }
        String permission = crate.permission();
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return player.hasPermission(permission);
    }

    private boolean isWorldBlocked(Player player, CrateDefinition crate) {
        String worldName = player.getWorld().getName();
        List<String> blockedWorlds = crate.blockedWorlds();
        if (blockedWorlds != null) {
            for (String blockedWorld : blockedWorlds) {
                if (blockedWorld.equalsIgnoreCase(worldName)) {
                    return true;
                }
            }
        }
        List<String> allowedWorlds = crate.allowedWorlds();
        if (allowedWorlds != null && !allowedWorlds.isEmpty()) {
            for (String allowedWorld : allowedWorlds) {
                if (allowedWorld.equalsIgnoreCase(worldName)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public void endSession(UUID playerId) {
        CrateSession session = sessions.get(playerId);
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
        List<Reward> rewards = rollRewards(rewardPool, random, buildRollLogger(player));
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

    public Collection<CrateSession> getSessions() {
        return List.copyOf(sessions.values());
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
        sessionRandoms.remove(playerId);
    }

    public void clearLocalState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        cooldowns.remove(playerId);
        pendingRewards.remove(playerId);
        history.remove(playerId);
        sessionRandoms.remove(playerId);
        clearCooldownDisplay(playerId);
    }

    public void clearStoredPlayerData(UUID playerId) {
        if (storage == null || playerId == null) {
            return;
        }
        storage.clearPlayerData(playerId);
    }

    public void handleSessionQuit(Player player, CrateSession session) {
        if (session == null || session.isPreview()) {
            return;
        }
        Reward reward = session.getActiveReward();
        if (reward != null) {
            setPendingReward(player, session.getCrate(), reward);
        }
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
        Location location = player.getEyeLocation();
        List<CutscenePoint> points = List.of(
                new CutscenePoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()),
                new CutscenePoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch())
        );
        return new CutscenePath(
                "default",
                3.0,
                true,
                0.15,
                "linear",
                "",
                com.extracrates.cutscene.CutsceneSpinSettings.disabled(),
                points,
                java.util.Set.of(),
                List.of(),
                List.of()
        );
    }

    private CutscenePath resolveCutscenePath(CrateDefinition crate, Reward reward, Player player) {
        CutscenePath rarityPath = resolveRarityPath(reward);
        if (rarityPath != null && !rarityPath.getPoints().isEmpty()) {
            return rarityPath;
        }
        String pathId = crate.animation() != null ? crate.animation().path() : null;
        CutscenePath path = pathId != null ? configLoader.getPaths().get(pathId) : null;
        if (path == null || path.getPoints().isEmpty()) {
            return buildDefaultPath(player);
        }
        return path;
    }

    private CutscenePath resolveRarityPath(Reward reward) {
        if (reward == null || reward.rarity() == null || reward.rarity().isBlank()) {
            return null;
        }
        RarityDefinition rarity = configLoader.getRarities().get(reward.rarity().toLowerCase(Locale.ROOT));
        if (rarity == null || rarity.path() == null || rarity.path().isBlank()) {
            return null;
        }
        return configLoader.getPaths().get(rarity.path());
    }

    private List<Reward> rollRewards(RewardPool pool, Random random, RewardSelector.RewardRollLogger logger) {
        if (pool == null || pool.rewards().isEmpty()) {
            return List.of();
        }
        List<Reward> results = new ArrayList<>();
        int rolls = Math.max(1, pool.rollCount());
        List<Reward> available = pool.preventDuplicateItems() ? new ArrayList<>(pool.rewards()) : null;
        for (int i = 0; i < rolls; i++) {
            List<Reward> candidates = available != null ? available : pool.rewards();
            if (candidates.isEmpty()) {
                break;
            }
            RarityDefinition rarity = configLoader.getRarities().isEmpty()
                    ? null
                    : RaritySelector.select(configLoader.getRarities(), random);
            List<Reward> rarityRewards = rarity == null ? List.of() : candidates.stream()
                    .filter(reward -> matchesRarity(reward, rarity.id()))
                    .toList();
            List<Reward> chosenPool = rarityRewards.isEmpty() ? candidates : rarityRewards;
            int index = random.nextInt(chosenPool.size());
            Reward reward = chosenPool.get(index);
            results.add(reward);
            if (available != null) {
                available.remove(reward);
            }
            if (logger != null) {
                logger.log(reward, index + 1, chosenPool.size());
            }
        }
        return results;
    }

    private List<Reward> rollRewardsByRarity(
            RewardPool pool,
            String rarityId,
            Random random,
            RewardSelector.RewardRollLogger logger
    ) {
        if (pool == null || pool.rewards().isEmpty() || rarityId == null || rarityId.isBlank()) {
            return List.of();
        }
        List<Reward> candidates = pool.rewards().stream()
                .filter(reward -> matchesRarity(reward, rarityId))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Reward> results = new ArrayList<>();
        int rolls = Math.max(1, pool.rollCount());
        List<Reward> available = pool.preventDuplicateItems() ? new ArrayList<>(candidates) : null;
        for (int i = 0; i < rolls; i++) {
            List<Reward> chosenPool = available != null ? available : candidates;
            if (chosenPool.isEmpty()) {
                break;
            }
            int index = random.nextInt(chosenPool.size());
            Reward reward = chosenPool.get(index);
            results.add(reward);
            if (available != null) {
                available.remove(reward);
            }
            if (logger != null) {
                logger.log(reward, index + 1, chosenPool.size());
            }
        }
        return results;
    }

    private boolean matchesRarity(Reward reward, String rarityId) {
        if (reward == null || rarityId == null) {
            return false;
        }
        String rewardRarity = reward.rarity();
        if (rewardRarity == null || rewardRarity.isBlank()) {
            return false;
        }
        return rewardRarity.equalsIgnoreCase(rarityId);
    }

    private RewardPool resolveRewardPool(CrateDefinition crate) {
        if (crate.rewardsPool() == null) {
            return null;
        }
        return configLoader.getRewardPools().get(crate.rewardsPool());
    }

    private String normalizeOpenMode(String openMode) {
        if (openMode == null || openMode.isBlank()) {
            return "reward-only";
        }
        return openMode.toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isOnCooldown(Player player, CrateDefinition crate) {
        return getCooldownRemainingSeconds(player, crate) > 0;
    }

    private boolean isQaMode() {
        return configLoader.getMainConfig().getBoolean("qa-mode", false);
    }

    private long getCooldownRemainingSeconds(Player player, String cooldownKey, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        Instant timestamp = getCooldownTimestamp(player, cooldownKey);
        if (timestamp == null) {
            return 0;
        }
        long elapsedSeconds = Duration.between(timestamp, Instant.now()).getSeconds();
        long remaining = cooldownSeconds - elapsedSeconds;
        return Math.max(0L, remaining);
    }

    public long getCooldownRemainingSeconds(Player player, CrateDefinition crate) {
        return getCooldownRemainingSeconds(player, crate.id(), crate.cooldownSeconds());
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
        applyCooldown(player, crate.id(), crate.cooldownSeconds(), now, true, crate.id());
    }

    private void applyCooldown(Player player, String cooldownKey, int cooldownSeconds, Instant timestamp, boolean record, String historyCrateId) {
        if (cooldownSeconds <= 0) {
            return;
        }
        Instant appliedAt = timestamp != null ? timestamp : Instant.now();
        cooldowns.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).put(cooldownKey, appliedAt);
        if (storage != null) {
            storage.setCooldown(player.getUniqueId(), cooldownKey, appliedAt);
        }
        if (record && syncBridge != null) {
            recordHistory(player.getUniqueId(), historyCrateId, null, SyncEventType.COOLDOWN_SET, appliedAt);
            syncBridge.recordCooldown(player.getUniqueId(), historyCrateId, appliedAt);
        } else if (record) {
            recordHistory(player.getUniqueId(), historyCrateId, null, SyncEventType.COOLDOWN_SET, appliedAt);
        }
    }

    private void restoreCooldown(UUID playerId, String crateId, Instant previous) {
        Map<String, Instant> userCooldowns = cooldowns.get(playerId);
        if (previous == null) {
            if (userCooldowns != null) {
                userCooldowns.remove(crateId);
            }
            if (storage != null) {
                storage.clearCooldown(playerId, crateId);
            }
            return;
        }
        cooldowns.computeIfAbsent(playerId, key -> new HashMap<>()).put(crateId, previous);
        if (storage != null) {
            storage.setCooldown(playerId, crateId, previous);
        }
    }

    private boolean hasKey(Player player, CrateDefinition crate) {
        return Arrays.stream(player.getInventory().getContents())
                .anyMatch(item -> isKeyItem(item, crate));
    }

    private boolean consumeKey(Player player, CrateDefinition crate) {
        return consumeKey(player, crate, true);
    }

    private boolean consumeKey(Player player, CrateDefinition crate, boolean record) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isKeyItem(item, crate)) {
                continue;
            }
            if (item != null) {
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
                return true;
            }
        }
        return false;
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

    public void applySpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void clearCrateEffects(Player player) {
        if (player == null) {
            return;
        }
        endSession(player.getUniqueId());
        removeSession(player.getUniqueId());
        if (storage != null) {
            for (String crateId : configLoader.getCrates().keySet()) {
                storage.releaseLock(player.getUniqueId(), crateId);
            }
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
        }
        toggleHud(player, false);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void toggleHud(Player player, boolean hidden) {
        try {
            String methodName = hidden ? "hideHud" : "showHud";
            java.lang.reflect.Method method = player.getClass().getMethod(methodName);
            method.invoke(player);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public void recordRewardGranted(Player player, CrateDefinition crate, Reward reward) {
        Instant timestamp = Instant.now();
        recordHistory(player.getUniqueId(), crate.id(), reward.id(), SyncEventType.REWARD_GRANTED, timestamp);
        if (syncBridge != null) {
            syncBridge.recordRewardGranted(player.getUniqueId(), crate.id(), reward.id(), timestamp);
        }
    }

    public void completeOpen(Player player, CrateDefinition crate, Reward reward, OpenState openState) {
        if (storage != null) {
            storage.logOpen(player.getUniqueId(), crate.id(), reward.id(), resolveServerId(), Instant.now());
            storage.recordDelivery(player.getUniqueId(), crate.id(), reward.id(), DeliveryStatus.COMPLETED, 1, Instant.now());
            storage.markRewardDelivered(player.getUniqueId(), crate.id(), reward.id());
            if (openState != null && openState.isLockAcquired() && !openState.isLockReleased()) {
                storage.releaseLock(player.getUniqueId(), crate.id());
                openState.markLockReleased();
            }
        }
        recordRewardGranted(player, crate, reward);
    }

    public void logRewardConfirmation(Player player, CrateDefinition crate, Reward reward, int rerollsUsed) {
        if (player == null || crate == null || reward == null) {
            return;
        }
        Instant timestamp = Instant.now();
        plugin.getLogger().info(() -> String.format(
                "Recompensa confirmada: jugador=%s crate=%s reward=%s rerolls=%d timestamp=%s",
                player.getName(),
                crate.id(),
                reward.id(),
                rerollsUsed,
                timestamp
        ));
    }

    public void handleSessionEnd(CrateSession session) {
        if (session == null || session.isPreview()) {
            return;
        }
        OpenState openState = session.getOpenState();
        if (openState == null) {
            return;
        }
        if (session.isRewardDelivered()) {
            if (storage != null && openState.isLockAcquired() && !openState.isLockReleased()) {
                storage.releaseLock(session.getPlayerId(), session.getCrateId());
                openState.markLockReleased();
            }
            return;
        }
        Reward reward = session.getActiveReward();
        if (reward != null) {
            setPendingReward(session.getPlayer(), session.getCrate(), reward);
        }
        if (openState.isCooldownApplied()) {
            restoreCooldown(session.getPlayerId(), session.getCrateId(), openState.getPreviousCooldown());
        }
        if (openState.isKeyConsumed()) {
            restoreKey(session.getPlayer(), session.getCrate());
        }
        if (storage != null && openState.isLockAcquired() && !openState.isLockReleased()) {
            storage.releaseLock(session.getPlayerId(), session.getCrateId());
            openState.markLockReleased();
        }
    }

    private void restoreKey(Player player, CrateDefinition crate) {
        ItemStack key = buildKeyItem(crate);
        Map<Integer, ItemStack> remaining = player.getInventory().addItem(key);
        if (!remaining.isEmpty()) {
            remaining.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    private String resolveServerId() {
        if (syncBridge != null && syncBridge.getSettings() != null) {
            return syncBridge.getSettings().getServerId();
        }
        String serverId = configLoader.getMainConfig().getString("sync.server-id", "local");
        return serverId != null ? serverId : "local";
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

    public StorageMigrationReport migrateStorage(StorageTarget target) {
        StorageSettings settings = StorageSettings.fromConfig(configLoader.getMainConfig());
        StorageMigrator migrator = new StorageMigrator();
        return migrator.migrate(storage, settings, target, plugin.getLogger());
    }

    public boolean isStorageEnabled() {
        return storageEnabled;
    }

    public CrateStorage getStorage() {
        return storage;
    }

    public List<CrateOpenEntry> getOpenHistory(UUID playerId, OpenHistoryFilter filter, int limit, int offset) {
        if (storage == null) {
            return List.of();
        }
        return storage.getOpenHistory(playerId, filter, limit, offset);
    }

    public List<CrateHistoryEntry> getHistory(UUID playerId, String crateId, int limit, int offset) {
        if (syncBridge != null && syncBridge.isHistoryAvailable()) {
            return syncBridge.getHistory(playerId, crateId, limit, offset);
        }
        Deque<CrateHistoryEntry> entries = history.get(playerId);
        if (entries == null || entries.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<CrateHistoryEntry> filtered = new java.util.ArrayList<>();
        int skipped = 0;
        for (CrateHistoryEntry entry : entries) {
            if (crateId != null && !crateId.isEmpty() && !crateId.equalsIgnoreCase(entry.crateId())) {
                continue;
            }
            if (skipped < offset) {
                skipped++;
                continue;
            }
            filtered.add(entry);
            if (filtered.size() >= limit) {
                break;
            }
        }
        return filtered;
    }

    public void grantKey(Player player, CrateDefinition crate, int amount) {
        if (amount <= 0 || player == null || crate == null) {
            return;
        }
        ItemStack key = buildKeyItem(crate);
        for (int i = 0; i < amount; i++) {
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(key.clone());
            if (!remaining.isEmpty()) {
                remaining.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }
    }

    public void updatePendingReward(Player player, CrateDefinition crate, Reward reward) {
        if (player == null || crate == null || reward == null) {
            return;
        }
        if (storage != null) {
            storage.setPendingReward(player.getUniqueId(), crate.id(), reward.id());
        }
        pendingRewards.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).put(crate.id(), reward.id());
    }

    private void setPendingReward(Player player, CrateDefinition crate, Reward reward) {
        if (player == null || crate == null || reward == null) {
            return;
        }
        if (storage != null) {
            storage.setPendingReward(player.getUniqueId(), crate.id(), reward.id());
        }
        pendingRewards.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).put(crate.id(), reward.id());
    }

    public void recordDeliveryStarted(Player player, CrateDefinition crate, Reward reward, int attempt) {
        if (storage == null || player == null || crate == null || reward == null) {
            return;
        }
        storage.recordDelivery(player.getUniqueId(), crate.id(), reward.id(), DeliveryStatus.STARTED, attempt, Instant.now());
    }

    public void recordDeliveryPending(Player player, CrateDefinition crate, Reward reward, int attempt) {
        if (storage == null || player == null || crate == null || reward == null) {
            return;
        }
        storage.recordDelivery(player.getUniqueId(), crate.id(), reward.id(), DeliveryStatus.PENDING, attempt, Instant.now());
        storage.setPendingReward(player.getUniqueId(), crate.id(), reward.id());
        pendingRewards.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).put(crate.id(), reward.id());
    }

    private void recordHistory(UUID playerId, String crateId, String rewardId, SyncEventType type, Instant timestamp) {
        if (playerId == null || crateId == null || type == null) {
            return;
        }
        Deque<CrateHistoryEntry> entries = history.computeIfAbsent(playerId, key -> new ArrayDeque<>());
        entries.addFirst(new CrateHistoryEntry(type, playerId, crateId, rewardId, timestamp, serverId));
        while (entries.size() > HISTORY_LIMIT) {
            entries.removeLast();
        }
    }

    private boolean chargeRerollCost(Player player, CrateDefinition crate) {
        if (player == null || crate == null) {
            return false;
        }
        double cost = crate.rerollCost();
        if (cost <= 0) {
            return true;
        }
        if (!economyService.isAvailable()) {
            return true;
        }
        if (!economyService.hasBalance(player, cost)) {
            player.sendMessage(Component.text("No tienes suficiente dinero para reroll."));
            return false;
        }
        return economyService.withdraw(player, cost).transactionSuccess();
    }

    private void logVerbose(String message, Object... args) {
        if (!plugin.getConfig().getBoolean("debug.verbose", false)) {
            return;
        }
        plugin.getLogger().info(String.format(message, args));
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

    private ItemStack buildKeyItem(CrateDefinition crate) {
        ItemStack key = new ItemStack(crate.keyMaterial());
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            String keyName = languageManager.getRaw(
                    "command.key-item-name",
                    java.util.Map.of("crate_name", crate.displayName())
            );
            meta.displayName(TextUtil.colorNoItalic(keyName));
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keyMarker, PersistentDataType.STRING, crate.id());
            int modelData = ResourcepackModelResolver.resolveCustomModelData(configLoader, crate.keyModel());
            if (modelData >= 0) {
                meta.setCustomModelData(modelData);
            }
            key.setItemMeta(meta);
        }
        return key;
    }

    public boolean isKeyItem(ItemStack item, CrateDefinition crate) {
        if (item == null || crate == null || item.getType() != crate.keyMaterial()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String stored = container.get(keyMarker, PersistentDataType.STRING);
        return stored != null && stored.equalsIgnoreCase(crate.id());
    }
}
