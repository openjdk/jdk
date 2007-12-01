/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jndi.ldap.pool;

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;

/*
 * This class defines a WeakReference to the ConnectionRef (the referent).
 *
 * The ConnectionRef enables to break the reference
 * cycle between Connection, LdapClient, Connections and ConnectionDesc,
 * shown in the figure below.
 *
 *        -------> Connections -----> ConnectionDesc
 *        |              ^                  |
 *        |              |                  |
 *        |              |                  |
 * ConnectionsRef    LdapClient <------------
 *        ^              |   ^
 *        :              |   |
 *        :              v   |
 * ConnectionsWeakRef  Connection
 *
 * The ConnectionsRef is for cleaning up the resources held by the
 * Connection thread by making them available to the GC. The pool
 * uses ConnectionRef to hold the pooled resources.
 *
 * This class in turn holds a WeakReference with a ReferenceQueue to the
 * ConnectionRef to track when the ConnectionRef becomes ready
 * for getting GC'ed. It extends from WeakReference in order to hold a
 * reference to Connections used for closing (which in turn terminates
 * the Connection thread) it by monitoring the ReferenceQueue.
 */
class ConnectionsWeakRef extends WeakReference {

    private final Connections conns;

    ConnectionsWeakRef (ConnectionsRef connsRef, ReferenceQueue queue) {
        super(connsRef, queue);
        this.conns = connsRef.getConnections();
    }

    Connections getConnections() {
        return conns;
    }
}
