package com.extracrates.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
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
    public void close() {
        pool.close();
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
