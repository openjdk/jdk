/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Abstract base class and factory for caches. A cache is a key-value mapping.
 * It has properties that make it more suitable for caching than a Map.
 *
 * The factory methods can be used to obtain two different implementations.
 * They have the following properties:
 *
 *  . keys and values reside in memory
 *
 *  . keys and values must be non-null
 *
 *  . maximum size. Replacements are made in LRU order.
 *
 *  . optional lifetime, specified in seconds.
 *
 *  . safe for concurrent use by multiple threads
 *
 *  . values are held by either standard references or via SoftReferences.
 *    SoftReferences have the advantage that they are automatically cleared
 *    by the garbage collector in response to memory demand. This makes it
 *    possible to simple set the maximum size to a very large value and let
 *    the GC automatically size the cache dynamically depending on the
 *    amount of available memory.
 *
 * However, note that because of the way SoftReferences are implemented in
 * HotSpot at the moment, this may not work perfectly as it clears them fairly
 * eagerly. Performance may be improved if the Java heap size is set to larger
 * value using e.g. java -ms64M -mx128M foo.Test
 *
 * Cache sizing: the memory cache is implemented on top of a LinkedHashMap.
 * In its current implementation, the number of buckets (NOT entries) in
 * (Linked)HashMaps is always a power of two. It is recommended to set the
 * maximum cache size to value that uses those buckets fully. For example,
 * if a cache with somewhere between 500 and 1000 entries is desired, a
 * maximum size of 750 would be a good choice: try 1024 buckets, with a
 * load factor of 0.75f, the number of entries can be calculated as
 * buckets / 4 * 3. As mentioned above, with a SoftReference cache, it is
 * generally reasonable to set the size to a fairly large value.
 *
 * @author Andreas Sterbenz
 */
public abstract class Cache<K,V> {

    protected Cache() {
        // empty
    }

    /**
     * Return the number of currently valid entries in the cache.
     */
    public abstract int size();

    /**
     * Remove all entries from the cache.
     */
    public abstract void clear();

    /**
     * Add an entry to the cache.
     */
    public abstract void put(K key, V value);

    public void put(K key, V value, boolean flag) {
        put(key, value);
    }

    /**
     * Get a value from the cache.
     */
    public abstract V get(Object key);

    /**
     * Remove an entry from the cache.
     */
    public abstract void remove(Object key);

    /**
     * Pull an entry from the cache.
     */
    public abstract V pull(Object key);

    /**
     * Set the maximum size.
     */
    public abstract void setCapacity(int size);

    /**
     * Set the timeout(in seconds).
     */
    public abstract void setTimeout(int timeout);

    /**
     * accept a visitor
     */
    public abstract void accept(CacheVisitor<K,V> visitor);

    /**
     * Return a new memory cache with the specified maximum size, unlimited
     * lifetime for entries, with the values held by SoftReferences.
     */
    public static <K,V> Cache<K,V> newSoftMemoryCache(int size) {
        return new MemoryCache<K,V>(CacheType.SOFT, size);
    }

    /**
     * Return a new memory cache with the specified maximum size, the
     * specified maximum lifetime (in seconds), with the values held
     * by SoftReferences.
     */
    public static <K,V> Cache<K,V> newSoftMemoryCache(int size, int timeout) {
        return new MemoryCache<K,V>(CacheType.SOFT, size, timeout);
    }

    public static <K,V> Cache<K,V> newSoftMemoryQueue(int size, int timeout) {
        return new MemoryCache<K,V>(CacheType.CACHE, size, timeout);
    }

    /**
     * Return a new memory cache with the specified maximum size, unlimited
     * lifetime for entries, with the values held by standard references.
     */
    public static <K,V> Cache<K,V> newHardMemoryCache(int size) {
        return new MemoryCache<K,V>(CacheType.HARD, size);
    }

    /**
     * Return a dummy cache that does nothing.
     */
    @SuppressWarnings("unchecked")
    public static <K,V> Cache<K,V> newNullCache() {
        return (Cache<K,V>) NullCache.INSTANCE;
    }

