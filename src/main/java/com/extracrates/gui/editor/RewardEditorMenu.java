package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.util.TextUtil;
import com.extracrates.gui.MenuSpacer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.UUID;

public class RewardEditorMenu implements Listener {
    // Layout: fila superior vacía, acciones en el centro, separación y footer.
    private static final int SLOT_LIST_CREATE = 45;
    private static final int SLOT_LIST_DELETE = 53;
    private static final int SLOT_LIST_BACK = 49;
    private static final int SLOT_DETAIL_BACK = 31;
    private static final int SLOT_DETAIL_DELETE = 35;
    private static final int[] LIST_NAV_FILLER_SLOTS = {46, 47, 48, 50, 51, 52, 53};
    private static final int[] DETAIL_NAV_FILLER_SLOTS = {27, 28, 29, 30, 32, 33, 34};

    private static final int SLOT_DETAIL_DISPLAY_NAME = 9;
    private static final int SLOT_DETAIL_CHANCE = 10;
    private static final int SLOT_DETAIL_ITEM = 11;
    private static final int SLOT_DETAIL_AMOUNT = 12;
    private static final int SLOT_DETAIL_COMMANDS = 13;
    private static final int SLOT_DETAIL_ENCHANTMENTS = 14;
    private static final int SLOT_DETAIL_GLOW = 15;
    private static final int SLOT_DETAIL_CUSTOM_MODEL = 16;
    private static final int SLOT_DETAIL_MAP_IMAGE = 17;

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
        this.poolTitle = TextUtil.colorNoItalic(text("editor.rewards.list.title"));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openPools(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, poolTitle);
        List<RewardPool> pools = new ArrayList<>(configLoader.getRewardPools().values());
        pools.sort(Comparator.comparing((RewardPool pool) -> resolveCreatedAt(pool.id()))
                .thenComparing(RewardPool::id, String.CASE_INSENSITIVE_ORDER));
        int slot = 9;
        for (RewardPool pool : pools) {
            if (slot > 35) {
                break;
            }
            inventory.setItem(slot++, buildPoolItem(pool));
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_CREATE, buildItem(Material.LIME_CONCRETE,
                text("editor.rewards.list.create.name"),
                List.of(text("editor.rewards.list.create.lore"))));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW,
                text("editor.rewards.list.back.name"),
                List.of(text("editor.rewards.list.back.lore"))));
        MenuSpacer.apply(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openPoolDetail(Player player, String poolId) {
        activePool.put(player.getUniqueId(), poolId);
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        Inventory inventory = Bukkit.createInventory(player, 54, TextUtil.colorNoItalic(text("editor.rewards.pool.title", Map.of("pool", poolId))));
        List<Reward> rewards = pool != null ? pool.rewards() : List.of();
        int slot = 9;
        for (Reward reward : rewards) {
            if (slot > 35) {
                break;
            }
            inventory.setItem(slot++, buildRewardItem(reward));
        }
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_CREATE, buildItem(Material.LIME_CONCRETE,
                text("editor.rewards.pool.create-reward.name"),
                List.of(text("editor.rewards.pool.create-reward.lore"))));
        inventory.setItem(SLOT_LIST_DELETE, buildItem(Material.RED_CONCRETE,
                text("editor.rewards.pool.delete.name"),
                List.of(text("editor.rewards.pool.delete.lore"))));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW,
                text("editor.rewards.pool.back.name"),
                List.of(text("editor.rewards.pool.back.lore"))));
        MenuSpacer.apply(inventory, buildSpacerItem());
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
        Inventory inventory = Bukkit.createInventory(player, 36, rewardTitle(rewardId));
        inventory.setItem(SLOT_DETAIL_DISPLAY_NAME, buildItem(Material.NAME_TAG, text("editor.rewards.reward.detail.display-name.name"), List.of(
                text("editor.common.current", Map.of("value", reward != null ? reward.displayName() : rewardId)),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_CHANCE, buildItem(Material.GOLD_NUGGET, text("editor.rewards.reward.detail.chance.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(reward != null ? reward.chance() : 0))),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_ITEM, buildItem(Material.CHEST, text("editor.rewards.reward.detail.item.name"), List.of(
                text("editor.common.current", Map.of("value", reward != null ? reward.item() : "STONE")),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_AMOUNT, buildItem(Material.PAPER, text("editor.rewards.reward.detail.amount.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(reward != null ? reward.amount() : 1))),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_COMMANDS, buildItem(Material.COMMAND_BLOCK, text("editor.rewards.reward.detail.commands.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(reward != null ? reward.commands().size() : 0))),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_ENCHANTMENTS, buildItem(Material.ENCHANTED_BOOK, text("editor.rewards.reward.detail.enchantments.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(reward != null ? reward.enchantments().size() : 0))),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_GLOW, buildItem(Material.GLOWSTONE_DUST, text("editor.rewards.reward.detail.glow.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(reward != null && reward.glow()))),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_CUSTOM_MODEL, buildItem(Material.SLIME_BALL, text("editor.rewards.reward.detail.custom-model.name"), List.of(
                text("editor.common.current", Map.of("value", reward != null ? emptyFallback(reward.customModel()) : "")),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_MAP_IMAGE, buildItem(Material.FILLED_MAP, text("editor.rewards.reward.detail.map-image.name"), List.of(
                text("editor.common.current", Map.of("value", reward != null ? emptyFallback(reward.mapImage()) : "")),
                text("editor.common.click-edit")
        )));
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE,
                text("editor.rewards.reward.delete.name"),
                List.of(text("editor.rewards.reward.delete.lore"))));
        inventory.setItem(SLOT_DETAIL_BACK, buildItem(Material.ARROW,
                text("editor.rewards.reward.back.name"),
                List.of(text("editor.rewards.reward.back.lore"))));
        MenuSpacer.apply(inventory, buildSpacerItem());
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
        if (slot == SLOT_LIST_CREATE) {
            promptCreatePool(player);
            return;
        }
        if (slot == SLOT_LIST_BACK) {
            parent.open(player);
            return;
        }
        List<RewardPool> pools = new ArrayList<>(configLoader.getRewardPools().values());
        pools.sort(Comparator.comparing((RewardPool pool) -> resolveCreatedAt(pool.id()))
                .thenComparing(RewardPool::id, String.CASE_INSENSITIVE_ORDER));
        int index = slot - 9;
        if (slot < 9 || slot > 35 || index < 0 || index >= pools.size()) {
            return;
        }
        RewardPool pool = pools.get(index);
        if (rightClick && shiftClick) {
            confirmationMenu.open(
                    player,
                    languageManager.getRaw("editor.confirmation.title.delete", java.util.Collections.emptyMap()),
                    languageManager.getRaw("editor.reward.confirm.delete-pool", Map.of("id", pool.id())),
                    () -> {
                        deletePool(pool.id());
                        player.sendMessage(languageManager.getMessage("editor.reward.success.pool-deleted"));
                        openPools(player);
                    },
                    () -> openPools(player)
            );
            return;
        }
        if (rightClick) {
            promptClonePool(player, pool.id());
            return;
        }
        openPoolDetail(player, pool.id());
    }

    private void handlePoolDetailClick(Player player, String poolId, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == SLOT_LIST_CREATE) {
            promptCreateReward(player, poolId);
            return;
        }
        if (slot == SLOT_LIST_DELETE) {
            confirmDeletePool(player, poolId);
            return;
        }
        if (slot == SLOT_LIST_BACK) {
            openPools(player);
            return;
        }
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        if (pool == null) {
            return;
        }
        List<Reward> rewards = pool.rewards();
        int index = slot - 9;
        if (slot < 9 || slot > 35 || index < 0 || index >= rewards.size()) {
            return;
        }
        Reward reward = rewards.get(index);
        if (rightClick && shiftClick) {
            confirmationMenu.open(
                    player,
                    languageManager.getRaw("editor.confirmation.title.delete", java.util.Collections.emptyMap()),
                    languageManager.getRaw("editor.reward.confirm.delete-reward", Map.of("id", reward.id())),
                    () -> {
                        deleteReward(poolId, reward.id());
                        player.sendMessage(languageManager.getMessage("editor.reward.success.reward-deleted"));
                        openPoolDetail(player, poolId);
                    },
                    () -> openPoolDetail(player, poolId)
            );
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
            case SLOT_DETAIL_DISPLAY_NAME -> promptRewardField(player, poolId, rewardId, "display-name", "editor.reward.prompt.display-name");
            case SLOT_DETAIL_CHANCE -> promptRewardField(player, poolId, rewardId, "chance", "editor.reward.prompt.chance");
            case SLOT_DETAIL_ITEM -> promptRewardField(player, poolId, rewardId, "item", "editor.reward.prompt.item");
            case SLOT_DETAIL_AMOUNT -> promptRewardField(player, poolId, rewardId, "amount", "editor.reward.prompt.amount");
            case SLOT_DETAIL_COMMANDS -> promptRewardField(player, poolId, rewardId, "commands", "editor.reward.prompt.commands");
            case SLOT_DETAIL_ENCHANTMENTS -> promptRewardField(player, poolId, rewardId, "enchantments", "editor.reward.prompt.enchantments");
            case SLOT_DETAIL_GLOW -> promptRewardField(player, poolId, rewardId, "glow", "editor.reward.prompt.glow");
            case SLOT_DETAIL_CUSTOM_MODEL -> promptRewardField(player, poolId, rewardId, "custom-model", "editor.reward.prompt.custom-model");
            case SLOT_DETAIL_MAP_IMAGE -> promptRewardField(player, poolId, rewardId, "map-image", "editor.reward.prompt.map-image");
            case SLOT_DETAIL_DELETE -> confirmDeleteReward(player, poolId, rewardId);
            case SLOT_DETAIL_BACK -> openPoolDetail(player, poolId);
            default -> {
            }
        }
    }

    private void confirmDeletePool(Player player, String poolId) {
        confirmationMenu.open(
                player,
                languageManager.getRaw("editor.confirmation.title.delete", java.util.Collections.emptyMap()),
                languageManager.getRaw("editor.reward.confirm.delete-pool", Map.of("id", poolId)),
                () -> {
            deletePool(poolId);
            player.sendMessage(languageManager.getMessage("editor.reward.success.pool-deleted"));
            openPools(player);
        }, () -> openPoolDetail(player, poolId));
    }

    private void confirmDeleteReward(Player player, String poolId, String rewardId) {
        confirmationMenu.open(
                player,
                languageManager.getRaw("editor.confirmation.title.delete", java.util.Collections.emptyMap()),
                languageManager.getRaw("editor.reward.confirm.delete-reward", Map.of("id", rewardId)),
                () -> {
            deleteReward(poolId, rewardId);
            player.sendMessage(languageManager.getMessage("editor.reward.success.reward-deleted"));
            openPoolDetail(player, poolId);
        }, () -> openRewardDetail(player, poolId, rewardId));
    }

    private void promptCreatePool(Player player) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "editor.reward.prompt.new-pool-id", input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.input.invalid-id"));
                return;
            }
            if (configLoader.getRewardPools().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("command.pool-already-exists"));
                return;
            }
            createPool(input);
            player.sendMessage(languageManager.getMessage("editor.reward.success.pool-created"));
        }, () -> openPools(player));
    }

    private void promptClonePool(Player player, String sourceId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "editor.reward.prompt.clone-pool-id", Map.of("id", sourceId), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.input.invalid-id"));
                return;
            }
            if (configLoader.getRewardPools().containsKey(input)) {
                player.sendMessage(languageManager.getMessage("command.pool-already-exists"));
                return;
            }
            clonePool(sourceId, input);
            player.sendMessage(languageManager.getMessage("editor.reward.success.pool-cloned"));
        }, () -> openPools(player));
    }

    private void promptCreateReward(Player player, String poolId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "editor.reward.prompt.new-reward-id", input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.input.invalid-id"));
                return;
            }
            if (rewardExists(poolId, input)) {
                player.sendMessage(languageManager.getMessage("command.reward-already-exists"));
                return;
            }
            createReward(poolId, input);
            player.sendMessage(languageManager.getMessage("editor.reward.success.reward-created"));
        }, () -> openPoolDetail(player, poolId));
    }

    private void promptCloneReward(Player player, String poolId, String rewardId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "editor.reward.prompt.clone-reward-id", Map.of("id", rewardId), input -> {
            if (input.isEmpty()) {
                player.sendMessage(languageManager.getMessage("editor.input.invalid-id"));
                return;
            }
            if (rewardExists(poolId, input)) {
                player.sendMessage(languageManager.getMessage("command.reward-already-exists"));
                return;
            }
            cloneReward(poolId, rewardId, input);
            player.sendMessage(languageManager.getMessage("editor.reward.success.reward-cloned"));
        }, () -> openPoolDetail(player, poolId));
    }

    private void promptRewardField(Player player, String poolId, String rewardId, String field, String promptKey) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, promptKey, input -> {
            ValidationResult validation = validateRewardField(field, input);
            if (!validation.valid()) {
                player.sendMessage(validation.errorMessage());
                return;
            }
            updateRewardField(poolId, rewardId, field, validation.value());
            player.sendMessage(languageManager.getMessage("editor.reward.success.reward-updated"));
        }, () -> openRewardDetail(player, poolId, rewardId));
    }

    private void setRewardItemFromHand(Player player, String poolId, String rewardId) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(languageManager.getMessage("editor.reward.error.missing-hand-item"));
            return;
        }
        updateRewardItemStack(poolId, rewardId, item);
        player.sendMessage(languageManager.getMessage("editor.reward.success.reward-updated"));
        openRewardDetail(player, poolId, rewardId);
    }

    private void createPool(String id) {
        FileConfiguration config = loadConfig();
        String path = "pools." + id;
        config.set(path + ".roll-count", 1);
        config.set(path + ".rewards", new HashMap<>());
        config.set(path + ".created-at", System.currentTimeMillis());
        saveConfig(config);
    }

    private void clonePool(String sourceId, String targetId) {
        FileConfiguration config = loadConfig();
        String sourcePath = "pools." + sourceId;
        String targetPath = "pools." + targetId;
        Object data = config.get(sourcePath);
        config.set(targetPath, data);
        config.set(targetPath + ".created-at", System.currentTimeMillis());
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
        } else {
            config.set(basePath, value);
        }
        saveConfig(config);
    }

    private void updateRewardItemStack(String poolId, String rewardId, ItemStack itemStack) {
        FileConfiguration config = loadConfig();
        String basePath = "pools." + poolId + ".rewards." + rewardId;
        config.set(basePath + ".item-stack", itemStack.clone());
        config.set(basePath + ".item", itemStack.getType().name().toLowerCase(Locale.ROOT));
        config.set(basePath + ".amount", itemStack.getAmount());
        config.set(basePath + ".custom-model", null);
        config.set(basePath + ".glow", null);
        config.set(basePath + ".enchantments", null);
        config.set(basePath + ".map-image", null);
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
        lore.add(text("editor.rewards.list.item.lore.rewards", Map.of("rewards", String.valueOf(pool.rewards().size()))));
        lore.add(text("editor.common.action.left-edit"));
        lore.add(text("editor.common.action.right-clone"));
        lore.add(text("editor.common.action.shift-right-delete"));
        return buildItem(Material.EMERALD, "&a" + pool.id(), lore);
    }

    private void fillListNavigation(Inventory inventory) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : LIST_NAV_FILLER_SLOTS) {
            inventory.setItem(slot, filler);
        }
    }

    private void fillDetailNavigation(Inventory inventory) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : DETAIL_NAV_FILLER_SLOTS) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack buildSpacerItem() {
        return buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private ItemStack buildRewardItem(Reward reward) {
        List<String> lore = new ArrayList<>();
        lore.add(text("editor.rewards.reward.item-lore.id", Map.of("id", reward.id())));
        lore.add(text("editor.rewards.reward.item-lore.chance", Map.of("chance", String.valueOf(reward.chance()))));
        lore.add(text("editor.rewards.reward.item-lore.item", Map.of("item", resolveRewardItemLabel(reward))));
        lore.add(text("editor.common.action.left-edit"));
        lore.add(text("editor.common.action.right-clone"));
        lore.add(text("editor.common.action.shift-right-delete"));
        return buildItem(Material.GOLD_INGOT, "&e" + reward.displayName(), lore);
    }

    private ItemStack buildItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorNoItalic(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream().map(TextUtil::colorNoItalic).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ValidationResult validateRewardField(String field, String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.invalid(languageManager.getMessage("editor.reward.error.invalid-value"));
        }
        return switch (field) {
            case "display-name" -> ValidationResult.valid(trimmed);
            case "chance" -> parseChance(trimmed);
            case "commands" -> parseCommands(trimmed);
            default -> ValidationResult.valid(trimmed);
        };
    }

    private ValidationResult parseChance(String input) {
        try {
            double value = Double.parseDouble(input);
            if (value < 0.01) {
                return ValidationResult.invalid(languageManager.getMessage("editor.reward.error.invalid-chance-min"));
            }
            if (value > 100) {
                return ValidationResult.invalid(languageManager.getMessage("editor.reward.error.invalid-chance-max"));
            }
            return ValidationResult.valid(value);
        } catch (NumberFormatException ex) {
            return ValidationResult.invalid(languageManager.getMessage("editor.reward.error.invalid-chance-number"));
        }
    }

    private String resolveRewardItemLabel(Reward reward) {
        if (reward == null) {
            return text("editor.common.none");
        }
        ItemStack stack = reward.itemStack();
        if (stack != null) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return TextUtil.serializeLegacy(meta.displayName());
            }
            return stack.getType().name().toLowerCase(Locale.ROOT);
        }
        return reward.item();
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
            return ValidationResult.invalid(languageManager.getMessage("editor.reward.error.invalid-commands"));
        }
        return ValidationResult.valid(commands);
    }

    private boolean isNoneValue(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("none") || normalized.equals("null") || normalized.equals("clear");
    }

    private record ValidationResult(boolean valid, Object value, Component errorMessage) {
        private static ValidationResult valid(Object value) {
            return new ValidationResult(true, value, Component.empty());
        }

        private static ValidationResult invalid(Component message) {
            return new ValidationResult(false, null, message);
        }
    }

    private Component poolTitle(String poolId) {
        return TextUtil.colorNoItalic(text("editor.rewards.pool.title", Map.of("pool", poolId)));
    }

    private Component rewardTitle(String rewardId) {
        return TextUtil.colorNoItalic(text("editor.rewards.reward.title", Map.of("reward", rewardId)));
    }

    private String emptyFallback(String value) {
        if (value == null || value.trim().isEmpty()) {
            return text("editor.common.none");
        }
        return value;
    }

    private String text(String key) {
        return languageManager.getRaw(key, java.util.Collections.emptyMap());
    }

    private String text(String key, Map<String, String> placeholders) {
        return languageManager.getRaw(key, placeholders);
    }

    private long resolveCreatedAt(String poolId) {
        FileConfiguration config = loadConfig();
        return config.getLong("pools." + poolId + ".created-at", 0L);
    }

}
