/*
 * Copyright (c) 1996, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.http;

import java.io.IOException;
import java.io.NotSerializableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.URL;

/**
 * A class that implements a cache of idle Http connections for keep-alive
 *
 * @author Stephen R. Pietrowicz (NCSA)
 * @author Dave Brown
 */
public class KeepAliveCache
    extends HashMap<KeepAliveKey, ClientVector>
    implements Runnable {
    private static final long serialVersionUID = -2937172892064557949L;

    /* maximum # keep-alive connections to maintain at once
     * This should be 2 by the HTTP spec, but because we don't support pipe-lining
     * a larger value is more appropriate. So we now set a default of 5, and the value
     * refers to the number of idle connections per destination (in the cache) only.
     * It can be reset by setting system property "http.maxConnections".
     */
    static final int MAX_CONNECTIONS = 5;
    static int result = -1;
    static int getMaxConnections() {
        if (result == -1) {
            result = java.security.AccessController.doPrivileged(
                new sun.security.action.GetIntegerAction("http.maxConnections",
                                                         MAX_CONNECTIONS))
                .intValue();
            if (result <= 0)
                result = MAX_CONNECTIONS;
        }
            return result;
    }

    static final int LIFETIME = 5000;

    private Thread keepAliveTimer = null;

    /**
     * Constructor
     */
    public KeepAliveCache() {}

    /**
     * Register this URL and HttpClient (that supports keep-alive) with the cache
     * @param url  The URL contains info about the host and port
     * @param http The HttpClient to be cached
     */
    public synchronized void put(final URL url, Object obj, HttpClient http) {
        boolean startThread = (keepAliveTimer == null);
        if (!startThread) {
            if (!keepAliveTimer.isAlive()) {
                startThread = true;
            }
        }
        if (startThread) {
            clear();
            /* Unfortunately, we can't always believe the keep-alive timeout we got
             * back from the server.  If I'm connected through a Netscape proxy
             * to a server that sent me a keep-alive
             * time of 15 sec, the proxy unilaterally terminates my connection
             * The robustness to get around this is in HttpClient.parseHTTP()
             */
            final KeepAliveCache cache = this;
            java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<Void>() {
                public Void run() {
                   // We want to create the Keep-Alive-Timer in the
                    // system threadgroup
                    ThreadGroup grp = Thread.currentThread().getThreadGroup();
                    ThreadGroup parent = null;
                    while ((parent = grp.getParent()) != null) {
                        grp = parent;
                    }

                    keepAliveTimer = new Thread(grp, cache, "Keep-Alive-Timer");
                    keepAliveTimer.setDaemon(true);
                    keepAliveTimer.setPriority(Thread.MAX_PRIORITY - 2);
                    // Set the context class loader to null in order to avoid
                    // keeping a strong reference to an application classloader.
                    keepAliveTimer.setContextClassLoader(null);
                    keepAliveTimer.start();
                    return null;
                }
            });
        }

        KeepAliveKey key = new KeepAliveKey(url, obj);
        ClientVector v = super.get(key);

        if (v == null) {
            int keepAliveTimeout = http.getKeepAliveTimeout();
            v = new ClientVector(keepAliveTimeout > 0?
                                 keepAliveTimeout*1000 : LIFETIME);
            v.put(http);
            super.put(key, v);
        } else {
            v.put(http);
        }
    }

    /* remove an obsolete HttpClient from its VectorCache */
    public synchronized void remove (HttpClient h, Object obj) {
        KeepAliveKey key = new KeepAliveKey(h.url, obj);
        ClientVector v = super.get(key);
        if (v != null) {
            v.remove(h);
            if (v.empty()) {
                removeVector(key);
            }
        }
    }

    /* called by a clientVector thread when all its connections have timed out
     * and that vector of connections should be removed.
     */
    synchronized void removeVector(KeepAliveKey k) {
        super.remove(k);
    }

    /**
     * Check to see if this URL has a cached HttpClient
     */
    public synchronized HttpClient get(URL url, Object obj) {

        KeepAliveKey key = new KeepAliveKey(url, obj);
        ClientVector v = super.get(key);
        if (v == null) { // nothing in cache yet
            return null;
        }
        return v.get();
    }

    /* Sleeps for an alloted timeout, then checks for timed out connections.
     * Errs on the side of caution (leave connections idle for a relatively
     * short time).
     */
    @Override
    public void run() {
        do {
            try {
                Thread.sleep(LIFETIME);
            } catch (InterruptedException e) {}
            synchronized (this) {
                /* Remove all unused HttpClients.  Starting from the
                 * bottom of the stack (the least-recently used first).
                 * REMIND: It'd be nice to not remove *all* connections
                 * that aren't presently in use.  One could have been added
                 * a second ago that's still perfectly valid, and we're
                 * needlessly axing it.  But it's not clear how to do this
                 * cleanly, and doing it right may be more trouble than it's
                 * worth.
                 */

                long currentTime = System.currentTimeMillis();

                ArrayList<KeepAliveKey> keysToRemove
                    = new ArrayList<KeepAliveKey>();

                for (KeepAliveKey key : keySet()) {
                    ClientVector v = get(key);
                    synchronized (v) {
                        int i;

                        for (i = 0; i < v.size(); i++) {
                            KeepAliveEntry e = v.elementAt(i);
                            if ((currentTime - e.idleStartTime) > v.nap) {
                                HttpClient h = e.hc;
                                h.closeServer();
                            } else {
                                break;
                            }
                        }
                        v.subList(0, i).clear();

                        if (v.size() == 0) {
                            keysToRemove.add(key);
                        }
                    }
                }

                for (KeepAliveKey key : keysToRemove) {
                    removeVector(key);
                }
            }
        } while (size() > 0);

        return;
    }

    /*
     * Do not serialize this class!
     */
    private void writeObject(java.io.ObjectOutputStream stream)
    throws IOException {
        throw new NotSerializableException();
    }

    private void readObject(java.io.ObjectInputStream stream)
    throws IOException, ClassNotFoundException {
        throw new NotSerializableException();
    }
}

