package Mistral.compat;

import java.lang.reflect.Method;
import org.bukkit.entity.Player;

/** Paper 某些版本由服务端处理 brand 包，优先读取其公开 API，Spigot 回退插件通道。 */
public final class ClientBrandSource {
    private static final Method GET_BRAND = findMethod();
    private ClientBrandSource() { }
    private static Method findMethod() {
        try { return Player.class.getMethod("getClientBrandName"); }
        catch (NoSuchMethodException exception) { return null; }
    }
    public static String read(Player player) {
        if (GET_BRAND == null) return null;
        try {
            Object value = GET_BRAND.invoke(player);
            return value instanceof String && BrandDecoder.isKnown((String) value) ? (String) value : null;
        } catch (ReflectiveOperationException exception) { return null; }
    }
}
