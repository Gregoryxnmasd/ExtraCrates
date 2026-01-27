package com.extracrates.placeholder;

import com.extracrates.config.ConfigLoader;
import com.extracrates.model.Reward;
import com.extracrates.runtime.core.CrateSession;
import com.extracrates.runtime.core.SessionManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class ExtraCratesPlaceholderExpansion extends PlaceholderExpansion {
    private final ConfigLoader configLoader;
    private final SessionManager sessionManager;

    public ExtraCratesPlaceholderExpansion(ConfigLoader configLoader, SessionManager sessionManager) {
        this.configLoader = configLoader;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getIdentifier() {
        return "extracrates";
    }

    @Override
    public String getAuthor() {
        return "ExtraCrates";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params == null || params.isBlank()) {
            return fallback("none", "none");
        }
        String key = params.trim().toLowerCase();
        CrateSession session = player != null ? sessionManager.getSession(player.getUniqueId()) : null;
        boolean opening = session != null;
        return switch (key) {
            case "opening" -> opening ? fallback("opening-true", "true") : fallback("opening-false", "false");
            case "crate_id" -> session != null ? session.getCrateId() : fallback("no-crate", "none");
            case "crate_name" -> session != null ? session.getCrate().displayName() : fallback("no-crate", "none");
            case "reward_id" -> resolveRewardId(session);
            case "reward_name" -> resolveRewardName(session);
            case "rerolls_remained", "rerolls_left" -> resolveRerollsRemaining(session);
            case "rerolls_used" -> resolveRerollsUsed(session);
            case "rerolls_max" -> resolveRerollsMax(session);
            default -> fallback("none", "none");
        };
    }

    private String resolveRewardId(CrateSession session) {
        if (session == null) {
            return fallback("no-reward", "none");
        }
        Reward reward = session.getActiveReward();
        return reward != null ? reward.id() : fallback("no-reward", "none");
    }

    private String resolveRewardName(CrateSession session) {
        if (session == null) {
            return fallback("no-reward", "none");
        }
        Reward reward = session.getActiveReward();
        return reward != null ? reward.displayName() : fallback("no-reward", "none");
    }

    private String resolveRerollsRemaining(CrateSession session) {
        if (session == null) {
            return resolveRerollsRemainingDisplay(0);
        }
        int max = session.getMaxRerolls();
        int used = session.getRerollsUsed();
        if (max <= 0) {
            return fallback("unlimited-rerolls", "∞");
        }
        int remaining = Math.max(0, max - used);
        return resolveRerollsRemainingDisplay(remaining);
    }

    private String resolveRerollsRemainingDisplay(int remaining) {
        if (configLoader == null || configLoader.getMainConfig() == null) {
            return Integer.toString(remaining);
        }
        String configured = configLoader.getMainConfig().getString("placeholders.rerolls-left." + remaining);
        if (configured == null || configured.isBlank()) {
            return Integer.toString(remaining);
        }
        return configured;
    }

    private String resolveRerollsUsed(CrateSession session) {
        if (session == null) {
            return "0";
        }
        return Integer.toString(Math.max(0, session.getRerollsUsed()));
    }

    private String resolveRerollsMax(CrateSession session) {
        if (session == null) {
            return "0";
        }
        int max = session.getMaxRerolls();
        if (max <= 0) {
            return fallback("unlimited-rerolls", "∞");
        }
        return Integer.toString(max);
    }

    private String fallback(String key, String defaultValue) {
        if (configLoader == null || configLoader.getMainConfig() == null) {
            return defaultValue;
        }
        String value = configLoader.getMainConfig().getString("placeholders." + key, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
