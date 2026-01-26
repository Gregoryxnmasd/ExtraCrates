package com.extracrates.util;

import java.util.Locale;
import java.util.Set;

public final class CommandUtil {
    private static final Set<String> ALWAYS_BROADCAST = Set.of("broadcast", "say", "me");
    private static final Set<String> TARGETED_MESSAGES = Set.of("tellraw", "title", "subtitle", "actionbar", "msg", "tell", "w");

    private CommandUtil() {
    }

    public static boolean isBroadcastMessage(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("\\s+");
        if (parts.length == 0) {
            return false;
        }
        String base = parts[0];
        int namespaceIndex = base.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < base.length() - 1) {
            base = base.substring(namespaceIndex + 1);
        }
        if (ALWAYS_BROADCAST.contains(base)) {
            return true;
        }
        if (!TARGETED_MESSAGES.contains(base)) {
            return false;
        }
        if (parts.length < 2) {
            return false;
        }
        String target = parts[1];
        return target.startsWith("@a") || target.startsWith("@e");
    }
}
