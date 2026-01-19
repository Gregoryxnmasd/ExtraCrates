package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RewardEditorMenu implements Listener {
    private static final int DEFAULT_INT_FALLBACK = 1;
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final EditorInputManager inputManager;
    private final ConfirmationMenu confirmationMenu;
    private final EditorMenu parent;
    private final Component poolTitle;
    private final Map<UUID, String> activePool = new HashMap<>();
    private final Map<UUID, String> activeReward = new HashMap<>();

    public RewardEditorMenu(
            ExtraCratesPlugin plugin,
            ConfigLoader configLoader,
            EditorInputManager inputManager,
            ConfirmationMenu confirmationMenu,
            EditorMenu parent
    ) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.languageManager = plugin.getLanguageManager();
        this.inputManager = inputManager;
        this.confirmationMenu = confirmationMenu;
        this.parent = parent;
        this.poolTitle = TextUtil.color(text("editor.rewards.title"));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openPools(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, poolTitle);
        List<RewardPool> pools = new ArrayList<>(configLoader.getRewardPools().values());
        pools.sort(Comparator.comparing(RewardPool::id));
        int slot = 0;
        for (RewardPool pool : pools) {
            inventory.setItem(slot++, buildPoolItem(pool));
            if (slot >= 45) {
                break;
            }
        }
        inventory.setItem(45, buildItem(
                Material.LIME_CONCRETE,
                text("editor.rewards.list.create.name"),
                List.of(text("editor.rewards.list.create.lore"))
        ));
        inventory.setItem(49, buildItem(
                Material.ARROW,
                text("editor.rewards.list.back.name"),
                List.of(text("editor.rewards.list.back.lore"))
        ));
        inventory.setItem(53, buildItem(
                Material.BOOK,
                text("editor.rewards.list.refresh.name"),
                List.of(text("editor.rewards.list.refresh.lore"))
        ));
        player.openInventory(inventory);
    }

    private void openPoolDetail(Player player, String poolId) {
        activePool.put(player.getUniqueId(), poolId);
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        Inventory inventory = Bukkit.createInventory(player, 54, TextUtil.color(text("editor.rewards.pool.title", Map.of("pool", poolId))));
        inventory.setItem(4, buildItem(Material.COMPARATOR, text("editor.rewards.pool.roll-count.name"), List.of(
                text("editor.rewards.reward.current", Map.of("value", pool != null ? String.valueOf(pool.rollCount()) : "1")),
                text("editor.rewards.reward.click-edit")
        )));
        List<Reward> rewards = pool != null ? pool.rewards() : List.of();
        int slot = 0;
        for (Reward reward : rewards) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, buildRewardItem(reward));
        }
        inventory.setItem(45, buildItem(
                Material.LIME_CONCRETE,
                text("editor.rewards.pool.create-reward.name"),
                List.of(text("editor.rewards.pool.create-reward.lore"))
        ));
        inventory.setItem(49, buildItem(
                Material.ARROW,
                text("editor.rewards.pool.back.name"),
                List.of(text("editor.rewards.pool.back.lore"))
        ));
        inventory.setItem(53, buildItem(
                Material.BOOK,
                text("editor.rewards.pool.refresh.name"),
                List.of(text("editor.rewards.pool.refresh.lore"))
        ));
        player.openInventory(inventory);
    }

    private void openRewardDetail(Player player, String poolId, String rewardId) {
        activePool.put(player.getUniqueId(), poolId);
        activeReward.put(player.getUniqueId(), rewardId);
        Reward reward = null;
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        if (pool != null) {
            for (Reward item : pool.rewards()) {
                if (item.id().equalsIgnoreCase(rewardId)) {
                    reward = item;
                    break;
                }
            }
        }
        Inventory inventory = Bukkit.createInventory(player, 27, TextUtil.color(text("editor.rewards.reward.title", Map.of("reward", rewardId))));
        inventory.setItem(10, buildItem(Material.NAME_TAG, text("editor.rewards.reward.display-name"), List.of(
                text("editor.rewards.reward.current", Map.of("value", reward != null ? reward.displayName() : rewardId)),
                text("editor.rewards.reward.click-edit")
        )));
        inventory.setItem(11, buildItem(Material.GOLD_NUGGET, text("editor.rewards.reward.chance"), List.of(
                text("editor.rewards.reward.current", Map.of("value", reward != null ? String.valueOf(reward.chance()) : "0")),
                text("editor.rewards.reward.click-edit")
        )));
        inventory.setItem(12, buildItem(Material.CHEST, text("editor.rewards.reward.item"), List.of(
                text("editor.rewards.reward.current", Map.of("value", reward != null ? reward.item() : "STONE")),
                text("editor.rewards.reward.click-edit")
        )));
        inventory.setItem(13, buildItem(Material.PAPER, text("editor.rewards.reward.amount"), List.of(
                text("editor.rewards.reward.current", Map.of("value", reward != null ? String.valueOf(reward.amount()) : "1")),
                text("editor.rewards.reward.click-edit")
        )));
        inventory.setItem(14, buildItem(Material.COMMAND_BLOCK, text("editor.rewards.reward.commands"), List.of(
                text("editor.rewards.reward.current", Map.of("value", reward != null ? String.valueOf(reward.commands().size()) : "0")),
                text("editor.rewards.reward.click-edit")
        )));
        inventory.setItem(15, buildItem(Material.ENCHANTED_BOOK, text("editor.rewards.reward.enchantments"), List.of(
                text("editor.rewards.reward.current", Map.of("value", reward != null ? String.valueOf(reward.enchantments().size()) : "0")),
                text("editor.rewards.reward.click-edit")
        )));
        inventory.setItem(16, buildItem(Material.GLOWSTONE_DUST, text("editor.rewards.reward.glow"), List.of(
                text("editor.rewards.reward.current", Map.of("value", String.valueOf(reward != null && reward.glow()))),
                text("editor.rewards.reward.click-edit")
        )));
        inventory.setItem(19, buildItem(Material.SLIME_BALL, text("editor.rewards.reward.custom-model"), List.of(
                text("editor.rewards.reward.current", Map.of("value", reward != null ? emptyFallback(reward.customModel()) : "")),
                text("editor.rewards.reward.click-edit")
        )));
        inventory.setItem(20, buildItem(Material.FILLED_MAP, text("editor.rewards.reward.map-image"), List.of(
                text("editor.rewards.reward.current", Map.of("value", reward != null ? emptyFallback(reward.mapImage()) : "")),
                text("editor.rewards.reward.click-edit")
        )));
        inventory.setItem(22, buildItem(Material.ARROW, text("editor.rewards.reward.back.name"), List.of(text("editor.rewards.reward.back.lore"))));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Component viewTitle = event.getView().title();
        if (viewTitle.equals(poolTitle)) {
            event.setCancelled(true);
            handlePoolListClick(player, event.getSlot(), event.isRightClick(), event.isShiftClick());
            return;
        }
        String poolId = activePool.get(player.getUniqueId());
        if (poolId != null && viewTitle.equals(poolTitle(poolId))) {
            event.setCancelled(true);
            handlePoolDetailClick(player, poolId, event.getSlot(), event.isRightClick(), event.isShiftClick());
            return;
        }
        String rewardId = activeReward.get(player.getUniqueId());
        if (poolId != null && rewardId != null && viewTitle.equals(rewardTitle(rewardId))) {
            event.setCancelled(true);
            handleRewardDetailClick(player, poolId, rewardId, event.getSlot());
        }
    }

    private void handlePoolListClick(Player player, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == 45) {
            promptCreatePool(player);
            return;
        }
        if (slot == 49) {
            parent.open(player);
            return;
        }
        if (slot == 53) {
            openPools(player);
            return;
        }
        List<RewardPool> pools = new ArrayList<>(configLoader.getRewardPools().values());
        pools.sort(Comparator.comparing(RewardPool::id));
        if (slot < 0 || slot >= pools.size() || slot >= 45) {
            return;
        }
        RewardPool pool = pools.get(slot);
        if (rightClick && shiftClick) {
            confirmationMenu.open(
                    player,
                    text("editor.rewards.confirm.delete-title"),
                    text("editor.rewards.confirm.delete-pool", Map.of("pool", pool.id())),
                    () -> {
                deletePool(pool.id());
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.pool-deleted"));
                openPools(player);
            }, () -> openPools(player));
            return;
        }
        if (rightClick) {
            promptClonePool(player, pool.id());
            return;
        }
        openPoolDetail(player, pool.id());
    }

    private void handlePoolDetailClick(Player player, String poolId, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == 4) {
            promptPoolRoll(player, poolId);
            return;
        }
        if (slot == 45) {
            promptCreateReward(player, poolId);
            return;
        }
        if (slot == 49) {
            openPools(player);
            return;
        }
        if (slot == 53) {
            openPoolDetail(player, poolId);
            return;
        }
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        if (pool == null) {
            return;
        }
        List<Reward> rewards = pool.rewards();
        if (slot < 0 || slot >= rewards.size() || slot >= 45) {
            return;
        }
        Reward reward = rewards.get(slot);
        if (rightClick && shiftClick) {
            confirmationMenu.open(
                    player,
                    text("editor.rewards.confirm.delete-title"),
                    text("editor.rewards.confirm.delete-reward", Map.of("reward", reward.id())),
                    () -> {
                deleteReward(poolId, reward.id());
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.reward-deleted"));
                openPoolDetail(player, poolId);
            }, () -> openPoolDetail(player, poolId));
            return;
        }
        if (rightClick) {
            promptCloneReward(player, poolId, reward.id());
            return;
        }
        openRewardDetail(player, poolId, reward.id());
    }

    private void handleRewardDetailClick(Player player, String poolId, String rewardId, int slot) {
        switch (slot) {
            case 10 -> promptRewardField(player, poolId, rewardId, "display-name", "editor.rewards.prompts.display-name");
            case 11 -> promptRewardField(player, poolId, rewardId, "chance", "editor.rewards.prompts.chance");
            case 12 -> promptRewardField(player, poolId, rewardId, "item", "editor.rewards.prompts.item");
            case 13 -> promptRewardField(player, poolId, rewardId, "amount", "editor.rewards.prompts.amount");
            case 14 -> promptRewardField(player, poolId, rewardId, "commands", "editor.rewards.prompts.commands");
            case 15 -> promptRewardField(player, poolId, rewardId, "enchantments", "editor.rewards.prompts.enchantments");
            case 16 -> promptRewardField(player, poolId, rewardId, "glow", "editor.rewards.prompts.glow");
            case 19 -> promptRewardField(player, poolId, rewardId, "custom-model", "editor.rewards.prompts.custom-model");
            case 20 -> promptRewardField(player, poolId, rewardId, "map-image", "editor.rewards.prompts.map-image");
            case 22 -> openPoolDetail(player, poolId);
            default -> {
            }
        }
    }

    private void promptCreatePool(Player player) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, text("editor.rewards.prompts.pool-id"), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.invalid-id"));
                return;
            }
            if (configLoader.getRewardPools().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.pool-exists"));
                return;
            }
            confirmationMenu.open(
                    player,
                    text("editor.rewards.confirm.create-title"),
                    text("editor.rewards.confirm.create-pool", Map.of("pool", input)),
                    () -> {
                createPool(input);
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.pool-created"));
                openPools(player);
            }, () -> openPools(player));
        });
    }

    private void promptClonePool(Player player, String sourceId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, text("editor.rewards.prompts.pool-clone-id", Map.of("source", sourceId)), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.invalid-id"));
                return;
            }
            if (configLoader.getRewardPools().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.pool-exists"));
                return;
            }
            confirmationMenu.open(
                    player,
                    text("editor.rewards.confirm.clone-title"),
                    text("editor.rewards.confirm.clone-pool", Map.of("source", sourceId, "target", input)),
                    () -> {
                clonePool(sourceId, input);
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.pool-cloned"));
                openPools(player);
            }, () -> openPools(player));
        });
    }

    private void promptPoolRoll(Player player, String poolId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, text("editor.rewards.prompts.roll-count"), input -> confirmationMenu.open(
                player,
                text("editor.rewards.confirm.update-title"),
                text("editor.rewards.confirm.update-pool", Map.of("pool", poolId)),
                () -> {
                    updatePoolField(poolId, "roll-count", parseInt(input));
                    player.sendMessage(languageManager.getMessage("editor.rewards.messages.pool-updated"));
                    openPoolDetail(player, poolId);
                },
                () -> openPoolDetail(player, poolId)
        ));
    }

    private void promptCreateReward(Player player, String poolId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, text("editor.rewards.prompts.reward-id"), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.invalid-id"));
                return;
            }
            if (rewardExists(poolId, input)) {
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.reward-exists"));
                return;
            }
            confirmationMenu.open(
                    player,
                    text("editor.rewards.confirm.create-title"),
                    text("editor.rewards.confirm.create-reward", Map.of("reward", input)),
                    () -> {
                createReward(poolId, input);
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.reward-created"));
                openPoolDetail(player, poolId);
            }, () -> openPoolDetail(player, poolId));
        });
    }

    private void promptCloneReward(Player player, String poolId, String rewardId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, text("editor.rewards.prompts.reward-clone-id", Map.of("source", rewardId)), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.invalid-id"));
                return;
            }
            if (rewardExists(poolId, input)) {
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.reward-exists"));
                return;
            }
            confirmationMenu.open(
                    player,
                    text("editor.rewards.confirm.clone-title"),
                    text("editor.rewards.confirm.clone-reward", Map.of("source", rewardId, "target", input)),
                    () -> {
                cloneReward(poolId, rewardId, input);
                player.sendMessage(languageManager.getMessage("editor.rewards.messages.reward-cloned"));
                openPoolDetail(player, poolId);
            }, () -> openPoolDetail(player, poolId));
        });
    }

    private void promptRewardField(Player player, String poolId, String rewardId, String field, String promptKey) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, text(promptKey), input -> {
            ValidationResult validation = validateRewardField(field, input);
            if (!validation.valid()) {
                player.sendMessage(TextUtil.color(validation.errorMessage()));
                return;
            }
            confirmationMenu.open(
                    player,
                    text("editor.rewards.confirm.update-title"),
                    text("editor.rewards.confirm.update-reward", Map.of("field", field, "reward", rewardId)),
                    () -> {
                        updateRewardField(poolId, rewardId, field, validation.value());
                        player.sendMessage(languageManager.getMessage("editor.rewards.messages.reward-updated"));
                        openRewardDetail(player, poolId, rewardId);
                    },
                    () -> openRewardDetail(player, poolId, rewardId)
            );
        });
    }

    private void createPool(String id) {
        FileConfiguration config = loadConfig();
        String path = "pools." + id;
        config.set(path + ".roll-count", 1);
        config.set(path + ".rewards", new HashMap<>());
        saveConfig(config);
    }

    private void clonePool(String sourceId, String targetId) {
        FileConfiguration config = loadConfig();
        String sourcePath = "pools." + sourceId;
        String targetPath = "pools." + targetId;
        Object data = config.get(sourcePath);
        config.set(targetPath, data);
        saveConfig(config);
    }

    private void deletePool(String id) {
        FileConfiguration config = loadConfig();
        config.set("pools." + id, null);
        saveConfig(config);
    }

    private void updatePoolField(String id, String field, Object value) {
        FileConfiguration config = loadConfig();
        config.set("pools." + id + "." + field, value);
        saveConfig(config);
    }

    private void createReward(String poolId, String rewardId) {
        FileConfiguration config = loadConfig();
        String path = "pools." + poolId + ".rewards." + rewardId;
        config.set(path + ".chance", 1.0);
        config.set(path + ".item", "STONE");
        config.set(path + ".amount", 1);
        config.set(path + ".display-name", rewardId);
        saveConfig(config);
    }

    private void cloneReward(String poolId, String sourceId, String targetId) {
        FileConfiguration config = loadConfig();
        String sourcePath = "pools." + poolId + ".rewards." + sourceId;
        String targetPath = "pools." + poolId + ".rewards." + targetId;
        Object data = config.get(sourcePath);
        config.set(targetPath, data);
        saveConfig(config);
    }

    private void deleteReward(String poolId, String rewardId) {
        FileConfiguration config = loadConfig();
        config.set("pools." + poolId + ".rewards." + rewardId, null);
        saveConfig(config);
    }

    private void updateRewardField(String poolId, String rewardId, String field, Object value) {
        FileConfiguration config = loadConfig();
        String basePath = "pools." + poolId + ".rewards." + rewardId + "." + field;
        if ("commands".equals(field) && value instanceof List<?> listValue) {
            config.set(basePath, listValue.isEmpty() ? null : listValue);
        } else if ("enchantments".equals(field) && value instanceof Map<?, ?> mapValue) {
            config.set(basePath, mapValue.isEmpty() ? null : mapValue);
        } else if (("custom-model".equals(field) || "map-image".equals(field)) && value instanceof String stringValue) {
            config.set(basePath, stringValue.isBlank() ? null : stringValue);
        } else {
            config.set(basePath, value);
        }
        saveConfig(config);
    }

    private boolean rewardExists(String poolId, String rewardId) {
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        if (pool == null) {
            return false;
        }
        return pool.rewards().stream().anyMatch(reward -> reward.id().equalsIgnoreCase(rewardId));
    }

    private FileConfiguration loadConfig() {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfig(FileConfiguration config) {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        try {
            config.save(file);
            configLoader.loadAll();
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar rewards.yml: " + ex.getMessage());
        }
    }

    private ItemStack buildPoolItem(RewardPool pool) {
        List<String> lore = new ArrayList<>();
        lore.add(text("editor.rewards.list.item.lore.id", Map.of("id", pool.id())));
        lore.add(text("editor.rewards.list.item.lore.roll-count", Map.of("roll_count", String.valueOf(pool.rollCount()))));
        lore.add(text("editor.rewards.list.item.lore.rewards", Map.of("rewards", String.valueOf(pool.rewards().size()))));
        lore.add(text("editor.rewards.list.item.lore.hint"));
        return buildItem(Material.EMERALD, "&a" + pool.id(), lore);
    }

    private ItemStack buildRewardItem(Reward reward) {
        List<String> lore = new ArrayList<>();
        lore.add(text("editor.rewards.reward.item-lore.id", Map.of("id", reward.id())));
        lore.add(text("editor.rewards.reward.item-lore.chance", Map.of("chance", String.valueOf(reward.chance()))));
        lore.add(text("editor.rewards.reward.item-lore.item", Map.of("item", reward.item())));
        lore.add(text("editor.rewards.reward.item-lore.hint"));
        return buildItem(Material.GOLD_INGOT, "&e" + reward.displayName(), lore);
    }

    private ItemStack buildItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.color(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream().map(TextUtil::color).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ValidationResult validateRewardField(String field, String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.invalid(text("editor.rewards.validation.invalid-value"));
        }
        return switch (field) {
            case "display-name" -> ValidationResult.valid(trimmed);
            case "chance" -> parseChance(trimmed);
            case "item" -> validateMaterial(trimmed);
            case "amount" -> parseAmount(trimmed);
            case "commands" -> parseCommands(trimmed);
            case "enchantments" -> parseEnchantments(trimmed);
            case "glow" -> parseGlow(trimmed);
            case "custom-model", "map-image" -> parseOptionalText(trimmed);
            default -> ValidationResult.valid(trimmed);
        };
    }

    private ValidationResult parseChance(String input) {
        try {
            double value = Double.parseDouble(input);
            if (value < 0) {
                return ValidationResult.invalid(text("editor.rewards.validation.chance-negative"));
            }
            return ValidationResult.valid(value);
        } catch (NumberFormatException ex) {
            return ValidationResult.invalid(text("editor.rewards.validation.chance-number"));
        }
    }

    private ValidationResult parseAmount(String input) {
        try {
            int value = Integer.parseInt(input);
            if (value <= 0) {
                return ValidationResult.invalid(text("editor.rewards.validation.amount-positive"));
            }
            return ValidationResult.valid(value);
        } catch (NumberFormatException ex) {
            return ValidationResult.invalid(text("editor.rewards.validation.amount-number"));
        }
    }

    private ValidationResult validateMaterial(String input) {
        Material material = Material.matchMaterial(input.toUpperCase(Locale.ROOT));
        if (material == null) {
            return ValidationResult.invalid(text("editor.rewards.validation.material", Map.of("value", input)));
        }
        return ValidationResult.valid(material.name().toLowerCase(Locale.ROOT));
    }

    private ValidationResult parseGlow(String input) {
        Optional<Boolean> value = parseBoolean(input);
        if (value.isEmpty()) {
            return ValidationResult.invalid(text("editor.rewards.validation.glow"));
        }
        return ValidationResult.valid(value.get());
    }

    private ValidationResult parseOptionalText(String input) {
        if (isNoneValue(input)) {
            return ValidationResult.valid("");
        }
        return ValidationResult.valid(input);
    }

    private ValidationResult parseCommands(String input) {
        if (isNoneValue(input)) {
            return ValidationResult.valid(List.of());
        }
        List<String> commands = new ArrayList<>();
        for (String entry : input.split(";")) {
            String command = entry.trim();
            if (!command.isEmpty()) {
                commands.add(command);
            }
        }
        if (commands.isEmpty()) {
            return ValidationResult.invalid(text("editor.rewards.validation.commands"));
        }
        return ValidationResult.valid(commands);
    }

    private ValidationResult parseEnchantments(String input) {
        if (isNoneValue(input)) {
            return ValidationResult.valid(Map.of());
        }
        Map<String, Integer> enchantments = new HashMap<>();
        for (String entry : input.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(":", 2);
            if (parts.length != 2) {
                return ValidationResult.invalid(text("editor.rewards.validation.enchantments-format"));
            }
            String key = parts[0].trim().toLowerCase(Locale.ROOT);
            String levelText = parts[1].trim();
            if (key.isEmpty() || levelText.isEmpty()) {
                return ValidationResult.invalid(text("editor.rewards.validation.enchantments-entry"));
            }
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
            if (enchantment == null) {
                return ValidationResult.invalid(text("editor.rewards.validation.enchantments-invalid", Map.of("value", key)));
            }
            int level;
            try {
                level = Integer.parseInt(levelText);
            } catch (NumberFormatException ex) {
                return ValidationResult.invalid(text("editor.rewards.validation.enchantments-level", Map.of("value", key)));
            }
            if (level <= 0) {
                return ValidationResult.invalid(text("editor.rewards.validation.enchantments-level", Map.of("value", key)));
            }
            enchantments.put(key, level);
        }
        if (enchantments.isEmpty()) {
            return ValidationResult.invalid(text("editor.rewards.validation.enchantments-empty"));
        }
        return ValidationResult.valid(enchantments);
    }

    private Optional<Boolean> parseBoolean(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "yes", "si", "on" -> Optional.of(true);
            case "false", "no", "off" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    private boolean isNoneValue(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("none") || normalized.equals("null") || normalized.equals("clear");
    }

    private String emptyFallback(String value) {
        return value == null || value.isBlank() ? text("editor.common.none") : value;
    }

    private record ValidationResult(boolean valid, Object value, String errorMessage) {
        private static ValidationResult valid(Object value) {
            return new ValidationResult(true, value, "");
        }

        private static ValidationResult invalid(String message) {
            return new ValidationResult(false, null, message);
        }
    }

    private int parseInt(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            return DEFAULT_INT_FALLBACK;
        }
    }

    private Component poolTitle(String poolId) {
        return TextUtil.color(text("editor.rewards.pool.title", Map.of("pool", poolId)));
    }

    private Component rewardTitle(String rewardId) {
        return TextUtil.color(text("editor.rewards.reward.title", Map.of("reward", rewardId)));
    }

    private String text(String key) {
        return languageManager.getRaw(key, java.util.Collections.emptyMap());
    }

    private String text(String key, Map<String, String> placeholders) {
        return languageManager.getRaw(key, placeholders);
    }

}
