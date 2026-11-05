package Mistral.compat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitTaskScheduler implements TaskScheduler {
    private final JavaPlugin plugin;
    private volatile boolean closed;
    public BukkitTaskScheduler(JavaPlugin plugin) { this.plugin = plugin; }

    @Override public void sync(Runnable action) {
        if (closed) return;
        if (Bukkit.isPrimaryThread()) { action.run(); return; }
        try { Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) action.run(); }); }
        catch (org.bukkit.plugin.IllegalPluginAccessException exception) {
            if (!closed && plugin.isEnabled()) throw exception;
        }
    }

    @Override public Task later(Runnable action, long ticks) {
        if (closed) return () -> { };
        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!closed) action.run(); }, ticks);
        return task::cancel;
    }

    public void close() { closed = true; Bukkit.getScheduler().cancelTasks(plugin); }
}
