package Mistral.cache;

import Mistral.model.PlayerSession;
import Mistral.util.PlayerNames;
import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** 容量受限、可过期的 LRU；所有访问统一加锁。 */
public final class SessionCache {
    private final int capacity;
    private final long ttlMillis;
    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>(16, .75f, true);

    public SessionCache(int capacity, long ttlMillis, Clock clock) {
        if (capacity < 1 || ttlMillis < 1) throw new IllegalArgumentException("缓存参数必须为正数");
        this.capacity = capacity;
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    public synchronized PlayerSession get(String name) {
        String key = PlayerNames.key(name);
        Entry entry = entries.get(key);
        if (entry == null) return null;
        long age = clock.millis() - entry.created;
        if (age < 0 || age >= ttlMillis) { entries.remove(key); return null; }
        return entry.value;
    }

    public synchronized void put(String name, PlayerSession value) {
        entries.put(PlayerNames.key(name), new Entry(value, clock.millis()));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }

    public synchronized void remove(String name) { entries.remove(PlayerNames.key(name)); }
    public synchronized int clear() { int count = entries.size(); entries.clear(); return count; }
    public synchronized int size() { return entries.size(); }
    public synchronized void removeExpired() {
        long now = clock.millis();
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            long age = now - iterator.next().created;
            if (age < 0 || age >= ttlMillis) iterator.remove();
        }
    }

    private static final class Entry {
        final PlayerSession value;
        final long created;
        Entry(PlayerSession value, long created) { this.value = value; this.created = created; }
    }
}
