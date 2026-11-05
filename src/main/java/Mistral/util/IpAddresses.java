package Mistral.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.bukkit.entity.Player;

public final class IpAddresses {
    private IpAddresses() { }

    public static String of(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null ? "" : address.getAddress().getHostAddress();
    }

    /** 仅接受 IP 字面值，不对玩家可控字符串发起 DNS 查询。 */
    private static InetAddress parse(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) return null;
        if (value.indexOf(':') >= 0) {
            if (!value.matches("[0-9a-fA-F:.]+")) return null;
        } else {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) return null;
            for (String part : parts) {
                if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255) return null;
            }
        }
        try { return InetAddress.getByName(value); }
        catch (UnknownHostException exception) { return null; }
    }

    public static boolean isUsable(String value) {
        InetAddress address = parse(value);
        return address != null && !address.isAnyLocalAddress() && !address.isLoopbackAddress() && !address.isMulticastAddress();
    }

    public static boolean same(String first, String second) {
        InetAddress a = parse(first);
        InetAddress b = parse(second);
        return a != null && b != null && Arrays.equals(a.getAddress(), b.getAddress());
    }

    public static String mask(String value) {
        InetAddress address = parse(value);
        if (address == null) return "unknown";
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) return (bytes[0] & 255) + ".**.**." + (bytes[3] & 255);
        return Integer.toHexString(((bytes[0] & 255) << 8) | (bytes[1] & 255)) + ":****:****:****";
    }
}
