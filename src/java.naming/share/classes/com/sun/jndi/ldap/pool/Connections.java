/*
 * Copyright (c) 2002, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.ldap.pool;

import java.util.ArrayList; // JDK 1.2
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import javax.naming.NamingException;
import javax.naming.InterruptedNamingException;
import javax.naming.CommunicationException;

/**
 * Represents a list of PooledConnections (actually, ConnectionDescs) with the
 * same pool id.
 * The list starts out with an initial number of connections.
 * Additional PooledConnections are created lazily upon demand.
 * The list has a maximum size. When the number of connections
 * reaches the maximum size, a request for a PooledConnection blocks until
 * a connection is returned to the list. A maximum size of zero means that
 * there is no maximum: connection creation will be attempted when
 * no idle connection is available.
 *
 * The list may also have a preferred size. If the current list size
 * is less than the preferred size, a request for a connection will result in
 * a PooledConnection being created (even if an idle connection is available).
 * If the current list size is greater than the preferred size,
 * a connection being returned to the list will be closed and removed from
 * the list. A preferred size of zero means that there is no preferred size:
 * connections are created only when no idle connection is available and
 * a connection being returned to the list is not closed. Regardless of the
 * preferred size, connection creation always observes the maximum size:
 * a connection won't be created if the list size is at or exceeds the
 * maximum size.
 *
 * @author Rosanna Lee
 */

// Package private: accessed only by Pool
final class Connections implements PoolCallback {
    private static final boolean debug = Pool.debug;
    private static final boolean trace =
        com.sun.jndi.ldap.LdapPoolManager.trace;
    private static final int DEFAULT_SIZE = 10;

    final private int initSize;
    private final int maxSize;
    private final int prefSize;
    private final List<ConnectionDesc> conns;
    final private PooledConnectionFactory factory;

    private boolean closed = false;   // Closed for business
    private Reference<Object> ref; // maintains reference to id to prevent premature GC

    private boolean initialized = false;
    private final ReentrantLock lock;
    private final Condition connectionsAvailable;

    /**
     * @param id the identity (connection request) of the connections in the list
     * @param initSize the number of connections to create initially
     * @param prefSize the preferred size of the pool. The pool will try
     * to maintain a pool of this size by creating and closing connections
     * as needed.
     * @param maxSize the maximum size of the pool. The pool will not exceed
     * this size. If the pool is at this size, a request for a connection
     * will block until an idle connection is released to the pool or
     * when one is removed.
     * @param factory The factory responsible for creating a connection
     */
    Connections(Object id, int initSize, int prefSize, int maxSize, PooledConnectionFactory factory,
            ReentrantLock lock) throws NamingException {
        this.maxSize = maxSize;
        this.lock = lock;
        this.connectionsAvailable = lock.newCondition();
        this.factory = factory;

        if (maxSize > 0) {
            // prefSize and initSize cannot exceed specified maxSize
            this.prefSize = Math.min(prefSize, maxSize);
            this.initSize = Math.min(initSize, maxSize);
        } else {
            this.prefSize = prefSize;
            this.initSize = initSize;
        }
        this.conns = new ArrayList<>(maxSize > 0 ? maxSize : DEFAULT_SIZE);
        this.initialized = initSize <= 0;

        // Maintain soft ref to id so that this Connections' entry in
        // Pool doesn't get GC'ed prematurely
        this.ref = new SoftReference<>(id);

        d("init size=", initSize);
        d("max size=", maxSize);
        d("preferred size=", prefSize);
    }

    void waitForAvailableConnection() throws InterruptedNamingException {
        try {
            d("get(): waiting");
            connectionsAvailable.await();
        } catch (InterruptedException e) {
            throw new InterruptedNamingException(
                    "Interrupted while waiting for a connection");
        }
    }

