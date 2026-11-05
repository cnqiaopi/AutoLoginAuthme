package Mistral.security;

import Mistral.compat.BrandDecoder;
import Mistral.model.PlayerSession;
import Mistral.util.IpAddresses;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class SessionPolicy {
    public enum Decision { ALLOW, MISSING, EXPIRED, ENVIRONMENT_CHANGED, OP_BLOCKED }
    private final Clock clock;
    private final ZoneId zone;
    private final long maxMinutes;
    private final boolean allowOp;

    public SessionPolicy(Clock clock, ZoneId zone, long maxMinutes, boolean allowOp) {
        this.clock = clock;
        this.zone = zone;
        this.maxMinutes = maxMinutes;
        this.allowOp = allowOp;
    }

    public Decision evaluate(PlayerSession session, String ip, String brand, boolean op) {
        if (op && !allowOp) return Decision.OP_BLOCKED;
        if (session == null) return Decision.MISSING;
        long now = clock.instant().getEpochSecond();
        long saved = session.getAuthenticatedAt();
        if (saved <= 0 || saved > now) return Decision.EXPIRED;
        if ((maxMinutes > 0 && (now - saved) / 60 >= maxMinutes)
                || !Instant.ofEpochSecond(saved).atZone(zone).toLocalDate().equals(LocalDate.now(clock.withZone(zone)))) {
            return Decision.EXPIRED;
        }
        if (!IpAddresses.isUsable(ip) || !IpAddresses.isUsable(session.getIp())
                || !IpAddresses.same(session.getIp(), ip) || !BrandDecoder.isKnown(brand)
                || !BrandDecoder.isKnown(session.getBrand()) || !session.getBrand().equals(brand)) {
            return Decision.ENVIRONMENT_CHANGED;
        }
        return Decision.ALLOW;
    }
}
