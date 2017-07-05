/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.security.*;
import java.security.cert.*;

import javax.net.ssl.*;

import com.sun.net.ssl.internal.ssl.X509ExtendedTrustManager;

import sun.security.validator.*;

import sun.security.util.HostnameChecker;

/**
 * This class implements the SunJSSE X.509 trust manager using the internal
 * validator API in J2SE core. The logic in this class is minimal.<p>
 *
 * This class supports both the Simple validation algorithm from previous
 * JSSE versions and PKIX validation. Currently, it is not possible for the
 * application to specify PKIX parameters other than trust anchors. This will
 * be fixed in a future release using new APIs. When that happens, it may also
 * make sense to separate the Simple and PKIX trust managers into separate
 * classes.
 *
 * @author Andreas Sterbenz
 * @author Xuelei Fan
 */
final class X509TrustManagerImpl extends X509ExtendedTrustManager
        implements X509TrustManager {

    /**
     * Flag indicating whether to enable revocation check for the PKIX trust
     * manager. Typically, this will only work if the PKIX implementation
     * supports CRL distribution points as we do not manually setup CertStores.
     */
    private final static boolean checkRevocation =
        Debug.getBooleanProperty("com.sun.net.ssl.checkRevocation", false);

    private final String validatorType;

    /**
     * The Set of trusted X509Certificates.
     */
    private final Collection<X509Certificate> trustedCerts;

    private final PKIXBuilderParameters pkixParams;

    // note that we need separate validator for client and server due to
    // the different extension checks. They are initialized lazily on demand.
    private volatile Validator clientValidator, serverValidator;

    private static final Debug debug = Debug.getInstance("ssl");

    X509TrustManagerImpl(String validatorType, KeyStore ks)
            throws KeyStoreException {
        this.validatorType = validatorType;
        this.pkixParams = null;
        if (ks == null) {
            trustedCerts = Collections.<X509Certificate>emptySet();
        } else {
            trustedCerts = KeyStores.getTrustedCerts(ks);
        }
        showTrustedCerts();
    }

    X509TrustManagerImpl(String validatorType, PKIXBuilderParameters params) {
        this.validatorType = validatorType;
        this.pkixParams = params;
        // create server validator eagerly so that we can conveniently
        // get the trusted certificates
        // clients need it anyway eventually, and servers will not mind
        // the little extra footprint
        Validator v = getValidator(Validator.VAR_TLS_SERVER);
        trustedCerts = v.getTrustedCertificates();
        serverValidator = v;
        showTrustedCerts();
    }

    private void showTrustedCerts() {
        if (debug != null && Debug.isOn("trustmanager")) {
            for (X509Certificate cert : trustedCerts) {
                System.out.println("adding as trusted cert:");
                System.out.println("  Subject: "
                                        + cert.getSubjectX500Principal());
                System.out.println("  Issuer:  "
                                        + cert.getIssuerX500Principal());
                System.out.println("  Algorithm: "
                                        + cert.getPublicKey().getAlgorithm()
                                        + "; Serial number: 0x"
                                        + cert.getSerialNumber().toString(16));
                System.out.println("  Valid from "
                                        + cert.getNotBefore() + " until "
                                        + cert.getNotAfter());
                System.out.println();
            }
        }
    }

    private Validator getValidator(String variant) {
        Validator v;
        if (pkixParams == null) {
            v = Validator.getInstance(validatorType, variant, trustedCerts);
            // if the PKIX validator is created from a KeyStore,
            // disable revocation checking
            if (v instanceof PKIXValidator) {
                PKIXValidator pkixValidator = (PKIXValidator)v;
                pkixValidator.getParameters().setRevocationEnabled
                                                            (checkRevocation);
            }
        } else {
            v = Validator.getInstance(validatorType, variant, pkixParams);
        }
        return v;
    }

    private static X509Certificate[] validate(Validator v,
            X509Certificate[] chain, String authType) throws CertificateException {
        Object o = JsseJce.beginFipsProvider();
        try {
            return v.validate(chain, null, authType);
        } finally {
            JsseJce.endFipsProvider(o);
        }
    }

    /**
     * Returns true if the client certificate can be trusted.
     *
     * @param chain certificates which establish an identity for the client.
     *      Chains of arbitrary length are supported, and certificates
     *      marked internally as trusted will short-circuit signature checks.
     * @throws IllegalArgumentException if null or zero-length chain
     *         is passed in for the chain parameter or if null or zero-length
     *         string is passed in for the authType parameter.
     * @throws CertificateException if the certificate chain is not trusted
     *      by this TrustManager.
     */
    public void checkClientTrusted(X509Certificate chain[], String authType)
            throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new IllegalArgumentException(
                "null or zero-length certificate chain");
        }
        if (authType == null || authType.length() == 0) {
            throw new IllegalArgumentException(
                "null or zero-length authentication type");
        }

        // assume double checked locking with a volatile flag works
        // (guaranteed under the new Tiger memory model)
        Validator v = clientValidator;
        if (v == null) {
            synchronized (this) {
                v = clientValidator;
                if (v == null) {
                    v = getValidator(Validator.VAR_TLS_CLIENT);
                    clientValidator = v;
                }
            }
        }
        X509Certificate[] trustedChain = validate(v, chain, null);
        if (debug != null && Debug.isOn("trustmanager")) {
            System.out.println("Found trusted certificate:");
            System.out.println(trustedChain[trustedChain.length - 1]);
        }
    }

    /**
     * Returns true if the server certifcate can be trusted.
     *
     * @param chain certificates which establish an identity for the server.
     *      Chains of arbitrary length are supported, and certificates
     *      marked internally as trusted will short-circuit signature checks.
     * @throws IllegalArgumentException if null or zero-length chain
     *         is passed in for the chain parameter or if null or zero-length
     *         string is passed in for the authType parameter.
     * @throws CertificateException if the certificate chain is not trusted
     *      by this TrustManager.
     */
    public void checkServerTrusted(X509Certificate chain[], String authType)
            throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new IllegalArgumentException(
                "null or zero-length certificate chain");
        }
        if (authType == null || authType.length() == 0) {
            throw new IllegalArgumentException(
                "null or zero-length authentication type");
        }

        // assume double checked locking with a volatile flag works
        // (guaranteed under the new Tiger memory model)
        Validator v = serverValidator;
        if (v == null) {
            synchronized (this) {
                v = serverValidator;
                if (v == null) {
                    v = getValidator(Validator.VAR_TLS_SERVER);
                    serverValidator = v;
                }
            }
        }
        X509Certificate[] trustedChain = validate(v, chain, authType);
        if (debug != null && Debug.isOn("trustmanager")) {
            System.out.println("Found trusted certificate:");
            System.out.println(trustedChain[trustedChain.length - 1]);
        }
    }

    /**
     * Returns a list of CAs accepted to authenticate entities for the
     * specified purpose.
     *
     * @param purpose activity for which CAs should be trusted
     * @return list of CAs accepted for authenticating such tasks
     */
    public X509Certificate[] getAcceptedIssuers() {
        X509Certificate[] certsArray = new X509Certificate[trustedCerts.size()];
        trustedCerts.toArray(certsArray);
        return certsArray;
    }

    /**
     * Given the partial or complete certificate chain provided by the
     * peer, check its identity and build a certificate path to a trusted
     * root, return if it can be validated and is trusted for client SSL
     * authentication based on the authentication type.
     */
    public void checkClientTrusted(X509Certificate[] chain, String authType,
        String hostname, String algorithm) throws CertificateException {
        checkClientTrusted(chain, authType);
        checkIdentity(hostname, chain[0], algorithm);
    }

    /**
     * Given the partial or complete certificate chain provided by the
     * peer, check its identity and build a certificate path to a trusted
     * root, return if it can be validated and is trusted for server SSL
     * authentication based on the authentication type.
     */
    public void checkServerTrusted(X509Certificate[] chain, String authType,
        String hostname, String algorithm) throws CertificateException {
        checkServerTrusted(chain, authType);
        checkIdentity(hostname, chain[0], algorithm);
    }

    // Identify the peer by its certificate and hostname.
    private void checkIdentity(String hostname, X509Certificate cert,
        String algorithm) throws CertificateException {
        if (algorithm != null && algorithm.length() != 0) {
            // if IPv6 strip off the "[]"
            if (hostname != null && hostname.startsWith("[") &&
                hostname.endsWith("]")) {
                hostname = hostname.substring(1, hostname.length()-1);
            }

            if (algorithm.equalsIgnoreCase("HTTPS")) {
                HostnameChecker.getInstance(HostnameChecker.TYPE_TLS).match(
                        hostname, cert);
            } else if (algorithm.equalsIgnoreCase("LDAP")) {
                HostnameChecker.getInstance(HostnameChecker.TYPE_LDAP).match(
                        hostname, cert);
            } else {
                throw new CertificateException(
                        "Unknown identification algorithm: " + algorithm);
            }
        }
    }
}
