/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.https;

import java.net.Authenticator;
import java.net.URL;
import java.net.Proxy;
import java.net.SecureCacheResponse;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLPeerUnverifiedException;
import sun.net.www.http.*;
import sun.net.www.protocol.http.HttpURLConnection;
import sun.net.www.protocol.http.HttpCallerInfo;

/**
 * HTTPS URL connection support.
 * We need this delegate because HttpsURLConnection is a subclass of
 * java.net.HttpURLConnection. We will avoid copying over the code from
 * sun.net.www.protocol.http.HttpURLConnection by having this class
 *
 */
public abstract class AbstractDelegateHttpsURLConnection extends
        HttpURLConnection {

    private SSLSession savedSession = null;
    protected AbstractDelegateHttpsURLConnection(URL url,
            sun.net.www.protocol.http.Handler handler) throws IOException {
        this(url, null, handler);
    }

    protected AbstractDelegateHttpsURLConnection(URL url, Proxy p,
            sun.net.www.protocol.http.Handler handler) throws IOException {
        super(url, p, handler);
    }

    protected abstract javax.net.ssl.SSLSocketFactory getSSLSocketFactory();

    protected abstract javax.net.ssl.HostnameVerifier getHostnameVerifier();

    /*
     * No user application is able to call these routines, as no one
     * should ever get access to an instance of
     * DelegateHttpsURLConnection (sun.* or com.*)
     */

    /**
     * Create a new HttpClient object, bypassing the cache of
     * HTTP client objects/connections.
     *
     * @param url the URL being accessed
     */
    public void setNewClient (URL url)
        throws IOException {
        setNewClient (url, false);
    }

    /**
     * Obtain a HttpClient object. Use the cached copy if specified.
     *
     * @param url       the URL being accessed
     * @param useCache  whether the cached connection should be used
     *        if present
     */
    public void setNewClient (URL url, boolean useCache)
        throws IOException {
        int readTimeout = getReadTimeout();
        savedSession = null;
        http = HttpsClient.New (getSSLSocketFactory(),
                                url,
                                getHostnameVerifier(),
                                null,
                                -1,
                                useCache,
                                getConnectTimeout(),
                                this);
        http.setReadTimeout(readTimeout);
        ((HttpsClient)http).afterConnect();
    }

    /**
     * Create a new HttpClient object, set up so that it uses
     * per-instance proxying to the given HTTP proxy.  This
     * bypasses the cache of HTTP client objects/connections.
     *
     * @param url       the URL being accessed
     * @param proxyHost the proxy host to use
     * @param proxyPort the proxy port to use
     */
    public void setProxiedClient (URL url, String proxyHost, int proxyPort)
            throws IOException {
        setProxiedClient(url, proxyHost, proxyPort, false);
    }

    /**
     * Obtain a HttpClient object, set up so that it uses per-instance
     * proxying to the given HTTP proxy. Use the cached copy of HTTP
     * client objects/connections if specified.
     *
     * @param url       the URL being accessed
     * @param proxyHost the proxy host to use
     * @param proxyPort the proxy port to use
     * @param useCache  whether the cached connection should be used
     *        if present
     */
    public void setProxiedClient (URL url, String proxyHost, int proxyPort,
            boolean useCache) throws IOException {
        proxiedConnect(url, proxyHost, proxyPort, useCache);
        if (!http.isCachedConnection()) {
            doTunneling();
        }
        ((HttpsClient)http).afterConnect();
    }

    protected void proxiedConnect(URL url, String proxyHost, int proxyPort,
            boolean useCache) throws IOException {
        if (connected)
            return;
        int readTimeout = getReadTimeout();
        http = HttpsClient.New (getSSLSocketFactory(),
                                url,
                                getHostnameVerifier(),
                                proxyHost,
                                proxyPort,
                                useCache,
                                getConnectTimeout(),
                                this);
        http.setReadTimeout(readTimeout);
        connected = true;
    }

    /**
     * Used by subclass to access "connected" variable.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Used by subclass to access "connected" variable.
     */
    public void setConnected(boolean conn) {
        connected = conn;
    }

    /**
     * Implements the HTTP protocol handler's "connect" method,
     * establishing an SSL connection to the server as necessary.
     */
    public void connect() throws IOException {
        if (connected)
            return;
        plainConnect();
        if (cachedResponse != null) {
            // using cached response
            return;
        }
        if (!http.isCachedConnection() && http.needsTunneling()) {
            doTunneling();
        }
        savedSession = null;
        ((HttpsClient)http).afterConnect();
    }

    // will try to use cached HttpsClient
    protected HttpClient getNewHttpClient(URL url, Proxy p, int connectTimeout)
        throws IOException {
        return HttpsClient.New(getSSLSocketFactory(), url,
                               getHostnameVerifier(), p, true, connectTimeout,
                               this);
    }

    // will open new connection
    protected HttpClient getNewHttpClient(URL url, Proxy p, int connectTimeout,
                                          boolean useCache)
        throws IOException {
        return HttpsClient.New(getSSLSocketFactory(), url,
                               getHostnameVerifier(), p,
                               useCache, connectTimeout, this);
    }

    @Override
    protected void noResponseBody() {
        savedSession = ((HttpsClient)http).getSSLSession();
        super.noResponseBody();
    }

    private SSLSession session() {
        if (http instanceof HttpsClient https) {
            return https.getSSLSession();
        }
        return savedSession;
    }

    /**
     * Returns the cipher suite in use on this connection.
     */
    public String getCipherSuite () {
        if (cachedResponse != null) {
            return ((SecureCacheResponse)cachedResponse).getCipherSuite();
        }

        var session = session();
        if (session == null) {
            throw new IllegalStateException("connection not yet open");
        }
        return session.getCipherSuite();
    }

    /**
     * Returns the certificate chain the client sent to the
     * server, or null if the client did not authenticate.
     */
    public java.security.cert.Certificate[] getLocalCertificates() {
        if (cachedResponse != null) {
            List<java.security.cert.Certificate> l = ((SecureCacheResponse)cachedResponse).getLocalCertificateChain();
            if (l == null) {
                return null;
            } else {
                return l.toArray(new java.security.cert.Certificate[0]);
            }
        }

        var session = session();
        if (session == null) {
            throw new IllegalStateException("connection not yet open");
        }
        return session.getLocalCertificates();
    }

    /**
     * Returns the server's certificate chain, or throws
     * SSLPeerUnverified Exception if
     * the server did not authenticate.
     */
    public java.security.cert.Certificate[] getServerCertificates()
            throws SSLPeerUnverifiedException {
        if (cachedResponse != null) {
            List<java.security.cert.Certificate> l =
                    ((SecureCacheResponse)cachedResponse)
                            .getServerCertificateChain();
            if (l == null) {
                return null;
            } else {
                return l.toArray(new java.security.cert.Certificate[0]);
            }
        }

        var session = session();
        if (session == null) {
            throw new IllegalStateException("connection not yet open");
        }
        return session.getPeerCertificates();
    }

    /**
     * Returns the server's principal, or throws SSLPeerUnverifiedException
     * if the server did not authenticate.
     */
    Principal getPeerPrincipal()
            throws SSLPeerUnverifiedException
    {
        if (cachedResponse != null) {
            return ((SecureCacheResponse)cachedResponse).getPeerPrincipal();
        }

        var session = session();
        if (session == null) {
            throw new IllegalStateException("connection not yet open");
        }
        return getPeerPrincipal(session);
    }

    /**
     * Returns the principal the client sent to the
     * server, or null if the client did not authenticate.
     */
    Principal getLocalPrincipal()
    {
        if (cachedResponse != null) {
            return ((SecureCacheResponse)cachedResponse).getLocalPrincipal();
        }

        var session = session();
        if (session == null) {
            throw new IllegalStateException("connection not yet open");
        }
        return getLocalPrincipal(session);
    }

    SSLSession getSSLSession() {
        if (cachedResponse != null) {
            Optional<SSLSession> option =
                    ((SecureCacheResponse)cachedResponse).getSSLSession();
            if (option.isPresent()) {
                return option.orElseThrow();
            }
        }

        var session = session();
        if (session == null) {
            throw new IllegalStateException("connection not yet open");
        }

        return session;
    }

    /*
     * If no SSL Session available or if the system config does not allow it
     * don't use the extended caller info (the server cert).
     * Otherwise return true to include the server cert
     */
    private boolean useExtendedCallerInfo(URL url) {
        HttpsClient https = (HttpsClient)http;
        if (https.getSSLSession() == null) {
            return false;
        }
        String prop = http.getSpnegoCBT();
        if (prop.equals("never")) {
            return false;
        }
        String target = url.getHost();
        if (prop.startsWith("domain:")) {
            String[] domains = prop.substring(7).split(",");
            for (String domain : domains) {
                if (target.equalsIgnoreCase(domain)) {
                    return true;
                }
                if (domain.startsWith("*.") && target.regionMatches(
                        true, target.length() - domain.length() + 1, domain, 1, domain.length() - 1)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    protected HttpCallerInfo getHttpCallerInfo(URL url, String proxy, int port,
                                               Authenticator authenticator)
    {
        if (!useExtendedCallerInfo(url)) {
            return super.getHttpCallerInfo(url, proxy, port, authenticator);
        }
        HttpsClient https = (HttpsClient)http;
        try {
            Certificate[] certs = https.getSSLSession().getPeerCertificates();
            if (certs[0] instanceof X509Certificate x509Cert) {
                return new HttpCallerInfo(url, proxy, port, x509Cert, authenticator);
            }
        } catch (SSLPeerUnverifiedException e) {
            // ignore
        }
        return super.getHttpCallerInfo(url, proxy, port, authenticator);
    }

    @Override
    protected HttpCallerInfo getHttpCallerInfo(URL url, Authenticator authenticator)
    {
        if (!useExtendedCallerInfo(url)) {
            return super.getHttpCallerInfo(url, authenticator);
        }
        HttpsClient https = (HttpsClient)http;
        try {
            Certificate[] certs = https.getSSLSession().getPeerCertificates();
            if (certs[0] instanceof X509Certificate x509Cert) {
                return new HttpCallerInfo(url, x509Cert, authenticator);
            }
        } catch (SSLPeerUnverifiedException e) {
            // ignore
        }
        return super.getHttpCallerInfo(url, authenticator);
    }

    @Override
    public void disconnect() {
        super.disconnect();
        savedSession = null;
    }

    /**
     * Returns the principal with which the server authenticated
     * itself, or throw a SSLPeerUnverifiedException if the
     * server did not authenticate.
     * @param session The {@linkplain #getSSLSession() SSL session}
     */
    private static Principal getPeerPrincipal(SSLSession session)
            throws SSLPeerUnverifiedException
    {
        Principal principal;
        try {
            principal = session.getPeerPrincipal();
        } catch (AbstractMethodError e) {
            // if the provider does not support it, fallback to peer certs.
            // return the X500Principal of the end-entity cert.
            java.security.cert.Certificate[] certs =
                    session.getPeerCertificates();
            principal = ((X509Certificate)certs[0]).getSubjectX500Principal();
        }
        return principal;
    }

    /**
     * Returns the principal the client sent to the
     * server, or null if the client did not authenticate.
     * @param session The {@linkplain #getSSLSession() SSL session}
     */
    private static Principal getLocalPrincipal(SSLSession session)
    {
        Principal principal;
        try {
            principal = session.getLocalPrincipal();
        } catch (AbstractMethodError e) {
            principal = null;
            // if the provider does not support it, fallback to local certs.
            // return the X500Principal of the end-entity cert.
            java.security.cert.Certificate[] certs =
                    session.getLocalCertificates();
            if (certs != null) {
                principal = ((X509Certificate)certs[0]).getSubjectX500Principal();
            }
        }
        return principal;
    }



}
