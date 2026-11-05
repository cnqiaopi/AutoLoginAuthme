package Mistral.listener;

import Mistral.auth.LoginCoordinator;
import Mistral.security.CredentialCommands;
import Mistral.storage.SessionStore;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** AuthMe 5.6 没有通用的密码修改事件，因此在其修改密码命令执行前撤销旧凭证。 */
public final class CredentialCommandListener implements Listener {
    private final JavaPlugin plugin;
    private final LoginCoordinator coordinator;
    private final SessionStore store;
    public CredentialCommandListener(JavaPlugin plugin, LoginCoordinator coordinator, SessionStore store) {
        this.plugin = plugin; this.coordinator = coordinator; this.store = store;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerCommand(PlayerCommandPreprocessEvent event) { inspect(event.getMessage(), event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void serverCommand(ServerCommandEvent event) { inspect(event.getCommand(), event.getSender()); }

    private void inspect(String command, CommandSender sender) {
        String label = command.replaceFirst("^/", "").trim().split("\\s+", 2)[0];
        PluginCommand registered = plugin.getServer().getPluginCommand(label);
        if (registered == null || !"AuthMe".equals(registered.getPlugin().getName())) return;
        String actor = sender instanceof Player ? sender.getName() : null;
        String target = CredentialCommands.affectedPlayer(command, actor, sender.hasPermission("authme.admin.changepassword"));
        if (target != null) { coordinator.revoke(target); store.delete(target); }
    }
}
