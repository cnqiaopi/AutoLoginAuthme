package Mistral.auth;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.entity.Player;

public final class AuthMeGateway implements AuthGateway {
    private final AuthMeApi api;
    public AuthMeGateway() {
        api = AuthMeApi.getInstance();
        if (api == null) throw new IllegalStateException("AuthMe API 尚未初始化");
    }
    @Override public boolean isAuthenticated(Player player) { return api.isAuthenticated(player); }
    @Override public void forceLogin(Player player) { api.forceLogin(player); }
    @Override public void forceLogout(Player player) { api.forceLogout(player); }
}
