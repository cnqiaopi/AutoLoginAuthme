package Mistral.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IpAddressesTest {
    @Test void acceptsIpv4AndIpv6Literals() { assertTrue(IpAddresses.isUsable("192.168.1.2")); assertTrue(IpAddresses.isUsable("2001:db8::1")); }
    @Test void comparesEquivalentIpv6Forms() { assertTrue(IpAddresses.same("2001:db8::1", "2001:0db8:0:0:0:0:0:1")); }
    @Test void rejectsHostnamesAndMalformedIps() { for (String ip : new String[]{"localhost", "example.com", "256.0.0.1", "1.2.3", "::ffff:invalid", ""}) assertFalse(IpAddresses.isUsable(ip), ip); }
    @Test void rejectsEveryLoopbackAndUnspecifiedAddress() { for (String ip : new String[]{"127.0.0.1", "127.8.9.10", "::1", "0:0:0:0:0:0:0:1", "0.0.0.0", "::"}) assertFalse(IpAddresses.isUsable(ip), ip); }
    @Test void masksBothAddressFamilies() { assertEquals("203.**.**.20", IpAddresses.mask("203.0.113.20")); assertEquals("2001:****:****:****", IpAddresses.mask("2001:db8::1")); }
    @Test void playerKeysAreCaseInsensitiveAndValidated() { assertEquals("alice", PlayerNames.key("Alice")); assertFalse(PlayerNames.isValid("a' OR 1=1")); assertThrows(IllegalArgumentException.class, () -> PlayerNames.key("../x")); }
}
