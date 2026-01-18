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
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private final ExtraCratesPlugin plugin;
    private FileConfiguration messages;

    public LanguageManager(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String language = plugin.getConfig().getString("language", "es_es");
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "lang/es_es.yml");
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public Component getMessage(String key) {
        return getMessage(key, Collections.emptyMap());
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        return TextUtil.color(getRaw(key, placeholders));
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
        String value = messages.getString(key, key);
        return applyPlaceholders(value, placeholders);
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
        String value = messages.getString(key, key);
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
        }
        if (cooldownSeconds != null) {
            merged.putIfAbsent("cooldown", Long.toString(cooldownSeconds));
        }
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
}
