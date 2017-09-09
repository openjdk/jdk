/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jdk.incubator.http.internal.common.Utils;

/**
 * Http 1.1 connection pool.
 */
final class ConnectionPool {

    // These counters are used to distribute ids for debugging
    // The ACTIVE_CLEANER_COUNTER will tell how many CacheCleaner
    // are active at a given time. It will increase when a new
    // CacheCleaner is started and decrease when it exits.
    static final AtomicLong ACTIVE_CLEANER_COUNTER = new AtomicLong();
    // The POOL_IDS_COUNTER increases each time a new ConnectionPool
    // is created. It may wrap and become negative but will never be
    // decremented.
    static final AtomicLong POOL_IDS_COUNTER = new AtomicLong();
    // The cleanerCounter is used to name cleaner threads within a
    // a connection pool, and increments monotically.
    // It may wrap and become negative but will never be
    // decremented.
    final AtomicLong cleanerCounter = new AtomicLong();

    static final long KEEP_ALIVE = Utils.getIntegerNetProperty(
            "jdk.httpclient.keepalive.timeout", 1200); // seconds

    // Pools of idle connections

    final HashMap<CacheKey,LinkedList<HttpConnection>> plainPool;
    final HashMap<CacheKey,LinkedList<HttpConnection>> sslPool;
    // A monotically increasing id for this connection pool.
    // It may be negative (that's OK)
    // Mostly used for debugging purposes when looking at thread dumps.
    // Global scope.
    final long poolID = POOL_IDS_COUNTER.incrementAndGet();
    final AtomicReference<CacheCleaner> cleanerRef;

    /**
     * Entries in connection pool are keyed by destination address and/or
     * proxy address:
     * case 1: plain TCP not via proxy (destination only)
     * case 2: plain TCP via proxy (proxy only)
     * case 3: SSL not via proxy (destination only)
     * case 4: SSL over tunnel (destination and proxy)
     */
    static class CacheKey {
        final InetSocketAddress proxy;
        final InetSocketAddress destination;

        CacheKey(InetSocketAddress destination, InetSocketAddress proxy) {
            this.proxy = proxy;
            this.destination = destination;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CacheKey other = (CacheKey) obj;
            if (!Objects.equals(this.proxy, other.proxy)) {
                return false;
            }
            if (!Objects.equals(this.destination, other.destination)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(proxy, destination);
        }
    }

    static class ExpiryEntry {
        final HttpConnection connection;
        final long expiry; // absolute time in seconds of expiry time
        ExpiryEntry(HttpConnection connection, long expiry) {
            this.connection = connection;
            this.expiry = expiry;
        }
    }

    final LinkedList<ExpiryEntry> expiryList;

    /**
     * There should be one of these per HttpClient.
     */
    ConnectionPool() {
        plainPool = new HashMap<>();
        sslPool = new HashMap<>();
        expiryList = new LinkedList<>();
        cleanerRef = new AtomicReference<>();
    }

    void start() {
    }

    static CacheKey cacheKey(InetSocketAddress destination,
                             InetSocketAddress proxy)
    {
        return new CacheKey(destination, proxy);
    }

    synchronized HttpConnection getConnection(boolean secure,
                                              InetSocketAddress addr,
                                              InetSocketAddress proxy) {
        CacheKey key = new CacheKey(addr, proxy);
        HttpConnection c = secure ? findConnection(key, sslPool)
                                  : findConnection(key, plainPool);
        //System.out.println ("getConnection returning: " + c);
        return c;
    }

    /**
     * Returns the connection to the pool.
     */
    synchronized void returnToPool(HttpConnection conn) {
        if (conn instanceof PlainHttpConnection) {
            putConnection(conn, plainPool);
        } else {
            putConnection(conn, sslPool);
        }
        addToExpiryList(conn);
        //System.out.println("Return to pool: " + conn);
    }

    private HttpConnection
    findConnection(CacheKey key,
                   HashMap<CacheKey,LinkedList<HttpConnection>> pool) {
        LinkedList<HttpConnection> l = pool.get(key);
        if (l == null || l.isEmpty()) {
            return null;
        } else {
            HttpConnection c = l.removeFirst();
            removeFromExpiryList(c);
            return c;
        }
    }

    /* called from cache cleaner only  */
    private void
    removeFromPool(HttpConnection c,
                   HashMap<CacheKey,LinkedList<HttpConnection>> pool) {
        //System.out.println("cacheCleaner removing: " + c);
        LinkedList<HttpConnection> l = pool.get(c.cacheKey());
        assert l != null;
        boolean wasPresent = l.remove(c);
        assert wasPresent;
    }

