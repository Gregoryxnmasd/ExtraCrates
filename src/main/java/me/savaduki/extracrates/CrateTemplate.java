package me.savaduki.extracrates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the static definition of a crate.
 */
public final class CrateTemplate {

    private final String id;
    private final String displayName;
    private final List<String> rewardPool;

    public CrateTemplate(String id, String displayName, List<String> rewardPool) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.rewardPool = Collections.unmodifiableList(new ArrayList<>(rewardPool));
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getRewardPool() {
        return rewardPool;
    }
}