    /**
     * Return a new memory cache with the specified maximum size, the
     * specified maximum lifetime (in seconds), with the values held
     * by standard references.
     */
    public static <K,V> Cache<K,V> newHardMemoryCache(int size, int timeout) {
        return new MemoryCache<K,V>(CacheType.HARD, size, timeout);
    }

    /**
     * Utility class that wraps a byte array and implements the equals()
     * and hashCode() contract in a way suitable for Maps and caches.
     */
    public static class EqualByteArray {

        private final byte[] b;
        private int hash;

        public EqualByteArray(byte[] b) {
            this.b = b;
        }

        public int hashCode() {
            int h = hash;
            if (h == 0 && b.length > 0) {
                hash = h = Arrays.hashCode(b);
            }
            return h;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof EqualByteArray other)) {
                return false;
            }
            return Arrays.equals(this.b, other.b);
        }
    }

    public interface CacheVisitor<K,V> {
        void visit(Map<K, V> map);
    }

}

enum CacheType { SOFT, HARD, CACHE }


class NullCache<K,V> extends Cache<K,V> {

    static final Cache<Object,Object> INSTANCE = new NullCache<>();

    private NullCache() {
        // empty
    }

    public int size() {
        return 0;
    }

    public void clear() {
        // empty
    }

    public void put(K key, V value) {
        // empty
    }

    public V get(Object key) {
        return null;
    }

    public void remove(Object key) {
        // empty
    }

    public V pull(Object key) {
        return null;
    }

    public void setCapacity(int size) {
        // empty
    }

    public void setTimeout(int timeout) {
        // empty
    }

    public void accept(CacheVisitor<K,V> visitor) {
        // empty
    }

}

class MemoryCache<K,V> extends Cache<K,V> {

    // Debugging
    private static final boolean DEBUG = false;

    private final Map<K, CacheEntry<K,V>> cacheMap;
    private int maxSize;
    private long lifetime;
    private long nextExpirationTime = Long.MAX_VALUE;
    private final CacheType cacheType;

    // ReferenceQueue is of type V instead of Cache<K,V>
    // to allow SoftCacheEntry to extend SoftReference<V>
    private final ReferenceQueue<V> queue;

    public MemoryCache(CacheType type, int maxSize) {
        this(type, maxSize, 0);
    }

    public MemoryCache(CacheType type, int maxSize, int lifetime) {
        this.maxSize = maxSize;
        this.lifetime = lifetime * 1000L;
        cacheType = type;
        if (cacheType == CacheType.HARD) {
            this.queue = null;
        } else {
            this.queue = new ReferenceQueue<>();
        }

        cacheMap = new ConcurrentHashMap<>();
    }

    /**
     * Empty the reference queue and remove all corresponding entries
     * from the cache.
     *
     * This method should be called at the beginning of each public
     * method.
     */

