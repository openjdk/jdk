/*
 * Copyright (c) 1999, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import sun.security.util.Cache;


final class SSLSessionContextImpl implements SSLSessionContext {
    private final Cache<SessionId, SSLSessionImpl> sessionCache;
                                        // session cache, session id as key
    private final Cache<String, SSLSessionImpl> sessionHostPortCache;
                                        // session cache, "host:port" as key
    private int cacheLimit;             // the max cache size
    private int timeout;                // timeout in seconds

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
    @Override
    public SSLSession getSession(byte[] sessionId) {
        if (sessionId == null) {
            throw new NullPointerException("session id cannot be null");
        }

        SSLSessionImpl sess = sessionCache.get(new SessionId(sessionId));
        if (!isTimedout(sess)) {
            return sess;
        }

        return null;
    }

    /**
     * Returns an enumeration of the active SSL sessions.
     */
    @Override
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
    @Override
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
    @Override
    public int getSessionTimeout() {
        return timeout;
    }

    /**
     * Sets the size of the cache used for storing
     * <code>SSLSession</code> objects.
     */
    @Override
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
    @Override
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

        SSLSessionImpl sess = sessionHostPortCache.get(getKey(hostname, port));
        if (!isTimedout(sess)) {
            return sess;
        }

        return null;
    }

    private static String getKey(String hostname, int port) {
        return (hostname + ":" +
            String.valueOf(port)).toLowerCase(Locale.ENGLISH);
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
        SSLSessionImpl s = sessionCache.get(key);
        if (s != null) {
            sessionCache.remove(key);
            sessionHostPortCache.remove(
                    getKey(s.getPeerHost(), s.getPeerPort()));
        }
    }

    private static int getDefaultCacheLimit() {
        int defaultCacheLimit = 0;
        try {
            String s = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<String>() {
                    @Override
                    public String run() {
                        return System.getProperty(
                            "javax.net.ssl.sessionCacheSize");
                    }
                });
                defaultCacheLimit = (s != null) ? Integer.parseInt(s) : 0;
        } catch (Exception e) {
            // swallow the exception
        }

        return (defaultCacheLimit > 0) ? defaultCacheLimit : 0;
    }

    private boolean isTimedout(SSLSession sess) {
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

    private final class SessionCacheVisitor
            implements Cache.CacheVisitor<SessionId, SSLSessionImpl> {
        ArrayList<byte[]> ids = null;

        // public void visit(java.util.Map<K,V> map) {}
        @Override
        public void visit(java.util.Map<SessionId, SSLSessionImpl> map) {
            ids = new ArrayList<>(map.size());

            for (SessionId key : map.keySet()) {
                SSLSessionImpl value = map.get(key);
                if (!isTimedout(value)) {
                    ids.add(key.getId());
                }
            }
        }

        Enumeration<byte[]> getSessionIds() {
            return  ids != null ? Collections.enumeration(ids) :
                                  Collections.emptyEnumeration();
        }
    }
}
