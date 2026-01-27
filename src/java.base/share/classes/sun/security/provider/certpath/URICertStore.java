/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URISyntaxException;
import java.net.URLConnection;
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
import java.security.cert.URICertStoreParameters;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import sun.security.x509.AccessDescription;
import sun.security.x509.GeneralNameInterface;
import sun.security.x509.URIName;
import sun.security.util.Cache;
import sun.security.util.Debug;
import sun.security.util.SecurityProperties;

import javax.security.auth.x500.X500Principal;

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
 * @since 1.7
 */
class URICertStore extends CertStoreSpi {

    private static final Debug debug = Debug.getInstance("certpath");

    // interval between checks for update of cached Certificates/CRLs
    // (30 seconds)
    private static final int CHECK_INTERVAL = 30 * 1000;

    // size of the cache (see Cache class for sizing recommendations)
    private static final int CACHE_SIZE = 185;

    // X.509 certificate factory instance
    private final CertificateFactory factory;

    // cached Collection of X509Certificates (may be empty, never null)
    private Collection<X509Certificate> certs = Collections.emptySet();

    // cached X509CRL (may be null)
    private X509CRL crl;

    // time we last checked for an update
    private long lastChecked;

    // time server returned as last modified time stamp
    // or 0 if not available
    private long lastModified;

    // the URI of this CertStore
    private final URI uri;

    // true if URI is ldap
    private boolean ldap = false;
    private CertStore ldapCertStore;

    // Default maximum connect timeout in milliseconds (15 seconds)
    // allowed when downloading CRLs
    private static final int DEFAULT_CRL_CONNECT_TIMEOUT = 15000;

    // Default maximum read timeout in milliseconds (15 seconds)
    // allowed when downloading CRLs
    private static final int DEFAULT_CRL_READ_TIMEOUT = 15000;

    // Default connect and read timeouts for CA certificate fetching (15 sec)
    private static final int DEFAULT_CACERT_CONNECT_TIMEOUT = 15000;
    private static final int DEFAULT_CACERT_READ_TIMEOUT = 15000;

    /**
     * Integer value indicating the connect timeout, in milliseconds, to be
     * used for the CRL download. A timeout of zero is interpreted as
     * an infinite timeout.
     */
    private static final int CRL_CONNECT_TIMEOUT =
        initializeTimeout("com.sun.security.crl.timeout",
                          DEFAULT_CRL_CONNECT_TIMEOUT);

    /**
     * Integer value indicating the read timeout, in milliseconds, to be
     * used for the CRL download. A timeout of zero is interpreted as
     * an infinite timeout.
     */
    private static final int CRL_READ_TIMEOUT =
        initializeTimeout("com.sun.security.crl.readtimeout",
                          DEFAULT_CRL_READ_TIMEOUT);

    /**
     * Integer value indicating the connect timeout, in milliseconds, to be
     * used for the CA certificate download. A timeout of zero is interpreted
     * as an infinite timeout.
     */
    private static final int CACERT_CONNECT_TIMEOUT =
            initializeTimeout("com.sun.security.cert.timeout",
                    DEFAULT_CACERT_CONNECT_TIMEOUT);

    /**
     * Integer value indicating the read timeout, in milliseconds, to be
     * used for the CA certificate download. A timeout of zero is interpreted
     * as an infinite timeout.
     */
    private static final int CACERT_READ_TIMEOUT =
            initializeTimeout("com.sun.security.cert.readtimeout",
                    DEFAULT_CACERT_READ_TIMEOUT);

    /**
     * Initialize the timeout length by getting the specified CRL timeout
     * system property. If the property has not been set, or if its
     * value is negative, set the timeout length to the specified default.
     */
    private static int initializeTimeout(String prop, int def) {
        int timeoutVal =
                SecurityProperties.getTimeoutSystemProp(prop, def, debug);
        if (debug != null) {
            debug.println(prop + " set to " + timeoutVal + " milliseconds");
        }
        return timeoutVal;
    }