    private void emptyQueue() {
        if (queue == null) {
            return;
        }
        int startSize = cacheMap.size();
        while (true) {
            @SuppressWarnings("unchecked")
            CacheEntry<K, V> entry = (CacheEntry<K, V>) queue.poll();
            if (entry == null) {
                break;
            }
            K key = entry.getKey();
            if (key == null) {
                // key is null, entry has already been removed
                continue;
            }

            CacheEntry<K, V> currentEntry = cacheMap.remove(key);
            // check if the entry in the map corresponds to the expired
            // entry. If not, re-add the entry
            if ((currentEntry != null) && (entry != currentEntry)) {
                cacheMap.put(key, currentEntry);
                continue;
            }

            if (currentEntry instanceof QueueCacheEntry<K,V> qe) {
                qe.clear();
            }
        }

        if (DEBUG) {
            int endSize = cacheMap.size();
            if (startSize != endSize) {
                System.out.println("*** Expunged " + (startSize - endSize)
                        + " entries, " + endSize + " entries left");
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
        int cnt = 0;
        long time = System.currentTimeMillis();
        if (nextExpirationTime > time) {
            return;
        }
        nextExpirationTime = Long.MAX_VALUE;
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
    }

    public synchronized int size() {
        expungeExpiredEntries();
        return cacheMap.size();
    }

    public synchronized void clear() {
        if (queue != null) {
            // if this is a SoftReference cache, first invalidate() all
            // entries so that GC does not have to enqueue them
            for (CacheEntry<K,V> entry : cacheMap.values()) {
                entry.invalidate();
            }
            while (queue.poll() != null) {
                // empty
            }
        }
        cacheMap.clear();
    }

    public void put(K key, V value) {
        put(key, value, false);
    }

    /**
     * This puts an entry into the cacheMap.
     *
     * If useQueue is true, V will be added using a QueueCacheEntry which
     * is added to cacheMap.  If false, V is added to the cacheMap directly.
     *
     * This method is synchronized to avoid multiple QueueCacheEntry
     * overwriting the same key.
     *
     * @param key key to the cacheMap
     * @param value value to be stored
     * @param canQueue can the value be put into a QueueCacheEntry
     */
    public synchronized void put(K key, V value, boolean canQueue) {
        emptyQueue();
        long expirationTime =
            (lifetime == 0) ? 0 : System.currentTimeMillis() + lifetime;
        if (expirationTime < nextExpirationTime) {
            nextExpirationTime = expirationTime;
        }

        CacheEntry<K,V> newEntry = newEntry(key, value, expirationTime, queue);
        if (cacheType != CacheType.CACHE) {
            cacheMap.put(key, newEntry);
        } else {
            CacheEntry<K, V> entry = cacheMap.get(key);
            switch (entry) {
                case QueueCacheEntry<K, V> qe -> qe.putValue(newEntry);
                case null,
                    default -> {
                    if (canQueue) {
                        var q = new QueueCacheEntry<>(key, value,
                            expirationTime, queue);
                        q.putValue(newEntry);
                        cacheMap.put(key, q);
                    } else {
                        cacheMap.put(key, newEntry);
                    }
                }
            }

            if (DEBUG) {
                System.out.println("Cache entry added: key=" +
                    key.toString() + ", class=" +
                    (entry != null ? entry.getClass().getName() : null));
            }
        }

        if (maxSize > 0 && cacheMap.size() > maxSize) {
            expungeExpiredEntries();
            if (cacheMap.size() > maxSize) { // still too large?
                Iterator<CacheEntry<K,V>> t = cacheMap.values().iterator();
                CacheEntry<K,V> lruEntry = t.next();
                if (DEBUG) {
                    System.out.println("** Overflow removal "
                        + lruEntry.getKey() + " | " + lruEntry.getValue());
                }
                t.remove();
                lruEntry.invalidate();
            }
        }
    }

    public V get(Object key) {
        emptyQueue();
        CacheEntry<K,V> entry = cacheMap.get(key);
        if (entry == null) {
            return null;
        }
        if (lifetime > 0 && !entry.isValid(System.currentTimeMillis())) {
            if (DEBUG) {
                System.out.println("Ignoring expired entry");
            }
            cacheMap.remove(key);
            return null;
        }

        // If the value is a queue, return a queue entry.
        if (entry instanceof QueueCacheEntry<K,V> qe) {
            V result = qe.getValue(lifetime);
            if (qe.isEmpty()) {
                cacheMap.remove(key);
            }
            return result;
        }

        return entry.getValue();
    }

    public void remove(Object key) {
        emptyQueue();
        CacheEntry<K,V> entry = cacheMap.remove(key);
        if (entry != null) {
            entry.invalidate();
        }
    }

    public V pull(Object key) {
        emptyQueue();
        CacheEntry<K,V> entry = cacheMap.remove(key);
        if (entry == null) {
            return null;
        }

        long time = (lifetime == 0) ? 0 : System.currentTimeMillis();
        if (entry.isValid(time)) {
            V value = entry.getValue();
            entry.invalidate();
            return value;
        } else {
            if (DEBUG) {
                System.out.println("Ignoring expired entry");
            }
            return null;
        }
    }

    public synchronized void setCapacity(int size) {
        expungeExpiredEntries();
        if (size > 0 && cacheMap.size() > size) {
            Iterator<CacheEntry<K,V>> t = cacheMap.values().iterator();
            for (int i = cacheMap.size() - size; i > 0; i--) {
                CacheEntry<K,V> lruEntry = t.next();
                if (DEBUG) {
                    System.out.println("** capacity reset removal "
                        + lruEntry.getKey() + " | " + lruEntry.getValue());
                }
                t.remove();
                lruEntry.invalidate();
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

    private interface CacheEntry<K,V> {

        boolean isValid(long currentTime);

        void invalidate();

        K getKey();

        V getValue();

        long getExpirationTime();
    }

    private static class HardCacheEntry<K,V> implements CacheEntry<K,V> {

        private K key;
        private V value;
        private long expirationTime;

        HardCacheEntry(K key, V value, long expirationTime) {
            this.key = key;
            this.value = value;
            this.expirationTime = expirationTime;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public long getExpirationTime() {
            return expirationTime;
        }

        public boolean isValid(long currentTime) {
            boolean valid = (currentTime <= expirationTime);
            if (!valid) {
                invalidate();
            }
            return valid;
        }

        public void invalidate() {
            key = null;
            value = null;
            expirationTime = -1;
        }
    }

    private static class SoftCacheEntry<K,V> extends SoftReference<V>
        implements CacheEntry<K,V> {

        private K key;
        private long expirationTime;

        SoftCacheEntry(K key, V value, long expirationTime,
                ReferenceQueue<V> queue) {
            super(value, queue);
            this.key = key;
            this.expirationTime = expirationTime;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return get();
        }

        public long getExpirationTime() {
            return expirationTime;
        }

        public boolean isValid(long currentTime) {
            boolean valid = (currentTime <= expirationTime) && (get() != null);
            if (!valid) {
                invalidate();
            }
            return valid;
        }

        public void invalidate() {
            clear();
            key = null;
            expirationTime = -1;
        }
    }

    private static class QueueCacheEntry<K,V> extends SoftReference<V>
        implements CacheEntry<K,V> {

        // Limit the number of queue entries.
        private static final int MAXQUEUESIZE = 10;

        final boolean DEBUG = false;
        private K key;
        private long expirationTime;
        final Queue<CacheEntry<K,V>> queue = new ConcurrentLinkedQueue<>();

        QueueCacheEntry(K key, V value, long expirationTime,
            ReferenceQueue<V> queue) {
            super(value, queue);
            this.key = key;
            this.expirationTime = expirationTime;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return getValue(0);
        }

        public V getValue(long lifetime) {
            long time = (lifetime == 0) ? 0 : System.currentTimeMillis();

            do {
                var entry = queue.poll();
                if (entry == null) {
                    return null;
                }
                if (entry.isValid(time)) {
                    return entry.getValue();
                }
                entry.invalidate();
            } while (!queue.isEmpty());

            return null;
        }

        public long getExpirationTime() {
            return expirationTime;
        }

        public void setExpirationTime(long time) {
            expirationTime = time;
        }

        public boolean putValue(CacheEntry<K,V> entry) {
            if (DEBUG) {
                System.out.println("Added to queue (size=" + queue.size() +
                    "): " + entry.getKey().toString() + ",  " + entry);
            }
            // Update the cache entry's expiration time to the latest entry.
            // the get() will remove expired tickets
            expirationTime = entry.getExpirationTime();
            // Limit the number of queue entries, removing the oldest.  As this
            // is a one for one entry swap, locking isn't necessary and plus or
            // minus a few entries is not critical.
            if (queue.size() > MAXQUEUESIZE) {
                queue.remove();
            }
            return queue.add(entry);
        }

        public boolean isValid(long currentTime) {
            boolean valid = (currentTime <= expirationTime) && (get() != null);
            if (!valid) {
                invalidate();
            }
            return valid;
        }

        public boolean isValid() {
            return isValid(System.currentTimeMillis());
        }

        public void invalidate() {
            clear();
            key = null;
            expirationTime = -1;
        }

        public void clear() {
            queue.forEach(CacheEntry::invalidate);
            queue.clear();
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public Queue<CacheEntry<K,V>> getQueue() {
            return queue;
        }

    }
}