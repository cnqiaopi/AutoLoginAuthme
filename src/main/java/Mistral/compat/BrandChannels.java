package Mistral.compat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrandChannels {
    private BrandChannels() { }
    public static String forVersion(String version) {
        Matcher match = Pattern.compile("^1\\.(\\d+)(?:\\D|$)").matcher(version);
        return match.find() && Integer.parseInt(match.group(1)) < 13 ? "MC|Brand" : "minecraft:brand";
    }
}