/* FILO order for recycling HttpClients, should run in a thread
 * to time them out.  If > maxConns are in use, block.
 */


class ClientVector extends java.util.Stack<KeepAliveEntry> {
    private static final long serialVersionUID = -8680532108106489459L;

    // sleep time in milliseconds, before cache clear
    int nap;



    ClientVector (int nap) {
        this.nap = nap;
    }

    synchronized HttpClient get() {
        if (empty()) {
            return null;
        } else {
            // Loop until we find a connection that has not timed out
            HttpClient hc = null;
            long currentTime = System.currentTimeMillis();
            do {
                KeepAliveEntry e = pop();
                if ((currentTime - e.idleStartTime) > nap) {
                    e.hc.closeServer();
                } else {
                    hc = e.hc;
                }
            } while ((hc== null) && (!empty()));
            return hc;
        }
    }

    /* return a still valid, unused HttpClient */
    synchronized void put(HttpClient h) {
        if (size() >= KeepAliveCache.getMaxConnections()) {
            h.closeServer(); // otherwise the connection remains in limbo
        } else {
            push(new KeepAliveEntry(h, System.currentTimeMillis()));
        }
    }

    /*
     * Do not serialize this class!
     */
    private void writeObject(java.io.ObjectOutputStream stream)
    throws IOException {
        throw new NotSerializableException();
    }

    private void readObject(java.io.ObjectInputStream stream)
    throws IOException, ClassNotFoundException {
        throw new NotSerializableException();
    }
}


class KeepAliveKey {
    private String      protocol = null;
    private String      host = null;
    private int         port = 0;
    private Object      obj = null; // additional key, such as socketfactory

    /**
     * Constructor
     *
     * @param url the URL containing the protocol, host and port information
     */
    public KeepAliveKey(URL url, Object obj) {
        this.protocol = url.getProtocol();
        this.host = url.getHost();
        this.port = url.getPort();
        this.obj = obj;
    }

    /**
     * Determine whether or not two objects of this type are equal
     */
    @Override
    public boolean equals(Object obj) {
        if ((obj instanceof KeepAliveKey) == false)
            return false;
        KeepAliveKey kae = (KeepAliveKey)obj;
        return host.equals(kae.host)
            && (port == kae.port)
            && protocol.equals(kae.protocol)
            && this.obj == kae.obj;
    }

    /**
     * The hashCode() for this object is the string hashCode() of
     * concatenation of the protocol, host name and port.
     */
    @Override
    public int hashCode() {
        String str = protocol+host+port;
        return this.obj == null? str.hashCode() :
            str.hashCode() + this.obj.hashCode();
    }
}

class KeepAliveEntry {
    HttpClient hc;
    long idleStartTime;

    KeepAliveEntry(HttpClient hc, long idleStartTime) {
        this.hc = hc;
        this.idleStartTime = idleStartTime;
    }
}
