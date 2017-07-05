/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import javax.crypto.SecretKey;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

/**
 * A helper class for Kerberos APIs.
 */
public final class Krb5Helper {

    private Krb5Helper() { }

    // loads Krb5Proxy implementation class if available
    private static final String IMPL_CLASS =
        "sun.security.ssl.krb5.Krb5ProxyImpl";

    private static final Krb5Proxy proxy =
        AccessController.doPrivileged(new PrivilegedAction<Krb5Proxy>() {
            @Override
            public Krb5Proxy run() {
                try {
                    Class<?> c = Class.forName(IMPL_CLASS, true, null);
                    return (Krb5Proxy)c.newInstance();
                } catch (ClassNotFoundException cnf) {
                    return null;
                } catch (InstantiationException e) {
                    throw new AssertionError(e);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }});

    /**
     * Returns true if Kerberos is available.
     */
    public static boolean isAvailable() {
        return proxy != null;
    }

    private static void ensureAvailable() {
        if (proxy == null)
            throw new AssertionError("Kerberos should have been available");
    }

    /**
     * Returns the Subject associated with client-side of the SSL socket.
     */
    public static Subject getClientSubject(AccessControlContext acc)
            throws LoginException {
        ensureAvailable();
        return proxy.getClientSubject(acc);
    }

    /**
     * Returns the Subject associated with server-side of the SSL socket.
     */
    public static Subject getServerSubject(AccessControlContext acc)
            throws LoginException {
        ensureAvailable();
        return proxy.getServerSubject(acc);
    }

    /**
     * Returns the KerberosKeys for the default server-side principal.
     */
    public static Object getServiceCreds(AccessControlContext acc)
            throws LoginException {
        ensureAvailable();
        return proxy.getServiceCreds(acc);
    }

    /**
     * Returns the server-side principal name associated with the KerberosKey.
     */
    public static String getServerPrincipalName(Object serviceCreds) {
        ensureAvailable();
        return proxy.getServerPrincipalName(serviceCreds);
    }

    /**
     * Returns the hostname embedded in the principal name.
     */
    public static String getPrincipalHostName(Principal principal) {
        ensureAvailable();
        return proxy.getPrincipalHostName(principal);
    }

    /**
     * Returns a ServicePermission for the principal name and action.
     */
    public static Permission getServicePermission(String principalName,
            String action) {
        ensureAvailable();
        return proxy.getServicePermission(principalName, action);
    }

    /**
     * Determines if the Subject might contain creds for princ.
     */
    public static boolean isRelated(Subject subject, Principal princ) {
        ensureAvailable();
        return proxy.isRelated(subject, princ);
    }
}
