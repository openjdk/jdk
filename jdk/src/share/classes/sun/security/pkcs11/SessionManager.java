/*
 * Copyright 2003-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.pkcs11;

import java.util.*;

import java.security.ProviderException;

import sun.security.util.Debug;

import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * Session manager. There is one session manager object per PKCS#11
 * provider. It allows code to checkout a session, release it
 * back to the pool, or force it to be closed.
 *
 * The session manager pools sessions to minimize the number of
 * C_OpenSession() and C_CloseSession() that have to be made. It
 * maintains two pools: one for "object" sessions and one for
 * "operation" sessions.
 *
 * The reason for this separation is how PKCS#11 deals with session objects.
 * It defines that when a session is closed, all objects created within
 * that session are destroyed. In other words, we may never close a session
 * while a Key created it in is still in use. We would like to keep the
 * number of such sessions low. Note that we occasionally want to explicitly
 * close a session, see P11Signature.
 *
 * NOTE that sessions obtained from this class SHOULD be returned using
 * either releaseSession() or closeSession() using a finally block when
 * not needed anymore. Otherwise, they will be left for cleanup via the
 * PhantomReference mechanism when GC kicks in, but it's best not to rely
 * on that since GC may not run timely enough since the native PKCS11 library
 * is also consuming memory.
 *
 * Note that sessions are automatically closed when they are not used for a
 * period of time, see Session.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class SessionManager {

    private final static int DEFAULT_MAX_SESSIONS = 32;

    private final static Debug debug = Debug.getInstance("pkcs11");

    // token instance
    private final Token token;

    // maximum number of sessions to open with this token
    private final int maxSessions;

    // pool of available object sessions
    private final Pool objSessions;

    // pool of available operation sessions
    private final Pool opSessions;

    // maximum number of active sessions during this invocation, for debugging
    private int maxActiveSessions;

    // flags to use in the C_OpenSession() call
    private final long openSessionFlags;

    SessionManager(Token token) {
        long n;
        if (token.isWriteProtected()) {
            openSessionFlags = CKF_SERIAL_SESSION;
            n = token.tokenInfo.ulMaxSessionCount;
        } else {
            openSessionFlags = CKF_SERIAL_SESSION | CKF_RW_SESSION;
            n = token.tokenInfo.ulMaxRwSessionCount;
        }
        if (n == CK_EFFECTIVELY_INFINITE) {
            n = Integer.MAX_VALUE;
        } else if ((n == CK_UNAVAILABLE_INFORMATION) || (n < 0)) {
            // choose an arbitrary concrete value
            n = DEFAULT_MAX_SESSIONS;
        }
        maxSessions = (int)Math.min(n, Integer.MAX_VALUE);
        this.token = token;
        this.objSessions = new Pool(this);
        this.opSessions = new Pool(this);
    }

    // returns whether only a fairly low number of sessions are
    // supported by this token.
    boolean lowMaxSessions() {
        return (maxSessions <= DEFAULT_MAX_SESSIONS);
    }

    // returns the total number of active sessions
    int totalSessionCount() {
        return SessionRef.totalCount();
    }

    synchronized Session getObjSession() throws PKCS11Exception {
        Session session = objSessions.poll();
        if (session != null) {
            return ensureValid(session);
        }
        session = opSessions.poll();
        if (session != null) {
            return ensureValid(session);
        }
        session = openSession();
        return ensureValid(session);
    }

    synchronized Session getOpSession() throws PKCS11Exception {
        Session session = opSessions.poll();
        if (session != null) {
            return ensureValid(session);
        }
        // create a new session rather than re-using an obj session
        // that avoids potential expensive cancels() for Signatures & RSACipher
        if (maxSessions == Integer.MAX_VALUE ||
                totalSessionCount() < maxSessions) {
            session = openSession();
            return ensureValid(session);
        }
        session = objSessions.poll();
        if (session != null) {
            return ensureValid(session);
        }
        throw new ProviderException("Could not obtain session");
    }

    private Session ensureValid(Session session) {
        session.id();
        return session;
    }

    synchronized Session killSession(Session session) {
        if ((session == null) || (token.isValid() == false)) {
            return null;
        }
        if (debug != null) {
            String location = new Exception().getStackTrace()[2].toString();
            System.out.println("Killing session (" + location + ") active: "
                + totalSessionCount());
        }
        closeSession(session);
        return null;
    }

    synchronized Session releaseSession(Session session) {
        if ((session == null) || (token.isValid() == false)) {
            return null;
        }

        if (session.hasObjects()) {
            objSessions.release(session);
        } else {
            opSessions.release(session);
        }
        return null;
    }

    synchronized void demoteObjSession(Session session) {
        if (token.isValid() == false) {
            return;
        }
        if (debug != null) {
            System.out.println("Demoting session, active: " +
                totalSessionCount());
        }
        boolean present = objSessions.remove(session);
        if (present == false) {
            // session is currently in use
            // will be added to correct pool on release, nothing to do now
            return;
        }
        opSessions.release(session);
    }

    private Session openSession() throws PKCS11Exception {
        if ((maxSessions != Integer.MAX_VALUE) &&
                (totalSessionCount() >= maxSessions)) {
            throw new ProviderException("No more sessions available");
        }
        long id = token.p11.C_OpenSession
                    (token.provider.slotID, openSessionFlags, null, null);
        Session session = new Session(token, id);
        if (debug != null) {
            int currTotal = totalSessionCount();
            if (currTotal > maxActiveSessions) {
                maxActiveSessions = currTotal;
                if (maxActiveSessions % 10 == 0) {
                    System.out.println("Open sessions: " + maxActiveSessions);
                }
            }
        }
        return session;
    }

    private void closeSession(Session session) {
        session.close();
    }

    private static final class Pool {

        private final SessionManager mgr;

        private final List<Session> pool;

        Pool(SessionManager mgr) {
            this.mgr = mgr;
            pool = new ArrayList<Session>();
        }

        boolean remove(Session session) {
            return pool.remove(session);
        }

        Session poll() {
            int n = pool.size();
            if (n == 0) {
                return null;
            }
            Session session = pool.remove(n - 1);
            return session;
        }

        void release(Session session) {
            pool.add(session);
            // if there are idle sessions, close them
            if (session.hasObjects()) {
                return;
            }
            int n = pool.size();
            if (n < 5) {
                return;
            }
            Session oldestSession = pool.get(0);
            long time = System.currentTimeMillis();
            if (session.isLive(time) && oldestSession.isLive(time)) {
                return;
            }
            Collections.sort(pool);
            int i = 0;
            while (i < n - 1) { // always keep at least 1 session open
                oldestSession = pool.get(i);
                if (oldestSession.isLive(time)) {
                    break;
                }
                i++;
                mgr.closeSession(oldestSession);
            }
            if (debug != null) {
                System.out.println("Closing " + i + " idle sessions, active: "
                        + mgr.totalSessionCount());
            }
            List<Session> subList = pool.subList(0, i);
            subList.clear();
        }

    }

}
