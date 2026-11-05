package Mistral.auth;

import Mistral.compat.BrandDecoder;
import Mistral.compat.ClientBrandSource;
import Mistral.compat.TaskScheduler;
import Mistral.config.PluginSettings;
import Mistral.message.PlayerMessages;
import Mistral.model.PlayerSession;
import Mistral.security.SessionPolicy;
import Mistral.storage.SessionStore;
import Mistral.util.IpAddresses;
import Mistral.util.PlayerNames;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/** 玩家对象和连接状态只在主线程访问；异步回调必须匹配原连接对象。 */
public final class LoginCoordinator implements AutoCloseable {
    private final Map<UUID, Connection> connections = new HashMap<UUID, Connection>();
    private final SessionStore store;
    private final AuthGateway auth;
    private final TaskScheduler scheduler;
    private final PluginSettings settings;
    private final PlayerMessages messages;
    private final SessionPolicy policy;
    private final Clock clock;
    private final Logger logger;
    private boolean closed;

    public LoginCoordinator(SessionStore store, AuthGateway auth, TaskScheduler scheduler, PluginSettings settings,
                            PlayerMessages messages, Clock clock, Logger logger) {
        this.store = store;
        this.auth = auth;
        this.scheduler = scheduler;
        this.settings = settings;
        this.messages = messages;
        this.clock = clock;
        this.logger = logger;
        policy = new SessionPolicy(clock, settings.zone, settings.maxMinutes, settings.allowOp);
    }

    public void join(Player player) {
        if (closed || !PlayerNames.isValid(player.getName())) return;
        quit(player);
        Connection connection = new Connection(player, IpAddresses.of(player));
        connections.put(player.getUniqueId(), connection);
        connection.delay = scheduler.later(() -> {
            if (!active(connection)) return;
            connection.ready = true;
            attempt(connection);
        }, settings.delayTicks);
        connection.timeout = scheduler.later(() -> {
            if (!active(connection)) return;
            connection.brandTimedOut = true;
            attempt(connection);
        }, settings.brandWaitTicks);
    }

    public void brand(Player player, byte[] payload) {
        Connection connection = connections.get(player.getUniqueId());
        if (connection == null || connection.player != player || !active(connection) || connection.brandRejected) return;
        String brand = BrandDecoder.decode(payload);
        if (brand == null || (connection.brand != null && !connection.brand.equals(brand))) {
            connection.revoked = true;
            connection.brandRejected = true;
            connection.brand = null;
            connection.stopped = true;
            connection.cancel();
            store.delete(connection.name);
            return;
        }
        connection.brand = brand;
        if (connection.manualAuthenticatedAt > 0) capture(connection);
        else attempt(connection);
    }

    private void attempt(Connection connection) {
        if (!connection.brandRejected && connection.brand == null) connection.brand = ClientBrandSource.read(connection.player);
        if (!active(connection) || connection.stopped || connection.lookup || !connection.ready
                || (connection.brand == null && !connection.brandTimedOut)) return;
        Player player = connection.player;
        if (auth.isAuthenticated(player) || (player.isOp() && !settings.allowOp) || !IpAddresses.isUsable(connection.ip)) {
            connection.stopped = true;
            return;
        }
        connection.lookup = true;
        final String requestedBrand = connection.brand;
        store.find(connection.name).whenComplete((session, failure) -> scheduler.sync(() -> {
            if (!active(connection) || connection.stopped || connection.revoked || !store.isHealthy()
                    || auth.isAuthenticated(player)) return;
            connection.lookup = false;
            if (failure != null) { connection.stopped = true; return; }
            // 查询期间品牌可能才到达：按最新品牌重新检查，绝不沿用旧快照。
            if (requestedBrand == null && connection.brand != null) { attempt(connection); return; }
            SessionPolicy.Decision decision = policy.evaluate(session, IpAddresses.of(player), connection.brand, player.isOp());
            connection.stopped = true;
            if (decision == SessionPolicy.Decision.ALLOW) {
                connection.autoRequested = true;
                try { auth.forceLogin(player); }
                catch (RuntimeException exception) {
                    connection.autoRequested = false;
                    connection.revoked = true;
                    logger.log(Level.WARNING, "AuthMe 自动登录调用失败: " + connection.name, exception);
                }
            } else if (decision == SessionPolicy.Decision.EXPIRED || decision == SessionPolicy.Decision.ENVIRONMENT_CHANGED) {
                store.delete(connection.name);
                if (decision == SessionPolicy.Decision.EXPIRED) messages.send(player, settings.expiredMessage);
                else if (settings.environmentAlert) messages.send(player, settings.environmentMessage);
            }
            if (settings.debug) logger.info("会话检查: " + connection.name + " -> " + decision);
        }));
    }

