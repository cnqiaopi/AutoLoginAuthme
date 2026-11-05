package Mistral.monitor;

import Mistral.config.PluginSettings;
import Mistral.storage.SessionStore;
import java.lang.management.ManagementFactory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CacheMaintenance implements AutoCloseable {
    private final BukkitTask task;
    public CacheMaintenance(JavaPlugin plugin, SessionStore store, PluginSettings settings) {
        task = settings.monitoringEnabled ? plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            boolean pressure = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() >= settings.memoryThresholdBytes;
            store.maintainCache(pressure);
        }, settings.monitorTicks, settings.monitorTicks) : null;
    }
    @Override public void close() { if (task != null) task.cancel(); }
}
