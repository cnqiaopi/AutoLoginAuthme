package Mistral.config;

import java.util.Arrays;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

public final class DatabaseSettings {
    public final String jdbcUrl, username, password;
    public final int maximumPoolSize, minimumIdle;
    public final long connectionTimeout, maxLifetime;

    DatabaseSettings(FileConfiguration c) {
        String host = c.getString("database.host", "localhost");
        if (!host.matches("[A-Za-z0-9.\\-_:]+")) throw new IllegalArgumentException("database.host 无效");
        if (host.indexOf(':') >= 0) host = "[" + host + "]";
        int port = (int) PluginSettings.range(c.getLong("database.port", 3306), 1, 65535, "database.port");
        String name = c.getString("database.database", "minecraft");
        if (!name.matches("[A-Za-z0-9_\\-]+")) throw new IllegalArgumentException("database.database 无效");
        String sslMode = c.getString("database.ssl-mode", "PREFERRED").toUpperCase(Locale.ROOT);
        if (!Arrays.asList("PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY", "DISABLED").contains(sslMode)) throw new IllegalArgumentException("database.ssl-mode 无效");
        String custom = c.getString("database.jdbc-url", "").trim();
        if (!custom.isEmpty() && !custom.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("database.jdbc-url 必须为 MySQL JDBC URL");
        jdbcUrl = custom.isEmpty() ? "jdbc:mysql://" + host + ":" + port + "/" + name
            + "?useUnicode=true&characterEncoding=UTF-8&connectTimeout=5000&socketTimeout=10000&sslMode=" + sslMode : custom;
        username = c.getString("database.username", "root");
        password = c.getString("database.password", "");
        maximumPoolSize = (int) PluginSettings.range(c.getLong("database.pool.maximum-pool-size", 10), 1, 128, "database.pool.maximum-pool-size");
        minimumIdle = (int) PluginSettings.range(c.getLong("database.pool.minimum-idle", 2), 0, maximumPoolSize, "database.pool.minimum-idle");
        connectionTimeout = PluginSettings.range(c.getLong("database.pool.connection-timeout", 5000), 250, 60000, "database.pool.connection-timeout");
        maxLifetime = PluginSettings.range(c.getLong("database.pool.max-lifetime", 1800000), 30000, 86400000, "database.pool.max-lifetime");
    }
}
