package com.extracrates.route;

import com.extracrates.ExtraCratesPlugin;
import com.extracrates.config.ConfigLoader;
import com.extracrates.config.LanguageManager;
import com.extracrates.cutscene.CutscenePoint;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class RouteEditorManager {
    private final ExtraCratesPlugin plugin;
    private final ConfigLoader configLoader;
    private final LanguageManager languageManager;
    private final Map<UUID, RouteEditorSession> sessions = new HashMap<>();

    public RouteEditorManager(ExtraCratesPlugin plugin, ConfigLoader configLoader) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.languageManager = plugin.getLanguageManager();
    }

    public boolean startSession(Player player, String pathId) {
        if (sessions.containsKey(player.getUniqueId())) {
            return false;
        }
        String particleName = configLoader.getMainConfig().getString("particles-default", "end_rod");
        if (configLoader.getPaths().containsKey(pathId)) {
            String preview = configLoader.getPaths().get(pathId).getParticlePreview();
            if (preview != null && !preview.isEmpty()) {
                particleName = preview;
            }
        }
        Particle particle = parseParticle(particleName);
        RouteEditorSession session = new RouteEditorSession(
                plugin,
                player,
                pathId,
                particle,
                particleName
        );
        sessions.put(player.getUniqueId(), session);
        session.startPreview();
        return true;
    }

    public boolean endSession(Player player, boolean save) {
        RouteEditorSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }
        session.cleanup();
        if (save) {
            saveSession(player, session);
        }
        return true;
    }

    public void capturePoint(Player player) {
        RouteEditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Location captureLocation = session.resolveCaptureLocation(player.getLocation());
        session.addPoint(captureLocation, captureLocation.getYaw(), captureLocation.getPitch());
    }

    public boolean hasNoSession(Player player) {
        return !sessions.containsKey(player.getUniqueId());
    }

    public void moveMarkerToPlayer(Player player) {
        RouteEditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.moveMarker(player.getLocation());
        player.sendMessage(languageManager.getMessage("route.editor.marker-moved"));
    }

    private void saveSession(Player player, RouteEditorSession session) {
        File file = new File(plugin.getDataFolder(), "paths.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection pathsSection = config.getConfigurationSection("paths");
        if (pathsSection == null) {
            pathsSection = config.createSection("paths");
        }
        ConfigurationSection section = pathsSection.getConfigurationSection(session.getPathId());
        if (section == null) {
            section = pathsSection.createSection(session.getPathId());
        }
        if (!section.contains("duration-seconds")) {
            section.set("duration-seconds", 4.0);
        }
        if (!section.contains("constant-speed")) {
            section.set("constant-speed", true);
        }
        if (!section.contains("step-resolution")) {
            section.set("step-resolution", 0.15);
        }
        if (!section.contains("smoothing")) {
            section.set("smoothing", "linear");
        }
        section.set("particle-preview", session.getParticleName());
        List<Map<String, Object>> points = new ArrayList<>();
        for (CutscenePoint point : session.getPoints()) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("x", point.x());
            values.put("y", point.y());
            values.put("z", point.z());
            values.put("yaw", point.yaw());
            values.put("pitch", point.pitch());
            points.add(values);
        }
        section.set("points", points);
        try {
            config.save(file);
            configLoader.loadAll();
            player.sendMessage(languageManager.getMessage(
                    "route.editor.saved",
                    java.util.Map.of(
                            "path", session.getPathId(),
                            "count", String.valueOf(points.size())
                    )
            ));
        } catch (IOException ex) {
            player.sendMessage(languageManager.getMessage("route.editor.save-failed"));
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar paths.yml para la ruta '" + session.getPathId() + "'.", ex);
        }
    }

    private Particle parseParticle(String particleName) {
        try {
            return Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Particle.END_ROD;
        }
    }
}
