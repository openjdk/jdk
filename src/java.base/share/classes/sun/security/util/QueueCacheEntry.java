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
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This cache entry is for use with the client side when multiple
 * NewSessionTickets are sent from the server.  This entry queues the
 * PSKs or stateless sessions for resumption.
 *
 * The SoftCacheEntry and HardCacheEntry only store one resumption entry. When
 * multiple client threads try to resume, only one session will resume and the
 * others are forced into a full handshake.  A queue allows multiple sessions
 * to resume when called quickly and accumulates new resumption tickets that
 * are sent.
 */

final class QueueCacheEntry<K,V> extends SoftReference<V>
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
        do {
            var entry = queue.poll();
            if (entry != null) {
                return entry.getValue();
            }
        } while (!queue.isEmpty());

        return null;
    }

    public V getValue(long lifetime) {
        long time = (lifetime == 0) ? 0 : System.currentTimeMillis();

        do {
            var entry = queue.poll();
            if (entry == null) {
                return null;
            }
            if (!entry.isValid(time)) {
                entry.invalidate();
            } else {
                return entry.getValue();
            }
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
        // Update the cache entry's expiration time to the latest entry.  One
        // should expect a ticket's expiration lifetime to be consistent across
        // all tickets.
        expirationTime = entry.getExpirationTime();
        // Limit the number of queue entries, removing the oldest.  As this is
        // a one for one entry swap, locking isn't necessary and plus or minus
        // a few entries is not critical.
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
