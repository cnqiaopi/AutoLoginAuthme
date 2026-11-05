package Mistral.listener;

import Mistral.auth.LoginCoordinator;
import Mistral.compat.TaskScheduler;
import Mistral.storage.SessionStore;
import Mistral.util.PlayerNames;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.LogoutEvent;
import fr.xephi.authme.events.UnregisterByAdminEvent;
import fr.xephi.authme.events.UnregisterByPlayerEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class AuthMeListener implements Listener {
    private final LoginCoordinator coordinator;
    private final SessionStore store;
    private final TaskScheduler scheduler;
    public AuthMeListener(LoginCoordinator coordinator, SessionStore store, TaskScheduler scheduler) {
        this.coordinator = coordinator; this.store = store; this.scheduler = scheduler;
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void login(LoginEvent event) { Player player = event.getPlayer(); scheduler.sync(() -> coordinator.authenticated(player)); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void logout(LogoutEvent event) { Player player = event.getPlayer(); scheduler.sync(() -> coordinator.logout(player)); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void unregister(UnregisterByPlayerEvent event) {
        Player player = event.getPlayer();
        scheduler.sync(() -> coordinator.logout(player));
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void unregisterAdmin(UnregisterByAdminEvent event) {
        String name = event.getPlayerName();
        if (!PlayerNames.isValid(name)) return;
        scheduler.sync(() -> { coordinator.revoke(name); store.delete(name); });
    }
}