    /**
     * Enumeration for the allowed schemes we support when following a
     * URI from an authorityInfoAccess extension on a certificate.
     */
    private enum AllowedScheme {
        HTTP(HttpFtpRuleMatcher.HTTP),
        HTTPS(HttpFtpRuleMatcher.HTTPS),
        LDAP(LdapRuleMatcher.LDAP),
        LDAPS(LdapRuleMatcher.LDAPS),
        FTP(HttpFtpRuleMatcher.FTP);

        final URIRuleMatcher ruleMatcher;

        AllowedScheme(URIRuleMatcher matcher) {
            ruleMatcher = matcher;
        }

        /**
         * Return an {@code AllowedScheme} based on a case-insensitive match
         * @param name the scheme name to be matched
         * @return the {@code AllowedScheme} that corresponds to the
         *      {@code name} provided, or null if there is no match.
         */
        static AllowedScheme nameOf(String name) {
            if (name == null) {
                return null;
            }

            try {
                return AllowedScheme.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException _) {
                return null;
            }
        }
    }

    private static Set<URI> CA_ISS_URI_FILTERS = null;
    private static final boolean CA_ISS_ALLOW_ANY;

    static {
        boolean allowAny = false;
        try {
            if (Builder.USE_AIA) {
                CA_ISS_URI_FILTERS = new LinkedHashSet<>();
                String aiaPropVal = Optional.ofNullable(
                        SecurityProperties.getOverridableProperty(
                                "com.sun.security.allowedAIALocations")).
                        map(String::trim).orElse("");
                if (aiaPropVal.equalsIgnoreCase("any")) {
                    allowAny = true;
                    if (debug != null) {
                        debug.println("allowedAIALocations: Warning: " +
                                "Allow-All URI filtering enabled!");
                    }
                } else {
                    // Load all the valid rules from the Security property
                    if (!aiaPropVal.isEmpty()) {
                        String[] aiaUriStrs = aiaPropVal.trim().split("\\s+");
                        addCaIssUriFilters(aiaUriStrs);
                    }

                    if (CA_ISS_URI_FILTERS.isEmpty()) {
                        if (debug != null) {
                            debug.println("allowedAIALocations: Warning: " +
                                    "No valid filters found. Deny-all URI " +
                                    "filtering is active.");
                        }
                    }
                }
            }
        } finally {
            CA_ISS_ALLOW_ANY = allowAny;
        }
    }

    /**
     * Populate the filter collection from the list of AIA CA issuer URIs
     * found in the {@code com.sun.security.allowedAIALocations} security
     * or system property.
     *
     * @param aiaUriStrs array containing String URI filters
     */
    private static void addCaIssUriFilters(String[] aiaUriStrs) {
        for (String aiaStr : aiaUriStrs) {
            if (aiaStr != null && !aiaStr.isEmpty()) {
                try {
                    AllowedScheme scheme;
                    URI aiaUri = new URI(aiaStr).normalize();
                    // It must be absolute and non-opaque
                    if (!aiaUri.isAbsolute() || aiaUri.isOpaque()) {
                        if (debug != null) {
                            debug.println("allowedAIALocations: Skipping " +
                                    "non-absolute or opaque URI " + aiaUri);
                        }
                    } else if (aiaUri.getHost() == null) {
                        // We do not allow rules with URIs that omit a hostname
                        // or address.
                        if (debug != null) {
                            debug.println("allowedAIALocations: Skipping " +
                                    "URI rule with no hostname or address: " +
                                    aiaUri);
                        }
                    } else if ((scheme = AllowedScheme.nameOf(
                            aiaUri.getScheme())) != null) {
                        // When it is an LDAP type, we can check the path
                        // portion (the DN) for proper structure and reject
                        // the rule early if it isn't correct.
                        if (scheme == AllowedScheme.LDAP ||
                                scheme == AllowedScheme.LDAPS) {
                            try {
                                new X500Principal(aiaUri.getPath().
                                        replaceFirst("^/+", ""));
                            } catch (IllegalArgumentException iae) {
                                if (debug != null) {
                                    debug.println("allowedAIALocations: " +
                                            "Skipping LDAP rule: " + iae);
                                }
                                continue;
                            }
                        }

                        // When a URI has a non-null query or fragment
                        // warn the user upon adding the rule that those
                        // components will be ignored
                        if (aiaUri.getQuery() != null) {
                            if (debug != null) {
                                debug.println("allowedAIALocations: " +
                                        "Rule will ignore non-null query");
                            }
                        }
                        if (aiaUri.getFragment() != null) {
                            if (debug != null) {
                                debug.println("allowedAIALocations: " +
                                        "Rule will ignore non-null fragment");
                            }
                        }

                        CA_ISS_URI_FILTERS.add(aiaUri);
                        if (debug != null) {
                            debug.println("allowedAIALocations: Added " +
                                    aiaUri + " to URI filters");
                        }
                    } else {
                        if (debug != null) {
                            debug.println("allowedAIALocations: Disallowed " +
                                    "filter URI scheme: " +
                                    aiaUri.getScheme());
                        }
                    }
                } catch (URISyntaxException urise) {
                    if (debug != null) {
                        debug.println("allowedAIALocations: Skipping " +
                                "filter URI entry " + aiaStr +
                                ": parse failure at index " + urise.getIndex());
                    }
                }
            }
        }
    }

