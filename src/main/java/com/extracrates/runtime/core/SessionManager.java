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
import com.extracrates.sync.SyncBridge;
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
import java.util.Arrays;
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
    private final Map<UUID, BossBar> cooldownBars = new HashMap<>();
    private final Map<UUID, BukkitRunnable> cooldownTasks = new HashMap<>();

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
        clearCooldownDisplay(playerId);
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
            syncBridge.recordCooldown(player.getUniqueId(), crate.id());
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
            syncBridge.recordRewardGranted(player.getUniqueId(), crate.id(), reward.id());
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
}