    private void
    putConnection(HttpConnection c,
                  HashMap<CacheKey,LinkedList<HttpConnection>> pool) {
        CacheKey key = c.cacheKey();
        LinkedList<HttpConnection> l = pool.get(key);
        if (l == null) {
            l = new LinkedList<>();
            pool.put(key, l);
        }
        l.add(c);
    }

    static String makeCleanerName(long poolId, long cleanerId) {
        return "HTTP-Cache-cleaner-" + poolId + "-" + cleanerId;
    }

    // only runs while entries exist in cache
    final static class CacheCleaner extends Thread {

        volatile boolean stopping;
        // A monotically increasing id. May wrap and become negative (that's OK)
        // Mostly used for debugging purposes when looking at thread dumps.
        // Scoped per connection pool.
        final long cleanerID;
        // A reference to the owning ConnectionPool.
        // This reference's referent may become null if the HttpClientImpl
        // that owns this pool is GC'ed.
        final WeakReference<ConnectionPool> ownerRef;

        CacheCleaner(ConnectionPool owner) {
            this(owner, owner.cleanerCounter.incrementAndGet());
        }

        CacheCleaner(ConnectionPool owner, long cleanerID) {
            super(null, null, makeCleanerName(owner.poolID, cleanerID), 0, false);
            this.cleanerID = cleanerID;
            this.ownerRef = new WeakReference<>(owner);
            setDaemon(true);
        }

        synchronized boolean stopping() {
            return stopping || ownerRef.get() == null;
        }

        synchronized void stopCleaner() {
            stopping = true;
        }

        @Override
        public void run() {
            ACTIVE_CLEANER_COUNTER.incrementAndGet();
            try {
                while (!stopping()) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {}
                    ConnectionPool owner = ownerRef.get();
                    if (owner == null) return;
                    owner.cleanCache(this);
                    owner = null;
                }
            } finally {
                ACTIVE_CLEANER_COUNTER.decrementAndGet();
            }
        }
    }

    synchronized void removeFromExpiryList(HttpConnection c) {
        if (c == null) {
            return;
        }
        ListIterator<ExpiryEntry> li = expiryList.listIterator();
        while (li.hasNext()) {
            ExpiryEntry e = li.next();
            if (e.connection.equals(c)) {
                li.remove();
                return;
            }
        }
        CacheCleaner cleaner = this.cleanerRef.get();
        if (expiryList.isEmpty() && cleaner != null) {
            this.cleanerRef.compareAndSet(cleaner, null);
            cleaner.stopCleaner();
            cleaner.interrupt();
        }
    }

    private void cleanCache(CacheCleaner cleaner) {
        long now = System.currentTimeMillis() / 1000;
        LinkedList<HttpConnection> closelist = new LinkedList<>();

        synchronized (this) {
            ListIterator<ExpiryEntry> li = expiryList.listIterator();
            while (li.hasNext()) {
                ExpiryEntry entry = li.next();
                if (entry.expiry <= now) {
                    li.remove();
                    HttpConnection c = entry.connection;
                    closelist.add(c);
                    if (c instanceof PlainHttpConnection) {
                        removeFromPool(c, plainPool);
                    } else {
                        removeFromPool(c, sslPool);
                    }
                }
            }
            if (expiryList.isEmpty() && cleaner != null) {
                this.cleanerRef.compareAndSet(cleaner, null);
                cleaner.stopCleaner();
            }
        }
        for (HttpConnection c : closelist) {
            //System.out.println ("KAC: closing " + c);
            c.close();
        }
    }

    private synchronized void addToExpiryList(HttpConnection conn) {
        long now = System.currentTimeMillis() / 1000;
        long then = now + KEEP_ALIVE;
        if (expiryList.isEmpty()) {
            CacheCleaner cleaner = new CacheCleaner(this);
            if (this.cleanerRef.compareAndSet(null, cleaner)) {
                cleaner.start();
            }
            expiryList.add(new ExpiryEntry(conn, then));
            return;
        }

        ListIterator<ExpiryEntry> li = expiryList.listIterator();
        while (li.hasNext()) {
            ExpiryEntry entry = li.next();

            if (then > entry.expiry) {
                li.previous();
                // insert here
                li.add(new ExpiryEntry(conn, then));
                return;
            }
        }
        // first element of list
        expiryList.add(new ExpiryEntry(conn, then));
    }
}
