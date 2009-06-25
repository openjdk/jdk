/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.www.protocol.http;

import java.io.IOException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import sun.security.jgss.GSSManagerImpl;
import sun.security.jgss.GSSUtil;
import sun.security.jgss.HttpCaller;

/**
 * This class encapsulates all JAAS and JGSS API calls in a separate class
 * outside NegotiateAuthentication.java so that J2SE build can go smoothly
 * without the presence of it.
 *
 * @author weijun.wang@sun.com
 * @since 1.6
 */
public class NegotiatorImpl extends Negotiator {

    private static final boolean DEBUG =
        java.security.AccessController.doPrivileged(
              new sun.security.action.GetBooleanAction("sun.security.krb5.debug"));

    private GSSContext context;
    private byte[] oneToken;

    /**
     * Initialize the object, which includes:<ul>
     * <li>Find out what GSS mechanism to use from the system property
     * <code>http.negotiate.mechanism.oid</code>, defaults SPNEGO
     * <li>Creating the GSSName for the target host, "HTTP/"+hostname
     * <li>Creating GSSContext
     * <li>A first call to initSecContext</ul>
     */
    private void init(HttpCallerInfo hci) throws GSSException {
        final Oid oid;

        if (hci.scheme.equalsIgnoreCase("Kerberos")) {
            // we can only use Kerberos mech when the scheme is kerberos
            oid = GSSUtil.GSS_KRB5_MECH_OID;
        } else {
            String pref = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<String>() {
                        public String run() {
                            return System.getProperty(
                                "http.auth.preference",
                                "spnego");
                        }
                    });
            if (pref.equalsIgnoreCase("kerberos")) {
                oid = GSSUtil.GSS_KRB5_MECH_OID;
            } else {
                // currently there is no 3rd mech we can use
                oid = GSSUtil.GSS_SPNEGO_MECH_OID;
            }
        }

        GSSManagerImpl manager = new GSSManagerImpl(
                new HttpCaller(hci));

        // RFC 4559 4.1 uses uppercase service name "HTTP".
        // RFC 4120 6.2.1 demands the host be lowercase
        String peerName = "HTTP@" + hci.host.toLowerCase();

        GSSName serverName = manager.createName(peerName,
                GSSName.NT_HOSTBASED_SERVICE);
        context = manager.createContext(serverName,
                                        oid,
                                        null,
                                        GSSContext.DEFAULT_LIFETIME);

        // In order to support credential delegation in HTTP/SPNEGO,
        // we always request it before initSecContext. The current
        // implementation will check the OK-AS-DELEGATE flag inside
        // the service ticket of the web server, and only enable
        // delegation when this flag is set. This check is only
        // performed when the GSS caller is CALLER_HTTP_NEGOTIATE,
        // so all other normal GSS-API calls are not affected.

        context.requestCredDeleg(true);
        oneToken = context.initSecContext(new byte[0], 0, 0);
    }

    /**
     * Constructor
     * @throws java.io.IOException If negotiator cannot be constructed
     */
    public NegotiatorImpl(HttpCallerInfo hci) throws IOException {
        try {
            init(hci);
        } catch (GSSException e) {
            if (DEBUG) {
                System.out.println("Negotiate support not initiated, will " +
                        "fallback to other scheme if allowed. Reason:");
                e.printStackTrace();
            }
            IOException ioe = new IOException("Negotiate support not initiated");
            ioe.initCause(e);
            throw ioe;
        }
    }

    /**
     * Return the first token of GSS, in SPNEGO, it's called NegTokenInit
     * @return the first token
     */
    public byte[] firstToken() {
        return oneToken;
    }

    /**
     * Return the rest tokens of GSS, in SPNEGO, it's called NegTokenTarg
     * @param token the token received from server
     * @return the next token
     * @throws java.io.IOException if the token cannot be created successfully
     */
    public byte[] nextToken(byte[] token) throws IOException {
        try {
            return context.initSecContext(token, 0, token.length);
        } catch (GSSException e) {
            if (DEBUG) {
                System.out.println("Negotiate support cannot continue. Reason:");
                e.printStackTrace();
            }
            IOException ioe = new IOException("Negotiate support cannot continue");
            ioe.initCause(e);
            throw ioe;
        }
    }
}
