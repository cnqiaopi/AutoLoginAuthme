package Mistral.security;

import Mistral.util.PlayerNames;
import java.util.Locale;

/** 只提取需要撤销凭证的账号，不保存、不记录密码参数。 */
public final class CredentialCommands {
    private CredentialCommands() { }
    public static String affectedPlayer(String command, String actor, boolean canChangeOthers) {
        String[] parts = command.replaceFirst("^/", "").trim().split("\\s+", 4);
        if (parts.length == 0) return null;
        String label = parts[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) label = label.substring(namespace + 1);
        if (("changepassword".equals(label) || "changepass".equals(label) || "cp".equals(label))
                && parts.length >= 3 && PlayerNames.isValid(actor)) return actor;
        if ("email".equals(label) && parts.length >= 3 && "setpassword".equalsIgnoreCase(parts[1])
                && PlayerNames.isValid(actor)) return actor;
        if ("authme".equals(label) && parts.length >= 4 && canChangeOthers
                && ("password".equalsIgnoreCase(parts[1]) || "changepassword".equalsIgnoreCase(parts[1]))
                && PlayerNames.isValid(parts[2])) return parts[2];
        return null;
    }
}
