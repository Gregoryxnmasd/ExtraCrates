package com.extracrates.config;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.model.CrateDefinition;
import com.extracrates.model.Reward;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LanguageManager {
    private final ExtraCratesPlugin plugin;
    private FileConfiguration messages;

    public LanguageManager(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String language = plugin.getConfig().getString("language", "en_us");
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "lang/en_us.yml");
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public Component getMessage(String key) {
        return getMessage(key, Collections.emptyMap());
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        return TextUtil.color(getRaw(key, placeholders));
    }

    public Component getMessage(
            String key,
            Player player,
            CrateDefinition crate,
            Reward reward,
            Long cooldownSeconds
    ) {
        return getMessage(key, player, crate, reward, cooldownSeconds, Collections.emptyMap());
    }

    public Component getMessage(
            String key,
            Player player,
            CrateDefinition crate,
            Reward reward,
            Long cooldownSeconds,
            Map<String, String> placeholders
    ) {
        return TextUtil.color(getRaw(key, player, crate, reward, cooldownSeconds, placeholders));
    }

    public void sendActionBar(Player player, String key) {
        sendActionBar(player, key, Collections.emptyMap());
    }

    public void sendActionBar(Player player, String key, Map<String, String> placeholders) {
        if (player == null) {
            return;
        }
        player.sendActionBar(getMessage(key, placeholders));
    }

    public BossBar createBossBar(String key, float progress, BossBar.Color color, BossBar.Overlay overlay) {
        return createBossBar(key, progress, color, overlay, Collections.emptyMap());
    }

    public BossBar createBossBar(String key, float progress, BossBar.Color color, BossBar.Overlay overlay, Map<String, String> placeholders) {
        return BossBar.bossBar(getMessage(key, placeholders), progress, color, overlay);
    }

    public String getRaw(String key, Map<String, String> placeholders) {
        if (messages == null) {
            return key;
        }
        if (!messages.contains(key)) {
            return "";
        }
        String value = messages.getString(key, key);
        if (isSuppressed(value)) {
            return "";
        }
        return applyPlaceholders(value, placeholders);
    }

    public List<String> getRawList(String key, Map<String, String> placeholders) {
        if (messages == null) {
            return List.of(key);
        }
        if (!messages.contains(key)) {
            return List.of();
        }
        if (!messages.isList(key)) {
            return List.of(getRaw(key, placeholders));
        }
        List<String> result = new ArrayList<>();
        for (String line : messages.getStringList(key)) {
            if (isSuppressed(line)) {
                continue;
            }
            result.add(applyPlaceholders(line, placeholders));
        }
        return result;
    }

    public String getRaw(
            String key,
            Player player,
            CrateDefinition crate,
            Reward reward,
            Long cooldownSeconds,
            Map<String, String> placeholders
    ) {
        if (messages == null) {
            return key;
        }
        if (!messages.contains(key)) {
            return "";
        }
        String value = messages.getString(key, key);
        if (isSuppressed(value)) {
            return "";
        }
        return applyPlaceholders(value, buildPlaceholders(player, crate, reward, cooldownSeconds, placeholders));
    }

    private Map<String, String> buildPlaceholders(
            Player player,
            CrateDefinition crate,
            Reward reward,
            Long cooldownSeconds,
            Map<String, String> placeholders
    ) {
        Map<String, String> merged = new HashMap<>();
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        if (player != null) {
            merged.putIfAbsent("player", player.getName());
        }
        if (crate != null) {
            merged.putIfAbsent("crate_id", crate.id());
            merged.putIfAbsent("crate_name", crate.displayName());
        }
        if (reward != null) {
            merged.putIfAbsent("reward", reward.displayName());
            merged.putIfAbsent("reward_id", reward.id());
            merged.putIfAbsent("reward_name", reward.displayName());
            merged.putIfAbsent("extracrates_reward_id", reward.id());
            merged.putIfAbsent("extracrates_reward_name", reward.displayName());
        } else {
            merged.putIfAbsent("reward_id", "none");
            merged.putIfAbsent("reward_name", "none");
            merged.putIfAbsent("extracrates_reward_id", "none");
            merged.putIfAbsent("extracrates_reward_name", "none");
        }
        if (cooldownSeconds != null) {
            merged.putIfAbsent("cooldown", Long.toString(cooldownSeconds));
        }
        if (crate != null) {
            merged.putIfAbsent("extracrates_crate_id", crate.id());
            merged.putIfAbsent("extracrates_crate_name", crate.displayName());
        }
        if (player != null) {
            merged.putIfAbsent("extracrates_player", player.getName());
        }
        merged.putIfAbsent("extracrates_opening", "false");
        merged.putIfAbsent("extracrates_rerolls_remained", "0");
        merged.putIfAbsent("extracrates_rerolls_used", "0");
        return merged;
    }

    private String applyPlaceholders(String message, Map<String, String> placeholders) {
        if (message == null || placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private boolean isSuppressed(String value) {
        if (value == null) {
            return true;
        }
        return value.trim().equalsIgnoreCase("none");
    }
}
