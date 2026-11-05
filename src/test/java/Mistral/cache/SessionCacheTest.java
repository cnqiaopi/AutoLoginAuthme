package Mistral.cache;

import Mistral.model.PlayerSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionCacheTest {
    final MutableClock clock = new MutableClock();
    final PlayerSession value = new PlayerSession("203.0.113.1", "vanilla", 1);
    @Test void expiresAtBoundary() { SessionCache cache = new SessionCache(2, 100, clock); cache.put("Alice", value); clock.millis = 100; assertNull(cache.get("alice")); }
    @Test void evictsLeastRecentlyUsedEntry() { SessionCache cache = new SessionCache(2, 100, clock); cache.put("Alice", value); cache.put("Bob", value); cache.get("ALICE"); cache.put("Carol", value); assertNull(cache.get("Bob")); assertSame(value, cache.get("alice")); assertEquals(2, cache.size()); }
    @Test void cleanupAndCaseInsensitiveRemovalWork() { SessionCache cache = new SessionCache(2, 100, clock); cache.put("Alice", value); cache.remove("ALICE"); assertEquals(0, cache.size()); cache.put("Bob", value); clock.millis = 101; cache.removeExpired(); assertEquals(0, cache.size()); }
    @Test void clockRollbackInvalidatesCache() { SessionCache cache = new SessionCache(2, 100, clock); cache.put("Alice", value); clock.millis = -1; assertNull(cache.get("alice")); }
    private static final class MutableClock extends Clock {
        long millis;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
