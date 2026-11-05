package Mistral.config;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

/** 启动和重载共用的不可变配置，避免异步任务读取 Bukkit 配置对象。 */
public final class PluginSettings {
    public final long maxMinutes;
    public final boolean allowOp;
    public final long delayTicks;
    public final long brandWaitTicks;
    public final boolean environmentAlert;
    public final String prefix, successMessage, environmentMessage, expiredMessage, messageType;
    public final ZoneId zone;
    public final boolean cacheEnabled;
    public final int cacheSize, queueSize;
    public final long cacheMillis;
    public final boolean monitoringEnabled;
    public final long monitorTicks, memoryThresholdBytes;
    public final boolean debug;
    public final DatabaseSettings database;

    private PluginSettings(FileConfiguration c) {
        maxMinutes = range(c.getLong("max_time", 60), 0, 52560000L, "max_time");
        allowOp = c.getBoolean("useon_op", false);
        delayTicks = range(c.getLong("lagging_enabled", 20), 1, 1200, "lagging_enabled");
        brandWaitTicks = range(c.getLong("brand_wait_ticks", 100), delayTicks, 2400, "brand_wait_ticks");
        environmentAlert = c.getBoolean("useaddress_alert", true);
        prefix = color(c.getString("prefix", "&7[&aAutoLoginAuthme&7] "));
        successMessage = color(c.getString("success_login", "&a已自动登录！"));
        environmentMessage = color(c.getString("address_change", "&c环境变更，请手动登录。"));
        expiredMessage = color(c.getString("time_out", "&7凭证已过期，请手动登录。"));
        messageType = c.getString("message_type", "message").toLowerCase(Locale.ROOT);
        if (!Arrays.asList("message", "title", "none").contains(messageType)) throw new IllegalArgumentException("message_type 必须为 message/title/none");
        String timezone = c.getString("timezone", "system");
        zone = "system".equalsIgnoreCase(timezone) ? ZoneId.systemDefault() : ZoneId.of(timezone);
        cacheEnabled = c.getBoolean("performance.enable_cache", true);
        cacheSize = (int) range(c.getLong("performance.cache_max_size", 1000), 1, 1000000, "performance.cache_max_size");
        cacheMillis = range(c.getLong("performance.cache_expire_minutes", 30), 1, 525600, "performance.cache_expire_minutes") * 60000;
        queueSize = (int) range(c.getLong("performance.database_queue_size", 1024), 16, 65536, "performance.database_queue_size");
        monitoringEnabled = c.getBoolean("monitoring.enable_memory_monitor", true);
        monitorTicks = range(c.getLong("monitoring.monitor_interval", 300), 1, 86400, "monitoring.monitor_interval") * 20;
        memoryThresholdBytes = range(c.getLong("monitoring.auto_clean_threshold", 100), 1, 1048576, "monitoring.auto_clean_threshold") * 1048576;
        debug = c.getBoolean("debug", false);
        database = new DatabaseSettings(c);
    }

    public static PluginSettings load(FileConfiguration config) { return new PluginSettings(config); }
    public static String color(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }

    static long range(long value, long minimum, long maximum, String key) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException(key + " 必须在 " + minimum + " 到 " + maximum + " 之间");
        return value;
    }
}
