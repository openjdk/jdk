/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath.ssl;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.CertSelector;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.CertStoreParameters;
import java.security.cert.CertStoreSpi;
import java.security.cert.CRLSelector;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A CertStore that retrieves an SSL server's certificate chain.
 */
public final class SSLServerCertStore extends CertStoreSpi {

    private final URI uri;

    SSLServerCertStore(URI uri) throws InvalidAlgorithmParameterException {
        super(null);
        this.uri = uri;
    }

    public synchronized Collection<X509Certificate> engineGetCertificates
        (CertSelector selector) throws CertStoreException
    {
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            GetChainTrustManager xtm = new GetChainTrustManager();
            sc.init(null, new TrustManager[] { xtm }, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(
                new HostnameVerifier() {
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
            });
            uri.toURL().openConnection().connect();
            return getMatchingCerts(xtm.serverChain, selector);
        } catch (GeneralSecurityException | IOException e) {
            throw new CertStoreException(e);
        }
    }

    private static List<X509Certificate> getMatchingCerts
        (List<X509Certificate> certs, CertSelector selector)
    {
        // if selector not specified, all certs match
        if (selector == null) {
            return certs;
        }
        List<X509Certificate> matchedCerts = new ArrayList<>(certs.size());
        for (X509Certificate cert : certs) {
            if (selector.match(cert)) {
                matchedCerts.add(cert);
            }
        }
        return matchedCerts;
    }

    public Collection<X509CRL> engineGetCRLs(CRLSelector selector)
        throws CertStoreException
    {
        throw new UnsupportedOperationException();
    }

    static synchronized CertStore getInstance(URI uri)
        throws InvalidAlgorithmParameterException
    {
        return new CS(new SSLServerCertStore(uri), null, "SSLServer", null);
    }

    /*
     * An X509TrustManager that simply stores a reference to the server's
     * certificate chain.
     */
    private static class GetChainTrustManager implements X509TrustManager {
        private List<X509Certificate> serverChain;

        public X509Certificate[] getAcceptedIssuers() {
            throw new UnsupportedOperationException();
        }

        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType)
            throws CertificateException
        {
            throw new UnsupportedOperationException();
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType)
            throws CertificateException
        {
            this.serverChain = (chain == null)
                               ? Collections.<X509Certificate>emptyList()
                               : Arrays.asList(chain);
        }
    }

    /**
     * This class allows the SSLServerCertStore to be accessed as a CertStore.
     */
    private static class CS extends CertStore {
        protected CS(CertStoreSpi spi, Provider p, String type,
                     CertStoreParameters params)
        {
            super(spi, p, type, params);
        }
    }
}
