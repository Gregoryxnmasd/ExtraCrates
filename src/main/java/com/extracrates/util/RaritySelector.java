package com.extracrates.util;

import com.extracrates.model.RarityDefinition;

import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RaritySelector {
    private RaritySelector() {
    }

    public static RarityDefinition select(Map<String, RarityDefinition> rarities, Random random) {
        if (rarities == null || rarities.isEmpty()) {
            return null;
        }
        List<RarityDefinition> values = List.copyOf(rarities.values());
        double total = values.stream().mapToDouble(RarityDefinition::chance).sum();
        if (total <= 0.0) {
            return values.get(random.nextInt(values.size()));
        }
        double roll = random.nextDouble() * total;
        double current = 0;
        for (RarityDefinition rarity : values) {
            current += rarity.chance();
            if (roll <= current) {
                return rarity;
            }
        }
        return values.getLast();
    }
}
