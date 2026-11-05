package Mistral.bootstrap;

import Mistral.loader.PluginLifecycle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import Mistral.auth.AuthGateway;
import Mistral.auth.AuthMeGateway;
import Mistral.bootstrap.PluginRuntime;
import Mistral.cache.SessionCache;
import Mistral.command.AutoLoginCommand;
import Mistral.compat.BukkitTaskScheduler;
import Mistral.config.PluginSettings;
import Mistral.storage.JdbcSessionRepository;
import Mistral.storage.SessionStore;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class PluginApplication implements PluginLifecycle {
    private final JavaPlugin plugin;
    public PluginApplication(JavaPlugin plugin) { this.plugin = plugin; }
    private volatile boolean stopping;
    private boolean reloading;
    private PluginRuntime runtime;
    private AuthGateway gateway;
    private BukkitTaskScheduler scheduler;
    private ExecutorService initializer;
    private final Set<SessionStore> stores = Collections.newSetFromMap(new ConcurrentHashMap<SessionStore, Boolean>());

    @Override public void enable() {
        stopping = false;
        scheduler = new BukkitTaskScheduler(plugin);
        initializer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "AutoLoginAuthme-initializer");
            thread.setDaemon(true);
            thread.setContextClassLoader(PluginApplication.class.getClassLoader());
            return thread;
        });
        SessionStore opened = null;
        try {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("AuthMe")) throw new IllegalStateException("请先启用兼容此服务端版本的 AuthMe");
            gateway = new AuthMeGateway();
            plugin.saveDefaultConfig();
            PluginSettings settings = readSettings();
            opened = openStore(settings);
            runtime = new PluginRuntime(plugin, opened, gateway, scheduler, settings);
            PluginCommand command = plugin.getCommand("autologin");
            if (command == null) throw new IllegalStateException("plugin.yml 缺少 autologin 命令");
            AutoLoginCommand handler = new AutoLoginCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
            plugin.getLogger().info("AutoLoginAuthme " + plugin.getDescription().getVersion() + " 已启用；AuthMe + MySQL，会话有效期 " + settings.maxMinutes + " 分钟，时区 " + settings.zone);
        } catch (Exception | LinkageError exception) {
            if (runtime == null && opened != null) opened.close();
            logDatabaseGuidance(exception);
            plugin.getLogger().log(Level.SEVERE, "AutoLoginAuthme 初始化失败", exception);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    private void logDatabaseGuidance(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (!(cause instanceof java.sql.SQLException)) continue;
            java.sql.SQLException sql = (java.sql.SQLException) cause;
            if (sql.getErrorCode() == 1045 || "28000".equals(sql.getSQLState())) {
                plugin.getLogger().severe("MySQL 身份验证被拒绝，请检查 " + new File(plugin.getDataFolder(), "config.yml").getAbsolutePath()
                    + " 中的 database.username、database.password 及账号访问权限。using password: NO 表示此次连接未提供密码；修正配置后重启服务端。");
                return;
            }
        }
    }

    private PluginSettings readSettings() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(plugin.getDataFolder(), "config.yml"));
        return PluginSettings.load(yaml);
    }

    private SessionStore openStore(PluginSettings settings) throws Exception {
        SessionStore store = new SessionStore(JdbcSessionRepository.open(settings.database),
            new SessionCache(settings.cacheSize, settings.cacheMillis, Clock.systemUTC()), settings.cacheEnabled, settings.queueSize, plugin.getLogger());
        stores.add(store);
        store.termination().whenComplete((ignored, failure) -> stores.remove(store));
        if (stopping) store.retire();
        return store;
    }

    public void reloadSettings(CommandSender sender) {
        if (reloading || stopping) { sender.sendMessage("§c配置正在重载或插件正在停止。"); return; }
        final PluginSettings next;
        try { next = readSettings(); }
        catch (Exception exception) { sender.sendMessage("§c配置无效，重载失败：" + exception.getMessage()); return; }
        reloading = true;
        sender.sendMessage("§7正在检查新数据库配置…");
        initializer.execute(() -> {
            final SessionStore replacement;
            try { replacement = openStore(next); }
            catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "配置重载失败，当前运行配置继续使用。", exception);
                scheduler.sync(() -> { reloading = false; sender.sendMessage("§c重载失败，数据库连接或表初始化失败，详情见控制台。"); });
                return;
            }
            if (stopping) { replacement.close(); return; }
            scheduler.sync(() -> {
                if (stopping) { replacement.retire(); return; }
                PluginRuntime previous = runtime;
                previous.close();
                previous.store.termination().whenComplete((ignored, failure) -> scheduler.sync(() -> {
                    if (stopping) { replacement.retire(); return; }
                    try {
                        runtime = new PluginRuntime(plugin, replacement, gateway, scheduler, next);
                        reloading = false;
                        sender.sendMessage(next.prefix + "§a配置、连接池、缓存和监控已重载。");
                    } catch (RuntimeException exception) {
                        replacement.retire();
                        plugin.getLogger().log(Level.SEVERE, "重载安装失败", exception);
                        plugin.getServer().getPluginManager().disablePlugin(plugin);
                    }
                }));
            });
        });
    }

    public PluginRuntime runtime() { return reloading ? null : runtime; }
    public BukkitTaskScheduler scheduler() { return scheduler; }

    public void toggleDebug(CommandSender sender) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            File file = new File(plugin.getDataFolder(), "config.yml");
            yaml.load(file);
            yaml.set("debug", !yaml.getBoolean("debug", false));
            yaml.save(file);
            reloadSettings(sender);
        } catch (Exception exception) { sender.sendMessage("§c保存调试配置失败：" + exception.getMessage()); }
    }

    @Override public CompletableFuture<Void> disable() {
        stopping = true;
        if (scheduler != null) scheduler.close();
        if (runtime != null) { runtime.close(); runtime = null; }
        for (SessionStore store : stores) store.retire();
        if (initializer != null) initializer.shutdown();
        CompletableFuture<Void> complete = new CompletableFuture<Void>();
        Thread closer = new Thread(() -> {
            try {
                if (initializer != null) initializer.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                CompletableFuture<?>[] pending = stores.stream().map(SessionStore::termination).toArray(CompletableFuture<?>[]::new);
                CompletableFuture.allOf(pending).join();
                releaseJdbcDriver();
                complete.complete(null);
            } catch (Exception exception) { complete.completeExceptionally(exception); }
        }, "AutoLoginAuthme-close");
        closer.setDaemon(true);
        closer.start();
        return complete;
    }

    private void releaseJdbcDriver() throws Exception {
        // MySQL 会注册 JVM 级清理线程和 JDBC 驱动，只清理本插件隔离加载器内的实例。
        Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread", true, getClass().getClassLoader())
            .getMethod("uncheckedShutdown").invoke(null);
        java.util.Enumeration<java.sql.Driver> drivers = java.sql.DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            java.sql.Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == getClass().getClassLoader()) java.sql.DriverManager.deregisterDriver(driver);
        }
    }
}
