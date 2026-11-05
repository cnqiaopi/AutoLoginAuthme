package Mistral.auth;

import Mistral.cache.SessionCache;
import Mistral.compat.BrandDecoderTest;
import Mistral.compat.TaskScheduler;
import Mistral.config.PluginSettings;
import Mistral.message.PlayerMessages;
import Mistral.model.PlayerSession;
import Mistral.storage.SessionRepository;
import Mistral.storage.SessionStore;
import Mistral.util.PlayerNames;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginCoordinatorTest {
    final long now = Instant.parse("2026-11-05T04:00:00Z").getEpochSecond();
    final Thread owner = Thread.currentThread();
    final MemoryRepository repository = new MemoryRepository();
    final FakeScheduler scheduler = new FakeScheduler();
    final FakeAuth auth = new FakeAuth();
    final List<String> messages = new ArrayList<String>();
    final UUID uuid = UUID.randomUUID();
    SessionStore store;
    LoginCoordinator coordinator;
    Player player;

    @BeforeEach void setup() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("timezone", "Asia/Shanghai");
        PluginSettings settings = PluginSettings.load(yaml);
        store = new SessionStore(repository, new SessionCache(20, 60000, Clock.systemUTC()), true, 32, Logger.getLogger("LoginCoordinatorTest"));
        coordinator = new LoginCoordinator(store, auth, scheduler, settings, new PlayerMessages(settings), Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC), Logger.getLogger("LoginCoordinatorTest"));
        player = newPlayer();
    }
    @AfterEach void cleanup() { coordinator.close(); store.close(); }

    Player newPlayer() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
            assertSame(owner, Thread.currentThread(), "Bukkit Player 只能在主线程访问");
            switch (method.getName()) {
                case "getName": return "Alice";
                case "getUniqueId": return uuid;
                case "getAddress": return new InetSocketAddress("203.0.113.1", 25565);
                case "isOnline": return true;
                case "isOp": return false;
                case "sendMessage": messages.add((String) args[0]); return null;
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "Player(Alice)";
                default:
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
            }
        });
    }
    void existing() { repository.data.put("alice", new PlayerSession("203.0.113.1", "vanilla", now - 30)); }
    void joinWithBrand() { coordinator.join(player); coordinator.brand(player, BrandDecoderTest.packet("vanilla")); scheduler.advance(20); }
    void callback() throws Exception { Runnable callback = scheduler.callbacks.poll(3, TimeUnit.SECONDS); assertNotNull(callback, "等待异步查询完成"); callback.run(); }
    void fence() throws Exception { store.find("Barrier").get(3, TimeUnit.SECONDS); }

    @Test void sendsSuccessOnlyAfterAuthMeEventAndDoesNotRefreshTimestamp() throws Exception {
        existing(); joinWithBrand(); callback();
        assertEquals(1, auth.logins);
        assertTrue(messages.isEmpty());
        auth.authenticated = true;
        coordinator.authenticated(player);
        assertEquals(1, messages.size());
        assertEquals(now - 30, store.find("Alice").get(3, TimeUnit.SECONDS).getAuthenticatedAt());
        assertEquals(0, repository.saves);
    }
    @Test void manualAuthenticationSavesImmutableSnapshot() throws Exception {
        coordinator.join(player);
        coordinator.brand(player, BrandDecoderTest.packet("vanilla"));
        auth.authenticated = true;
        coordinator.authenticated(player);
        PlayerSession saved = store.find("Alice").get(3, TimeUnit.SECONDS);
        assertEquals(now, saved.getAuthenticatedAt());
        assertEquals("vanilla", saved.getBrand());
        assertEquals(1, repository.saves);
    }
    @Test void manualAuthenticationCanReceiveBrandLater() throws Exception {
        coordinator.join(player);
        coordinator.authenticated(player);
        fence(); assertEquals(0, repository.saves);
        coordinator.brand(player, BrandDecoderTest.packet("vanilla"));
        fence(); assertEquals(1, repository.saves);
    }
    @Test void disconnectInvalidatesQueryCallback() throws Exception {
        existing(); joinWithBrand(); coordinator.quit(player); callback(); assertEquals(0, auth.logins);
    }
    @Test void oldCallbackCannotAuthenticateReconnectedPlayerWithSameUuid() throws Exception {
        existing(); joinWithBrand();
        coordinator.quit(player);
        Player replacement = newPlayer(); coordinator.join(replacement);
        callback(); assertEquals(0, auth.logins);
    }
    @Test void adminRemovalInvalidatesAlreadyQueuedResult() throws Exception {
        existing(); joinWithBrand(); coordinator.revoke("ALICE"); store.delete("ALICE").get(3, TimeUnit.SECONDS); callback();
        assertEquals(0, auth.logins); assertNull(store.find("Alice").get(3, TimeUnit.SECONDS));
    }
    @Test void missingBrandWaitsThenRevokesInsteadOfBypassingCheck() throws Exception {
        existing(); coordinator.join(player); scheduler.advance(20); assertTrue(scheduler.callbacks.isEmpty());
        scheduler.advance(80); callback(); fence();
        assertEquals(0, auth.logins); assertNull(repository.data.get("alice"));
    }
    @Test void malformedBrandCannotLaterMintCredentialFromStaleBrand() throws Exception {
        coordinator.join(player); coordinator.brand(player, BrandDecoderTest.packet("vanilla"));
        coordinator.brand(player, new byte[]{1, (byte) 255});
        coordinator.authenticated(player); fence();
        assertEquals(0, repository.saves);
    }
    @Test void logoutDeletesCredentialAndAllowsFreshManualLogin() throws Exception {
        existing(); coordinator.join(player); coordinator.brand(player, BrandDecoderTest.packet("vanilla"));
        coordinator.authenticated(player); fence();
        coordinator.logout(player); fence(); assertNull(repository.data.get("alice"));
        coordinator.authenticated(player); fence(); assertNotNull(repository.data.get("alice"));
    }
    @Test void revokedInFlightAuthMeLoginIsLoggedOut() throws Exception {
        existing(); joinWithBrand(); callback(); coordinator.revoke("Alice"); coordinator.authenticated(player);
        assertEquals(1, auth.logouts); assertTrue(messages.isEmpty());
    }
    @Test void closeInvalidatesPendingCallbacks() throws Exception {
        existing(); joinWithBrand(); coordinator.close(); callback(); assertEquals(0, auth.logins);
    }

    final class MemoryRepository implements SessionRepository {
        final Map<String, PlayerSession> data = new HashMap<String, PlayerSession>();
        int saves;
        @Override public PlayerSession find(String name) { assertNotSame(owner, Thread.currentThread()); return data.get(PlayerNames.key(name)); }
        @Override public void save(String name, PlayerSession session) { assertNotSame(owner, Thread.currentThread()); data.put(PlayerNames.key(name), session); saves++; }
        @Override public void delete(String name) { assertNotSame(owner, Thread.currentThread()); data.remove(PlayerNames.key(name)); }
        @Override public void close() { }
    }
    static final class FakeAuth implements AuthGateway {
        boolean authenticated;
        int logins, logouts;
        @Override public boolean isAuthenticated(Player player) { return authenticated; }
        @Override public void forceLogin(Player player) { logins++; }
        @Override public void forceLogout(Player player) { logouts++; }
    }
    static final class FakeScheduler implements TaskScheduler {
        long now;
        final BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<Runnable>();
        final List<Later> delayed = new ArrayList<Later>();
        @Override public void sync(Runnable action) { callbacks.add(action); }
        @Override public Task later(Runnable action, long ticks) { Later later = new Later(action, now + ticks); delayed.add(later); return () -> later.cancelled = true; }
        void advance(long ticks) {
            long target = now + ticks;
            while (true) {
                Later next = delayed.stream().filter(task -> !task.cancelled && task.due <= target).min(Comparator.comparingLong(task -> task.due)).orElse(null);
                if (next == null) break;
                delayed.remove(next); now = next.due; next.action.run();
            }
            now = target;
        }
        static final class Later {
            final Runnable action; final long due; boolean cancelled;
            Later(Runnable action, long due) { this.action = action; this.due = due; }
        }
    }
}
