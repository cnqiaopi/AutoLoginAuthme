package Mistral.storage;

import Mistral.cache.SessionCache;
import Mistral.model.PlayerSession;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 单个有界工作队列串行读写，确保保存、删除、后续查询的先后顺序。 */
public final class SessionStore implements AutoCloseable {
    private final SessionRepository repository;
    private final SessionCache cache;
    private final boolean cacheEnabled;
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final Logger logger;
    private final CompletableFuture<Void> termination = new CompletableFuture<Void>();

    public SessionStore(SessionRepository repository, SessionCache cache, boolean cacheEnabled, int queueSize, Logger logger) {
        this.repository = repository;
        this.cache = cache;
        this.cacheEnabled = cacheEnabled;
        this.logger = logger;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(queueSize), task -> {
            Thread worker = new Thread(task, "AutoLoginAuthme-database");
            worker.setDaemon(true);
            worker.setContextClassLoader(SessionStore.class.getClassLoader());
            return worker;
        }) {
            @Override protected void terminated() {
                try { repository.close(); termination.complete(null); }
                catch (RuntimeException exception) { termination.completeExceptionally(exception); }
            }
        };
    }

    public CompletableFuture<PlayerSession> find(String name) {
        return submit(() -> {
            if (!healthy.get()) throw new IllegalStateException("存储故障后已暂停自动登录，请检查数据库并 /autologin reload");
            PlayerSession session = cacheEnabled ? cache.get(name) : null;
            if (session == null) {
                session = repository.find(name);
                if (cacheEnabled && session != null) cache.put(name, session);
            }
            return session;
        });
    }

    public CompletableFuture<Void> save(String name, PlayerSession session) {
        return submit(() -> {
            repository.save(name, session);
            if (cacheEnabled) cache.put(name, session);
            return null;
        });
    }

    public CompletableFuture<Void> delete(String name) {
        // 立即失效缓存；队列中的数据库操作仍严格按提交顺序执行。
        cache.remove(name);
        return submit(() -> { cache.remove(name); repository.delete(name); return null; });
    }

    private <T> CompletableFuture<T> submit(DatabaseTask<T> action) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        try {
            executor.execute(() -> {
                try { future.complete(action.run()); }
                catch (Exception exception) { fail(exception); future.completeExceptionally(exception); }
            });
        } catch (RejectedExecutionException exception) {
            fail(exception);
            future.completeExceptionally(exception);
        }
        return future;
    }

    private void fail(Exception exception) {
        cache.clear();
        if (healthy.getAndSet(false)) logger.log(Level.SEVERE, "会话存储失败，已暂停本实例自动登录；修复后执行 /autologin reload。", exception);
    }

    public int clearCache() { return cache.clear(); }
    public void maintainCache(boolean clear) { if (clear) cache.clear(); else cache.removeExpired(); }
    public int cachedPlayers() { return cache.size(); }
    public boolean isHealthy() { return healthy.get() && !executor.isShutdown(); }
    public void retire() { executor.shutdown(); }
    public CompletableFuture<Void> termination() { return termination; }

    @Override public void close() {
        retire();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) logger.warning("数据库任务尚在结束，连接池将在队列处理完成后关闭。");
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private interface DatabaseTask<T> { T run() throws Exception; }
}
