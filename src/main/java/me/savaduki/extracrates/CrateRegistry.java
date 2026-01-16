package me.savaduki.extracrates;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for crate definitions.
 * <p>
 * The class currently behaves as an in-memory store and is intentionally
 * lightweight; it will evolve to support persistence layers and distributed
 * synchronization when proxy support is added.
 */
public final class CrateRegistry {

    private final Map<String, CrateTemplate> crates = new LinkedHashMap<>();

    public void register(CrateTemplate crate) {
        crates.put(crate.getId().toLowerCase(), crate);
    }

    public Optional<CrateTemplate> find(String id) {
        return Optional.ofNullable(crates.get(id.toLowerCase()));
    }

    public Map<String, CrateTemplate> getAll() {
        return Collections.unmodifiableMap(crates);
    }
}
