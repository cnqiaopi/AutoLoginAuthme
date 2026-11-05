package Mistral.bootstrap;

import Mistral.auth.AuthGateway;
import Mistral.auth.LoginCoordinator;
import Mistral.compat.TaskScheduler;
import Mistral.config.PluginSettings;
import Mistral.listener.AuthMeListener;
import Mistral.listener.ClientBrandListener;
import Mistral.listener.CredentialCommandListener;
import Mistral.listener.PlayerConnectionListener;
import Mistral.message.PlayerMessages;
import Mistral.monitor.CacheMaintenance;
import Mistral.storage.SessionStore;
import java.time.Clock;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginRuntime implements AutoCloseable {
    public final PluginSettings settings;
    public final SessionStore store;
    public final LoginCoordinator coordinator;
    private final AuthMeListener authListener;
    private final PlayerConnectionListener playerListener;
    private final CredentialCommandListener credentialListener;
    private ClientBrandListener brandListener;
    private CacheMaintenance maintenance;

    public PluginRuntime(JavaPlugin plugin, SessionStore store, AuthGateway gateway, TaskScheduler scheduler, PluginSettings settings) {
        this.settings = settings;
        this.store = store;
        coordinator = new LoginCoordinator(store, gateway, scheduler, settings, new PlayerMessages(settings), Clock.systemUTC(), plugin.getLogger());
        authListener = new AuthMeListener(coordinator, store, scheduler);
        playerListener = new PlayerConnectionListener(coordinator);
        credentialListener = new CredentialCommandListener(plugin, coordinator, store);
        try {
            brandListener = new ClientBrandListener(plugin, coordinator);
            maintenance = new CacheMaintenance(plugin, store, settings);
            plugin.getServer().getPluginManager().registerEvents(authListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(playerListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(credentialListener, plugin);
            // 重载时保留在线玩家的连接身份，但没有品牌的连接必须重新手动认证。
            for (Player player : plugin.getServer().getOnlinePlayers()) coordinator.join(player);
        } catch (RuntimeException | LinkageError exception) { close(); throw exception; }
    }

    @Override public void close() {
        coordinator.close();
        HandlerList.unregisterAll(authListener);
        HandlerList.unregisterAll(playerListener);
        HandlerList.unregisterAll(credentialListener);
        if (brandListener != null) { brandListener.close(); brandListener = null; }
        if (maintenance != null) { maintenance.close(); maintenance = null; }
        store.retire();
    }
}
