package com.extracrates.gui;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.runtime.core.SessionManager;
import com.extracrates.storage.CrateOpenEntry;
import com.extracrates.storage.OpenHistoryFilter;
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class OpenHistoryGui implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 27;
    private static final int PREVIOUS_PAGE_SLOT = 47;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int CRATE_FILTER_SLOT = 49;
    private static final int DATE_FILTER_SLOT = 51;
    private static final int RESET_FILTERS_SLOT = 45;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withLocale(new Locale("es", "ES"))
            .withZone(ZoneId.systemDefault());

    private final ConfigLoader configLoader;
    private final SessionManager sessionManager;
    private final Map<UUID, HistoryState> states = new HashMap<>();

    public OpenHistoryGui(ExtraCratesPlugin plugin, ConfigLoader configLoader, SessionManager sessionManager) {
        this.configLoader = configLoader;
        this.sessionManager = sessionManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(@NotNull Player player) {
        open(player, 0);
    }

    private void open(@NotNull Player player, int pageIndex) {
        HistoryState state = states.computeIfAbsent(player.getUniqueId(), key -> new HistoryState());
        state.pageIndex = Math.max(0, pageIndex);

        List<String> crateOptions = getCrateOptions();
        if (state.crateIndex >= crateOptions.size()) {
            state.crateIndex = 0;
        }

        HistoryDateFilter dateFilter = state.dateFilter;
        OpenHistoryFilter filter = buildFilter(crateOptions.get(state.crateIndex), dateFilter);
        int offset = state.pageIndex * PAGE_SIZE;
        List<CrateOpenEntry> entries = sessionManager.getOpenHistory(player.getUniqueId(), filter, PAGE_SIZE + 1, offset);
        boolean hasNext = entries.size() > PAGE_SIZE;
        if (hasNext) {
            entries = entries.subList(0, PAGE_SIZE);
        }

        String title = configLoader.getMainConfig().getString("gui.history-title", "&8Historial de Crates");
        HistoryGuiHolder holder = new HistoryGuiHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, TextUtil.colorNoItalic(title));
        holder.setInventory(inventory);
        int slot = 9;
        int count = 0;
        if (entries.isEmpty()) {
            inventory.setItem(22, buildStaticItem(Material.BARRIER, "&cSin aperturas", List.of("&7No hay registros para los filtros actuales.")));
        } else {
            for (CrateOpenEntry entry : entries) {
                if (count >= PAGE_SIZE) {
                    break;
                }
                inventory.setItem(slot++, buildEntryItem(entry));
                count++;
            }
        }

        if (state.pageIndex > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, buildNavItem(Material.ARROW, "&ePágina anterior"));
        }
        if (hasNext) {
            inventory.setItem(NEXT_PAGE_SLOT, buildNavItem(Material.ARROW, "&ePágina siguiente"));
        }
        inventory.setItem(CRATE_FILTER_SLOT, buildCrateFilterItem(crateOptions, state.crateIndex));
        inventory.setItem(DATE_FILTER_SLOT, buildDateFilterItem(state.dateFilter));
        inventory.setItem(RESET_FILTERS_SLOT, buildStaticItem(Material.BOOK, "&aResetear filtros", List.of("&7Haz click para limpiar filtros.")));

        MenuSpacer.applyTopRow(inventory, buildSpacerItem());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof HistoryGuiHolder holder)) {
            return;
        }
        if (!holder.playerId().equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        HistoryState state = states.computeIfAbsent(player.getUniqueId(), key -> new HistoryState());
        int slot = event.getSlot();
        if (slot == PREVIOUS_PAGE_SLOT) {
            open(player, state.pageIndex - 1);
            return;
        }
        if (slot == NEXT_PAGE_SLOT) {
            open(player, state.pageIndex + 1);
            return;
        }
        if (slot == CRATE_FILTER_SLOT) {
            state.crateIndex = (state.crateIndex + 1) % getCrateOptions().size();
            state.pageIndex = 0;
            open(player, state.pageIndex);
            return;
        }
        if (slot == DATE_FILTER_SLOT) {
            state.dateFilter = state.dateFilter.next();
            state.pageIndex = 0;
            open(player, state.pageIndex);
            return;
        }
        if (slot == RESET_FILTERS_SLOT) {
            state.reset();
            open(player, state.pageIndex);
        }
    }

    private List<String> getCrateOptions() {
        List<String> options = new ArrayList<>();
        options.add("");
        configLoader.getCrates().values().stream()
                .sorted(Comparator.comparing(CrateDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
                .forEach(crate -> options.add(crate.id()));
        return options;
    }

    private OpenHistoryFilter buildFilter(String crateId, HistoryDateFilter dateFilter) {
        String normalizedCrateId = crateId == null || crateId.isEmpty() ? null : crateId;
        Instant now = Instant.now();
        Instant from = dateFilter.duration() != null ? now.minus(dateFilter.duration()) : null;
        return new OpenHistoryFilter(normalizedCrateId, from, null);
    }

    private ItemStack buildEntryItem(CrateOpenEntry entry) {
        String crateName = resolveCrateName(entry.crateId());
        String rewardName = resolveRewardName(entry.rewardId());
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorNoItalic("&e" + rewardName));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.colorNoItalic("&7Crate: &f" + crateName));
            lore.add(TextUtil.colorNoItalic("&7Reward ID: &f" + entry.rewardId()));
            lore.add(TextUtil.colorNoItalic("&7Fecha: &f" + DATE_FORMAT.format(entry.openedAt())));
            lore.add(TextUtil.colorNoItalic("&7Servidor: &f" + entry.serverId()));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String resolveCrateName(String crateId) {
        if (crateId == null) {
            return "Desconocida";
        }
        CrateDefinition crate = configLoader.getCrates().get(crateId);
        if (crate == null) {
            return crateId;
        }
        return crate.displayName();
    }

    private String resolveRewardName(String rewardId) {
        if (rewardId == null || rewardId.isEmpty()) {
            return "Desconocida";
        }
        Optional<Reward> reward = configLoader.findRewardById(rewardId);
        return reward.map(Reward::displayName).orElse(rewardId);
    }

    private ItemStack buildNavItem(Material material, String name) {
        return buildStaticItem(material, name, List.of());
    }

    private ItemStack buildSpacerItem() {
        return buildStaticItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private ItemStack buildCrateFilterItem(List<String> options, int index) {
        String current = index <= 0 ? "Todas" : options.get(index);
        return buildStaticItem(
                Material.CHEST,
                "&6Filtro crate",
                List.of(
                        "&7Actual: &f" + current,
                        "&fClick para cambiar."
                )
        );
    }

    private ItemStack buildDateFilterItem(HistoryDateFilter filter) {
        return buildStaticItem(
                Material.CLOCK,
                "&6Filtro fecha",
                List.of(
                        "&7Actual: &f" + filter.label(),
                        "&fClick para cambiar."
                )
        );
    }

    private ItemStack buildStaticItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorNoItalic(name));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(TextUtil.colorNoItalic(line));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private enum HistoryDateFilter {
        ALL("Todas", null),
        LAST_7_DAYS("Últimos 7 días", Duration.ofDays(7)),
        LAST_30_DAYS("Últimos 30 días", Duration.ofDays(30));

        private final String label;
        private final Duration duration;

        HistoryDateFilter(String label, Duration duration) {
            this.label = label;
            this.duration = duration;
        }

        public String label() {
            return label;
        }

        public Duration duration() {
            return duration;
        }

        public HistoryDateFilter next() {
            HistoryDateFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private static final class HistoryState {
        private int pageIndex;
        private int crateIndex;
        private HistoryDateFilter dateFilter = HistoryDateFilter.ALL;

        private void reset() {
            pageIndex = 0;
            crateIndex = 0;
            dateFilter = HistoryDateFilter.ALL;
        }
    }

    private static final class HistoryGuiHolder implements org.bukkit.inventory.InventoryHolder {
        private final UUID playerId;
        private Inventory inventory;

        private HistoryGuiHolder(UUID playerId) {
            this.playerId = playerId;
        }

        private UUID playerId() {
            return playerId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
