package com.extracrates.sync;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class SyncSettings {
    private final boolean enabled;
    private final SyncMode mode;
    private final String serverId;
    private final RedisSettings redis;
    private final PostgresSettings postgres;

    public SyncSettings(boolean enabled, SyncMode mode, String serverId, RedisSettings redis, PostgresSettings postgres) {
        this.enabled = enabled;
        this.mode = mode;
        this.serverId = serverId;
        this.redis = redis;
        this.postgres = postgres;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SyncMode getMode() {
        return mode;
    }

    public String getServerId() {
        return serverId;
    }

    public RedisSettings getRedis() {
        return redis;
    }

    public PostgresSettings getPostgres() {
        return postgres;
    }

    public static SyncSettings fromConfig(FileConfiguration config) {
        ConfigurationSection sync = config.getConfigurationSection("sync");
        boolean enabled = sync != null && sync.getBoolean("enabled", false);
        SyncMode mode = SyncMode.fromText(sync != null ? sync.getString("mode", "eventual") : "eventual");
        String serverId = sync != null ? sync.getString("server-id", "local") : "local";
        RedisSettings redis = RedisSettings.fromSection(sync != null ? sync.getConfigurationSection("redis") : null);
        PostgresSettings postgres = PostgresSettings.fromSection(sync != null ? sync.getConfigurationSection("postgres") : null);
        return new SyncSettings(enabled, mode, serverId, redis, postgres);
    }

    public static class RedisSettings {
        private final String host;
        private final int port;
        private final String password;
        private final String channel;

        public RedisSettings(String host, int port, String password, String channel) {
            this.host = host;
            this.port = port;
            this.password = password;
            this.channel = channel;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getPassword() {
            return password;
        }

        public String getChannel() {
            return channel;
        }

        public static RedisSettings fromSection(ConfigurationSection section) {
            if (section == null) {
                return new RedisSettings("localhost", 6379, "", "extracrates:sync");
            }
            return new RedisSettings(
                    section.getString("host", "localhost"),
                    section.getInt("port", 6379),
                    section.getString("password", ""),
                    section.getString("channel", "extracrates:sync")
            );
        }
    }

    public static class PostgresSettings {
        private final String host;
        private final int port;
        private final String database;
        private final String user;
        private final String password;
        private final String schema;

        public PostgresSettings(String host, int port, String database, String user, String password, String schema) {
            this.host = host;
            this.port = port;
            this.database = database;
            this.user = user;
            this.password = password;
            this.schema = schema;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getDatabase() {
            return database;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }

        public String getSchema() {
            return schema;
        }

        public static PostgresSettings fromSection(ConfigurationSection section) {
            if (section == null) {
                return new PostgresSettings("localhost", 5432, "extracrates", "postgres", "", "public");
            }
            return new PostgresSettings(
                    section.getString("host", "localhost"),
                    section.getInt("port", 5432),
                    section.getString("database", "extracrates"),
                    section.getString("user", "postgres"),
                    section.getString("password", ""),
                    section.getString("schema", "public")
            );
        }
    }
}
