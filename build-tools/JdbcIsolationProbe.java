import Mistral.loader.ExternalLibraries;
import Mistral.loader.LibraryClassLoader;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Properties;

/** 独立进程验证：父加载器已有 MySQL 9.2 时，Hikari 实际选中的驱动来源。无需连接数据库。 */
public final class JdbcIsolationProbe {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        Path serverDriverJar = root.resolve("build-tools/runtime-cache/libraries/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar");
        URL protobuf = root.resolve("libs/protobuf-java-3.25.1.jar").toUri().toURL();
        try (URLClassLoader server = new URLClassLoader(new URL[]{serverDriverJar.toUri().toURL(), protobuf}, ClassLoader.getSystemClassLoader().getParent());
             LibraryClassLoader plugin = new LibraryClassLoader(ExternalLibraries.urls(root.resolve("AutoLoginAuthme-3.0.0.jar").toUri().toURL(), root.resolve("libs")), server)) {
            Driver shared = (Driver) Class.forName("com.mysql.cj.jdbc.Driver", true, server).getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(shared);
            System.out.println("Server driver: " + shared.getMajorVersion() + "." + shared.getMinorVersion());
            Thread.currentThread().setContextClassLoader(server);
            Class<?> dataSourceType = Class.forName("com.zaxxer.hikari.util.DriverDataSource", true, plugin);
            Object dataSource = dataSourceType.getConstructor(String.class, String.class, Properties.class, String.class, String.class)
                .newInstance("jdbc:mysql://localhost:3306/minecraft", "com.mysql.cj.jdbc.Driver", new Properties(), "root", "");
            Field field = dataSourceType.getDeclaredField("driver");
            field.setAccessible(true);
            Driver legacy = (Driver) field.get(dataSource);
            System.out.println("Driver-class lookup selected: " + legacy.getMajorVersion() + "." + legacy.getMinorVersion());
            Class<?> mysqlType = Class.forName("com.mysql.cj.jdbc.MysqlDataSource", true, plugin);
            Object explicitSource = mysqlType.getDeclaredConstructor().newInstance();
            mysqlType.getMethod("setURL", String.class).invoke(explicitSource, "jdbc:mysql://localhost:3306/minecraft");
            Class<?> configType = Class.forName("com.zaxxer.hikari.HikariConfig", true, plugin);
            Object config = configType.getDeclaredConstructor().newInstance();
            configType.getMethod("setDataSource", javax.sql.DataSource.class).invoke(config, explicitSource);
            configType.getMethod("setMinimumIdle", int.class).invoke(config, 0);
            configType.getMethod("setInitializationFailTimeout", long.class).invoke(config, -1L);
            Object pool = Class.forName("com.zaxxer.hikari.HikariDataSource", true, plugin).getConstructor(configType).newInstance(config);
            try {
                Object pooled = pool.getClass().getMethod("unwrap", Class.class).invoke(pool, mysqlType);
                if (pooled != explicitSource) throw new AssertionError("Hikari replaced the explicit DataSource");
            } finally { pool.getClass().getMethod("close").invoke(pool); }
            Field ownDriver = mysqlType.getDeclaredField("mysqlDriver");
            ownDriver.setAccessible(true);
            Driver actual = (Driver) ownDriver.get(null);
            if (actual.getClass().getClassLoader() != plugin || actual.getMajorVersion() != 8 || actual.getMinorVersion() != 4) {
                throw new AssertionError("Hikari selected a foreign driver: " + actual.getClass().getProtectionDomain().getCodeSource().getLocation());
            }
            System.out.println("Hikari driver: " + actual.getMajorVersion() + "." + actual.getMinorVersion());
            System.out.println("Hikari driver source: " + actual.getClass().getProtectionDomain().getCodeSource().getLocation());
            Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread", true, plugin).getMethod("uncheckedShutdown").invoke(null);
            Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread", true, server).getMethod("uncheckedShutdown").invoke(null);
            System.out.println("PASS: isolated driver retained; no database connection attempted.");
        }
    }
}
