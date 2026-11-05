package Mistral.security;

import Mistral.model.PlayerSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import static Mistral.security.SessionPolicy.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionPolicyTest {
    final ZoneId zone = ZoneId.of("Asia/Shanghai");
    final Instant now = Instant.parse("2026-11-05T04:00:00Z");
    SessionPolicy policy(long minutes, boolean allowOp) { return new SessionPolicy(Clock.fixed(now, zone), zone, minutes, allowOp); }
    PlayerSession session(long secondsAgo) { return new PlayerSession("203.0.113.20", "vanilla", now.getEpochSecond() - secondsAgo); }

    @Test void matchingEnvironmentIsAllowed() { assertEquals(ALLOW, policy(60, false).evaluate(session(30), "203.0.113.20", "vanilla", false)); }
    @Test void missingRecordIsDenied() { assertEquals(MISSING, policy(60, false).evaluate(null, "203.0.113.20", "vanilla", false)); }
    @Test void exactExpiryBoundaryIsDenied() { assertEquals(EXPIRED, policy(60, false).evaluate(session(3600), "203.0.113.20", "vanilla", false)); }
    @Test void futureTimeIsDenied() { assertEquals(EXPIRED, policy(0, false).evaluate(session(-1), "203.0.113.20", "vanilla", false)); }
    @Test void zeroTimestampIsDenied() { assertEquals(EXPIRED, policy(0, false).evaluate(new PlayerSession("203.0.113.20", "vanilla", 0), "203.0.113.20", "vanilla", false)); }
    @Test void zeroLimitStillExpiresAtMidnight() { assertEquals(EXPIRED, policy(0, false).evaluate(session(86400), "203.0.113.20", "vanilla", false)); }
    @Test void midnightBoundaryHasNoHourLongCacheDelay() {
        Instant midnight = Instant.parse("2026-11-04T16:00:00Z");
        SessionPolicy p = new SessionPolicy(Clock.fixed(midnight, zone), zone, 60, false);
        assertEquals(EXPIRED, p.evaluate(new PlayerSession("203.0.113.20", "vanilla", midnight.getEpochSecond() - 1), "203.0.113.20", "vanilla", false));
        assertEquals(ALLOW, p.evaluate(new PlayerSession("203.0.113.20", "vanilla", midnight.getEpochSecond()), "203.0.113.20", "vanilla", false));
    }
    @Test void unknownBrandNeverBypassesCheck() {
        assertEquals(ENVIRONMENT_CHANGED, policy(60, false).evaluate(session(1), "203.0.113.20", "unknown", false));
        assertEquals(ENVIRONMENT_CHANGED, policy(60, false).evaluate(new PlayerSession("203.0.113.20", "unknown", now.getEpochSecond()), "203.0.113.20", "vanilla", false));
    }
    @Test void emptySavedIpNeverBypassesCheck() { assertEquals(ENVIRONMENT_CHANGED, policy(60, false).evaluate(new PlayerSession("", "vanilla", now.getEpochSecond()), "203.0.113.20", "vanilla", false)); }
    @Test void changedIpAndBrandAreDenied() {
        assertEquals(ENVIRONMENT_CHANGED, policy(60, false).evaluate(session(1), "203.0.113.21", "vanilla", false));
        assertEquals(ENVIRONMENT_CHANGED, policy(60, false).evaluate(session(1), "203.0.113.20", "fabric", false));
    }
    @Test void loopbackIpv6IsDenied() { assertEquals(ENVIRONMENT_CHANGED, policy(60, false).evaluate(session(1), "::1", "vanilla", false)); }
    @Test void operatorsRequireOptIn() {
        assertEquals(OP_BLOCKED, policy(60, false).evaluate(session(1), "203.0.113.20", "vanilla", true));
        assertEquals(ALLOW, policy(60, true).evaluate(session(1), "203.0.113.20", "vanilla", true));
    }
}