    void waitForAvailableConnection(long waitTime) throws InterruptedNamingException {
        try {
            d("get(): waiting");
            connectionsAvailable.await(waitTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new InterruptedNamingException(
                    "Interrupted while waiting for a connection");
        }
    }

    /**
     * Retrieves an idle connection from this list if one is available.
     */
    PooledConnection getAvailableConnection(long timeout) throws NamingException {
        if (!initialized) {
            PooledConnection conn = createConnection(factory, timeout);
            if (conns.size() >= initSize) {
                this.initialized = true;
            }
            return conn;
        }
        int size = conns.size(); // Current number of idle/nonidle conns

        if (prefSize <= 0 || size >= prefSize) {
            // If no prefSize specified, or list size already meets or
            // exceeds prefSize, then first look for an idle connection
            ConnectionDesc entry;
            for (ConnectionDesc connectionDesc : conns) {
                PooledConnection conn;
                entry = connectionDesc;
                if ((conn = entry.tryUse()) != null) {
                    d("get(): use ", conn);
                    td("Use ", conn);
                    return conn;
                }
            }
        }
        return null;
    }

    /*
     * Creates a new Connection if maxSize hasn't been reached.
     * If maxSize has been reached, return null.
     * Caller must hold the ReentrantLock.
     */
    PooledConnection createConnection(PooledConnectionFactory factory, long timeout)
            throws NamingException {
        int size = conns.size(); // Current number of idle/non-idle connections
        if (maxSize == 0 || size < maxSize) {
            PooledConnection conn = factory.createPooledConnection(this, timeout);
            td("Create and use ", conn, factory);
            conns.add(new ConnectionDesc(conn, true)); // Add new conn to pool
            return conn;
        }

        return null;
    }

    /**
     * Releases connection back into list.
     * If the list size is below prefSize, the connection may be reused.
     * If the list size exceeds prefSize, then the connection is closed
     * and removed from the list.
     * <p>
     * public because implemented as part of PoolCallback.
     */
    public boolean releasePooledConnection(PooledConnection conn) {
        lock.lock();
        try {
            ConnectionDesc entry;
            int loc = conns.indexOf(entry = new ConnectionDesc(conn));

            d("release(): ", conn);

            if (loc >= 0) {
                // Found entry

                if (closed || (prefSize > 0 && conns.size() > prefSize)) {
                    // If list size exceeds prefSize, close connection

                    d("release(): closing ", conn);
                    td("Close ", conn);

                    // size must be >= 2 so don't worry about empty list
                    conns.remove(entry);
                    conn.closeConnection();
                } else {
                    d("release(): release ", conn);
                    td("Release ", conn);
                    // Get ConnectionDesc from list to get correct state info
                    entry = conns.get(loc);
                    // Return connection to list, ready for reuse
                    entry.release();
                }
                connectionsAvailable.signalAll();
                d("release(): notify");
                return true;
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

    /**
     * Removes PooledConnection from list of connections.
     * The closing of the connection is separate from this method.
     * This method is called usually when the caller encounters an error
     * when using the connection and wants it removed from the pool.
     *
     * @return true if conn removed; false if it was not in pool
     * <p>
     * public because implemented as part of PoolCallback.
     */
    public boolean removePooledConnection(PooledConnection conn) {
        lock.lock();
        try {
            if (conns.remove(new ConnectionDesc(conn))) {
                d("remove(): ", conn);

                connectionsAvailable.signalAll();

                d("remove(): notify");
                td("Remove ", conn);

                if (conns.isEmpty()) {
                    // Remove softref to make pool entry eligible for GC.
                    // Once ref has been removed, it cannot be reinstated.
                    ref = null;
                }

                return true;
            } else {
                d("remove(): not found ", conn);
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

    /**
     * Goes through all entries in list, removes and closes ones that have been
     * idle before threshold.
     *
     * @param threshold an entry idle since this time has expired.
     * @return true if no more connections in list
     */
    boolean expire(long threshold) {
        List<ConnectionDesc> clonedConns;
        lock.lock();
        try {
            clonedConns = new ArrayList<>(conns);
        } finally {
            lock.unlock();
        }
        List<ConnectionDesc> expired = new ArrayList<>();

        for (ConnectionDesc entry : clonedConns) {
            d("expire(): ", entry);
            if (entry.expire(threshold)) {
                expired.add(entry);
                td("expire(): Expired ", entry);
            }
        }

        lock.lock();
        try {
            conns.removeAll(expired);
            // Don't need to call notify() because we're
            // removing only idle connections. If there were
            // idle connections, then there should be no waiters.
            return conns.isEmpty();  // whether whole list has 'expired'
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called when this instance of Connections has been removed from Pool.
     * This means that no one can get any pooled connections from this
     * Connections any longer. Expire all idle connections as of 'now'
     * and leave indicator so that any in-use connections will be closed upon
     * their return.
     */
    synchronized void close() {
        expire(System.currentTimeMillis());     // Expire idle connections
        closed = true;   // Close in-use connections when they are returned
    }

    String getStats() {
        int idle = 0;
        int busy = 0;
        int expired = 0;
        long use = 0;
        int len;

        synchronized (this) {
            len = conns.size();

            ConnectionDesc entry;
            for (int i = 0; i < len; i++) {
                entry = conns.get(i);
                use += entry.getUseCount();
                switch (entry.getState()) {
                case ConnectionDesc.BUSY:
                    ++busy;
                    break;
                case ConnectionDesc.IDLE:
                    ++idle;
                    break;
                case ConnectionDesc.EXPIRED:
                    ++expired;
                }
            }
        }
        return "size=" + len + "; use=" + use + "; busy=" + busy
            + "; idle=" + idle + "; expired=" + expired;
    }

    boolean grabLock(long timeout) throws InterruptedNamingException {
        final long start = System.nanoTime();
        long current = start;
        long remaining = timeout;
        boolean locked = false;
        while (!locked && remaining > 0) {
            try {
                locked = lock.tryLock(remaining, TimeUnit.MILLISECONDS);
                remaining -= TimeUnit.NANOSECONDS.toMillis(current - start);
            } catch (InterruptedException ignore) {
                throw new InterruptedNamingException(
                        "Interrupted while waiting for the connection pool lock");
            }
            current = System.nanoTime();
            remaining -= TimeUnit.NANOSECONDS.toMillis(current - start);
        }
        return locked;
    }

    void unlock() {
        lock.unlock();
    }

    private void d(String msg, Object o1) {
        if (debug) {
            d(msg + o1);
        }
    }

    private void d(String msg, int i) {
        if (debug) {
            d(msg + i);
        }
    }

    private void d(String msg) {
        if (debug) {
            System.err.println(this + "." + msg + "; size: " + conns.size());
        }
    }

    private void td(String msg, Object o1, Object o2) {
        if (trace) { // redo test to avoid object creation
            td(msg + o1 + "[" + o2 + "]");
        }
    }
    private void td(String msg, Object o1) {
        if (trace) { // redo test to avoid object creation
            td(msg + o1);
        }
    }
    private void td(String msg) {
        if (trace) {
            System.err.println(msg);
        }
    }
}
