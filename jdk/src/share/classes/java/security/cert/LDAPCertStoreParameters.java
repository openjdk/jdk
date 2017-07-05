/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security.cert;

/**
 * Parameters used as input for the LDAP <code>CertStore</code> algorithm.
 * <p>
 * This class is used to provide necessary configuration parameters (server
 * name and port number) to implementations of the LDAP <code>CertStore</code>
 * algorithm.
 * <p>
 * <b>Concurrent Access</b>
 * <p>
 * Unless otherwise specified, the methods defined in this class are not
 * thread-safe. Multiple threads that need to access a single
 * object concurrently should synchronize amongst themselves and
 * provide the necessary locking. Multiple threads each manipulating
 * separate objects need not synchronize.
 *
 * @since       1.4
 * @author      Steve Hanna
 * @see         CertStore
 */
public class LDAPCertStoreParameters implements CertStoreParameters {

    private static final int LDAP_DEFAULT_PORT = 389;

    /**
     * the port number of the LDAP server
     */
    private int port;

    /**
     * the DNS name of the LDAP server
     */
    private String serverName;

    /**
     * Creates an instance of <code>LDAPCertStoreParameters</code> with the
     * specified parameter values.
     *
     * @param serverName the DNS name of the LDAP server
     * @param port the port number of the LDAP server
     * @exception NullPointerException if <code>serverName</code> is
     * <code>null</code>
     */
    public LDAPCertStoreParameters(String serverName, int port) {
        if (serverName == null)
            throw new NullPointerException();
        this.serverName = serverName;
        this.port = port;
    }

    /**
     * Creates an instance of <code>LDAPCertStoreParameters</code> with the
     * specified server name and a default port of 389.
     *
     * @param serverName the DNS name of the LDAP server
     * @exception NullPointerException if <code>serverName</code> is
     * <code>null</code>
     */
    public LDAPCertStoreParameters(String serverName) {
        this(serverName, LDAP_DEFAULT_PORT);
    }

    /**
     * Creates an instance of <code>LDAPCertStoreParameters</code> with the
     * default parameter values (server name "localhost", port 389).
     */
    public LDAPCertStoreParameters() {
        this("localhost", LDAP_DEFAULT_PORT);
    }

    /**
     * Returns the DNS name of the LDAP server.
     *
     * @return the name (not <code>null</code>)
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Returns the port number of the LDAP server.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns a copy of this object. Changes to the copy will not affect
     * the original and vice versa.
     * <p>
     * Note: this method currently performs a shallow copy of the object
     * (simply calls <code>Object.clone()</code>). This may be changed in a
     * future revision to perform a deep copy if new parameters are added
     * that should not be shared.
     *
     * @return the copy
     */
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            /* Cannot happen */
            throw new InternalError(e.toString());
        }
    }

    /**
     * Returns a formatted string describing the parameters.
     *
     * @return a formatted string describing the parameters
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("LDAPCertStoreParameters: [\n");

        sb.append("  serverName: " + serverName + "\n");
        sb.append("  port: " + port + "\n");
        sb.append("]");
        return sb.toString();
    }
}
