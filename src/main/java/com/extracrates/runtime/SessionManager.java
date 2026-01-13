package com.extracrates.runtime;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.CutscenePath;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.util.RewardSelector;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class SessionManager {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final Economy economy;
    private final Map<UUID, CrateSession> sessions = new HashMap<>();
    private final Map<UUID, Map<String, Instant>> cooldowns = new HashMap<>();

    public SessionManager(ExtraCratesPlugin plugin, ConfigLoader configLoader, Economy economy) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.economy = economy;
    }

    public void shutdown() {
        sessions.values().forEach(CrateSession::end);
        sessions.clear();
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
        if (crate.getType() == com.extracrates.model.CrateType.KEYED && !hasKey(player, crate)) {
            player.sendMessage(Component.text("Necesitas una llave para esta crate."));
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
        if (!chargePlayer(player, crate)) {
            return false;
        }
        Reward reward = rewards.get(0);
        CutscenePath path = configLoader.getPaths().get(crate.getAnimation().getPath());
        CrateSession session = new CrateSession(plugin, configLoader, player, crate, reward, path, this);
        sessions.put(player.getUniqueId(), session);
        if (crate.getType() == com.extracrates.model.CrateType.KEYED) {
            consumeKey(player, crate);
        }
        session.start();
        applyCooldown(player, crate);
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

    public void endSession(UUID playerId) {
        CrateSession session = sessions.remove(playerId);
        if (session != null) {
            session.end();
        }
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    private boolean isOnCooldown(Player player, CrateDefinition crate) {
        if (crate.getCooldownSeconds() <= 0) {
            return false;
        }
        Map<String, Instant> userCooldowns = cooldowns.get(player.getUniqueId());
        if (userCooldowns == null) {
            return false;
        }
        Instant last = userCooldowns.get(crate.getId());
        if (last == null) {
            return false;
        }
        Duration elapsed = Duration.between(last, Instant.now());
        return elapsed.getSeconds() < crate.getCooldownSeconds();
    }

    private void applyCooldown(Player player, CrateDefinition crate) {
        if (crate.getCooldownSeconds() <= 0) {
            return;
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).put(crate.getId(), Instant.now());
    }

    private boolean hasKey(Player player, CrateDefinition crate) {
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
                return;
            }
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
}
