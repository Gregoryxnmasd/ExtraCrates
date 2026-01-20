package com.extracrates.gui;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.model.RewardPool;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused")
public class CrateGui implements Listener {
    private static final int PAGE_SIZE = 27;
    private static final int INVENTORY_SIZE = 36;
    private static final int PREVIOUS_PAGE_SLOT = 30;
    private static final int NEXT_PAGE_SLOT = 32;
    private static final int ACTION_MENU_SIZE = 27;
    private static final int ACTION_OPEN_SLOT = 11;
    private static final int ACTION_PREVIEW_SLOT = 15;
    private static final int ACTION_BACK_SLOT = 22;
    private static final int REWARD_INVENTORY_SIZE = 54;
    private static final int REWARD_PAGE_SIZE = 45;
    private static final int REWARD_PREVIOUS_PAGE_SLOT = 45;
    private static final int REWARD_NEXT_PAGE_SLOT = 53;
    private static final int REWARD_FILTER_SLOT = 49;
    private static final int REWARD_BACK_SLOT = 48;
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final SessionManager sessionManager;
    private final LanguageManager languageManager;

    public CrateGui(ExtraCratesPlugin plugin, ConfigLoader configLoader, SessionManager sessionManager) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.sessionManager = sessionManager;
        this.languageManager = plugin.getLanguageManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(@NotNull Player player) {
        open(player, 0);
    }

