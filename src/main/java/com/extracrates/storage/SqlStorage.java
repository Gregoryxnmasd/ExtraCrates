package com.extracrates.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class SqlStorage implements CrateStorage {
    private final Logger logger;
    private final SqlConnectionPool pool;

    public SqlStorage(StorageSettings settings, Logger logger) {
        this.logger = logger;
        this.pool = new SqlConnectionPool(settings, logger);
        ensureFirstOpenTable();
        ensureOpenStartedTable();
    }

    @Override
    public Optional<Instant> getCooldown(UUID playerId, String crateId) {
        String sql = "SELECT cooldown_at FROM crate_cooldowns WHERE player_uuid=? AND crate_id=?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, crateId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    long epoch = resultSet.getLong("cooldown_at");
                    return Optional.of(Instant.ofEpochMilli(epoch));
                }
            }
        });
    }

    @Override
    public void setCooldown(UUID playerId, String crateId, Instant timestamp) {
        withConnection(connection -> {
            String deleteSql = "DELETE FROM crate_cooldowns WHERE player_uuid=? AND crate_id=?";
            String insertSql = "INSERT INTO crate_cooldowns (player_uuid, crate_id, cooldown_at) VALUES (?, ?, ?)";
            try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                delete.setString(1, playerId.toString());
                delete.setString(2, crateId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, playerId.toString());
                insert.setString(2, crateId);
                insert.setLong(3, timestamp.toEpochMilli());
                insert.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void clearCooldown(UUID playerId, String crateId) {
        withConnection(connection -> {
            String deleteSql = "DELETE FROM crate_cooldowns WHERE player_uuid=? AND crate_id=?";
            try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                delete.setString(1, playerId.toString());
                delete.setString(2, crateId);
                delete.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public int getKeyCount(UUID playerId, String crateId) {
        String sql = "SELECT amount FROM crate_keys WHERE player_uuid=? AND crate_id=?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, crateId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return 0;
                    }
                    return resultSet.getInt("amount");
                }
            }
        });
    }

    @Override
    public boolean consumeKey(UUID playerId, String crateId) {
        return withConnection(connection -> {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String updateSql = "UPDATE crate_keys SET amount = amount - 1 WHERE player_uuid=? AND crate_id=? AND amount > 0";
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setString(1, playerId.toString());
                    update.setString(2, crateId);
                    int updated = update.executeUpdate();
                    if (updated <= 0) {
                        connection.rollback();
                        return false;
                    }
                }
                String cleanupSql = "DELETE FROM crate_keys WHERE player_uuid=? AND crate_id=? AND amount <= 0";
                try (PreparedStatement cleanup = connection.prepareStatement(cleanupSql)) {
                    cleanup.setString(1, playerId.toString());
                    cleanup.setString(2, crateId);
                    cleanup.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        });
    }

    @Override
    public void addKey(UUID playerId, String crateId, int amount) {
        if (amount <= 0) {
            return;
        }
        withConnection(connection -> {
            String updateSql = "UPDATE crate_keys SET amount = amount + ? WHERE player_uuid=? AND crate_id=?";
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setInt(1, amount);
                update.setString(2, playerId.toString());
                update.setString(3, crateId);
                int updated = update.executeUpdate();
                if (updated > 0) {
                    return null;
                }
            }
            String insertSql = "INSERT INTO crate_keys (player_uuid, crate_id, amount) VALUES (?, ?, ?)";
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, playerId.toString());
                insert.setString(2, crateId);
                insert.setInt(3, amount);
                insert.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void logOpen(UUID playerId, String crateId, String rewardId, String serverId, Instant timestamp) {
        String sql = "INSERT INTO crate_opens (player_uuid, crate_id, reward_id, server_id, opened_at) VALUES (?, ?, ?, ?, ?)";
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, crateId);
                statement.setString(3, rewardId);
                statement.setString(4, serverId);
                statement.setLong(5, timestamp.toEpochMilli());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void logOpenStarted(UUID playerId, String crateId, String serverId, Instant timestamp) {
        String sql = "INSERT INTO crate_open_starts (player_uuid, crate_id, server_id, opened_at) VALUES (?, ?, ?, ?)";
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, crateId);
                statement.setString(3, serverId);
                statement.setLong(4, timestamp.toEpochMilli());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void recordDelivery(UUID playerId, String crateId, String rewardId, DeliveryStatus status, int attempt, Instant timestamp) {
        withConnection(connection -> {
            String deleteSql = "DELETE FROM crate_deliveries WHERE player_uuid=? AND crate_id=? AND reward_id=?";
            String insertSql = "INSERT INTO crate_deliveries (player_uuid, crate_id, reward_id, status, attempt, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                delete.setString(1, playerId.toString());
                delete.setString(2, crateId);
                delete.setString(3, rewardId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, playerId.toString());
                insert.setString(2, crateId);
                insert.setString(3, rewardId);
                insert.setString(4, status.name());
                insert.setInt(5, attempt);
                insert.setLong(6, timestamp.toEpochMilli());
                insert.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<CrateOpenEntry> getOpenHistory(UUID playerId, OpenHistoryFilter filter, int limit, int offset) {
        if (limit <= 0) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT crate_id, reward_id, server_id, opened_at FROM crate_opens WHERE player_uuid=?"
        );
        List<Object> params = new ArrayList<>();
        params.add(playerId.toString());
        if (filter != null && filter.crateId() != null && !filter.crateId().isEmpty()) {
            sql.append(" AND crate_id=?");
            params.add(filter.crateId());
        }
        if (filter != null && filter.from() != null) {
            sql.append(" AND opened_at>=?");
            params.add(filter.from().toEpochMilli());
        }
        if (filter != null && filter.to() != null) {
            sql.append(" AND opened_at<=?");
            params.add(filter.to().toEpochMilli());
        }
        sql.append(" ORDER BY opened_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String value) {
                        statement.setString(i + 1, value);
                    } else if (param instanceof Long value) {
                        statement.setLong(i + 1, value);
                    } else if (param instanceof Integer value) {
                        statement.setInt(i + 1, value);
                    } else {
                        statement.setObject(i + 1, param);
                    }
                }
                List<CrateOpenEntry> entries = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String crateId = resultSet.getString("crate_id");
                        String rewardId = resultSet.getString("reward_id");
                        String serverId = resultSet.getString("server_id");
                        Instant openedAt = Instant.ofEpochMilli(resultSet.getLong("opened_at"));
                        entries.add(new CrateOpenEntry(playerId, crateId, rewardId, serverId, openedAt));
                    }
                }
                return entries;
            }
        });
    }

    @Override
    public boolean acquireLock(UUID playerId, String crateId) {
        String sql = "INSERT INTO crate_locks (player_uuid, crate_id, locked_at) VALUES (?, ?, ?)";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, crateId);
                statement.setLong(3, Instant.now().toEpochMilli());
                statement.executeUpdate();
                return true;
            } catch (SQLIntegrityConstraintViolationException ex) {
                return false;
            } catch (SQLException ex) {
                if (isConstraintViolation(ex)) {
                    return false;
                }
                throw ex;
            }
        });
    }

    @Override
    public void releaseLock(UUID playerId, String crateId) {
        String sql = "DELETE FROM crate_locks WHERE player_uuid=? AND crate_id=?";
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, crateId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<PendingReward> getPendingReward(UUID playerId) {
        String sql = "SELECT crate_id, reward_id, status, updated_at FROM crate_pending_rewards WHERE player_uuid=?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    String crateId = resultSet.getString("crate_id");
                    String rewardId = resultSet.getString("reward_id");
                    String status = resultSet.getString("status");
                    Instant updatedAt = Instant.ofEpochMilli(resultSet.getLong("updated_at"));
                    return Optional.of(new PendingReward(crateId, rewardId, RewardDeliveryStatus.fromString(status), updatedAt));
                }
            }
        });
    }

    @Override
    public void setPendingReward(UUID playerId, String crateId, String rewardId) {
        withConnection(connection -> {
            String deleteSql = "DELETE FROM crate_pending_rewards WHERE player_uuid=?";
            try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
            }
            String insertSql = "INSERT INTO crate_pending_rewards (player_uuid, crate_id, reward_id, status, updated_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, playerId.toString());
                insert.setString(2, crateId);
                insert.setString(3, rewardId);
                insert.setString(4, RewardDeliveryStatus.PENDING.name());
                insert.setLong(5, Instant.now().toEpochMilli());
                insert.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void markRewardDelivered(UUID playerId, String crateId, String rewardId) {
        withConnection(connection -> {
            String updateSql = "UPDATE crate_pending_rewards SET status=?, updated_at=? WHERE player_uuid=? AND crate_id=? AND reward_id=?";
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setString(1, RewardDeliveryStatus.DELIVERED.name());
                update.setLong(2, Instant.now().toEpochMilli());
                update.setString(3, playerId.toString());
                update.setString(4, crateId);
                update.setString(5, rewardId);
                int updated = update.executeUpdate();
                if (updated > 0) {
                    return null;
                }
            }
            String insertSql = "INSERT INTO crate_pending_rewards (player_uuid, crate_id, reward_id, status, updated_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, playerId.toString());
                insert.setString(2, crateId);
                insert.setString(3, rewardId);
                insert.setString(4, RewardDeliveryStatus.DELIVERED.name());
                insert.setLong(5, Instant.now().toEpochMilli());
                insert.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void clearPlayerData(UUID playerId) {
        withConnection(connection -> {
            String[] tables = {
                    "crate_cooldowns",
                    "crate_keys",
                    "crate_locks",
                    "crate_deliveries",
                    "crate_opens",
                    "crate_open_starts",
                    "crate_pending_rewards"
            };
            for (String table : tables) {
                String deleteSql = "DELETE FROM " + table + " WHERE player_uuid=?";
                try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                    delete.setString(1, playerId.toString());
                    delete.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public void close() {
        pool.close();
    }

    void setKeyCount(UUID playerId, String crateId, int amount) {
        if (amount <= 0) {
            withConnection(connection -> {
                String deleteSql = "DELETE FROM crate_keys WHERE player_uuid=? AND crate_id=?";
                try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                    delete.setString(1, playerId.toString());
                    delete.setString(2, crateId);
                    delete.executeUpdate();
                }
                return null;
            });
            return;
        }
        withConnection(connection -> {
            String updateSql = "UPDATE crate_keys SET amount = ? WHERE player_uuid=? AND crate_id=?";
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setInt(1, amount);
                update.setString(2, playerId.toString());
                update.setString(3, crateId);
                int updated = update.executeUpdate();
                if (updated > 0) {
                    return null;
                }
            }
            String insertSql = "INSERT INTO crate_keys (player_uuid, crate_id, amount) VALUES (?, ?, ?)";
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, playerId.toString());
                insert.setString(2, crateId);
                insert.setInt(3, amount);
                insert.executeUpdate();
            }
            return null;
        });
    }

    void clearMigrationData() {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM crate_cooldowns")) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM crate_keys")) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM crate_opens")) {
                statement.executeUpdate();
            }
            return null;
        });
    }

    Map<UUID, Map<String, Instant>> fetchCooldowns() {
        String sql = "SELECT player_uuid, crate_id, cooldown_at FROM crate_cooldowns";
        return withConnection(connection -> {
            Map<UUID, Map<String, Instant>> result = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerId = UUID.fromString(resultSet.getString("player_uuid"));
                    String crateId = resultSet.getString("crate_id");
                    Instant timestamp = Instant.ofEpochMilli(resultSet.getLong("cooldown_at"));
                    result.computeIfAbsent(playerId, key -> new HashMap<>()).put(crateId, timestamp);
                }
            }
            return result;
        });
    }

    Map<UUID, Map<String, Integer>> fetchKeys() {
        String sql = "SELECT player_uuid, crate_id, amount FROM crate_keys";
        return withConnection(connection -> {
            Map<UUID, Map<String, Integer>> result = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerId = UUID.fromString(resultSet.getString("player_uuid"));
                    String crateId = resultSet.getString("crate_id");
                    int amount = resultSet.getInt("amount");
                    result.computeIfAbsent(playerId, key -> new HashMap<>()).put(crateId, amount);
                }
            }
            return result;
        });
    }

    List<OpenHistoryEntry> fetchOpenHistory() {
        String sql = "SELECT player_uuid, crate_id, reward_id, server_id, opened_at FROM crate_opens";
        return withConnection(connection -> {
            List<OpenHistoryEntry> history = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerId = UUID.fromString(resultSet.getString("player_uuid"));
                    String crateId = resultSet.getString("crate_id");
                    String rewardId = resultSet.getString("reward_id");
                    String serverId = resultSet.getString("server_id");
                    Instant timestamp = Instant.ofEpochMilli(resultSet.getLong("opened_at"));
                    history.add(new OpenHistoryEntry(playerId, crateId, rewardId, serverId, timestamp));
                }
            }
            return history;
        });
    }

    private boolean isConstraintViolation(SQLException ex) {
        String state = ex.getSQLState();
        return "23000".equals(state) || "23505".equals(state);
    }

    private <T> T withConnection(SqlFunction<Connection, T> function) {
        Connection connection = null;
        try {
            connection = pool.borrowConnection();
            return function.apply(connection);
        } catch (SQLException ex) {
            throw new StorageUnavailableException("Error SQL en storage", ex);
        } finally {
            if (connection != null) {
                pool.releaseConnection(connection);
            }
        }
    }

    private void ensureFirstOpenTable() {
        String sql = "CREATE TABLE IF NOT EXISTS crate_first_opens ("
                + "player_uuid VARCHAR(36) PRIMARY KEY,"
                + "opened_at BIGINT NOT NULL"
                + ")";
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void ensureOpenStartedTable() {
        String sql = "CREATE TABLE IF NOT EXISTS crate_open_starts ("
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "crate_id VARCHAR(64) NOT NULL,"
                + "server_id VARCHAR(64),"
                + "opened_at BIGINT NOT NULL"
                + ")";
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
            return null;
        });
    }

    @FunctionalInterface
    private interface SqlFunction<T, R> {
        R apply(T value) throws SQLException;
    }

    private static class SqlConnectionPool {
        private final BlockingQueue<Connection> connections;
        private final StorageSettings settings;
        private final Logger logger;

        SqlConnectionPool(StorageSettings settings, Logger logger) {
            this.settings = settings;
            this.logger = logger;
            int size = Math.max(1, settings.poolSize());
            this.connections = new ArrayBlockingQueue<>(size);
            initializeConnections(size);
        }

        private void initializeConnections(int size) {
            for (int i = 0; i < size; i++) {
                try {
                    connections.add(createConnection());
                } catch (SQLException ex) {
                    close();
                    throw new StorageUnavailableException("No se pudo crear el pool de conexiones", ex);
                }
            }
        }

        Connection borrowConnection() {
            try {
                Connection connection = connections.poll(settings.poolTimeoutMillis(), TimeUnit.MILLISECONDS);
                if (connection == null) {
                    throw new StorageUnavailableException("Timeout esperando una conexión SQL");
                }
                if (!connection.isValid(2)) {
                    logger.warning("Conexión SQL inválida, recreando.");
                    closeConnection(connection);
                    connection = createConnection();
                }
                return connection;
            } catch (SQLException ex) {
                throw new StorageUnavailableException("No se pudo obtener conexión SQL", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new StorageUnavailableException("Interrupción esperando conexión SQL", ex);
            }
        }

        void releaseConnection(Connection connection) {
            if (connection == null) {
                return;
            }
            if (!connections.offer(connection)) {
                closeConnection(connection);
            }
        }

        void close() {
            Connection connection;
            while ((connection = connections.poll()) != null) {
                closeConnection(connection);
            }
        }

        private Connection createConnection() throws SQLException {
            if (settings.jdbcUrl().isBlank()) {
                throw new SQLException("jdbc-url vacío");
            }
            Properties props = new Properties();
            if (!settings.username().isBlank()) {
                props.setProperty("user", settings.username());
            }
            if (!settings.password().isBlank()) {
                props.setProperty("password", settings.password());
            }
            return DriverManager.getConnection(settings.jdbcUrl(), props);
        }

        private void closeConnection(Connection connection) {
            try {
                connection.close();
            } catch (SQLException ex) {
                logger.warning("No se pudo cerrar conexión SQL: " + ex.getMessage());
            }
        }
    }
}
