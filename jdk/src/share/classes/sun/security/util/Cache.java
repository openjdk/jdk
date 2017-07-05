/*
 * Copyright (c) 2002, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.lang.ref.*;

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
 *  . save for concurrent use by multiple threads
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
public abstract class Cache {

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
    public abstract void put(Object key, Object value);

    /**
     * Get a value from the cache.
     */
    public abstract Object get(Object key);

    /**
     * Remove an entry from the cache.
     */
    public abstract void remove(Object key);

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
    public abstract void accept(CacheVisitor visitor);

    /**
     * Return a new memory cache with the specified maximum size, unlimited
     * lifetime for entries, with the values held by SoftReferences.
     */
    public static Cache newSoftMemoryCache(int size) {
        return new MemoryCache(true, size);
    }

    /**
     * Return a new memory cache with the specified maximum size, the
     * specified maximum lifetime (in seconds), with the values held
     * by SoftReferences.
     */
    public static Cache newSoftMemoryCache(int size, int timeout) {
        return new MemoryCache(true, size, timeout);
    }

    /**
     * Return a new memory cache with the specified maximum size, unlimited
     * lifetime for entries, with the values held by standard references.
     */
    public static Cache newHardMemoryCache(int size) {
        return new MemoryCache(false, size);
    }

    /**
     * Return a dummy cache that does nothing.
     */
    public static Cache newNullCache() {
        return NullCache.INSTANCE;
    }

    /**
     * Return a new memory cache with the specified maximum size, the
     * specified maximum lifetime (in seconds), with the values held
     * by standard references.
     */
    public static Cache newHardMemoryCache(int size, int timeout) {
        return new MemoryCache(false, size, timeout);
    }

    /**
     * Utility class that wraps a byte array and implements the equals()
     * and hashCode() contract in a way suitable for Maps and caches.
     */
    public static class EqualByteArray {

        private final byte[] b;
        private volatile int hash;

        public EqualByteArray(byte[] b) {
            this.b = b;
        }

        public int hashCode() {
            int h = hash;
            if (h == 0) {
                h = b.length + 1;
                for (int i = 0; i < b.length; i++) {
                    h += (b[i] & 0xff) * 37;
                }
                hash = h;
            }
            return h;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof EqualByteArray == false) {
                return false;
            }
            EqualByteArray other = (EqualByteArray)obj;
            return Arrays.equals(this.b, other.b);
        }
    }

    public interface CacheVisitor {
        public void visit(Map<Object, Object> map);
    }

}

class NullCache extends Cache {

    final static Cache INSTANCE = new NullCache();

    private NullCache() {
        // empty
    }

    public int size() {
        return 0;
    }

    public void clear() {
        // empty
    }

    public void put(Object key, Object value) {
        // empty
    }

    public Object get(Object key) {
        return null;
    }

    public void remove(Object key) {
        // empty
    }

    public void setCapacity(int size) {
        // empty
    }

    public void setTimeout(int timeout) {
        // empty
    }

    public void accept(CacheVisitor visitor) {
        // empty
    }

}

class MemoryCache extends Cache {

    private final static float LOAD_FACTOR = 0.75f;

    // XXXX
    private final static boolean DEBUG = false;

    private final Map<Object, CacheEntry> cacheMap;
    private int maxSize;
    private long lifetime;
    private final ReferenceQueue queue;

    public MemoryCache(boolean soft, int maxSize) {
        this(soft, maxSize, 0);
    }

