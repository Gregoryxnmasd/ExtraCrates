package com.extracrates.gui.editor;

import com.extracrates.ExtraCratesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EditorInputManager implements Listener {
    private final ExtraCratesPlugin plugin;
    private final Map<UUID, InputRequest> pendingInputs = new ConcurrentHashMap<>();

    public EditorInputManager(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void requestInput(Player player, String prompt, Consumer<String> onInput) {
        pendingInputs.put(player.getUniqueId(), new InputRequest(onInput));
        player.sendMessage(Component.text(prompt + " (escribe 'cancel' para cancelar)"));
    }

    public boolean hasPending(Player player) {
        return pendingInputs.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        InputRequest request = pendingInputs.get(event.getPlayer().getUniqueId());
        if (request == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        pendingInputs.remove(event.getPlayer().getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                event.getPlayer().sendMessage(Component.text("Edición cancelada."));
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
