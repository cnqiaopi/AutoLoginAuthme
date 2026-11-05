package Mistral.storage;

import Mistral.config.DatabaseSettings;
import Mistral.model.PlayerSession;
import Mistral.util.PlayerNames;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

public final class JdbcSessionRepository implements SessionRepository {
    private final DataSource dataSource;
    private final Runnable closer;

    public static JdbcSessionRepository open(DatabaseSettings settings) throws SQLException {
        HikariDataSource pool = new HikariDataSource(poolConfiguration(settings));
        try { return new JdbcSessionRepository(pool, pool::close); }
        catch (SQLException exception) { pool.close(); throw exception; }
    }

    static HikariConfig poolConfiguration(DatabaseSettings settings) throws SQLException {
        // 直接使用本加载器的实例，避免 Hikari 经线程上下文/DriverManager 选中服务端的同名驱动。
        MysqlDataSource mysql = new MysqlDataSource();
        mysql.setURL(settings.jdbcUrl);
        mysql.setUser(settings.username);
        mysql.setPassword(settings.password);
        mysql.setCachePrepStmts(true);
        mysql.setPrepStmtCacheSize(250);
        mysql.setPrepStmtCacheSqlLimit(2048);
        HikariConfig config = new HikariConfig();
        config.setPoolName("AutoLoginAuthme-MySQL");
        config.setDataSource(mysql);
        config.setUsername(settings.username);
        config.setPassword(settings.password);
        config.setMaximumPoolSize(settings.maximumPoolSize);
        config.setMinimumIdle(settings.minimumIdle);
        config.setConnectionTimeout(settings.connectionTimeout);
        config.setMaxLifetime(settings.maxLifetime);
        config.setInitializationFailTimeout(settings.connectionTimeout);
        return config;
    }

    public JdbcSessionRepository(DataSource dataSource, Runnable closer) throws SQLException {
        this.dataSource = dataSource;
        this.closer = closer;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS login_sessions ("
                + "player_name VARCHAR(16) NOT NULL PRIMARY KEY, last_ip VARCHAR(45) NOT NULL DEFAULT '', "
                + "last_brand VARCHAR(64) NOT NULL DEFAULT 'unknown', time BIGINT NOT NULL DEFAULT 0, INDEX idx_time (time)) "
                + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    @Override public PlayerSession find(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT last_ip, last_brand, time FROM login_sessions WHERE LOWER(player_name) = ? ORDER BY time DESC LIMIT 1")) {
            statement.setString(1, PlayerNames.key(name));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new PlayerSession(result.getString(1), result.getString(2), result.getLong(3)) : null;
            }
        }
    }

    /** 事务中规范化旧版大小写记录，避免同一账号出现两条凭证。 */
    @Override public void save(String name, PlayerSession session) throws SQLException {
        String key = PlayerNames.key(name);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                delete(connection, key);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO login_sessions (player_name, last_ip, last_brand, time) VALUES (?, ?, ?, ?)")) {
                    statement.setString(1, key);
                    statement.setString(2, session.getIp());
                    statement.setString(3, session.getBrand());
                    statement.setLong(4, session.getAuthenticatedAt());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            } finally { connection.setAutoCommit(autoCommit); }
        }
    }

    @Override public void delete(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection()) { delete(connection, PlayerNames.key(name)); }
    }

    private void delete(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM login_sessions WHERE LOWER(player_name) = ?")) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    @Override public void close() { closer.run(); }
}
