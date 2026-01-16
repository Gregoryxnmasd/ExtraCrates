package com.extracrates.config;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
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

    public String getRaw(String key, Map<String, String> placeholders) {
        if (messages == null) {
            return key;
        }
        String value = messages.getString(key, key);
        return applyPlaceholders(value, placeholders);
    }

    private String applyPlaceholders(String message, Map<String, String> placeholders) {
        if (message == null || placeholders.isEmpty()) {
            return message;
        }
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }
}
