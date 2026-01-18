package com.extracrates.runtime;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.extracrates.ExtraCratesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolEntityHider {
    private static final List<PacketType> ENTITY_PACKETS = List.of(
            PacketType.Play.Server.SPAWN_ENTITY,
            PacketType.Play.Server.SPAWN_ENTITY_LIVING,
            PacketType.Play.Server.ENTITY_METADATA,
            PacketType.Play.Server.ENTITY_TELEPORT,
            PacketType.Play.Server.REL_ENTITY_MOVE,
            PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
            PacketType.Play.Server.ENTITY_LOOK,
            PacketType.Play.Server.ENTITY_HEAD_ROTATION,
            PacketType.Play.Server.ENTITY_EQUIPMENT
    );

    private final ExtraCratesPlugin plugin;
    private final ProtocolManager protocolManager;
    private final Map<Integer, UUID> entityOwners = new ConcurrentHashMap<>();
    private final PacketAdapter packetAdapter;

    public ProtocolEntityHider(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.packetAdapter = new PacketAdapter(plugin, ListenerPriority.HIGH, ENTITY_PACKETS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                handlePacket(event);
            }
        };
        protocolManager.addPacketListener(packetAdapter);
    }

    public static ProtocolEntityHider createIfPresent(ExtraCratesPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            return null;
        }
        return new ProtocolEntityHider(plugin);
    }

    public void trackEntity(Player owner, Entity entity) {
        entityOwners.put(entity.getEntityId(), owner.getUniqueId());
        sendDestroyToOthers(owner, entity);
    }

    public void untrackEntity(Entity entity) {
        entityOwners.remove(entity.getEntityId());
    }

    public void shutdown() {
        protocolManager.removePacketListener(packetAdapter);
        entityOwners.clear();
    }

    private void handlePacket(PacketEvent event) {
        Integer entityId = event.getPacket().getIntegers().readSafely(0);
        if (entityId == null) {
            return;
        }
        UUID owner = entityOwners.get(entityId);
        if (owner == null) {
            return;
        }
        if (!event.getPlayer().getUniqueId().equals(owner)) {
            event.setCancelled(true);
        }
    }

    private void sendDestroyToOthers(Player owner, Entity entity) {
        PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntLists().write(0, List.of(entity.getEntityId()));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            protocolManager.sendServerPacket(online, destroyPacket);
        }
    }
}
