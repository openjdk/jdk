/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * Is a reference to Connections that is stored in Pool.
 * This is an intermediate object that is outside of the circular
 * reference loop of
 *  com.sun.jndi.ldap.Connection <-> com.sun.jndi.ldap.LdapClient
 *    <-> com.sun.jndi.ldap.pool.Connections
 *
 * Because Connection is a daemon thread, it will keep LdapClient
 * alive until LdapClient closes Connection. This will in turn
 * keep Connections alive. So even when Connections is removed
 * from (the WeakHashMap of) Pool, it won't be finalized.
 * ConnectionsRef acts as Connections's finalizer.
 *
 * Without connection pooling, com.sun.jndi.ldap.LdapCtx's finalize()
 * closes LdapClient, which in turn closes Connection.
 * With connection pooling, ConnectionsRef's finalize() calls
 * Connections.close(), which in turn will close all idle connections
 * and mark Connections such that in-use connections will be closed
 * when they are returned to the pool.
 */
final class ConnectionsRef {
    final private Connections conns;

    ConnectionsRef(Connections conns) {
        this.conns = conns;
    }

    Connections getConnections() {
        return conns;
    }
}
