package Mistral.compat;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class BrandDecoder {
    public static final int MAX_BYTES = 256;
    private BrandDecoder() { }

    /** MC|Brand 与 minecraft:brand 都使用 VarInt 字节长度 + UTF-8 字符串。 */
    public static String decode(byte[] payload) {
        if (payload == null || payload.length < 2 || payload.length > MAX_BYTES + 5) return null;
        int length = 0;
        int offset = 0;
        for (; offset < 5 && offset < payload.length; offset++) {
            int value = payload[offset] & 255;
            if (offset == 4 && (value & 240) != 0) return null;
            length |= (value & 127) << (offset * 7);
            if ((value & 128) == 0) {
                offset++;
                if (offset > 1 && length < (1 << ((offset - 1) * 7))) return null;
                if (length <= 0 || length > MAX_BYTES || payload.length - offset != length) return null;
                try {
                    String brand = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(payload, offset, length)).toString();
                    return isKnown(brand) ? brand : null;
                } catch (CharacterCodingException exception) { return null; }
            }
        }
        return null;
    }

    public static boolean isKnown(String brand) {
        if (brand == null || brand.isEmpty() || brand.length() > 64 || !brand.equals(brand.trim())
                || "unknown".equalsIgnoreCase(brand)) return false;
        for (int i = 0; i < brand.length(); i++) {
            if (Character.isISOControl(brand.charAt(i)) || brand.charAt(i) == '\u00a7') return false;
        }
        return true;
    }
}
