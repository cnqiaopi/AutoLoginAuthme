package Mistral.compat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BrandDecoderTest {
    public static byte[] packet(String brand) {
        byte[] bytes = brand.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int length = bytes.length;
        do {
            int part = length & 127;
            length >>>= 7;
            output.write(part | (length == 0 ? 0 : 128));
        } while (length != 0);
        output.write(bytes, 0, bytes.length);
        return output.toByteArray();
    }
    @Test void readsVarintPrefixWithoutChangingBrand() { assertEquals("vanilla", BrandDecoder.decode(packet("vanilla"))); }
    @Test void preservesMeaningfulPunctuationAndUnicode() { assertEquals("fabric (模组)", BrandDecoder.decode(packet("fabric (模组)"))); }
    @Test void rejectsRawUnframedBrand() { assertNull(BrandDecoder.decode("vanilla".getBytes(StandardCharsets.UTF_8))); }
    @Test void rejectsMissingEmptyAndUnknownBrands() { assertNull(BrandDecoder.decode(null)); assertNull(BrandDecoder.decode(packet(""))); assertNull(BrandDecoder.decode(packet("unknown"))); }
    @Test void rejectsTruncationAndTrailingData() { assertNull(BrandDecoder.decode(new byte[]{4, 65})); assertNull(BrandDecoder.decode(new byte[]{1, 65, 66})); }
    @Test void rejectsMalformedVarintAndUtf8() {
        assertNull(BrandDecoder.decode(new byte[]{(byte) 128, (byte) 128, (byte) 128, (byte) 128, 16, 65}));
        assertNull(BrandDecoder.decode(new byte[]{(byte) 129, 0, 65}));
        assertNull(BrandDecoder.decode(new byte[]{1, (byte) 255}));
    }
    @Test void rejectsControlCharactersAndOverlongBrands() {
        assertNull(BrandDecoder.decode(packet("vanilla\n")));
        assertNull(BrandDecoder.decode(packet("§cvanilla")));
        assertNull(BrandDecoder.decode(packet(String.join("", java.util.Collections.nCopies(65, "a")))));
    }
    @Test void selectsChannelForLegacyAndModernServers() {
        assertEquals("MC|Brand", BrandChannels.forVersion("1.8.8-R0.1-SNAPSHOT"));
        assertEquals("MC|Brand", BrandChannels.forVersion("1.12.2-R0.1-SNAPSHOT"));
        assertEquals("minecraft:brand", BrandChannels.forVersion("1.13-R0.1-SNAPSHOT"));
        assertEquals("minecraft:brand", BrandChannels.forVersion("26.3-242"));
    }
}
