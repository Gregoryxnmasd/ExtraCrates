package me.savaduki.extracrates;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry that maps reward identifiers to their serialized representation.
 * <p>
 * Rewards will later be expanded to full-fledged objects capable of executing
 * commands, giving items, or interacting with proxies. For now, they are simple
 * placeholders for upcoming features.
 */
public final class RewardRegistry {

    private final Map<String, String> rewards = new LinkedHashMap<>();

    public void register(String id, String serializedReward) {
        rewards.put(id.toLowerCase(), serializedReward);
    }

    public Optional<String> find(String id) {
        return Optional.ofNullable(rewards.get(id.toLowerCase()));
    }

    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(rewards);
    }
}