    public void open(@NotNull Player player, int pageIndex) {
        String title = configLoader.getMainConfig().getString("gui.title", "&8ExtraCrates");
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        crates.sort(Comparator.comparing((CrateDefinition crate) -> resolveCreatedAt(crate.id()))
                .thenComparing(CrateDefinition::id, String.CASE_INSENSITIVE_ORDER));
        int totalPages = Math.max(1, (int) Math.ceil(crates.size() / (double) PAGE_SIZE));
        int safePageIndex = Math.max(0, Math.min(pageIndex, totalPages - 1));
        CrateGuiHolder holder = new CrateGuiHolder(safePageIndex);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, TextUtil.colorNoItalic(title));
        holder.setInventory(inventory);
        int startIndex = safePageIndex * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, crates.size());
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            CrateDefinition crate = crates.get(i);
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.colorNoItalic(crate.displayName()));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.colorNoItalic(languageManager.getRaw(
                        "gui.crate.list.item-lore.id",
                        java.util.Map.of("id", crate.id())
                )));
                lore.add(TextUtil.colorNoItalic(languageManager.getRaw(
                        "gui.crate.list.item-lore.type",
                        java.util.Map.of("type", crate.type().name())
                )));
                lore.add(TextUtil.colorNoItalic(languageManager.getRaw(
                        "gui.crate.list.item-lore.preview-hint",
                        java.util.Collections.emptyMap()
                )));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot++, item);
        }
        if (safePageIndex > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, buildNavItem(Material.ARROW, languageManager.getRaw(
                    "gui.crate.list.nav.previous",
                    java.util.Collections.emptyMap()
            )));
        }
        if (safePageIndex < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, buildNavItem(Material.ARROW, languageManager.getRaw(
                    "gui.crate.list.nav.next",
                    java.util.Collections.emptyMap()
            )));
        }
        MenuSpacer.apply(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        int slot = event.getSlot();
        if (holder instanceof CrateGuiHolder crateHolder) {
            handleCrateListClick(player, crateHolder, slot, event.isRightClick());
            return;
        }
        if (holder instanceof CrateActionHolder actionHolder) {
            handleActionMenuClick(player, actionHolder, slot);
            return;
        }
        if (holder instanceof RewardPreviewHolder rewardHolder) {
            handleRewardPreviewClick(player, rewardHolder, slot);
        }
    }

    private void handleCrateListClick(@NotNull Player player, @NotNull CrateGuiHolder holder, int slot, boolean rightClick) {
        if (slot == PREVIOUS_PAGE_SLOT) {
            open(player, holder.pageIndex() - 1);
            return;
        }
        if (slot == NEXT_PAGE_SLOT) {
            open(player, holder.pageIndex() + 1);
            return;
        }
        List<CrateDefinition> crates = new ArrayList<>(configLoader.getCrates().values());
        int crateIndex = holder.pageIndex() * PAGE_SIZE + slot;
        if (crateIndex < 0 || crateIndex >= crates.size()) {
            return;
        }
        CrateDefinition crate = crates.get(crateIndex);
        if (rightClick) {
            openActionMenu(player, crate, holder.pageIndex());
            return;
        }
        sessionManager.openCrate(player, crate, false);
        player.closeInventory();
    }

    private void handleActionMenuClick(@NotNull Player player, @NotNull CrateActionHolder holder, int slot) {
        CrateDefinition crate = configLoader.getCrates().get(holder.crateId());
        if (crate == null) {
            open(player, holder.pageIndex());
            return;
        }
        if (slot == ACTION_OPEN_SLOT) {
            sessionManager.openCrate(player, crate, false);
            player.closeInventory();
            return;
        }
        if (slot == ACTION_PREVIEW_SLOT) {
            openRewardPreview(player, crate, holder.pageIndex(), 0, RewardFilter.ALL);
            return;
        }
        if (slot == ACTION_BACK_SLOT) {
            open(player, holder.pageIndex());
        }
    }

    private void handleRewardPreviewClick(@NotNull Player player, @NotNull RewardPreviewHolder holder, int slot) {
        CrateDefinition crate = configLoader.getCrates().get(holder.crateId());
        if (crate == null) {
            open(player, holder.pageIndex());
            return;
        }
        if (slot == REWARD_BACK_SLOT) {
            openActionMenu(player, crate, holder.pageIndex());
            return;
        }
        if (slot == REWARD_PREVIOUS_PAGE_SLOT) {
            openRewardPreview(player, crate, holder.pageIndex(), holder.rewardPageIndex() - 1, holder.filter());
            return;
        }
        if (slot == REWARD_NEXT_PAGE_SLOT) {
            openRewardPreview(player, crate, holder.pageIndex(), holder.rewardPageIndex() + 1, holder.filter());
            return;
        }
        if (slot == REWARD_FILTER_SLOT) {
            openRewardPreview(player, crate, holder.pageIndex(), 0, holder.filter().next());
        }
    }

    private @NotNull ItemStack buildNavItem(@NotNull Material material, @NotNull String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorNoItalic(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private @NotNull ItemStack buildItem(@NotNull Material material, @NotNull String name, @NotNull List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorNoItalic(name));
            meta.lore(loreLines.stream().map(TextUtil::colorNoItalic).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openActionMenu(@NotNull Player player, @NotNull CrateDefinition crate, int pageIndex) {
        CrateActionHolder holder = new CrateActionHolder(pageIndex, crate.id());
        Inventory inventory = Bukkit.createInventory(holder, ACTION_MENU_SIZE, TextUtil.colorNoItalic(languageManager.getRaw(
                "gui.crate.action.title",
                java.util.Map.of("crate", crate.displayName())
        )));
        holder.setInventory(inventory);
        String poolValue = crate.rewardsPool() != null ? crate.rewardsPool() : "";
        inventory.setItem(ACTION_OPEN_SLOT, buildItem(
                Material.CHEST,
                languageManager.getRaw("gui.crate.action.open.name", java.util.Collections.emptyMap()),
                List.of(languageManager.getRaw("gui.crate.action.open.lore", java.util.Collections.emptyMap()))
        ));
        inventory.setItem(ACTION_PREVIEW_SLOT, buildItem(
                Material.BOOK,
                languageManager.getRaw("gui.crate.action.preview.name", java.util.Collections.emptyMap()),
                languageManager.getRawList("gui.crate.action.preview.lore", java.util.Map.of("pool", poolValue))
        ));
        inventory.setItem(ACTION_BACK_SLOT, buildItem(
                Material.ARROW,
                languageManager.getRaw("gui.crate.action.back.name", java.util.Collections.emptyMap()),
                List.of(languageManager.getRaw("gui.crate.action.back.lore", java.util.Collections.emptyMap()))
        ));
        MenuSpacer.apply(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private void openRewardPreview(
            @NotNull Player player,
            @NotNull CrateDefinition crate,
            int pageIndex,
            int rewardPageIndex,
            @NotNull RewardFilter filter
    ) {
        RewardPool pool = crate.rewardsPool() != null ? configLoader.getRewardPools().get(crate.rewardsPool()) : null;
        List<Reward> rewards = pool != null ? pool.rewards() : List.of();
        List<Reward> filtered = filterRewards(rewards, filter);
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) REWARD_PAGE_SIZE));
        int safeRewardPage = Math.max(0, Math.min(rewardPageIndex, totalPages - 1));
        RewardPreviewHolder holder = new RewardPreviewHolder(pageIndex, crate.id(), safeRewardPage, filter);
        Inventory inventory = Bukkit.createInventory(holder, REWARD_INVENTORY_SIZE, TextUtil.colorNoItalic(languageManager.getRaw(
                "gui.crate.rewards.title",
                java.util.Map.of("crate", crate.displayName())
        )));
        holder.setInventory(inventory);
        int startIndex = safeRewardPage * REWARD_PAGE_SIZE;
        int endIndex = Math.min(startIndex + REWARD_PAGE_SIZE, filtered.size());
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Reward reward = filtered.get(i);
            inventory.setItem(slot++, buildRewardItem(reward, rewards));
        }
        inventory.setItem(REWARD_BACK_SLOT, buildItem(
                Material.ARROW,
                languageManager.getRaw("gui.crate.rewards.back.name", java.util.Collections.emptyMap()),
                List.of(languageManager.getRaw("gui.crate.rewards.back.lore", java.util.Collections.emptyMap()))
        ));
        inventory.setItem(REWARD_FILTER_SLOT, buildFilterItem(filter));
        if (safeRewardPage > 0) {
            inventory.setItem(REWARD_PREVIOUS_PAGE_SLOT, buildNavItem(Material.ARROW, languageManager.getRaw(
                    "gui.crate.rewards.nav.previous",
                    java.util.Collections.emptyMap()
            )));
        }
        if (safeRewardPage < totalPages - 1) {
            inventory.setItem(REWARD_NEXT_PAGE_SLOT, buildNavItem(Material.ARROW, languageManager.getRaw(
                    "gui.crate.rewards.nav.next",
                    java.util.Collections.emptyMap()
            )));
        }
        MenuSpacer.apply(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    private @NotNull ItemStack buildSpacerItem() {
        return buildNavItem(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private @NotNull ItemStack buildRewardItem(@NotNull Reward reward, @NotNull List<Reward> allRewards) {
        Material material = parseMaterial(reward.item());
        int amount = Math.max(1, Math.min(64, reward.amount()));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorNoItalic("&e" + reward.displayName()));
            List<String> lore = new ArrayList<>();
            lore.add(languageManager.getRaw("gui.crate.rewards.reward-lore.id", java.util.Map.of("id", reward.id())));
            lore.add(languageManager.getRaw("gui.crate.rewards.reward-lore.chance", java.util.Map.of("chance", String.valueOf(reward.chance()))));
            lore.add(languageManager.getRaw("gui.crate.rewards.reward-lore.rarity", java.util.Map.of(
                    "rarity",
                    describeRarity(reward, allRewards)
            )));
            meta.lore(lore.stream().map(TextUtil::colorNoItalic).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private @NotNull ItemStack buildFilterItem(@NotNull RewardFilter filter) {
        List<String> lore = new ArrayList<>();
        lore.add(languageManager.getRaw("gui.crate.rewards.filter.current", java.util.Map.of("filter", filter.label(languageManager))));
        lore.add(languageManager.getRaw("gui.crate.rewards.filter.click", java.util.Collections.emptyMap()));
        lore.add(filter.description(languageManager));
        return buildItem(
                Material.COMPARATOR,
                languageManager.getRaw("gui.crate.rewards.filter.name", java.util.Collections.emptyMap()),
                lore
        );
    }

    private @NotNull List<Reward> filterRewards(@NotNull List<Reward> rewards, @NotNull RewardFilter filter) {
        if (filter == RewardFilter.ALL) {
            return rewards;
        }
        double maxChance = rewards.stream().mapToDouble(Reward::chance).max().orElse(0);
        List<Reward> filtered = new ArrayList<>();
        for (Reward reward : rewards) {
            double ratio = maxChance > 0 ? reward.chance() / maxChance : 0;
            if (filter.matchesRatio(ratio)) {
                filtered.add(reward);
            }
        }
        filtered.sort(Comparator.comparingDouble(Reward::chance).reversed());
        return filtered;
    }

    private long resolveCreatedAt(String crateId) {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getLong("crates." + crateId + ".created-at", 0L);
    }

    private @NotNull String describeRarity(@NotNull Reward reward, @NotNull List<Reward> rewards) {
        double maxChance = rewards.stream().mapToDouble(Reward::chance).max().orElse(0);
        double ratio = maxChance > 0 ? reward.chance() / maxChance : 0;
        if (ratio >= RewardFilter.COMMON.minRatio()) {
            return RewardFilter.COMMON.label(languageManager);
        }
        if (ratio >= RewardFilter.UNCOMMON.minRatio()) {
            return RewardFilter.UNCOMMON.label(languageManager);
        }
        if (ratio >= RewardFilter.RARE.minRatio()) {
            return RewardFilter.RARE.label(languageManager);
        }
        return RewardFilter.EPIC.label(languageManager);
    }

    private @NotNull Material parseMaterial(@NotNull String item) {
        Material material = Material.matchMaterial(item.toUpperCase(Locale.ROOT));
        return material != null ? material : Material.CHEST;
    }

    private static final class CrateGuiHolder implements org.bukkit.inventory.InventoryHolder {
        private final int pageIndex;
        private Inventory inventory;

        private CrateGuiHolder(int pageIndex) {
            this.pageIndex = pageIndex;
        }

        private int pageIndex() {
            return pageIndex;
        }

        private void setInventory(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final class CrateActionHolder implements org.bukkit.inventory.InventoryHolder {
        private final int pageIndex;
        private final String crateId;
        private Inventory inventory;

        private CrateActionHolder(int pageIndex, @NotNull String crateId) {
            this.pageIndex = pageIndex;
            this.crateId = crateId;
        }

        private int pageIndex() {
            return pageIndex;
        }

        private String crateId() {
            return crateId;
        }

        private void setInventory(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final class RewardPreviewHolder implements org.bukkit.inventory.InventoryHolder {
        private final int pageIndex;
        private final String crateId;
        private final int rewardPageIndex;
        private final RewardFilter filter;
        private Inventory inventory;

        private RewardPreviewHolder(int pageIndex, @NotNull String crateId, int rewardPageIndex, @NotNull RewardFilter filter) {
            this.pageIndex = pageIndex;
            this.crateId = crateId;
            this.rewardPageIndex = rewardPageIndex;
            this.filter = filter;
        }

        private int pageIndex() {
            return pageIndex;
        }

        private String crateId() {
            return crateId;
        }

        private int rewardPageIndex() {
            return rewardPageIndex;
        }

        private RewardFilter filter() {
            return filter;
        }

        private void setInventory(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private enum RewardFilter {
        ALL("gui.crate.rewards.filter-labels.all", "gui.crate.rewards.filter-desc.all", 0),
        COMMON("gui.crate.rewards.filter-labels.common", "gui.crate.rewards.filter-desc.common", 0.6),
        UNCOMMON("gui.crate.rewards.filter-labels.uncommon", "gui.crate.rewards.filter-desc.uncommon", 0.3),
        RARE("gui.crate.rewards.filter-labels.rare", "gui.crate.rewards.filter-desc.rare", 0.1),
        EPIC("gui.crate.rewards.filter-labels.epic", "gui.crate.rewards.filter-desc.epic", 0);

        private final String labelKey;
        private final String descriptionKey;
        private final double minRatio;

        RewardFilter(String labelKey, String descriptionKey, double minRatio) {
            this.labelKey = labelKey;
            this.descriptionKey = descriptionKey;
            this.minRatio = minRatio;
        }

        private String label(LanguageManager languageManager) {
            return languageManager.getRaw(labelKey, java.util.Collections.emptyMap());
        }

        private String description(LanguageManager languageManager) {
            return languageManager.getRaw(descriptionKey, java.util.Collections.emptyMap());
        }

        private double minRatio() {
            return minRatio;
        }

        private boolean matchesRatio(double ratio) {
            return switch (this) {
                case ALL -> true;
                case COMMON, UNCOMMON, RARE -> ratio >= minRatio;
                case EPIC -> ratio < RARE.minRatio;
            };
        }

        private RewardFilter next() {
            RewardFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
