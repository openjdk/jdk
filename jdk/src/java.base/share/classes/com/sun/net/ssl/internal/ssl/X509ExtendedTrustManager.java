/*
 * Copyright (c) 2005, 2007, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.net.ssl.internal.ssl;

import javax.net.ssl.X509TrustManager;

import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;

/**
 * Instance of this class is an extension of <code>X509TrustManager</code>.
 * <p>
 * Note that this class is referenced by the Deploy workspace. Any updates
 * must make sure that they do not cause any breakage there.
 * <p>
 * It takes the responsiblity of checking the peer identity with its
 * principal declared in the cerificate.
 * <p>
 * The class provides an alternative to <code>HostnameVerifer</code>.
 * If application customizes its <code>HostnameVerifer</code> for
 * <code>HttpsURLConnection</code>, the peer identity will be checked
 * by the customized <code>HostnameVerifer</code>; otherwise, it will
 * be checked by the extended trust manager.
 * <p>
 * RFC2830 defines the server identification specification for "LDAP"
 * algorithm. RFC2818 defines both the server identification and the
 * client identification specification for "HTTPS" algorithm.
 *
 * @see X509TrustManager
 * @see HostnameVerifier
 *
 * @since 1.6
 * @author Xuelei Fan
 */
public abstract class X509ExtendedTrustManager implements X509TrustManager {
    /**
     * Constructor used by subclasses only.
     */
    protected X509ExtendedTrustManager() {
    }

    /**
     * Given the partial or complete certificate chain provided by the
     * peer, check its identity and build a certificate path to a trusted
     * root, return if it can be validated and is trusted for client SSL
     * authentication based on the authentication type.
     * <p>
     * The authentication type is determined by the actual certificate
     * used. For instance, if RSAPublicKey is used, the authType
     * should be "RSA". Checking is case-sensitive.
     * <p>
     * The algorithm parameter specifies the client identification protocol
     * to use. If the algorithm and the peer hostname are available, the
     * peer hostname is checked against the peer's identity presented in
     * the X509 certificate, in order to prevent masquerade attacks.
     *
     * @param chain the peer certificate chain
     * @param authType the authentication type based on the client certificate
     * @param hostname the peer hostname
     * @param algorithm the identification algorithm
     * @throws IllegalArgumentException if null or zero-length chain
     *         is passed in for the chain parameter or if null or zero-length
     *         string is passed in for the  authType parameter
     * @throws CertificateException if the certificate chain is not trusted
     *         by this TrustManager.
     */
    public abstract void checkClientTrusted(X509Certificate[] chain,
        String authType, String hostname, String algorithm)
        throws CertificateException;

    /**
     * Given the partial or complete certificate chain provided by the
     * peer, check its identity and build a certificate path to a trusted
     * root, return if it can be validated and is trusted for server SSL
     * authentication based on the authentication type.
     * <p>
     * The authentication type is the key exchange algorithm portion
     * of the cipher suites represented as a String, such as "RSA",
     * "DHE_DSS". Checking is case-sensitive.
     * <p>
     * The algorithm parameter specifies the server identification protocol
     * to use. If the algorithm and the peer hostname are available, the
     * peer hostname is checked against the peer's identity presented in
     * the X509 certificate, in order to prevent masquerade attacks.
     *
     * @param chain the peer certificate chain
     * @param authType the key exchange algorithm used
     * @param hostname the peer hostname
     * @param algorithm the identification algorithm
     * @throws IllegalArgumentException if null or zero-length chain
     *         is passed in for the chain parameter or if null or zero-length
     *         string is passed in for the  authType parameter
     * @throws CertificateException if the certificate chain is not trusted
     *         by this TrustManager.
     */
    public abstract void checkServerTrusted(X509Certificate[] chain,
        String authType, String hostname, String algorithm)
        throws CertificateException;
}
