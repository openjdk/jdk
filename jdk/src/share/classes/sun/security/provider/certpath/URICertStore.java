/*
 * Copyright (c) 2006, 2010 Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath;

import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertSelector;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.CertStoreParameters;
import java.security.cert.CertStoreSpi;
import java.security.cert.CRLException;
import java.security.cert.CRLSelector;
import java.security.cert.X509Certificate;
import java.security.cert.X509CertSelector;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import sun.security.x509.AccessDescription;
import sun.security.x509.GeneralNameInterface;
import sun.security.x509.URIName;
import sun.security.util.Cache;
import sun.security.util.Debug;

/**
 * A <code>CertStore</code> that retrieves <code>Certificates</code> or
 * <code>CRL</code>s from a URI, for example, as specified in an X.509
 * AuthorityInformationAccess or CRLDistributionPoint extension.
 * <p>
 * For CRLs, this implementation retrieves a single DER encoded CRL per URI.
 * For Certificates, this implementation retrieves a single DER encoded CRL or
 * a collection of Certificates encoded as a PKCS#7 "certs-only" CMS message.
 * <p>
 * This <code>CertStore</code> also implements Certificate/CRL caching.
 * Currently, the cache is shared between all applications in the VM and uses a
 * hardcoded policy. The cache has a maximum size of 185 entries, which are held
 * by SoftReferences. A request will be satisfied from the cache if we last
 * checked for an update within CHECK_INTERVAL (last 30 seconds). Otherwise,
 * we open an URLConnection to download the Certificate(s)/CRL using an
 * If-Modified-Since request (HTTP) if possible. Note that both positive and
 * negative responses are cached, i.e. if we are unable to open the connection
 * or the Certificate(s)/CRL cannot be parsed, we remember this result and
 * additional calls during the CHECK_INTERVAL period do not try to open another
 * connection.
 * <p>
 * The URICertStore is not currently a standard CertStore type. We should
 * consider adding a standard "URI" CertStore type.
 *
 * @author Andreas Sterbenz
 * @author Sean Mullan
 * @since 7.0
 */
class URICertStore extends CertStoreSpi {

    private static final Debug debug = Debug.getInstance("certpath");

    // interval between checks for update of cached Certificates/CRLs
    // (30 seconds)
    private final static int CHECK_INTERVAL = 30 * 1000;

    // size of the cache (see Cache class for sizing recommendations)
    private final static int CACHE_SIZE = 185;

    // X.509 certificate factory instance
    private final CertificateFactory factory;

    // cached Collection of X509Certificates (may be empty, never null)
    private Collection<X509Certificate> certs =
        Collections.<X509Certificate>emptySet();

    // cached X509CRL (may be null)
    private X509CRL crl;

    // time we last checked for an update
    private long lastChecked;

    // time server returned as last modified time stamp
    // or 0 if not available
    private long lastModified;

    // the URI of this CertStore
    private URI uri;

    // true if URI is ldap
    private boolean ldap = false;
    private CertStore ldapCertStore;
    private String ldapPath;

    /**
     * Holder class to lazily load LDAPCertStoreHelper if present.
     */
    private static class LDAP {
        private static final String CERT_STORE_HELPER =
            "sun.security.provider.certpath.ldap.LDAPCertStoreHelper";
        private static final CertStoreHelper helper =
            AccessController.doPrivileged(
                new PrivilegedAction<CertStoreHelper>() {
                    public CertStoreHelper run() {
                        try {
                            Class<?> c = Class.forName(CERT_STORE_HELPER, true, null);
                            return (CertStoreHelper)c.newInstance();
                        } catch (ClassNotFoundException cnf) {
                            return null;
                        } catch (InstantiationException e) {
                            throw new AssertionError(e);
                        } catch (IllegalAccessException e) {
                            throw new AssertionError(e);
                        }
                    }});
        static CertStoreHelper helper() {
            return helper;
        }
    }

