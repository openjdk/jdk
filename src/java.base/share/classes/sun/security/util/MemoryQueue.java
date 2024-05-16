package sun.security.util;

import sun.security.ssl.SSLSessionImpl;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This Cache will store either SoftCacheEntry or QueueCacheEntry depending on
 * the entries:
 * - If the value is an SSLSessionImpl that does not contain a
 * stateless ticket or TLS 1.3 PSK, it will use a SoftCacheEntry because there
 * only needs to be one entry per key.
 * - If the value is an SSLSessionImpl that does contain a stateless ticket
 * or TLS 1.3 PSK, it will use a QueueCacheEntry to allow multiple entries.
 *
 * This Cache is designed for SSLSessionImpl and only used with the
 * client-side cache
 */

class MemoryQueue<K,V> extends Cache<K,V> {
    private static final float LOAD_FACTOR = 0.75f;
    private static final boolean DEBUG = true;

    private final ConcurrentHashMap<K, CacheEntry<K,V>> cacheMap;
    private int maxSize;
    private long lifetime;
    private long nextExpirationTime = Long.MAX_VALUE;

    private static ReentrantReadWriteLock lock;
    private static ReentrantLock qlock;

    // ReferenceQueue is of type V instead of Cache<K,V>
    // to allow SoftCacheEntry to extend SoftReference<V>
    private final ReferenceQueue<V> rQueue;

    public MemoryQueue(boolean soft, int maxSize) {
        this(soft, maxSize, 0);
    }

    public MemoryQueue(boolean soft, int maxSize, int lifetime) {
        this.maxSize = maxSize;
        this.lifetime = lifetime * 1000L;
        this.rQueue = (soft ? new ReferenceQueue<>() : null);
        cacheMap = new ConcurrentHashMap<>(1, LOAD_FACTOR);
    }

    /**
     * Empty the reference queue and remove all corresponding entries
     * from the cache.
     *
     * This method should be called at the beginning of each public
     * method.
     */
    private void emptyQueue() {
        if (rQueue == null) {
            return;
        }

        int count = 0;
        while (true) {
            @SuppressWarnings("unchecked")
            CacheEntry<K,V> entry = (CacheEntry<K,V>) rQueue.poll();

            if (entry == null) {
                break;
            }
            K key = entry.getKey();
            if (key == null) {
                // key is null, entry has already been removed
                continue;
            }
            var value = cacheMap.get(key);
            if (value instanceof QueueCacheEntry<K,V> qe) {
                Queue<CacheEntry<K,V>> queue = qe.getQueue();
                queue.forEach(e -> queue.remove(e));
            }
            cacheMap.remove(key);
            count++;
        }
        if (DEBUG) {
            if (count > 0) {
                System.out.println("*** Expunged " + count
                    + " entries, " + cacheMap.size() + " entries left");
            }
        }
    }

    /**
     * Scan all entries and remove all expired ones.
     */
    private void expungeExpiredEntries() {
        emptyQueue();
        if (lifetime == 0) {
            return;
        }
        //int cnt = 0;
        long time = System.currentTimeMillis();
        if (nextExpirationTime > time) {
            return;
        }
        nextExpirationTime = Long.MAX_VALUE;
        //AtomicInteger cnt = new AtomicInteger(0);

        cacheMap.keySet().parallelStream().forEach(k -> {
            var v = cacheMap.get(k);
            if (v.isValid(time)) {
                cacheMap.remove(k);
                if (v instanceof QueueCacheEntry<K,V> q) {
                    q.invalidate();
                }
            }
        });

    }

    public synchronized int size() {
        expungeExpiredEntries();
        return cacheMap.size();
    }

    public synchronized void clear() {
        if (rQueue != null) {
            cacheMap.values().parallelStream().forEach(CacheEntry::invalidate);
        }
        cacheMap.clear();
    }

