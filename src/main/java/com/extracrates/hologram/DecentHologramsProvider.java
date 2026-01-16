package com.extracrates.hologram;

import com.extracrates.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DecentHologramsProvider implements HologramProvider {
    private final boolean available;
    private final Method createHologram;
    private final Method setLines;
    private final Method moveHologram;
    private final Method deleteHologram;
    private final boolean setLinesStatic;
    private final boolean moveHologramStatic;

    public DecentHologramsProvider() {
        boolean resolved = false;
        Method createMethod = null;
        Method setLinesMethod = null;
        Method moveMethod = null;
        Method deleteMethod = null;
        boolean setLinesIsStatic = false;
        boolean moveIsStatic = false;

        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") != null) {
            try {
                Class<?> apiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
                Class<?> hologramClass = Class.forName("eu.decentsoftware.holograms.api.holograms.Hologram");
                createMethod = resolveCreateMethod(apiClass);
                setLinesMethod = resolveSetLinesMethod(apiClass, hologramClass);
                moveMethod = resolveMoveMethod(apiClass, hologramClass);
                deleteMethod = resolveDeleteMethod(hologramClass);
                if (setLinesMethod != null) {
                    setLinesIsStatic = Modifier.isStatic(setLinesMethod.getModifiers());
                }
                if (moveMethod != null) {
                    moveIsStatic = Modifier.isStatic(moveMethod.getModifiers());
                }
                resolved = createMethod != null;
            } catch (ReflectiveOperationException ignored) {
                resolved = false;
            }
        }

        this.available = resolved;
        this.createHologram = createMethod;
        this.setLines = setLinesMethod;
        this.moveHologram = moveMethod;
        this.deleteHologram = deleteMethod;
        this.setLinesStatic = setLinesIsStatic;
        this.moveHologramStatic = moveIsStatic;
    }

    @Override
    public String getName() {
        return "decentholograms";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public HologramInstance spawnHologram(Location location, Component text, Player viewer) {
        if (!available || location == null) {
            return null;
        }
        try {
            String name = "extracrates_" + UUID.randomUUID();
            Object[] args = buildCreateArgs(name, location, text);
            Object hologram = createHologram.invoke(null, args);
            if (hologram == null) {
                return null;
            }
            if (args.length <= 2 && setLines != null) {
                new DecentHologramInstance(hologram).setText(text);
            }
            return new DecentHologramInstance(hologram);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private Object[] buildCreateArgs(String name, Location location, Component text) {
        List<String> lines = Collections.singletonList(TextUtil.serializeLegacy(text));
        Class<?>[] params = createHologram.getParameterTypes();
        if (params.length == 4) {
            return new Object[]{name, location, Boolean.FALSE, lines};
        }
        if (params.length == 3) {
            return new Object[]{name, location, lines};
        }
        return new Object[]{name, location};
    }

    private static Method resolveCreateMethod(Class<?> apiClass) throws ReflectiveOperationException {
        for (Method method : apiClass.getMethods()) {
            if (!method.getName().equals("createHologram")) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length >= 2 && params[0] == String.class && params[1] == Location.class) {
                return method;
            }
        }
        return null;
    }

    private static Method resolveSetLinesMethod(Class<?> apiClass, Class<?> hologramClass) {
        for (Method method : apiClass.getMethods()) {
            if (method.getName().equals("setHologramLines")) {
                return method;
            }
        }
        for (Method method : apiClass.getMethods()) {
            if (method.getName().equals("setHologramLine")) {
                return method;
            }
        }
        try {
            return hologramClass.getMethod("setLines", List.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveMoveMethod(Class<?> apiClass, Class<?> hologramClass) {
        for (Method method : apiClass.getMethods()) {
            if (method.getName().equals("moveHologram")) {
                return method;
            }
        }
        try {
            return hologramClass.getMethod("setLocation", Location.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveDeleteMethod(Class<?> hologramClass) {
        try {
            return hologramClass.getMethod("delete");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private class DecentHologramInstance implements HologramInstance {
        private final Object hologram;

        private DecentHologramInstance(Object hologram) {
            this.hologram = hologram;
        }

        @Override
        public void setText(Component text) {
            if (setLines == null) {
                return;
            }
            try {
                String line = TextUtil.serializeLegacy(text);
                int paramCount = setLines.getParameterCount();
                if (paramCount == 3) {
                    setLines.invoke(setLinesStatic ? null : hologram, hologram, 0, line);
                } else if (paramCount == 2) {
                    if (setLinesStatic) {
                        setLines.invoke(null, hologram, Collections.singletonList(line));
                    } else {
                        setLines.invoke(hologram, Collections.singletonList(line));
                    }
                } else if (paramCount == 1) {
                    setLines.invoke(hologram, Collections.singletonList(line));
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public void teleport(Location location) {
            if (moveHologram == null) {
                return;
            }
            try {
                int paramCount = moveHologram.getParameterCount();
                if (paramCount == 2) {
                    moveHologram.invoke(moveHologramStatic ? null : hologram, hologram, location);
                } else if (paramCount == 1) {
                    moveHologram.invoke(hologram, location);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public void remove() {
            if (deleteHologram == null) {
                return;
            }
            try {
                deleteHologram.invoke(hologram);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public Entity getEntity() {
            return null;
        }
    }
}
