package Mistral.listener;

import Mistral.auth.LoginCoordinator;
import Mistral.compat.BrandChannels;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ClientBrandListener implements PluginMessageListener, AutoCloseable {
    private final JavaPlugin plugin;
    private final LoginCoordinator coordinator;
    private final String channel;
    public ClientBrandListener(JavaPlugin plugin, LoginCoordinator coordinator) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        channel = BrandChannels.forVersion(plugin.getServer().getBukkitVersion());
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, this);
    }
    @Override public void onPluginMessageReceived(String name, Player player, byte[] message) {
        if (channel.equals(name)) coordinator.brand(player, message);
    }
    @Override public void close() { plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, channel, this); }
}
