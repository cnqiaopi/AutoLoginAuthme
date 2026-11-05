package Mistral.storage;

import Mistral.cache.SessionCache;
import Mistral.model.PlayerSession;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {
    static final PlayerSession VALUE = new PlayerSession("203.0.113.1", "vanilla", 100);
    final Logger logger = Logger.getLogger("SessionStoreTest");
    { logger.setLevel(Level.OFF); }
    SessionStore store(SessionRepository repository, SessionCache cache, int capacity) { return new SessionStore(repository, cache, true, capacity, logger); }

    @Test void failedSaveCannotPopulateCacheOrPermitFurtherAutoLogin() throws Exception {
        SessionCache cache = new SessionCache(2, 10000, Clock.systemUTC());
        SessionRepository repository = new Repository() { @Override public void save(String name, PlayerSession session) throws SQLException { throw new SQLException("offline"); } };
        try (SessionStore store = store(repository, cache, 8)) {
            assertThrows(ExecutionException.class, () -> store.save("Alice", VALUE).get(2, TimeUnit.SECONDS));
            assertEquals(0, cache.size());
            assertFalse(store.isHealthy());
            assertThrows(ExecutionException.class, () -> store.find("Alice").get(2, TimeUnit.SECONDS));
        }
    }
    @Test void operationsAreSerializedAndPoolClosesAfterWrites() throws Exception {
        List<String> operations = new ArrayList<String>();
        SessionRepository repository = new Repository() {
            @Override public void save(String name, PlayerSession session) { operations.add("save"); }
            @Override public void delete(String name) { operations.add("delete"); }
            @Override public void close() { operations.add("close"); }
        };
        SessionStore store = store(repository, new SessionCache(2, 10000, Clock.systemUTC()), 8);
        store.save("Alice", VALUE);
        store.delete("Alice").get(2, TimeUnit.SECONDS);
        store.close();
        assertEquals(java.util.Arrays.asList("save", "delete", "close"), operations);
    }
    @Test void failedDeletionClearsCacheAndDisablesReads() throws Exception {
        SessionCache cache = new SessionCache(2, 10000, Clock.systemUTC());
        cache.put("Alice", VALUE);
        SessionRepository repository = new Repository() { @Override public void delete(String name) throws SQLException { throw new SQLException("offline"); } };
        try (SessionStore store = store(repository, cache, 8)) {
            assertThrows(ExecutionException.class, () -> store.delete("Alice").get(2, TimeUnit.SECONDS));
            assertNull(cache.get("Alice"));
            assertFalse(store.isHealthy());
        }
    }
    @Test void queueSaturationDoesNotRunDatabaseOnCallingThread() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnMain = new AtomicBoolean();
        Thread main = Thread.currentThread();
        SessionRepository repository = new Repository() {
            @Override public PlayerSession find(String name) throws SQLException {
                calledOnMain.set(Thread.currentThread() == main);
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                return null;
            }
        };
        try (SessionStore store = store(repository, new SessionCache(2, 10000, Clock.systemUTC()), 1)) {
            store.find("Alice");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            store.find("Bob");
            CompletableFuture<PlayerSession> rejected = store.find("Carol");
            assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS));
            assertFalse(store.isHealthy());
            release.countDown();
            assertFalse(calledOnMain.get());
        } finally { release.countDown(); }
    }
    static class Repository implements SessionRepository {
        @Override public PlayerSession find(String name) throws SQLException { return null; }
        @Override public void save(String name, PlayerSession session) throws SQLException { }
        @Override public void delete(String name) throws SQLException { }
        @Override public void close() { }
    }
}