    /** 只有 AuthMe 的成功事件才确认登录；本插件触发的自动登录不延长凭证。 */
    public void authenticated(Player player) {
        Connection connection = connections.get(player.getUniqueId());
        if (connection == null || connection.player != player || !active(connection)) return;
        connection.stopped = true;
        connection.cancel();
        if (connection.autoRequested) {
            if (connection.revoked) { auth.forceLogout(player); return; }
            if (!connection.revoked && !connection.successSent) {
                messages.send(player, settings.successMessage);
                connection.successSent = true;
            }
            return;
        }
        if (connection.manualAuthenticatedAt > 0) return;
        connection.revoked = false;
        connection.manualAuthenticatedAt = clock.instant().getEpochSecond();
        capture(connection);
    }

    private void capture(Connection connection) {
        if (!connection.brandRejected && connection.brand == null) connection.brand = ClientBrandSource.read(connection.player);
        if (!active(connection) || connection.saved || connection.revoked || connection.autoRequested || connection.brandRejected
                || !BrandDecoder.isKnown(connection.brand) || !IpAddresses.isUsable(connection.ip)
                || (connection.player.isOp() && !settings.allowOp)) return;
        connection.saved = true;
        // 在主线程完成所有玩家状态读取后，只向数据库队列传不可变快照。
        store.save(connection.name, new PlayerSession(connection.ip, connection.brand, connection.manualAuthenticatedAt));
    }

    /** 先取消在线连接的待登录请求，再排队删除，防止旧查询回调恢复凭证。 */
    public void revoke(String name) {
        String key = PlayerNames.key(name);
        for (Connection connection : connections.values()) {
            if (PlayerNames.key(connection.name).equals(key)) {
                connection.revoked = true;
                connection.stopped = true;
                connection.manualAuthenticatedAt = 0;
                connection.cancel();
            }
        }
    }

    public void logout(Player player) {
        Connection connection = connections.get(player.getUniqueId());
        if (connection != null && connection.player == player) {
            if (connection.successSent) { connection.autoRequested = false; connection.successSent = false; }
            connection.saved = false;
        }
        revoke(player.getName());
        store.delete(player.getName());
    }

    public void quit(Player player) {
        Connection connection = connections.get(player.getUniqueId());
        if (connection != null && connection.player == player) {
            connections.remove(player.getUniqueId());
            connection.cancel();
        }
    }

    private boolean active(Connection connection) {
        return !closed && connections.get(connection.player.getUniqueId()) == connection && connection.player.isOnline();
    }

    @Override public void close() {
        closed = true;
        for (Connection connection : connections.values()) connection.cancel();
        connections.clear();
    }

    private static final class Connection {
        final Player player;
        final String name;
        final String ip;
        String brand;
        boolean ready, brandTimedOut, lookup, stopped, revoked, autoRequested, successSent, saved, brandRejected;
        long manualAuthenticatedAt;
        TaskScheduler.Task delay, timeout;
        Connection(Player player, String ip) { this.player = player; name = player.getName(); this.ip = ip; }
        void cancel() { if (delay != null) delay.cancel(); if (timeout != null) timeout.cancel(); }
    }
}
