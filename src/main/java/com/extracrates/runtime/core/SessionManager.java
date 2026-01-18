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
import com.extracrates.storage.LocalStorage;
import com.extracrates.storage.SqlStorage;
import com.extracrates.storage.StorageFallback;
import com.extracrates.storage.StorageSettings;
import com.extracrates.sync.SyncBridge;
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
    private final Map<UUID, Deque<Instant>> recentOpens = new HashMap<>();

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
            logVerbose("Bloqueado: jugador=%s crate=%s preview=%s (sesion activa)", player.getName(), crate.id(), preview);
            return false;
        }
        CutscenePath path = resolveCutscenePath(crate, player);
        RewardPool rewardPool = resolveRewardPool(crate);
        if (rewardPool == null) {
            player.sendMessage(Component.text("No se encontró el pool de recompensas para esta crate."));
            logVerbose("Fallido: jugador=%s crate=%s preview=%s (pool nulo)", player.getName(), crate.id(), preview);
            return false;
        }
        if (!preview && crate.type() == com.extracrates.model.CrateType.KEYED && !hasKey(player, crate)) {
            player.sendMessage(Component.text("Necesitas una llave para esta crate."));
            logVerbose("Fallido: jugador=%s crate=%s (sin llave)", player.getName(), crate.id());
            return false;
        }
        if (!preview && isOnCooldown(player, crate)) {
            long remaining = getCooldownRemainingSeconds(player, crate);
            player.sendMessage(Component.text("Esta crate está en cooldown. Restante: " + remaining + "s"));
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
            logVerbose("Fallido: jugador=%s crate=%s (sin rewards)", player.getName(), crate.id());
            return false;
        }
        CrateOpenEvent openEvent = new CrateOpenEvent(player, crate, rewards, preview);
        Bukkit.getPluginManager().callEvent(openEvent);
        if (openEvent.isCancelled()) {
            return false;
        }
        List<Reward> sessionRewards = openEvent.getRewards();
        if (sessionRewards == null || sessionRewards.isEmpty()) {
            player.sendMessage(languageManager.getMessage("session.no-rewards"));
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
        if (!preview) {
            recordOpen(player);
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
        if (syncBridge != null) {
            String rewardId = resolveHistoryRewardId(crate, reward);
            syncBridge.recordRewardGranted(player.getUniqueId(), crate.id(), rewardId);
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

    private String resolveHistoryRewardId(CrateDefinition crate, Reward reward) {
        if (reward == null) {
            plugin.getLogger().warning("Recompensa nula al registrar historial para crate " + crate.id() + ". Guardando unknown.");
            return "unknown";
        }
        RewardPool pool = resolveRewardPool(crate);
        if (pool == null) {
            plugin.getLogger().warning("Pool de recompensas no encontrado para crate " + crate.id() + ". Guardando unknown.");
            return "unknown";
        }
        String rewardId = reward.id();
        boolean exists = pool.rewards().stream().anyMatch(entry -> entry.id().equalsIgnoreCase(rewardId));
        if (!exists) {
            plugin.getLogger().warning("Recompensa '" + rewardId + "' no existe en pool '" + pool.id() + "'. Guardando unknown.");
            return "unknown";
        }
        return rewardId;
    }
}