    /**
     * Creates a URICertStore.
     *
     * @param parameters specifying the URI
     */
    URICertStore(CertStoreParameters params)
        throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        super(params);
        if (!(params instanceof URICertStoreParameters)) {
            throw new InvalidAlgorithmParameterException
                ("params must be instanceof URICertStoreParameters");
        }
        this.uri = ((URICertStoreParameters) params).uri;
        // if ldap URI, use an LDAPCertStore to fetch certs and CRLs
        if (uri.getScheme().toLowerCase(Locale.ENGLISH).equals("ldap")) {
            if (LDAP.helper() == null)
                throw new NoSuchAlgorithmException("LDAP not present");
            ldap = true;
            ldapCertStore = LDAP.helper().getCertStore(uri);
            ldapPath = uri.getPath();
            // strip off leading '/'
            if (ldapPath.charAt(0) == '/') {
                ldapPath = ldapPath.substring(1);
            }
        }
        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException();
        }
    }

    /**
     * Returns a URI CertStore. This method consults a cache of
     * CertStores (shared per JVM) using the URI as a key.
     */
    private static final Cache certStoreCache =
        Cache.newSoftMemoryCache(CACHE_SIZE);
    static synchronized CertStore getInstance(URICertStoreParameters params)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        if (debug != null) {
            debug.println("CertStore URI:" + params.uri);
        }
        CertStore ucs = (CertStore) certStoreCache.get(params);
        if (ucs == null) {
            ucs = new UCS(new URICertStore(params), null, "URI", params);
            certStoreCache.put(params, ucs);
        } else {
            if (debug != null) {
                debug.println("URICertStore.getInstance: cache hit");
            }
        }
        return ucs;
    }

    /**
     * Creates a CertStore from information included in the AccessDescription
     * object of a certificate's Authority Information Access Extension.
     */
    static CertStore getInstance(AccessDescription ad) {
        if (!ad.getAccessMethod().equals(AccessDescription.Ad_CAISSUERS_Id)) {
            return null;
        }
        GeneralNameInterface gn = ad.getAccessLocation().getName();
        if (!(gn instanceof URIName)) {
            return null;
        }
        URI uri = ((URIName) gn).getURI();
        try {
            return URICertStore.getInstance
                (new URICertStore.URICertStoreParameters(uri));
        } catch (Exception ex) {
            if (debug != null) {
                debug.println("exception creating CertStore: " + ex);
                ex.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Returns a <code>Collection</code> of <code>X509Certificate</code>s that
     * match the specified selector. If no <code>X509Certificate</code>s
     * match the selector, an empty <code>Collection</code> will be returned.
     *
     * @param selector a <code>CertSelector</code> used to select which
     *  <code>X509Certificate</code>s should be returned. Specify
     *  <code>null</code> to return all <code>X509Certificate</code>s.
     * @return a <code>Collection</code> of <code>X509Certificate</code>s that
     *         match the specified selector
     * @throws CertStoreException if an exception occurs
     */
    public synchronized Collection<X509Certificate> engineGetCertificates
        (CertSelector selector) throws CertStoreException {

        // if ldap URI we wrap the CertSelector in an LDAPCertSelector to
        // avoid LDAP DN matching issues (see LDAPCertSelector for more info)
        if (ldap) {
            X509CertSelector xsel = (X509CertSelector) selector;
            try {
                xsel = LDAP.helper().wrap(xsel, xsel.getSubject(), ldapPath);
            } catch (IOException ioe) {
                throw new CertStoreException(ioe);
            }
            // Fetch the certificates via LDAP. LDAPCertStore has its own
            // caching mechanism, see the class description for more info.
            return (Collection<X509Certificate>)
                ldapCertStore.getCertificates(xsel);
        }

        // Return the Certificates for this entry. It returns the cached value
        // if it is still current and fetches the Certificates otherwise.
        // For the caching details, see the top of this class.
        long time = System.currentTimeMillis();
        if (time - lastChecked < CHECK_INTERVAL) {
            if (debug != null) {
                debug.println("Returning certificates from cache");
            }
            return getMatchingCerts(certs, selector);
        }
        lastChecked = time;
        InputStream in = null;
        try {
            URLConnection connection = uri.toURL().openConnection();
            if (lastModified != 0) {
                connection.setIfModifiedSince(lastModified);
            }
            in = connection.getInputStream();
            long oldLastModified = lastModified;
            lastModified = connection.getLastModified();
            if (oldLastModified != 0) {
                if (oldLastModified == lastModified) {
                    if (debug != null) {
                        debug.println("Not modified, using cached copy");
                    }
                    return getMatchingCerts(certs, selector);
                } else if (connection instanceof HttpURLConnection) {
                    // some proxy servers omit last modified
                    HttpURLConnection hconn = (HttpURLConnection) connection;
                    if (hconn.getResponseCode()
                                == HttpURLConnection.HTTP_NOT_MODIFIED) {
                        if (debug != null) {
                            debug.println("Not modified, using cached copy");
                        }
                        return getMatchingCerts(certs, selector);
                    }
                }
            }
            if (debug != null) {
                debug.println("Downloading new certificates...");
            }
            certs = (Collection<X509Certificate>)
                factory.generateCertificates(in);
            return getMatchingCerts(certs, selector);
        } catch (IOException e) {
            if (debug != null) {
                debug.println("Exception fetching certificates:");
                e.printStackTrace();
            }
        } catch (CertificateException e) {
            if (debug != null) {
                debug.println("Exception fetching certificates:");
                e.printStackTrace();
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        // exception, forget previous values
        lastModified = 0;
        certs = Collections.<X509Certificate>emptySet();
        return certs;
    }

    /**
     * Iterates over the specified Collection of X509Certificates and
     * returns only those that match the criteria specified in the
     * CertSelector.
     */
    private static Collection<X509Certificate> getMatchingCerts
        (Collection<X509Certificate> certs, CertSelector selector) {
        // if selector not specified, all certs match
        if (selector == null) {
            return certs;
        }
        List<X509Certificate> matchedCerts =
            new ArrayList<X509Certificate>(certs.size());
        for (X509Certificate cert : certs) {
            if (selector.match(cert)) {
                matchedCerts.add(cert);
            }
        }
        return matchedCerts;
    }

    /**
     * Returns a <code>Collection</code> of <code>X509CRL</code>s that
     * match the specified selector. If no <code>X509CRL</code>s
     * match the selector, an empty <code>Collection</code> will be returned.
     *
     * @param selector A <code>CRLSelector</code> used to select which
     *  <code>X509CRL</code>s should be returned. Specify <code>null</code>
     *  to return all <code>X509CRL</code>s.
     * @return A <code>Collection</code> of <code>X509CRL</code>s that
     *         match the specified selector
     * @throws CertStoreException if an exception occurs
     */
    public synchronized Collection<X509CRL> engineGetCRLs(CRLSelector selector)
        throws CertStoreException {

        // if ldap URI we wrap the CRLSelector in an LDAPCRLSelector to
        // avoid LDAP DN matching issues (see LDAPCRLSelector for more info)
        if (ldap) {
            X509CRLSelector xsel = (X509CRLSelector) selector;
            try {
                xsel = LDAP.helper().wrap(xsel, null, ldapPath);
            } catch (IOException ioe) {
                throw new CertStoreException(ioe);
            }
            // Fetch the CRLs via LDAP. LDAPCertStore has its own
            // caching mechanism, see the class description for more info.
            return (Collection<X509CRL>) ldapCertStore.getCRLs(xsel);
        }

        // Return the CRLs for this entry. It returns the cached value
        // if it is still current and fetches the CRLs otherwise.
        // For the caching details, see the top of this class.
        long time = System.currentTimeMillis();
        if (time - lastChecked < CHECK_INTERVAL) {
            if (debug != null) {
                debug.println("Returning CRL from cache");
            }
            return getMatchingCRLs(crl, selector);
        }
        lastChecked = time;
        InputStream in = null;
        try {
            URLConnection connection = uri.toURL().openConnection();
            if (lastModified != 0) {
                connection.setIfModifiedSince(lastModified);
            }
            in = connection.getInputStream();
            long oldLastModified = lastModified;
            lastModified = connection.getLastModified();
            if (oldLastModified != 0) {
                if (oldLastModified == lastModified) {
                    if (debug != null) {
                        debug.println("Not modified, using cached copy");
                    }
                    return getMatchingCRLs(crl, selector);
                } else if (connection instanceof HttpURLConnection) {
                    // some proxy servers omit last modified
                    HttpURLConnection hconn = (HttpURLConnection) connection;
                    if (hconn.getResponseCode()
                                == HttpURLConnection.HTTP_NOT_MODIFIED) {
                        if (debug != null) {
                            debug.println("Not modified, using cached copy");
                        }
                        return getMatchingCRLs(crl, selector);
                    }
                }
            }
            if (debug != null) {
                debug.println("Downloading new CRL...");
            }
            crl = (X509CRL) factory.generateCRL(in);
            return getMatchingCRLs(crl, selector);
        } catch (IOException e) {
            if (debug != null) {
                debug.println("Exception fetching CRL:");
                e.printStackTrace();
            }
        } catch (CRLException e) {
            if (debug != null) {
                debug.println("Exception fetching CRL:");
                e.printStackTrace();
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        // exception, forget previous values
        lastModified = 0;
        crl = null;
        return Collections.<X509CRL>emptyList();
    }

    /**
     * Checks if the specified X509CRL matches the criteria specified in the
     * CRLSelector.
     */
    private static Collection<X509CRL> getMatchingCRLs
        (X509CRL crl, CRLSelector selector) {
        if (selector == null || (crl != null && selector.match(crl))) {
            return Collections.<X509CRL>singletonList(crl);
        } else {
            return Collections.<X509CRL>emptyList();
        }
    }

    /**
     * CertStoreParameters for the URICertStore.
     */
    static class URICertStoreParameters implements CertStoreParameters {
        private final URI uri;
        private volatile int hashCode = 0;
        URICertStoreParameters(URI uri) {
            this.uri = uri;
        }
        public boolean equals(Object obj) {
            if (!(obj instanceof URICertStoreParameters)) {
                return false;
            }
            URICertStoreParameters params = (URICertStoreParameters) obj;
            return uri.equals(params.uri);
        }
        public int hashCode() {
            if (hashCode == 0) {
                int result = 17;
                result = 37*result + uri.hashCode();
                hashCode = result;
            }
            return hashCode;
        }
        public Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                /* Cannot happen */
                throw new InternalError(e.toString());
            }
        }
    }

    /**
     * This class allows the URICertStore to be accessed as a CertStore.
     */
    private static class UCS extends CertStore {
        protected UCS(CertStoreSpi spi, Provider p, String type,
            CertStoreParameters params) {
            super(spi, p, type, params);
        }
    }
}
