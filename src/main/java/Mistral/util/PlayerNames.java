package Mistral.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PlayerNames {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private PlayerNames() { }

    public static boolean isValid(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

    public static String key(String name) {
        if (!isValid(name)) throw new IllegalArgumentException("无效的玩家名");
        return name.toLowerCase(Locale.ROOT);
    }
}
