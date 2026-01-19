package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.LanguageManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EditorInputManager implements Listener {
    private final ExtraCratesPlugin plugin;
    private final LanguageManager languageManager;
    private final Map<UUID, InputRequest> pendingInputs = new ConcurrentHashMap<>();

    public EditorInputManager(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void requestInput(Player player, String promptKey, Consumer<String> onInput) {
        requestInput(player, promptKey, Collections.emptyMap(), onInput);
    }

    public void requestInput(Player player, String promptKey, Map<String, String> placeholders, Consumer<String> onInput) {
        pendingInputs.put(player.getUniqueId(), new InputRequest(onInput));
        String prompt = languageManager.getRaw(promptKey, placeholders);
        player.sendMessage(languageManager.getMessage("editor.input.prompt", Map.of("prompt", prompt)));
    }

    public boolean hasPending(Player player) {
        return pendingInputs.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        InputRequest request = pendingInputs.get(event.getPlayer().getUniqueId());
        if (request == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        pendingInputs.remove(event.getPlayer().getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                event.getPlayer().sendMessage(languageManager.getMessage("editor.input.cancelled"));
                return;
            }
            request.onInput.accept(message);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }

    private record InputRequest(Consumer<String> onInput) {
    }
}
