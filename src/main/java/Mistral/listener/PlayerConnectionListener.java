package Mistral.listener;

import Mistral.auth.LoginCoordinator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {
    private final LoginCoordinator coordinator;
    public PlayerConnectionListener(LoginCoordinator coordinator) { this.coordinator = coordinator; }
    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) { coordinator.join(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) { coordinator.quit(event.getPlayer()); }
}
