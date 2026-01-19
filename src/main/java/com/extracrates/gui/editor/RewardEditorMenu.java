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
    // Layout: acciones principales al centro, navegación en fila inferior.
    private static final int SLOT_LIST_CREATE = 45;
    private static final int SLOT_LIST_DELETE = 47;
    private static final int SLOT_LIST_BACK = 49;
    private static final int SLOT_LIST_REFRESH = 53;
    private static final int SLOT_DETAIL_DELETE = 18;
    private static final int SLOT_DETAIL_BACK = 22;
    private static final int SLOT_DETAIL_REFRESH = 26;
    private static final int[] LIST_NAV_FILLER_SLOTS = {46, 48, 50, 51, 52};
    private static final int[] DETAIL_NAV_FILLER_SLOTS = {19, 20, 21, 23, 24, 25};

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
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_CREATE, buildItem(Material.LIME_CONCRETE, "&aCrear pool", List.of("&7Nuevo pool de rewards.")));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW, "&eVolver", List.of("&7Regresar al menú principal.")));
        inventory.setItem(SLOT_LIST_REFRESH, buildItem(Material.BOOK, "&bRefrescar", List.of("&7Recargar lista.")));
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
        fillListNavigation(inventory);
        inventory.setItem(SLOT_LIST_CREATE, buildItem(Material.LIME_CONCRETE, "&aCrear reward", List.of("&7Agregar nueva reward.")));
        inventory.setItem(SLOT_LIST_DELETE, buildItem(Material.RED_CONCRETE, "&cBorrar pool", List.of("&7Eliminar pool actual.")));
        inventory.setItem(SLOT_LIST_BACK, buildItem(Material.ARROW, "&eVolver", List.of("&7Regresar a pools.")));
        inventory.setItem(SLOT_LIST_REFRESH, buildItem(Material.BOOK, "&bRefrescar", List.of("&7Recargar lista.")));
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
        Inventory inventory = Bukkit.createInventory(player, 27, TextUtil.color("&8Reward: " + rewardId));
        inventory.setItem(9, buildItem(Material.NAME_TAG, "&eDisplay Name", List.of(
                "&7Actual: &f" + (reward != null ? reward.displayName() : rewardId),
                "&7Click para editar."
        )));
        inventory.setItem(10, buildItem(Material.GOLD_NUGGET, "&eChance", List.of(
                "&7Actual: &f" + (reward != null ? reward.chance() : 0),
                "&7Click para editar."
        )));
        inventory.setItem(11, buildItem(Material.CHEST, "&eItem", List.of(
                "&7Actual: &f" + (reward != null ? reward.item() : "STONE"),
                "&7Click para editar."
        )));
        inventory.setItem(12, buildItem(Material.PAPER, "&eAmount", List.of(
                "&7Actual: &f" + (reward != null ? reward.amount() : 1),
                "&7Click para editar."
        )));
        inventory.setItem(13, buildItem(Material.COMMAND_BLOCK, "&eCommands", List.of(
                "&7Actual: &f" + (reward != null ? reward.commands().size() : 0),
                "&7Click para editar."
        )));
        inventory.setItem(14, buildItem(Material.ENCHANTED_BOOK, "&eEnchantments", List.of(
                "&7Actual: &f" + (reward != null ? reward.enchantments().size() : 0),
                "&7Click para editar."
        )));
        inventory.setItem(15, buildItem(Material.GLOWSTONE_DUST, "&eGlow", List.of(
                "&7Actual: &f" + (reward != null && reward.glow()),
                "&7Click para editar."
        )));
        inventory.setItem(16, buildItem(Material.SLIME_BALL, "&eCustom Model", List.of(
                "&7Actual: &f" + (reward != null ? emptyFallback(reward.customModel()) : ""),
                "&7Click para editar."
        )));
        inventory.setItem(17, buildItem(Material.FILLED_MAP, "&eMap Image", List.of(
                "&7Actual: &f" + (reward != null ? emptyFallback(reward.mapImage()) : ""),
                "&7Click para editar."
        )));
        fillDetailNavigation(inventory);
        inventory.setItem(SLOT_DETAIL_DELETE, buildItem(Material.RED_CONCRETE, "&cBorrar reward", List.of("&7Eliminar reward actual.")));
        inventory.setItem(SLOT_DETAIL_BACK, buildItem(Material.ARROW, "&eVolver", List.of("&7Regresar al pool.")));
        inventory.setItem(SLOT_DETAIL_REFRESH, buildItem(Material.BOOK, "&bRefrescar", List.of("&7Recargar datos.")));
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
        if (slot == SLOT_LIST_REFRESH) {
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
        if (slot == SLOT_LIST_REFRESH) {
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
            case 9 -> promptRewardField(player, poolId, rewardId, "display-name", "Display name nuevo");
            case 10 -> promptRewardField(player, poolId, rewardId, "chance", "Chance (número)");
            case 11 -> promptRewardField(player, poolId, rewardId, "item", "Material del item");
            case 12 -> promptRewardField(player, poolId, rewardId, "amount", "Cantidad");
            case 13 -> promptRewardField(player, poolId, rewardId, "commands", "Comandos (separa con ';') o 'none'");
            case 14 -> promptRewardField(player, poolId, rewardId, "enchantments", "Encantamientos clave:nivel separados por ',' o 'none'");
            case 15 -> promptRewardField(player, poolId, rewardId, "glow", "Glow (true/false)");
            case 16 -> promptRewardField(player, poolId, rewardId, "custom-model", "Custom model (texto) o 'none'");
            case 17 -> promptRewardField(player, poolId, rewardId, "map-image", "Map image (texto) o 'none'");
            case SLOT_DETAIL_DELETE -> confirmDeleteReward(player, poolId, rewardId);
            case SLOT_DETAIL_BACK -> openPoolDetail(player, poolId);
            case SLOT_DETAIL_REFRESH -> openRewardDetail(player, poolId, rewardId);
            default -> {
            }
        }
    }

    private void confirmDeletePool(Player player, String poolId) {
        confirmationMenu.open(player, "&8Confirmar borrado", "Eliminar pool " + poolId, () -> {
            deletePool(poolId);
            player.sendMessage(Component.text("Pool eliminada y guardada en YAML."));
            openPools(player);
        }, () -> openPoolDetail(player, poolId));
    }

    private void confirmDeleteReward(Player player, String poolId, String rewardId) {
        confirmationMenu.open(player, "&8Confirmar borrado", "Eliminar reward " + rewardId, () -> {
            deleteReward(poolId, rewardId);
            player.sendMessage(Component.text("Reward eliminada y guardada en YAML."));
            openPoolDetail(player, poolId);
        }, () -> openRewardDetail(player, poolId, rewardId));
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
            confirmationMenu.open(player, "&8Confirmar creación", "Crear pool " + input, () -> {
                createPool(input);
                player.sendMessage(Component.text("Pool creada y guardada en YAML."));
                openPools(player);
            }, () -> openPools(player));
        }, () -> openPools(player));
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
        }, () -> openPools(player));
    }

    private void promptPoolRoll(Player player, String poolId) {
        if (inputManager.hasPending(player)) {
            player.sendMessage(languageManager.getMessage("editor.input.pending"));
            return;
        }
        inputManager.requestInput(player, "Nuevo roll-count", input -> confirmationMenu.open(
                player,
                "&8Confirmar cambio",
                "Actualizar roll-count de " + poolId,
                () -> {
                    updatePoolField(poolId, "roll-count", parseInt(input));
                    player.sendMessage(Component.text("Pool actualizada y guardada en YAML."));
                    openPoolDetail(player, poolId);
                },
                () -> openPoolDetail(player, poolId)
        ), () -> openPoolDetail(player, poolId));
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
            confirmationMenu.open(player, "&8Confirmar creación", "Crear reward " + input, () -> {
                createReward(poolId, input);
                player.sendMessage(Component.text("Reward creada y guardada en YAML."));
                openPoolDetail(player, poolId);
            }, () -> openPoolDetail(player, poolId));
        }, () -> openPoolDetail(player, poolId));
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
        }, () -> openPoolDetail(player, poolId));
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
                    "&8Confirmar cambio",
                    "Actualizar " + field + " de " + rewardId,
                    () -> {
                        updateRewardField(poolId, rewardId, field, validation.value());
                        player.sendMessage(Component.text("Reward actualizada y guardada en YAML."));
                        openRewardDetail(player, poolId, rewardId);
                    },
                    () -> openRewardDetail(player, poolId, rewardId)
            );
        }, () -> openRewardDetail(player, poolId, rewardId));
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

    private void fillListNavigation(Inventory inventory) {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        inventory.setItem(SLOT_LIST_DELETE, buildItem(Material.RED_CONCRETE, "&cBorrar", List.of("&7Usa el detalle para borrar.")));
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
