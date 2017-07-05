/*
 * Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.Random;

import sun.net.www.HeaderParser;

import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.net.*;

/**
 * NTLMAuthentication:
 *
 * @author Michael McMahon
 */

class NTLMAuthentication extends AuthenticationInfo {

    private static final long serialVersionUID = 100L;

    static final char NTLM_AUTH = 'N';
    private String hostname;
    private static String defaultDomain; /* Domain to use if not specified by user */

    static {
        defaultDomain = java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("http.auth.ntlm.domain",
                                                      "domain"));
    };

    private void init0() {

        hostname = java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<String>() {
            public String run() {
                String localhost;
                try {
                    localhost = InetAddress.getLocalHost().getHostName().toUpperCase();
                } catch (UnknownHostException e) {
                     localhost = "localhost";
                }
                return localhost;
            }
        });
        int x = hostname.indexOf ('.');
        if (x != -1) {
            hostname = hostname.substring (0, x);
        }
    }

    String username;
    String ntdomain;
    String password;

    /**
     * Create a NTLMAuthentication:
     * Username may be specified as domain<BACKSLASH>username in the application Authenticator.
     * If this notation is not used, then the domain will be taken
     * from a system property: "http.auth.ntlm.domain".
     */
    public NTLMAuthentication(boolean isProxy, URL url, PasswordAuthentication pw) {
        super(isProxy?PROXY_AUTHENTICATION:SERVER_AUTHENTICATION, NTLM_AUTH, url, "");
        init (pw);
    }

    private void init (PasswordAuthentication pw) {
        this.pw = pw;
        if (pw != null) {
            String s = pw.getUserName();
            int i = s.indexOf ('\\');
            if (i == -1) {
                username = s;
                ntdomain = defaultDomain;
            } else {
                ntdomain = s.substring (0, i).toUpperCase();
                username = s.substring (i+1);
            }
            password = new String (pw.getPassword());
        } else {
            /* credentials will be acquired from OS */
            username = null;
            ntdomain = null;
            password = null;
        }
        init0();
    }

   /**
    * Constructor used for proxy entries
    */
    public NTLMAuthentication(boolean isProxy, String host, int port,
                                PasswordAuthentication pw) {
        super(isProxy?PROXY_AUTHENTICATION:SERVER_AUTHENTICATION, NTLM_AUTH,host, port, "");
        init (pw);
    }

    /**
     * @return true if this authentication supports preemptive authorization
     */
    boolean supportsPreemptiveAuthorization() {
        return false;
    }

    /**
     * @return true if NTLM supported transparently (no password needed, SSO)
     */
    static boolean supportsTransparentAuth() {
        return true;
    }

    /**
     * @return the name of the HTTP header this authentication wants set
     */
    String getHeaderName() {
        if (type == SERVER_AUTHENTICATION) {
            return "Authorization";
        } else {
            return "Proxy-authorization";
        }
    }

    /**
     * Not supported. Must use the setHeaders() method
     */
    String getHeaderValue(URL url, String method) {
        throw new RuntimeException ("getHeaderValue not supported");
    }

    /**
     * Check if the header indicates that the current auth. parameters are stale.
     * If so, then replace the relevant field with the new value
     * and return true. Otherwise return false.
     * returning true means the request can be retried with the same userid/password
     * returning false means we have to go back to the user to ask for a new
     * username password.
     */
    boolean isAuthorizationStale (String header) {
        return false; /* should not be called for ntlm */
    }

    /**
     * Set header(s) on the given connection.
     * @param conn The connection to apply the header(s) to
     * @param p A source of header values for this connection, not used because
     *          HeaderParser converts the fields to lower case, use raw instead
     * @param raw The raw header field.
     * @return true if all goes well, false if no headers were set.
     */
    synchronized boolean setHeaders(HttpURLConnection conn, HeaderParser p, String raw) {

        try {
            NTLMAuthSequence seq = (NTLMAuthSequence)conn.authObj;
            if (seq == null) {
                seq = new NTLMAuthSequence (username, password, ntdomain);
                conn.authObj = seq;
            }
            String response = "NTLM " + seq.getAuthHeader (raw.length()>6?raw.substring(5):null);
            conn.setAuthenticationProperty(getHeaderName(), response);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /* This is a no-op for NTLM, because there is no authentication information
     * provided by the server to the client
     */
    public void checkResponse (String header, String method, URL url) throws IOException {
    }
}