    public MemoryCache(boolean soft, int maxSize, int lifetime) {
        this.maxSize = maxSize;
        this.lifetime = lifetime * 1000;
        this.queue = soft ? new ReferenceQueue() : null;
        int buckets = (int)(maxSize / LOAD_FACTOR) + 1;
        cacheMap = new LinkedHashMap<Object, CacheEntry>(buckets,
                                                        LOAD_FACTOR, true);
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
            CacheEntry entry = (CacheEntry)queue.poll();
            if (entry == null) {
                break;
            }
            Object key = entry.getKey();
            if (key == null) {
                // key is null, entry has already been removed
                continue;
            }
            CacheEntry currentEntry = cacheMap.remove(key);
            // check if the entry in the map corresponds to the expired
            // entry. If not, readd the entry
            if ((currentEntry != null) && (entry != currentEntry)) {
                cacheMap.put(key, currentEntry);
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
        for (Iterator<CacheEntry> t = cacheMap.values().iterator();
                t.hasNext(); ) {
            CacheEntry entry = t.next();
            if (entry.isValid(time) == false) {
                t.remove();
                cnt++;
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
            for (CacheEntry entry : cacheMap.values()) {
                entry.invalidate();
            }
            while (queue.poll() != null) {
                // empty
            }
        }
        cacheMap.clear();
    }

    public synchronized void put(Object key, Object value) {
        emptyQueue();
        long expirationTime = (lifetime == 0) ? 0 :
                                        System.currentTimeMillis() + lifetime;
        CacheEntry newEntry = newEntry(key, value, expirationTime, queue);
        CacheEntry oldEntry = cacheMap.put(key, newEntry);
        if (oldEntry != null) {
            oldEntry.invalidate();
            return;
        }
        if (maxSize > 0 && cacheMap.size() > maxSize) {
            expungeExpiredEntries();
            if (cacheMap.size() > maxSize) { // still too large?
                Iterator<CacheEntry> t = cacheMap.values().iterator();
                CacheEntry lruEntry = t.next();
                if (DEBUG) {
                    System.out.println("** Overflow removal "
                        + lruEntry.getKey() + " | " + lruEntry.getValue());
                }
                t.remove();
                lruEntry.invalidate();
            }
        }
    }

    public synchronized Object get(Object key) {
        emptyQueue();
        CacheEntry entry = cacheMap.get(key);
        if (entry == null) {
            return null;
        }
        long time = (lifetime == 0) ? 0 : System.currentTimeMillis();
        if (entry.isValid(time) == false) {
            if (DEBUG) {
                System.out.println("Ignoring expired entry");
            }
            cacheMap.remove(key);
            return null;
        }
        return entry.getValue();
    }

    public synchronized void remove(Object key) {
        emptyQueue();
        CacheEntry entry = cacheMap.remove(key);
        if (entry != null) {
            entry.invalidate();
        }
    }

    public synchronized void setCapacity(int size) {
        expungeExpiredEntries();
        if (size > 0 && cacheMap.size() > size) {
            Iterator<CacheEntry> t = cacheMap.values().iterator();
            for (int i = cacheMap.size() - size; i > 0; i--) {
                CacheEntry lruEntry = t.next();
                if (DEBUG) {
                    System.out.println("** capacity reset removal "
                        + lruEntry.getKey() + " | " + lruEntry.getValue());
                }
                t.remove();
                lruEntry.invalidate();
            }
        }

        maxSize = size > 0 ? size : 0;

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
    public synchronized void accept(CacheVisitor visitor) {
        expungeExpiredEntries();
        Map<Object, Object> cached = getCachedEntries();

        visitor.visit(cached);
    }

    private Map<Object, Object> getCachedEntries() {
        Map<Object,Object> kvmap = new HashMap<Object,Object>(cacheMap.size());

        for (CacheEntry entry : cacheMap.values()) {
            kvmap.put(entry.getKey(), entry.getValue());
        }

        return kvmap;
    }

    protected CacheEntry newEntry(Object key, Object value,
            long expirationTime, ReferenceQueue queue) {
        if (queue != null) {
            return new SoftCacheEntry(key, value, expirationTime, queue);
        } else {
            return new HardCacheEntry(key, value, expirationTime);
        }
    }

    private static interface CacheEntry {

        boolean isValid(long currentTime);

        void invalidate();

        Object getKey();

        Object getValue();

    }

    private static class HardCacheEntry implements CacheEntry {

        private Object key, value;
        private long expirationTime;

        HardCacheEntry(Object key, Object value, long expirationTime) {
            this.key = key;
            this.value = value;
            this.expirationTime = expirationTime;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public boolean isValid(long currentTime) {
            boolean valid = (currentTime <= expirationTime);
            if (valid == false) {
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

    private static class SoftCacheEntry
            extends SoftReference implements CacheEntry {

        private Object key;
        private long expirationTime;

        SoftCacheEntry(Object key, Object value, long expirationTime,
                ReferenceQueue queue) {
            super(value, queue);
            this.key = key;
            this.expirationTime = expirationTime;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return get();
        }

        public boolean isValid(long currentTime) {
            boolean valid = (currentTime <= expirationTime) && (get() != null);
            if (valid == false) {
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

}
