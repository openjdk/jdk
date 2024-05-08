package sun.security.util;

import sun.security.ssl.SSLSessionImpl;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class MemoryQueue<K,V> extends Cache<K,V> {


    private static final float LOAD_FACTOR = 0.75f;

    // XXXX
    private static final boolean DEBUG = false;

    private final ConcurrentHashMap<K, CacheEntry<K,V>> cacheMap;
    private int maxSize;
    private long lifetime;
    private long nextExpirationTime = Long.MAX_VALUE;
    private boolean softEntry;

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
        this.softEntry = soft;
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
                queue.stream().forEach(e -> queue.remove(e));
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

        /*
        for (Iterator<CacheEntry<K,V>> t = cacheMap.values().iterator();
             t.hasNext(); ) {
            CacheEntry<K,V> entry = t.next();
            if (!entry.isValid(time)) {
                t.remove();
                cnt++;
            } else if (nextExpirationTime > entry.getExpirationTime()) {
                nextExpirationTime = entry.getExpirationTime();
            }
        }
        if (DEBUG) {
            if (cnt != 0) {
                System.out.println("Removed " + cnt
                    + " expired entries, remaining " + cacheMap.size());
            }
        }

         */
    }

    public synchronized int size() {
        expungeExpiredEntries();
        return cacheMap.size();
    }

    public synchronized void clear() {
        if (rQueue != null) {
            // if this is a SoftReference cache, first invalidate() all
            // entries so that GC does not have to enqueue them
//            for (CacheEntry<K,V> entry : cacheMap.values()) {
//                entry.invalidate();
//            }


            cacheMap.values().parallelStream().forEach(q -> q.invalidate());
            //q.getQueue().forEach(e -> e.invalidate()));

            while (rQueue.poll() != null) {
                // empty
            }
        }
        cacheMap.clear();
    }

    /*
    private void cleanQueues() {
        cacheMap.keySet().parallelStream().forEach(k -> {
            var q = cacheMap.get(k).getQueue(); // Queue<QueueCacheEntry<K,V>>
            qlock.lock();
            CacheEntry<K, V> lastEntry, entry = q.poll();
            if (entry == null) {
                cacheMap.remove(k);
            }

            do {
                lastEntry = entry;
                entry = q.poll();
            } while (entry != null);
            q.add(lastEntry);
            qlock.unlock();
        });
    }

     */

    public void put(K key, V value) {
        emptyQueue();
        long expirationTime = (lifetime == 0) ? 0 : System.currentTimeMillis() + lifetime;
        if (expirationTime < nextExpirationTime) {
            nextExpirationTime = expirationTime;
        }
        boolean isPSK = false;
        if (value instanceof SSLSessionImpl session) {
            isPSK = session.isPSK();
        }

        CacheEntry<K,V> newEntry = newEntry(key, value, expirationTime, rQueue);
        CacheEntry<K,V> entry = cacheMap.get(key);
        switch (entry) {
            case QueueCacheEntry<K,V> qe ->
                    qe.putValue(newEntry);
            case null,
            default -> {
                if (isPSK) {
                    var q = new QueueCacheEntry<>(key, value, expirationTime, this.rQueue);
                    q.putValue(newEntry);
                    cacheMap.put(key, q);
                } else {
                    cacheMap.put(key, newEntry);
                }
            }

        }

        //QueueCacheEntry<K,V> q = new QueueCacheEntry<K,V>(key, value, expirationTime, this.queue);
        // lock?
        /*
        QueueCacheEntry<K, V> q = cacheMap.get(key);
        if (q == null) {
            q = new QueueCacheEntry<K, V>(key, value, expirationTime, rQueue);
            cacheMap.put(key, q);
        }
        q.putValue(newEntry);

         */

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
        V v;
        if (value == null) return null;

        if (lifetime > 0 && !value.isValid(System.currentTimeMillis())) {
            cacheMap.remove(key);
            return null;
        }


        // cleanup
        if (value instanceof QueueCacheEntry<K,V> qe) {
            v = (lifetime == 0 ? qe.getValue() : qe.getValue(lifetime));
            if (qe.isEmpty()) {
                cacheMap.remove(key);
            }
        } else {
            v = value.getValue();
        }

        return v;
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
        maxSize = Math.max(size, 0);

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
