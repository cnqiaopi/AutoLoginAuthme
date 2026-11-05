package Mistral.message;

import Mistral.config.PluginSettings;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;

public final class PlayerMessages {
    private final PluginSettings settings;
    private final Method timedTitle;
    private final Method legacyTitle;

    public PlayerMessages(PluginSettings settings) {
        this.settings = settings;
        timedTitle = method(String.class, String.class, int.class, int.class, int.class);
        legacyTitle = method(String.class, String.class);
    }

    private static Method method(Class<?>... arguments) {
        try { return Player.class.getMethod("sendTitle", arguments); }
        catch (NoSuchMethodException exception) { return null; }
    }

    public void send(Player player, String text) {
        if ("none".equals(settings.messageType)) return;
        if ("title".equals(settings.messageType)) {
            try {
                if (timedTitle != null) { timedTitle.invoke(player, " ", text, 20, 70, 20); return; }
                if (legacyTitle != null) { legacyTitle.invoke(player, " ", text); return; }
            } catch (ReflectiveOperationException | IllegalArgumentException exception) {
                // 不支持标题的分支回退到聊天栏。
            }
        }
        player.sendMessage(settings.prefix + text);
    }
}
