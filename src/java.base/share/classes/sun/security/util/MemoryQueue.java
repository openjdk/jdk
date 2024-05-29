/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

    private static final boolean DEBUG = true;

    private final ConcurrentHashMap<K, CacheEntry<K,V>> cacheMap;
    private int maxSize;
    private long lifetime;
    private long nextExpirationTime = Long.MAX_VALUE;

    private static ReentrantReadWriteLock lock;
    private static ReentrantLock qlock;

    private long lastExpunge = 0;

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
        cacheMap = new ConcurrentHashMap<>(1);
    }

    /**
     * Empty the reference queue and remove all corresponding entries
     * from the cache.
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
                queue.forEach(queue::remove);
            }
            try {
                cacheMap.remove(key);
                count++;
            } catch (NullPointerException e) {
                // Something else took and evaluated the entry
            }
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

        long time = System.currentTimeMillis();
        if (nextExpirationTime > time) {
            return;
        }

        nextExpirationTime = Long.MAX_VALUE;
        cacheMap.values().parallelStream().forEach(entry -> {
            if (!entry.isValid(time)) {
                try {
                    cacheMap.remove(entry.getKey());
                } catch (NullPointerException e) {
                    // Something else took and evaluated the entry
                }
            } else if (nextExpirationTime > entry.getExpirationTime()) {
                nextExpirationTime = entry.getExpirationTime();
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
        if (value == null) {
            return null;
        }

        if (lifetime > 0 && !value.isValid(System.currentTimeMillis())) {
            try {
                cacheMap.remove(key);
            } catch (NullPointerException e) {
                // Something else took and evaluated the entry
            }
            return null;
        }


        // If the value is a queue, return a queue entry.
        if (value instanceof QueueCacheEntry<K,V> qe) {
            result = (lifetime == 0 ? qe.getValue() : qe.getValue(lifetime));
            if (qe.isEmpty()) {
                try {
                    cacheMap.remove(key);
                } catch (NullPointerException e) {
                    // Something else took and evaluated the entry
                }
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
        if (cacheMap.size() <= size) {
            return;
        }

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
        Map<K, V> cached = HashMap.newHashMap(cacheMap.size());
        cacheMap.values().forEach(entry ->
            cached.put(entry.getKey(), entry.getValue()));

        visitor.visit(cached);
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
