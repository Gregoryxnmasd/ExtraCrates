package com.extracrates.config;

import com.extracrates.model.CrateDefinition;
import com.extracrates.model.RewardPool;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void loadsCratesAndRewardsFromMockFiles(@TempDir Path tempDir) throws IOException {
        writeYaml(tempDir.resolve("crates.yml"), """
                crates:
                  starter:
                    display-name: "Starter"
                    open-mode: "cinematic"
                    rewards-pool: "starter-pool"
                """);
        writeYaml(tempDir.resolve("rewards.yml"), """
                pools:
                  starter-pool:
                    roll-count: 3
                    rewards:
                      reward-a:
                        chance: 0.5
                        item: "STONE"
                      reward-b:
                        chance: 0.5
                        item: "DIRT"
                """);
        writeYaml(tempDir.resolve("paths.yml"), """
                paths: {}
                """);

        FileConfiguration mainConfig = new YamlConfiguration();

        ConfigLoader loader = new ConfigLoader(() -> mainConfig, tempDir::toFile, Logger.getLogger("ConfigLoaderTest"));
        loader.loadAll();

        Map<String, CrateDefinition> crates = loader.getCrates();
        Map<String, RewardPool> pools = loader.getRewardPools();

        assertTrue(crates.containsKey("starter"));
        assertTrue(pools.containsKey("starter-pool"));

        CrateDefinition crate = crates.get("starter");
        assertNotNull(crate);
        assertEquals("cinematic", crate.openMode());
        assertEquals("starter-pool", crate.rewardsPool());

        RewardPool pool = pools.get("starter-pool");
        assertNotNull(pool);
        assertEquals(3, pool.rollCount());
        assertEquals(2, pool.rewards().size());
    }

    private void writeYaml(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }
}
