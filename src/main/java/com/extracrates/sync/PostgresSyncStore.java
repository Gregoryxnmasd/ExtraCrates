package com.extracrates.sync;

import com.extracrates.ExtraCratesPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class PostgresSyncStore implements SyncStore {
    private static final int DEFAULT_KEY_COUNT = 0;
    private final ExtraCratesPlugin plugin;
    private final SyncSettings settings;
    private volatile boolean healthy = true;

    public PostgresSyncStore(ExtraCratesPlugin plugin, SyncSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @Override
    public void init() {
        String schema = settings.getPostgres().getSchema();
        String createSchema = "CREATE SCHEMA IF NOT EXISTS " + schema;
        String cooldowns = "CREATE TABLE IF NOT EXISTS " + schema + ".crate_cooldowns ("
                + "player_id UUID NOT NULL,"
                + "crate_id TEXT NOT NULL,"
                + "last_used TIMESTAMP NOT NULL,"
                + "server_id TEXT NOT NULL,"
                + "PRIMARY KEY (player_id, crate_id)"
                + ")";
        String keyInventory = "CREATE TABLE IF NOT EXISTS " + schema + ".key_inventory ("
                + "player_id UUID NOT NULL,"
                + "crate_id TEXT NOT NULL,"
                + "key_count INT NOT NULL,"
                + "server_id TEXT NOT NULL,"
                + "PRIMARY KEY (player_id, crate_id)"
                + ")";
        String openHistory = "CREATE TABLE IF NOT EXISTS " + schema + ".crate_open_history ("
                + "id SERIAL PRIMARY KEY,"
                + "player_id UUID NOT NULL,"
                + "crate_id TEXT NOT NULL,"
                + "opened_at TIMESTAMP NOT NULL,"
                + "server_id TEXT NOT NULL"
                + ")";
        String rewardHistory = "CREATE TABLE IF NOT EXISTS " + schema + ".crate_reward_history ("
                + "id SERIAL PRIMARY KEY,"
                + "player_id UUID NOT NULL,"
                + "crate_id TEXT NOT NULL,"
                + "reward_id TEXT,"
                + "granted_at TIMESTAMP NOT NULL,"
                + "server_id TEXT NOT NULL"
                + ")";
        String eventHistory = "CREATE TABLE IF NOT EXISTS " + schema + ".crate_event_history ("
                + "id SERIAL PRIMARY KEY,"
                + "player_id UUID NOT NULL,"
                + "crate_id TEXT NOT NULL,"
                + "reward_id TEXT,"
                + "event_type TEXT NOT NULL,"
                + "occurred_at TIMESTAMP NOT NULL,"
                + "server_id TEXT NOT NULL"
                + ")";
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createSchema);
            statement.execute(cooldowns);
            statement.execute(keyInventory);
            statement.execute(openHistory);
            statement.execute(rewardHistory);
            statement.execute(eventHistory);
        } catch (SQLException ex) {
            healthy = false;
            plugin.getLogger().log(Level.WARNING, "[Sync] No se pudo inicializar Postgres", ex);
        }
    }

    @Override
    public void recordCooldown(UUID playerId, String crateId, Instant timestamp, String serverId) {
        if (!healthy) {
            return;
        }
        String sql = "INSERT INTO " + settings.getPostgres().getSchema() + ".crate_cooldowns "
                + "(player_id, crate_id, last_used, server_id) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (player_id, crate_id) DO UPDATE SET last_used = EXCLUDED.last_used, server_id = EXCLUDED.server_id";
        execute(sql, playerId, crateId, timestamp, serverId);
    }

    @Override
    public void recordKeyConsumed(UUID playerId, String crateId, Instant timestamp, String serverId) {
        if (!healthy) {
            return;
        }
        String sql = "INSERT INTO " + settings.getPostgres().getSchema() + ".key_inventory "
                + "(player_id, crate_id, key_count, server_id) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (player_id, crate_id) DO UPDATE SET key_count = GREATEST(" + settings.getPostgres().getSchema()
                + ".key_inventory.key_count - 1, 0), server_id = EXCLUDED.server_id";
        executeKeyInventory(sql, playerId, crateId, serverId);
        String history = "INSERT INTO " + settings.getPostgres().getSchema() + ".crate_reward_history "
                + "(player_id, crate_id, reward_id, granted_at, server_id) VALUES (?, ?, ?, ?, ?)";
        execute(history, playerId, crateId, timestamp, serverId, "key_consumed");
    }

    @Override
    public void recordCrateOpen(UUID playerId, String crateId, Instant timestamp, String serverId) {
        if (!healthy) {
            return;
        }
        String sql = "INSERT INTO " + settings.getPostgres().getSchema() + ".crate_open_history "
                + "(player_id, crate_id, opened_at, server_id) VALUES (?, ?, ?, ?)";
        execute(sql, playerId, crateId, timestamp, serverId);
    }

    @Override
    public void recordRewardGranted(UUID playerId, String crateId, String rewardId, Instant timestamp, String serverId) {
        if (!healthy) {
            return;
        }
        String sql = "INSERT INTO " + settings.getPostgres().getSchema() + ".crate_reward_history "
                + "(player_id, crate_id, reward_id, granted_at, server_id) VALUES (?, ?, ?, ?, ?)";
        execute(sql, playerId, crateId, timestamp, serverId, rewardId);
    }

    @Override
    public void recordEvent(UUID playerId, String crateId, SyncEventType type, String rewardId, Instant timestamp, String serverId) {
        if (!healthy) {
            return;
        }
        String sql = "INSERT INTO " + settings.getPostgres().getSchema() + ".crate_event_history "
                + "(player_id, crate_id, reward_id, event_type, occurred_at, server_id) VALUES (?, ?, ?, ?, ?, ?)";
        executeEvent(sql, playerId, crateId, rewardId, type, timestamp, serverId);
    }

    @Override
    public List<CrateHistoryEntry> getHistory(UUID playerId, String crateId, int limit, int offset) {
        if (!healthy) {
            return List.of();
        }
        String schema = settings.getPostgres().getSchema();
        StringBuilder sql = new StringBuilder("SELECT event_type, crate_id, reward_id, occurred_at, server_id FROM ")
                .append(schema)
                .append(".crate_event_history WHERE player_id = ?");
        if (crateId != null && !crateId.isBlank()) {
            sql.append(" AND crate_id = ?");
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?");
        List<CrateHistoryEntry> results = new ArrayList<>();
        try (Connection connection = openConnection(); PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            int index = 1;
            stmt.setObject(index++, playerId);
            if (crateId != null && !crateId.isBlank()) {
                stmt.setString(index++, crateId);
            }
            stmt.setInt(index++, limit);
            stmt.setInt(index, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SyncEventType type = SyncEventType.valueOf(rs.getString("event_type"));
                    String eventCrateId = rs.getString("crate_id");
                    String rewardId = rs.getString("reward_id");
                    Instant timestamp = rs.getTimestamp("occurred_at").toInstant();
                    String serverId = rs.getString("server_id");
                    results.add(new CrateHistoryEntry(type, playerId, eventCrateId, rewardId, timestamp, serverId));
                }
            }
        } catch (SQLException ex) {
            healthy = false;
            plugin.getLogger().log(Level.WARNING, "[Sync] Error leyendo historial en Postgres", ex);
        }
        return results;
    }

    @Override
    public void clearPlayerHistory(UUID playerId) {
        if (!healthy) {
            return;
        }
        String schema = settings.getPostgres().getSchema();
        String openHistory = "DELETE FROM " + schema + ".crate_open_history WHERE player_id = ?";
        String rewardHistory = "DELETE FROM " + schema + ".crate_reward_history WHERE player_id = ?";
        String eventHistory = "DELETE FROM " + schema + ".crate_event_history WHERE player_id = ?";
        try (Connection connection = openConnection();
             PreparedStatement openStmt = connection.prepareStatement(openHistory);
             PreparedStatement rewardStmt = connection.prepareStatement(rewardHistory);
             PreparedStatement eventStmt = connection.prepareStatement(eventHistory)) {
            openStmt.setObject(1, playerId);
            rewardStmt.setObject(1, playerId);
            eventStmt.setObject(1, playerId);
            openStmt.executeUpdate();
            rewardStmt.executeUpdate();
            eventStmt.executeUpdate();
        } catch (SQLException ex) {
            healthy = false;
            plugin.getLogger().log(Level.WARNING, "[Sync] No se pudo limpiar el historial en Postgres", ex);
        }
    }

    @Override
    public void flush() {
        if (!healthy) {
            return;
        }
        String schema = settings.getPostgres().getSchema();
        String sql = "TRUNCATE TABLE " + schema + ".crate_cooldowns, " + schema + ".key_inventory";
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            healthy = false;
            plugin.getLogger().log(Level.WARNING, "[Sync] No se pudo limpiar el estado en Postgres", ex);
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public void shutdown() {
        healthy = false;
    }

    @Override
    public String getName() {
        return "Postgres";
    }

    private Connection openConnection() throws SQLException {
        SyncSettings.PostgresSettings pg = settings.getPostgres();
        String url = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getPort() + "/" + pg.getDatabase();
        return DriverManager.getConnection(url, pg.getUser(), pg.getPassword());
    }

    private void execute(String sql, UUID playerId, String crateId, Instant timestamp, String serverId) {
        try (Connection connection = openConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, playerId);
            stmt.setString(2, crateId);
            stmt.setTimestamp(3, Timestamp.from(timestamp));
            stmt.setString(4, serverId);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            healthy = false;
            plugin.getLogger().log(Level.WARNING, "[Sync] Error escribiendo en Postgres", ex);
        }
    }

    private void execute(String sql, UUID playerId, String crateId, Instant timestamp, String serverId, String rewardId) {
        try (Connection connection = openConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, playerId);
            stmt.setString(2, crateId);
            stmt.setString(3, rewardId);
            stmt.setTimestamp(4, Timestamp.from(timestamp));
            stmt.setString(5, serverId);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            healthy = false;
            plugin.getLogger().log(Level.WARNING, "[Sync] Error escribiendo en Postgres", ex);
        }
    }

    private void executeKeyInventory(String sql, UUID playerId, String crateId, String serverId) {
        try (Connection connection = openConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, playerId);
            stmt.setString(2, crateId);
            stmt.setInt(3, DEFAULT_KEY_COUNT);
            stmt.setString(4, serverId);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            healthy = false;
            plugin.getLogger().log(Level.WARNING, "[Sync] Error escribiendo en Postgres", ex);
        }
    }

    private void executeEvent(
            String sql,
            UUID playerId,
            String crateId,
            String rewardId,
            SyncEventType type,
            Instant timestamp,
            String serverId
    ) {
        try (Connection connection = openConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, playerId);
            stmt.setString(2, crateId);
            stmt.setString(3, rewardId);
            stmt.setString(4, type.name());
            stmt.setTimestamp(5, Timestamp.from(timestamp));
            stmt.setString(6, serverId);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            healthy = false;
            plugin.getLogger().log(Level.WARNING, "[Sync] Error escribiendo historial en Postgres", ex);
        }
    }
}
