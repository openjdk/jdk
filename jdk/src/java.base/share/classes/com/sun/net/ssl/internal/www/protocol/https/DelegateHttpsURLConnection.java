/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.net.ssl.internal.www.protocol.https;

import java.net.URL;
import java.net.Proxy;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Iterator;

import java.security.Principal;
import java.security.cert.*;

import javax.security.auth.x500.X500Principal;

import sun.security.util.HostnameChecker;
import sun.security.util.DerValue;
import sun.security.x509.X500Name;

import sun.net.www.protocol.https.AbstractDelegateHttpsURLConnection;

/**
 * This class was introduced to provide an additional level of
 * abstraction between javax.net.ssl.HttpURLConnection and
 * com.sun.net.ssl.HttpURLConnection objects. <p>
 *
 * javax.net.ssl.HttpURLConnection is used in the new sun.net version
 * of protocol implementation (this one)
 * com.sun.net.ssl.HttpURLConnection is used in the com.sun version.
 *
 */
@Deprecated(since="9")
@SuppressWarnings("deprecation") // HttpsURLConnection is deprecated
public class DelegateHttpsURLConnection extends AbstractDelegateHttpsURLConnection {

    // we need a reference to the HttpsURLConnection to get
    // the properties set there
    // we also need it to be public so that it can be referenced
    // from sun.net.www.protocol.http.HttpURLConnection
    // this is for ResponseCache.put(URI, URLConnection)
    // second parameter needs to be cast to javax.net.ssl.HttpsURLConnection
    // instead of AbstractDelegateHttpsURLConnection

    public com.sun.net.ssl.HttpsURLConnection httpsURLConnection;

    DelegateHttpsURLConnection(URL url,
            sun.net.www.protocol.http.Handler handler,
            com.sun.net.ssl.HttpsURLConnection httpsURLConnection)
            throws IOException {
        this(url, null, handler, httpsURLConnection);
    }

    DelegateHttpsURLConnection(URL url, Proxy p,
            sun.net.www.protocol.http.Handler handler,
            com.sun.net.ssl.HttpsURLConnection httpsURLConnection)
            throws IOException {
        super(url, p, handler);
        this.httpsURLConnection = httpsURLConnection;
    }

    protected javax.net.ssl.SSLSocketFactory getSSLSocketFactory() {
        return httpsURLConnection.getSSLSocketFactory();
    }

    protected javax.net.ssl.HostnameVerifier getHostnameVerifier() {
        // note: getHostnameVerifier() never returns null
        return new VerifierWrapper(httpsURLConnection.getHostnameVerifier());
    }

    /*
     * Called by layered delegator's finalize() method to handle closing
     * the underlying object.
     */
    protected void dispose() throws Throwable {
        super.finalize();
    }
}

class VerifierWrapper implements javax.net.ssl.HostnameVerifier {
    @SuppressWarnings("deprecation")
    private com.sun.net.ssl.HostnameVerifier verifier;

    @SuppressWarnings("deprecation")
    VerifierWrapper(com.sun.net.ssl.HostnameVerifier verifier) {
        this.verifier = verifier;
    }

    /*
     * In com.sun.net.ssl.HostnameVerifier the method is defined
     * as verify(String urlHostname, String certHostname).
     * This means we need to extract the hostname from the X.509 certificate
     * or from the Kerberos principal name, in this wrapper.
     */
    public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
        try {
            String serverName;
            // Use ciphersuite to determine whether Kerberos is active.
            if (session.getCipherSuite().startsWith("TLS_KRB5")) {
                serverName =
                    HostnameChecker.getServerName(getPeerPrincipal(session));

            } else { // X.509
                Certificate[] serverChain = session.getPeerCertificates();
                if ((serverChain == null) || (serverChain.length == 0)) {
                    return false;
                }
                if (serverChain[0] instanceof X509Certificate == false) {
                    return false;
                }
                X509Certificate serverCert = (X509Certificate)serverChain[0];
                serverName = getServername(serverCert);
            }
            if (serverName == null) {
                return false;
            }
            return verifier.verify(hostname, serverName);
        } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
            return false;
        }
    }

    /*
     * Get the peer principal from the session
     */
    private Principal getPeerPrincipal(javax.net.ssl.SSLSession session)
        throws javax.net.ssl.SSLPeerUnverifiedException
    {
        Principal principal;
        try {
            principal = session.getPeerPrincipal();
        } catch (AbstractMethodError e) {
            // if the provider does not support it, return null, since
            // we need it only for Kerberos.
            principal = null;
        }
        return principal;
    }

    /*
     * Extract the name of the SSL server from the certificate.
     *
     * Note this code is essentially a subset of the hostname extraction
     * code in HostnameChecker.
     */
    private static String getServername(X509Certificate peerCert) {
        try {
            // compare to subjectAltNames if dnsName is present
            Collection<List<?>> subjAltNames = peerCert.getSubjectAlternativeNames();
            if (subjAltNames != null) {
                for (Iterator<List<?>> itr = subjAltNames.iterator(); itr.hasNext(); ) {
                    List<?> next = itr.next();
                    if (((Integer)next.get(0)).intValue() == 2) {
                        // compare dNSName with host in url
                        String dnsName = ((String)next.get(1));
                        return dnsName;
                    }
                }
            }

            // else check against common name in the subject field
            X500Name subject = HostnameChecker.getSubjectX500Name(peerCert);

            DerValue derValue = subject.findMostSpecificAttribute
                                                (X500Name.commonName_oid);
            if (derValue != null) {
                try {
                    String name = derValue.getAsString();
                    return name;
                } catch (IOException e) {
                    // ignore
                }
            }
        } catch (java.security.cert.CertificateException e) {
            // ignore
        }
        return null;
    }

}