    /**
     * Creates a URICertStore.
     *
     * @param params specifying the URI
     */
    URICertStore(CertStoreParameters params)
        throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        super(params);
        if (!(params instanceof URICertStoreParameters)) {
            throw new InvalidAlgorithmParameterException
                ("params must be instanceof URICertStoreParameters");
        }
        this.uri = ((URICertStoreParameters) params).getURI();
        // if ldap URI, use an LDAPCertStore to fetch certs and CRLs
        if (uri.getScheme().toLowerCase(Locale.ENGLISH).equals("ldap")) {
            ldap = true;
            ldapCertStore = CertStore.getInstance("LDAP", params);
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
    private static final Cache<URICertStoreParameters, CertStore>
        certStoreCache = Cache.newSoftMemoryCache(CACHE_SIZE);
    static synchronized CertStore getInstance(URICertStoreParameters params)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        if (debug != null) {
            debug.println("CertStore URI:" + params.getURI());
        }
        CertStore ucs = certStoreCache.get(params);
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
        if (!ad.getAccessMethod().equals(
                AccessDescription.Ad_CAISSUERS_Id)) {
            return null;
        }
        GeneralNameInterface gn = ad.getAccessLocation().getName();
        if (!(gn instanceof URIName)) {
            return null;
        }
        URI uri = ((URIName) gn).getURI();

        // Before performing any instantiation make sure that
        // the URI passes any filtering rules.  This processing should
        // only occur if the com.sun.security.enableAIAcaIssuers is true
        // and the "any" rule has not been specified.
        if (Builder.USE_AIA && !CA_ISS_ALLOW_ANY) {
            URI normAIAUri = uri.normalize();
            AllowedScheme scheme = AllowedScheme.nameOf(normAIAUri.getScheme());

            if (scheme == null) {
                if (debug != null) {
                    debug.println("allowedAIALocations: No matching ruleset " +
                            "for scheme " + normAIAUri.getScheme());
                }
                return null;
            }

            // Go through each of the filter rules and see if any will
            // make a positive match against the caIssuer URI.  If nothing
            // matches then we won't instantiate a URICertStore.
            if (CA_ISS_URI_FILTERS.stream().noneMatch(rule ->
                    scheme.ruleMatcher.matchRule(rule, normAIAUri))) {
                if (debug != null) {
                    debug.println("allowedAIALocations: Warning - " +
                        "The caIssuer URI " + normAIAUri +
                        " in the AuthorityInfoAccess extension is denied " +
                        "access. Use the com.sun.security.allowedAIALocations" +
                        " security/system property to allow access.");
                }
                return null;
            }
        }

        try {
            return URICertStore.getInstance(new URICertStoreParameters(uri));
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
    @Override
    @SuppressWarnings("unchecked")
    public synchronized Collection<X509Certificate> engineGetCertificates
            (CertSelector selector) throws CertStoreException {

        if (ldap) {
            // caching mechanism, see the class description for more info.
            return (Collection<X509Certificate>)
                ldapCertStore.getCertificates(selector);
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
        try {
            URLConnection connection = uri.toURL().openConnection();
            if (lastModified != 0) {
                connection.setIfModifiedSince(lastModified);
            }
            long oldLastModified = lastModified;
            connection.setConnectTimeout(CACERT_CONNECT_TIMEOUT);
            connection.setReadTimeout(CACERT_READ_TIMEOUT);
            try (InputStream in = connection.getInputStream()) {
                lastModified = connection.getLastModified();
                if (oldLastModified != 0) {
                    if (oldLastModified == lastModified) {
                        if (debug != null) {
                            debug.println("Not modified, using cached copy");
                        }
                        return getMatchingCerts(certs, selector);
                    } else if (connection instanceof HttpURLConnection hconn) {
                        // some proxy servers omit last modified
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
                // Safe cast since factory is an X.509 certificate factory
                certs = (Collection<X509Certificate>)
                    factory.generateCertificates(in);
            }
            return getMatchingCerts(certs, selector);
        } catch (IOException | CertificateException e) {
            if (debug != null) {
                debug.println("Exception fetching certificates:");
                e.printStackTrace();
            }
        }
        // exception, forget previous values
        lastModified = 0;
        certs = Collections.emptySet();
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
        List<X509Certificate> matchedCerts = new ArrayList<>(certs.size());
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
    @Override
    @SuppressWarnings("unchecked")
    public synchronized Collection<X509CRL> engineGetCRLs(CRLSelector selector)
        throws CertStoreException {

        if (ldap) {
            // Fetch the CRLs via LDAP. LDAPCertStore has its own
            // caching mechanism, see the class description for more info.
            try {
                return (Collection<X509CRL>) ldapCertStore.getCRLs(selector);
            } catch (CertStoreException cse) {
                throw new PKIX.CertStoreTypeException("LDAP", cse);
            }
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
        try {
            URLConnection connection = uri.toURL().openConnection();
            if (lastModified != 0) {
                connection.setIfModifiedSince(lastModified);
            }
            long oldLastModified = lastModified;
            connection.setConnectTimeout(CRL_CONNECT_TIMEOUT);
            connection.setReadTimeout(CRL_READ_TIMEOUT);
            try (InputStream in = connection.getInputStream()) {
                lastModified = connection.getLastModified();
                if (oldLastModified != 0) {
                    if (oldLastModified == lastModified) {
                        if (debug != null) {
                            debug.println("Not modified, using cached copy");
                        }
                        return getMatchingCRLs(crl, selector);
                    } else if (connection instanceof HttpURLConnection hconn) {
                        // some proxy servers omit last modified
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
            }
            return getMatchingCRLs(crl, selector);
        } catch (IOException | CRLException e) {
            if (debug != null) {
                debug.println("Exception fetching CRL:");
                e.printStackTrace();
            }
            // exception, forget previous values
            lastModified = 0;
            crl = null;
            throw new PKIX.CertStoreTypeException("URI",
                                                  new CertStoreException(e));
        }
    }

    /**
     * Checks if the specified X509CRL matches the criteria specified in the
     * CRLSelector.
     */
    private static Collection<X509CRL> getMatchingCRLs
        (X509CRL crl, CRLSelector selector) {
        if (selector == null || (crl != null && selector.match(crl))) {
            return Collections.singletonList(crl);
        } else {
            return Collections.emptyList();
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

    /**
     * URIRuleMatcher - abstract base class for the rule sets used for
     * various URI schemes.
     */
    static abstract class URIRuleMatcher {
        protected final int wellKnownPort;

        protected URIRuleMatcher(int port) {
            wellKnownPort = port;
        }

        /**
         * Attempt to match the scheme, host and port between a filter
         * rule URI and a URI coming from an AIA extension.
         *
         * @param filterRule the filter rule to match against
         * @param caIssuer the AIA URI being compared
         * @return true if the scheme, host and port numbers match, false if
         * any of the components do not match. If a port number is omitted in
         * either the filter rule or AIA URI, the well-known port for that
         * scheme is used in the comparison.
         */
        boolean schemeHostPortCheck(URI filterRule, URI caIssuer) {
            if (!filterRule.getScheme().equalsIgnoreCase(
                    caIssuer.getScheme())) {
                return false;
            } else if (!filterRule.getHost().equalsIgnoreCase(
                    caIssuer.getHost())) {
                return false;
            } else {
                try {
                    // Check for port matching, taking into consideration
                    // default ports
                    int fPort = (filterRule.getPort() == -1) ? wellKnownPort :
                            filterRule.getPort();
                    int caiPort = (caIssuer.getPort() == -1) ? wellKnownPort :
                            caIssuer.getPort();
                    if (fPort != caiPort) {
                        return false;
                    }
                } catch (IllegalArgumentException iae) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Attempt to match an AIA URI against a specific filter rule.  The
         * specific rules to apply are implementation dependent.
         *
         * @param filterRule the filter rule to match against
         * @param caIssuer the AIA URI being compared
         * @return true if all matching rules pass, false if any fail.
         */
        abstract boolean matchRule(URI filterRule, URI caIssuer);
    }

    static class HttpFtpRuleMatcher extends URIRuleMatcher {
        static final HttpFtpRuleMatcher HTTP = new HttpFtpRuleMatcher(80);
        static final HttpFtpRuleMatcher HTTPS = new HttpFtpRuleMatcher(443);
        static final HttpFtpRuleMatcher FTP = new HttpFtpRuleMatcher(21);

        private HttpFtpRuleMatcher(int port) {
            super(port);
        }

        @Override
        boolean matchRule(URI filterRule, URI caIssuer) {
            // Check for scheme/host/port matching
            if (!schemeHostPortCheck(filterRule, caIssuer)) {
                return false;
            }

            // Check the path component to make sure the filter is at
            // least a root of the AIA caIssuer URI's path.  It must be
            // a case-sensitive match for all platforms.
            if (!isRootOf(filterRule, caIssuer)) {
                if (debug != null) {
                    debug.println("allowedAIALocations: Match failed: " +
                            "AIA URI is not within the rule's path hierarchy.");
                }
                return false;
            }
            return true;
        }

        /**
         * Performs a hierarchical containment check, ensuring that the
         * base URI's path is a root component of the candidate path.  The
         * path comparison is case-sensitive.  If the base path ends in a
         * slash (/) then all candidate paths that begin with the base
         * path are allowed.  If it does not end in a slash, then it is
         * assumed that the leaf node in the base path is a file component
         * and both paths must match exactly.
         *
         * @param base the URI that contains the root path
         * @param candidate the URI that contains the path being evaluated
         * @return true if {@code candidate} is a child path of {@code base},
         *         false otherwise.
         */
        private static boolean isRootOf(URI base, URI candidate) {
            // Note: The URIs have already been normalized at this point and
            // HTTP URIs cannot have null paths.  If it's an empty path
            // then consider the path to be "/".
            String basePath = Optional.of(base.getPath()).
                    filter(p -> !p.isEmpty()).orElse("/");
            String candPath = Optional.of(candidate.getPath()).
                    filter(p -> !p.isEmpty()).orElse("/");
            return (basePath.endsWith("/")) ? candPath.startsWith(basePath) :
                    candPath.equals(basePath);
        }
    }

    static class LdapRuleMatcher extends URIRuleMatcher {
        static final LdapRuleMatcher LDAP = new LdapRuleMatcher(389);
        static final LdapRuleMatcher LDAPS = new LdapRuleMatcher(636);

        private LdapRuleMatcher(int port) {
            super(port);
        }

        @Override
        boolean matchRule(URI filterRule, URI caIssuer) {
            // Check for scheme/host/port matching
            if (!schemeHostPortCheck(filterRule, caIssuer)) {
                return false;
            }

            // Obtain the base DN component and compare
            try {
                X500Principal filterBaseDn = new X500Principal(
                        filterRule.getPath().replaceFirst("^/+", ""));
                X500Principal caIssBaseDn = new X500Principal(
                        caIssuer.getPath().replaceFirst("^/+", ""));
                if (!filterBaseDn.equals(caIssBaseDn)) {
                    if (debug != null) {
                        debug.println("allowedAIALocations: Match failed: " +
                                "Base DN mismatch (" + filterBaseDn + " vs " +
                                caIssBaseDn + ")");
                    }
                    return false;
                }
            } catch (IllegalArgumentException iae) {
                if (debug != null) {
                    debug.println("allowedAIALocations: Match failed on DN: " +
                            iae);
                }
                return false;
            }

            return true;
        }
    }
}
