/*
 * Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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


package sun.security.ssl;

import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.Vector;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import sun.security.util.Cache;


final class SSLSessionContextImpl implements SSLSessionContext {
    private Cache sessionCache;         // session cache, session id as key
    private Cache sessionHostPortCache; // session cache, "host:port" as key
    private int cacheLimit;             // the max cache size
    private int timeout;                // timeout in seconds

    private static final Debug debug = Debug.getInstance("ssl");

    // package private
    SSLSessionContextImpl() {
        cacheLimit = getDefaultCacheLimit();    // default cache size
        timeout = 86400;                        // default, 24 hours

        // use soft reference
        sessionCache = Cache.newSoftMemoryCache(cacheLimit, timeout);
        sessionHostPortCache = Cache.newSoftMemoryCache(cacheLimit, timeout);
    }

    /**
     * Returns the <code>SSLSession</code> bound to the specified session id.
     */
    public SSLSession getSession(byte[] sessionId) {
        if (sessionId == null) {
            throw new NullPointerException("session id cannot be null");
        }

        SSLSessionImpl sess =
                (SSLSessionImpl)sessionCache.get(new SessionId(sessionId));
        if (!isTimedout(sess)) {
            return sess;
        }

        return null;
    }

    /**
     * Returns an enumeration of the active SSL sessions.
     */
    public Enumeration<byte[]> getIds() {
        SessionCacheVisitor scVisitor = new SessionCacheVisitor();
        sessionCache.accept(scVisitor);

        return scVisitor.getSessionIds();
    }

    /**
     * Sets the timeout limit for cached <code>SSLSession</code> objects
     *
     * Note that after reset the timeout, the cached session before
     * should be timed within the shorter one of the old timeout and the
     * new timeout.
     */
    public void setSessionTimeout(int seconds)
                 throws IllegalArgumentException {
        if (seconds < 0) {
            throw new IllegalArgumentException();
        }

        if (timeout != seconds) {
            sessionCache.setTimeout(seconds);
            sessionHostPortCache.setTimeout(seconds);
            timeout = seconds;
        }
    }

    /**
     * Gets the timeout limit for cached <code>SSLSession</code> objects
     */
    public int getSessionTimeout() {
        return timeout;
    }

    /**
     * Sets the size of the cache used for storing
     * <code>SSLSession</code> objects.
     */
    public void setSessionCacheSize(int size)
                 throws IllegalArgumentException {
        if (size < 0)
            throw new IllegalArgumentException();

        if (cacheLimit != size) {
            sessionCache.setCapacity(size);
            sessionHostPortCache.setCapacity(size);
            cacheLimit = size;
        }
    }

    /**
     * Gets the size of the cache used for storing
     * <code>SSLSession</code> objects.
     */
    public int getSessionCacheSize() {
        return cacheLimit;
    }


    // package-private method, used ONLY by ServerHandshaker
    SSLSessionImpl get(byte[] id) {
        return (SSLSessionImpl)getSession(id);
    }

    // package-private method, used ONLY by ClientHandshaker
    SSLSessionImpl get(String hostname, int port) {
        /*
         * If no session caching info is available, we won't
         * get one, so exit before doing a lookup.
         */
        if (hostname == null && port == -1) {
            return null;
        }

        SSLSessionImpl sess =
            (SSLSessionImpl)sessionHostPortCache.get(getKey(hostname, port));
        if (!isTimedout(sess)) {
            return sess;
        }

        return null;
    }

    private String getKey(String hostname, int port) {
        return (hostname + ":" + String.valueOf(port)).toLowerCase();
    }

    // cache a SSLSession
    //
    // In SunJSSE implementation, a session is created while getting a
    // client hello or a server hello message, and cached while the
    // handshaking finished.
    // Here we time the session from the time it cached instead of the
    // time it created, which is a little longer than the expected. So
    // please do check isTimedout() while getting entry from the cache.
    void put(SSLSessionImpl s) {
        sessionCache.put(s.getSessionId(), s);

        // If no hostname/port info is available, don't add this one.
        if ((s.getPeerHost() != null) && (s.getPeerPort() != -1)) {
            sessionHostPortCache.put(
                getKey(s.getPeerHost(), s.getPeerPort()), s);
        }

        s.setContext(this);
    }

    // package-private method, remove a cached SSLSession
    void remove(SessionId key) {
        SSLSessionImpl s = (SSLSessionImpl)sessionCache.get(key);
        if (s != null) {
            sessionCache.remove(key);
            sessionHostPortCache.remove(
                        getKey(s.getPeerHost(), s.getPeerPort()));
        }
    }

    private int getDefaultCacheLimit() {
        int cacheLimit = 0;
        try {
        String s = java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<String>() {
                public String run() {
                    return System.getProperty(
                        "javax.net.ssl.sessionCacheSize");
                }
            });
            cacheLimit = (s != null) ? Integer.valueOf(s).intValue() : 0;
        } catch (Exception e) {
        }

        return (cacheLimit > 0) ? cacheLimit : 0;
    }

    boolean isTimedout(SSLSession sess) {
        if (timeout == 0) {
            return false;
        }

        if ((sess != null) && ((sess.getCreationTime() + timeout * 1000L)
                                        <= (System.currentTimeMillis()))) {
            sess.invalidate();
            return true;
        }

        return false;
    }

    final class SessionCacheVisitor
            implements sun.security.util.Cache.CacheVisitor {
        Vector<byte[]> ids = null;

        // public void visit(java.util.Map<Object, Object> map) {}
        public void visit(java.util.Map<Object, Object> map) {
            ids = new Vector<byte[]>(map.size());

            for (Object key : map.keySet()) {
                SSLSessionImpl value = (SSLSessionImpl)map.get(key);
                if (!isTimedout(value)) {
                    ids.addElement(((SessionId)key).getId());
                }
            }
        }

        public Enumeration<byte[]> getSessionIds() {
            return  ids != null ? ids.elements() :
                                  new Vector<byte[]>().elements();
        }
    }

}
