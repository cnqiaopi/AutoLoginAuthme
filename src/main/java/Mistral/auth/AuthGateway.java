package Mistral.auth;

import org.bukkit.entity.Player;

public interface AuthGateway {
    boolean isAuthenticated(Player player);
    void forceLogin(Player player);
    void forceLogout(Player player);
}
