package com.extracrates.util;

import com.extracrates.ExtraCratesPlugin;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MapImageCache {
    private static final int MAP_SIZE = 128;
    private final ExtraCratesPlugin plugin;
    private final ConcurrentMap<String, Optional<BufferedImage>> cache = new ConcurrentHashMap<>();
    private final Path imagesDirectory;

    public MapImageCache(ExtraCratesPlugin plugin) {
        this.plugin = plugin;
        this.imagesDirectory = plugin.getDataFolder().toPath().resolve("map-images");
        ensureDirectory();
    }

    public Optional<BufferedImage> getImage(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalizedKey = key.trim();
        return cache.computeIfAbsent(normalizedKey, this::loadImage);
    }

    private Optional<BufferedImage> loadImage(String key) {
        Path path = resolvePath(key);
        if (path == null || !Files.exists(path)) {
            plugin.getLogger().warning("No se encontró la imagen del mapa: " + key);
            return Optional.empty();
        }
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                plugin.getLogger().warning("No se pudo leer la imagen del mapa: " + path.getFileName());
                return Optional.empty();
            }
            return Optional.of(scaleToMap(image));
        } catch (IOException ex) {
            plugin.getLogger().warning("Error al cargar la imagen del mapa " + path.getFileName() + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    private BufferedImage scaleToMap(BufferedImage original) {
        BufferedImage scaled = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(original, 0, 0, MAP_SIZE, MAP_SIZE, null);
        graphics.dispose();
        return scaled;
    }

    private Path resolvePath(String key) {
        Path candidate = imagesDirectory.resolve(key);
        if (Files.exists(candidate)) {
            return candidate;
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (!lowerKey.endsWith(".png")) {
            Path withExtension = imagesDirectory.resolve(key + ".png");
            if (Files.exists(withExtension)) {
                return withExtension;
            }
        }
        return candidate;
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(imagesDirectory);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo crear el directorio de imágenes: " + imagesDirectory);
        }
    }
}