    public void put(K key, V value) {
        put(key, value, false);
    }
    public void put(K key, V value, boolean queueable) {
        emptyQueue();
        long expirationTime =
            (lifetime == 0) ? 0 : System.currentTimeMillis() + lifetime;
        if (expirationTime < nextExpirationTime) {
            nextExpirationTime = expirationTime;
        }

        CacheEntry<K,V> newEntry = newEntry(key, value, expirationTime, rQueue);
        CacheEntry<K,V> entry = cacheMap.get(key);

        if (DEBUG) {
            System.out.println("Cache entry added: key=" +
                key.toString() + ", class=" +
                (entry != null ? entry.getClass().getName() : null));
        }

        switch (entry) {
            case QueueCacheEntry<K,V> qe ->
                qe.putValue(newEntry);
            case null,
                default -> {
                if (queueable) {
                    var q = new QueueCacheEntry<>(key, value, expirationTime,
                        this.rQueue);
                    q.putValue(newEntry);
                    cacheMap.put(key, q);
                } else {
                    cacheMap.put(key, newEntry);
                }
            }
        }


        if (maxSize > 0 && cacheMap.size() > maxSize) {
            expungeExpiredEntries();
            if (cacheMap.size() > maxSize) { // still too large?
                clear();
            }
        }
    }

    public V get(Object key) {
        emptyQueue();
        CacheEntry<K,V> value = cacheMap.get(key);
        V result;
        if (value == null) return null;

        if (lifetime > 0 && !value.isValid(System.currentTimeMillis())) {
            cacheMap.remove(key);
            return null;
        }


        // If the value is a queue, return a queue entry.
        if (value instanceof QueueCacheEntry<K,V> qe) {
            result = (lifetime == 0 ? qe.getValue() : qe.getValue(lifetime));
            if (qe.isEmpty()) {
                cacheMap.remove(key);
            }
        } else {
            result = value.getValue();
        }

        return result;
    }

    public void remove(Object key) {
        emptyQueue();
        CacheEntry<K,V> entry = cacheMap.get(key);

        // If this is a QueueCacheEntry the entry is not removed from the
        // cacheMap as the session has been popped from the queue.  If the entry
        // is empty, it is removed.
        // If it's a SoftCacheEntry, it is removed.


        if (entry instanceof QueueCacheEntry<K,V> qe && !qe.isEmpty()) {
            return;
        }
        CacheEntry<K,V> q = cacheMap.remove(key); // QueueCacheEntry<K,V>>
        if (q != null) {
            q.invalidate();
        }
    }

    public V pull(Object key) {
        // It doesn't make sense to remove the entry when this is a queue
        return get(key);
    }

    public synchronized void setCapacity(int size) {
        expungeExpiredEntries();
        if (cacheMap.size() <= size) return;
        var list = cacheMap.keys();
        while (cacheMap.size() > size) {
            var entry = cacheMap.remove(list.nextElement());
            entry.invalidate();
            if (DEBUG) {
                System.out.println("** capacity reset removal "
                    + entry.getKey() + " | " + entry.getValue());
            }
        }
        maxSize = size;

        if (DEBUG) {
            System.out.println("** capacity reset to " + size);
        }
    }

    public synchronized void setTimeout(int timeout) {
        emptyQueue();
        lifetime = timeout > 0 ? timeout * 1000L : 0L;

        if (DEBUG) {
            System.out.println("** lifetime reset to " + timeout);
        }
    }

    // it is a heavyweight method.
    public synchronized void accept(CacheVisitor<K,V> visitor) {
        expungeExpiredEntries();
        Map<K,V> cached = getCachedEntries();

        visitor.visit(cached);
    }

    private Map<K,V> getCachedEntries() {
        Map<K,V> kvmap = HashMap.newHashMap(cacheMap.size());

        for (CacheEntry<K,V> entry : cacheMap.values()) {
            kvmap.put(entry.getKey(), entry.getValue());
        }

        return kvmap;
    }

    protected CacheEntry<K,V> newEntry(K key, V value,
        long expirationTime, ReferenceQueue<V> queue) {
        if (queue != null) {
            return new SoftCacheEntry<>(key, value, expirationTime, queue);
        } else {
            return new HardCacheEntry<>(key, value, expirationTime);
        }
    }
}
