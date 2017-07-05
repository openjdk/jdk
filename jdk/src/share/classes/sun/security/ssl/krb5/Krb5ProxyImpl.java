/*
 * Copyright 2009 Sun Microsystems, Inc. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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

package sun.security.ssl.krb5;

import java.security.AccessControlContext;
import java.security.Permission;
import java.security.Principal;
import javax.crypto.SecretKey;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.ServicePermission;
import javax.security.auth.login.LoginException;

import sun.security.jgss.GSSCaller;
import sun.security.jgss.krb5.Krb5Util;
import sun.security.krb5.PrincipalName;
import sun.security.ssl.Krb5Proxy;

/**
 * An implementatin of Krb5Proxy that simply delegates to the appropriate
 * Kerberos APIs.
 */
public class Krb5ProxyImpl implements Krb5Proxy {

    public Krb5ProxyImpl() { }

    @Override
    public Subject getClientSubject(AccessControlContext acc)
            throws LoginException {
        return Krb5Util.getSubject(GSSCaller.CALLER_SSL_CLIENT, acc);
    }

    @Override
    public Subject getServerSubject(AccessControlContext acc)
            throws LoginException {
        return Krb5Util.getSubject(GSSCaller.CALLER_SSL_SERVER, acc);
    }

    @Override
    public SecretKey[] getServerKeys(AccessControlContext acc)
            throws LoginException {
        return Krb5Util.getKeys(GSSCaller.CALLER_SSL_SERVER, null, acc);
    }

    @Override
    public String getServerPrincipalName(SecretKey kerberosKey) {
        return ((KerberosKey)kerberosKey).getPrincipal().getName();
    }

    @Override
    public String getPrincipalHostName(Principal principal) {
        if (principal == null) {
           return null;
        }
        String hostName = null;
        try {
            PrincipalName princName =
                new PrincipalName(principal.getName(),
                        PrincipalName.KRB_NT_SRV_HST);
            String[] nameParts = princName.getNameStrings();
            if (nameParts.length >= 2) {
                hostName = nameParts[1];
            }
        } catch (Exception e) {
            // ignore
        }
        return hostName;
    }


    @Override
    public Permission getServicePermission(String principalName,
            String action) {
        return new ServicePermission(principalName, action);
    }
}
