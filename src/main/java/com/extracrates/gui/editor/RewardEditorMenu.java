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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

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
    private static final int SLOT_DETAIL_REWARD_ITEM = 10;
    private static final int SLOT_DETAIL_DISPLAY_ITEM = 11;
    private static final int SLOT_DETAIL_COMMANDS = 12;
    private static final int SLOT_DETAIL_RARITY = 13;

    private static final int RARITY_MENU_SIZE = 45;
    private static final int RARITY_BACK_SLOT = 40;
    private static final int RARITY_ROW_START = 18;
    private static final int RARITY_ROW_END = 26;
    private static final List<Material> MAGISTRAL_COLORS = List.of(
            Material.WHITE_WOOL,
            Material.LIGHT_GRAY_WOOL,
            Material.GRAY_WOOL,
            Material.BLACK_WOOL,
            Material.BROWN_WOOL,
            Material.RED_WOOL,
            Material.ORANGE_WOOL,
            Material.YELLOW_WOOL,
            Material.LIME_WOOL,
            Material.GREEN_WOOL,
            Material.CYAN_WOOL,
            Material.LIGHT_BLUE_WOOL,
            Material.BLUE_WOOL,
            Material.PURPLE_WOOL,
            Material.MAGENTA_WOOL,
            Material.PINK_WOOL
    );

    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final EditorInputManager inputManager;
    private final ConfirmationMenu confirmationMenu;
    private final EditorMenu parent;
    private final Component poolTitle;
    private final Map<UUID, String> activePool = new HashMap<>();
    private final Map<UUID, String> activeReward = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> raritySlots = new HashMap<>();
    private final Map<UUID, BukkitRunnable> rarityAnimations = new HashMap<>();

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
        List<ItemStack> poolItems = new ArrayList<>();
        for (RewardPool pool : pools) {
            if (poolItems.size() >= 27) {
                break;
            }
            poolItems.add(buildPoolItem(pool));
        }
        MenuSpacer.applyCenteredItems(inventory, 9, 35, poolItems);
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_CREATE, buildItem(Material.LIME_CONCRETE,
                text("editor.rewards.list.create.name"),
                List.of(text("editor.rewards.list.create.lore"))));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW,
                text("editor.rewards.list.back.name"),
                List.of(text("editor.rewards.list.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openPoolDetail(Player player, String poolId) {
        activePool.put(player.getUniqueId(), poolId);
        RewardPool pool = configLoader.getRewardPools().get(poolId);
        Inventory inventory = Bukkit.createInventory(player, 54, TextUtil.colorNoItalic(text("editor.rewards.pool.title", Map.of("pool", poolId))));
        List<Reward> rewards = pool != null ? pool.rewards() : List.of();
        List<ItemStack> rewardItems = new ArrayList<>();
        for (Reward reward : rewards) {
            if (rewardItems.size() >= 27) {
                break;
            }
            rewardItems.add(buildRewardItem(reward));
        }
        MenuSpacer.applyCenteredItems(inventory, 9, 35, rewardItems);
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
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
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
        inventory.setItem(SLOT_DETAIL_REWARD_ITEM, buildItem(Material.CHEST, text("editor.rewards.reward.detail.reward-item.name"), List.of(
                text("editor.common.current", Map.of("value", reward != null ? resolveRewardItemLabel(reward) : text("editor.common.none"))),
                text("editor.rewards.reward.detail.reward-item.desc")
        )));
        inventory.setItem(SLOT_DETAIL_DISPLAY_ITEM, buildItem(Material.ITEM_FRAME, text("editor.rewards.reward.detail.display-item.name"), List.of(
                text("editor.common.current", Map.of("value", reward != null ? resolveDisplayItemLabel(reward) : text("editor.common.none"))),
                text("editor.rewards.reward.detail.display-item.desc")
        )));
        inventory.setItem(SLOT_DETAIL_COMMANDS, buildItem(Material.COMMAND_BLOCK, text("editor.rewards.reward.detail.commands.name"), List.of(
                text("editor.common.current", Map.of("value", String.valueOf(reward != null ? reward.commands().size() : 0))),
                text("editor.common.click-edit")
        )));
        inventory.setItem(SLOT_DETAIL_RARITY, buildItem(resolveRarityMaterial(reward != null ? reward.rarity() : ""), text("editor.rewards.reward.detail.rarity.name"), List.of(
                text("editor.common.current", Map.of("value", resolveRarityLabel(reward))),
                text("editor.rewards.reward.detail.rarity.desc"),
                text("editor.common.click-select")
        )));
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE,
                text("editor.rewards.reward.delete.name"),
                List.of(text("editor.rewards.reward.delete.lore"))));
        inventory.setItem(SLOT_DETAIL_BACK, buildItem(Material.ARROW,
                text("editor.rewards.reward.back.name"),
                List.of(text("editor.rewards.reward.back.lore"))));
        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
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
            return;
        }
        if (poolId != null && rewardId != null && viewTitle.equals(rarityTitle(rewardId))) {
            event.setCancelled(true);
            handleRarityClick(player, poolId, rewardId, event.getSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        String rewardId = activeReward.get(player.getUniqueId());
        if (rewardId != null && event.getView().title().equals(rarityTitle(rewardId))) {
            stopRarityAnimation(player);
            raritySlots.remove(player.getUniqueId());
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
        int visibleCount = Math.min(pools.size(), 27);
        int centeredIndex = MenuSpacer.centeredIndex(9, 35, visibleCount, slot);
        if (centeredIndex < 0 || centeredIndex >= visibleCount) {
            return;
        }
        RewardPool pool = pools.get(centeredIndex);
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
        int visibleCount = Math.min(rewards.size(), 27);
        int centeredIndex = MenuSpacer.centeredIndex(9, 35, visibleCount, slot);
        if (centeredIndex < 0 || centeredIndex >= visibleCount) {
            return;
        }
        Reward reward = rewards.get(centeredIndex);
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
            case SLOT_DETAIL_REWARD_ITEM -> setRewardItemFromHand(player, poolId, rewardId);
            case SLOT_DETAIL_DISPLAY_ITEM -> setDisplayItemFromHand(player, poolId, rewardId);
            case SLOT_DETAIL_COMMANDS -> promptRewardField(player, poolId, rewardId, "commands", "editor.reward.prompt.commands");
            case SLOT_DETAIL_RARITY -> openRaritySelector(player, poolId, rewardId);
            case SLOT_DETAIL_DELETE -> confirmDeleteReward(player, poolId, rewardId);
            case SLOT_DETAIL_BACK -> openPoolDetail(player, poolId);
            default -> {
            }
        }
    }

    private void openRaritySelector(Player player, String poolId, String rewardId) {
        activePool.put(player.getUniqueId(), poolId);
        activeReward.put(player.getUniqueId(), rewardId);
        Inventory inventory = Bukkit.createInventory(player, RARITY_MENU_SIZE, rarityTitle(rewardId));
        fillRarityFrame(inventory);
        inventory.setItem(RARITY_BACK_SLOT, buildItem(Material.ARROW,
                text("editor.rewards.rarity.back.name"),
                List.of(text("editor.rewards.rarity.back.lore"))));
        List<com.extracrates.model.RarityDefinition> rarities = new ArrayList<>(configLoader.getRarities().values());
        rarities.sort(Comparator.comparingDouble(com.extracrates.model.RarityDefinition::chance).reversed()
                .thenComparing(com.extracrates.model.RarityDefinition::id));
        Map<Integer, String> slotMap = new HashMap<>();
        int maxRarities = Math.min(rarities.size(), RARITY_ROW_END - RARITY_ROW_START + 1);
        List<ItemStack> rarityItems = new ArrayList<>();
        for (int i = 0; i < maxRarities; i++) {
            com.extracrates.model.RarityDefinition rarity = rarities.get(i);
            rarityItems.add(buildRarityItem(rarity, resolveRarityMaterial(rarity.id())));
        }
        List<Integer> slots = MenuSpacer.centeredSlots(RARITY_ROW_START, RARITY_ROW_END, rarityItems.size());
        for (int i = 0; i < slots.size(); i++) {
            com.extracrates.model.RarityDefinition rarity = rarities.get(i);
            int slot = slots.get(i);
            inventory.setItem(slot, buildRarityItem(rarity, resolveRarityMaterial(rarity.id())));
            slotMap.put(slot, rarity.id());
            if ("magistral".equalsIgnoreCase(rarity.id())) {
                startRarityAnimation(player, slot, rarity);
            }
        }
        raritySlots.put(player.getUniqueId(), slotMap);
        player.openInventory(inventory);
    }

    private void handleRarityClick(Player player, String poolId, String rewardId, int slot) {
        if (slot == RARITY_BACK_SLOT) {
            stopRarityAnimation(player);
            openRewardDetail(player, poolId, rewardId);
            return;
        }
        Map<Integer, String> slotMap = raritySlots.get(player.getUniqueId());
        if (slotMap == null || !slotMap.containsKey(slot)) {
            return;
        }
        String rarityId = slotMap.get(slot);
        updateRewardField(poolId, rewardId, "rarity", rarityId);
        stopRarityAnimation(player);
        openRewardDetail(player, poolId, rewardId);
    }

    private void fillRarityFrame(Inventory inventory) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, filler);
        }
        for (int slot = 36; slot < 45; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void startRarityAnimation(Player player, int slot, com.extracrates.model.RarityDefinition rarity) {
        stopRarityAnimation(player);
        BukkitRunnable task = new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                Inventory inventory = player.getOpenInventory().getTopInventory();
                if (inventory == null || inventory.getSize() != RARITY_MENU_SIZE) {
                    cancel();
                    return;
                }
                Material material = MAGISTRAL_COLORS.get(index++ % MAGISTRAL_COLORS.size());
                inventory.setItem(slot, buildRarityItem(rarity, material));
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        rarityAnimations.put(player.getUniqueId(), task);
    }

    private void stopRarityAnimation(Player player) {
        BukkitRunnable task = rarityAnimations.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
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

    private void setDisplayItemFromHand(Player player, String poolId, String rewardId) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(languageManager.getMessage("editor.reward.error.missing-hand-item"));
            return;
        }
        updateDisplayItemStack(poolId, rewardId, item);
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
        config.set(basePath + ".reward-item", itemStack.clone());
        config.set(basePath + ".item-stack", null);
        config.set(basePath + ".item", null);
        config.set(basePath + ".amount", null);
        config.set(basePath + ".custom-model", null);
        config.set(basePath + ".glow", null);
        config.set(basePath + ".enchantments", null);
        saveConfig(config);
    }

    private void updateDisplayItemStack(String poolId, String rewardId, ItemStack itemStack) {
        FileConfiguration config = loadConfig();
        String basePath = "pools." + poolId + ".rewards." + rewardId;
        config.set(basePath + ".display-item", itemStack.clone());
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            config.set(basePath + ".display-name", TextUtil.serializeLegacy(meta.displayName()));
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
        ItemStack item = buildRewardDisplayItem(reward);
        List<String> lore = new ArrayList<>();
        lore.add(text("editor.rewards.reward.item-lore.id", Map.of("id", reward.id())));
        lore.add(text("editor.rewards.reward.item-lore.display-name", Map.of("name", reward.displayName())));
        lore.add(text("editor.rewards.reward.item-lore.item", Map.of("item", resolveRewardItemLabel(reward))));
        lore.add(text("editor.common.action.left-edit"));
        lore.add(text("editor.common.action.right-clone"));
        lore.add(text("editor.common.action.shift-right-delete"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorNoItalic("&e" + reward.displayName()));
            meta.lore(lore.stream().map(TextUtil::colorNoItalic).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildRewardDisplayItem(Reward reward) {
        ItemStack baseItem = reward.displayItemStack();
        if (baseItem == null) {
            baseItem = reward.itemStack();
        }
        if (baseItem != null) {
            ItemStack item = baseItem.clone();
            item.setAmount(1);
            return item;
        }
        return new ItemStack(parseMaterial(reward.item()));
    }

    private ItemStack buildRarityItem(com.extracrates.model.RarityDefinition rarity, Material material) {
        List<String> lore = new ArrayList<>();
        lore.add(text("editor.rewards.rarity.item-lore.chance", Map.of("chance", String.valueOf(rarity.chance()))));
        String pathValue = rarity.path() == null || rarity.path().isBlank() ? text("editor.common.none") : rarity.path();
        lore.add(text("editor.rewards.rarity.item-lore.path", Map.of("path", pathValue)));
        lore.add(text("editor.rewards.rarity.item-lore.select"));
        return buildItem(material, "&e" + rarity.displayName(), lore);
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
            case "commands" -> parseCommands(trimmed);
            default -> ValidationResult.valid(trimmed);
        };
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

    private String resolveDisplayItemLabel(Reward reward) {
        if (reward == null) {
            return text("editor.common.none");
        }
        ItemStack stack = reward.displayItemStack();
        if (stack != null) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return TextUtil.serializeLegacy(meta.displayName());
            }
            return stack.getType().name().toLowerCase(Locale.ROOT);
        }
        return text("editor.common.none");
    }

    private Material parseMaterial(String item) {
        Material material = Material.matchMaterial(item.toUpperCase(Locale.ROOT));
        return material != null ? material : Material.CHEST;
    }

    private Material resolveRarityMaterial(String rarityId) {
        if (rarityId == null) {
            return Material.WHITE_WOOL;
        }
        return switch (rarityId.trim().toLowerCase(Locale.ROOT)) {
            case "rare" -> Material.LIGHT_BLUE_WOOL;
            case "epic" -> Material.PURPLE_WOOL;
            case "legendary" -> Material.ORANGE_WOOL;
            case "magistral" -> Material.MAGENTA_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }

    private String resolveRarityLabel(Reward reward) {
        if (reward == null || reward.rarity() == null || reward.rarity().isBlank()) {
            return text("editor.common.none");
        }
        String rarityId = reward.rarity();
        com.extracrates.model.RarityDefinition rarity = configLoader.getRarities().get(rarityId.toLowerCase(Locale.ROOT));
        if (rarity == null) {
            return rarityId;
        }
        return rarity.displayName();
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

    private Component rarityTitle(String rewardId) {
        return TextUtil.colorNoItalic(text("editor.rewards.rarity.title", Map.of("reward", rewardId)));
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
