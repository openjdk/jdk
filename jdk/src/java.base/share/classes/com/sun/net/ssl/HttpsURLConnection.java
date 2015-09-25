/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * NOTE:  this file was copied from javax.net.ssl.HttpsURLConnection
 */

package com.sun.net.ssl;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.security.cert.Certificate;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * HTTP URL connection with support for HTTPS-specific features. See
 * <A HREF="http://www.w3.org/pub/WWW/Protocols/"> the spec </A> for
 * details.
 *
 * @deprecated As of JDK 1.4, this implementation-specific class was
 *      replaced by {@link javax.net.ssl.HttpsURLConnection}.
 */
@Deprecated
public abstract
class HttpsURLConnection extends HttpURLConnection
{
    /*
     * Initialize an HTTPS URLConnection ... could check that the URL
     * is an "https" URL, and that the handler is also an HTTPS one,
     * but that's established by other code in this package.
     * @param url the URL
     */
    public HttpsURLConnection(URL url) throws IOException {
        super(url);
    }

    /**
     * Returns the cipher suite in use on this connection.
     * @return the cipher suite
     */
    public abstract String getCipherSuite();

    /**
     * Returns the server's X.509 certificate chain, or null if
     * the server did not authenticate.
     * @return the server certificate chain
     */
    public abstract Certificate[] getServerCertificates()
        throws SSLPeerUnverifiedException;

    /**
     * HostnameVerifier provides a callback mechanism so that
     * implementers of this interface can supply a policy for
     * handling the case where the host to connect to and
     * the server name from the certificate mismatch.
     *
     * The default implementation will deny such connections.
     */
    private static HostnameVerifier defaultHostnameVerifier =
        new HostnameVerifier() {
            public boolean verify(String urlHostname, String certHostname) {
                return false;
            }
        };

    protected HostnameVerifier hostnameVerifier = defaultHostnameVerifier;

    /**
     * Sets the default HostnameVerifier inherited when an instance
     * of this class is created.
     * @param v the default host name verifier
     */
    public static void setDefaultHostnameVerifier(HostnameVerifier v) {
        if (v == null) {
            throw new IllegalArgumentException(
                "no default HostnameVerifier specified");
        }

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SSLPermission("setHostnameVerifier"));
        }
        defaultHostnameVerifier = v;
    }

    /**
     * Gets the default HostnameVerifier.
     * @return the default host name verifier
     */
    public static HostnameVerifier getDefaultHostnameVerifier() {
        return defaultHostnameVerifier;
    }

    /**
     * Sets the HostnameVerifier.
     * @param v the host name verifier
     */
    public void setHostnameVerifier(HostnameVerifier v) {
        if (v == null) {
            throw new IllegalArgumentException(
                "no HostnameVerifier specified");
        }

        hostnameVerifier = v;
    }

    /**
     * Gets the HostnameVerifier.
     * @return the host name verifier
     */
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    private static SSLSocketFactory defaultSSLSocketFactory = null;

    private SSLSocketFactory sslSocketFactory = getDefaultSSLSocketFactory();

    /**
     * Sets the default SSL socket factory inherited when an instance
     * of this class is created.
     * @param sf the default SSL socket factory
     */
    public static void setDefaultSSLSocketFactory(SSLSocketFactory sf) {
        if (sf == null) {
            throw new IllegalArgumentException(
                "no default SSLSocketFactory specified");
        }

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkSetFactory();
        }
        defaultSSLSocketFactory = sf;
    }

    /**
     * Gets the default SSL socket factory.
     * @return the default SSL socket factory
     */
    public static SSLSocketFactory getDefaultSSLSocketFactory() {
        if (defaultSSLSocketFactory == null) {
            defaultSSLSocketFactory =
                (SSLSocketFactory)SSLSocketFactory.getDefault();
        }
        return defaultSSLSocketFactory;
    }

    /**
     * Sets the SSL socket factory.
     * @param sf the SSL socket factory
     */
    public void setSSLSocketFactory(SSLSocketFactory sf) {
        if (sf == null) {
            throw new IllegalArgumentException(
                "no SSLSocketFactory specified");
        }

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkSetFactory();
        }

        sslSocketFactory = sf;
    }

    /**
     * Gets the SSL socket factory.
     * @return the SSL socket factory
     */
    public SSLSocketFactory getSSLSocketFactory() {
        return sslSocketFactory;
    }
}
