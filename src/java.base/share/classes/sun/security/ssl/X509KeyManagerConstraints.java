/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.net.Socket;
import java.security.AlgorithmConstraints;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.StandardConstants;
import javax.net.ssl.X509ExtendedKeyManager;
import sun.security.provider.certpath.AlgorithmChecker;
import sun.security.util.KnownOIDs;
import sun.security.validator.Validator;

abstract class X509KeyManagerConstraints extends X509ExtendedKeyManager {

    // Indicates whether we should skip the constraints check.
    private final boolean constraintsDisabled;

    protected X509KeyManagerConstraints() {
        constraintsDisabled = isConstraintsDisabled();
    }

    // Gets algorithm constraints of the socket.
    protected AlgorithmConstraints getAlgorithmConstraints(Socket socket) {

        if (constraintsDisabled) {
            return null;
        }

        if (socket != null && socket.isConnected() &&
                socket instanceof SSLSocket sslSocket) {

            SSLSession session = sslSocket.getHandshakeSession();

            if (session != null) {
                if (ProtocolVersion.useTLS12PlusSpec(session.getProtocol())) {
                    String[] peerSupportedSignAlgs = null;

                    if (session instanceof ExtendedSSLSession extSession) {
                        peerSupportedSignAlgs =
                                extSession.getPeerSupportedSignatureAlgorithms();
                    }

                    return SSLAlgorithmConstraints.forSocket(
                            sslSocket, peerSupportedSignAlgs, true);
                }
            }

            return SSLAlgorithmConstraints.forSocket(sslSocket, true);
        }

        return SSLAlgorithmConstraints.DEFAULT;
    }

    // Gets algorithm constraints of the engine.
    protected AlgorithmConstraints getAlgorithmConstraints(SSLEngine engine) {

        if (constraintsDisabled) {
            return null;
        }

        if (engine != null) {
            SSLSession session = engine.getHandshakeSession();
            if (session != null) {
                if (ProtocolVersion.useTLS12PlusSpec(session.getProtocol())) {
                    String[] peerSupportedSignAlgs = null;

                    if (session instanceof ExtendedSSLSession extSession) {
                        peerSupportedSignAlgs =
                                extSession.getPeerSupportedSignatureAlgorithms();
                    }

                    return SSLAlgorithmConstraints.forEngine(
                            engine, peerSupportedSignAlgs, true);
                }
            }
        }

        return SSLAlgorithmConstraints.forEngine(engine, true);
    }

    protected boolean conformsToAlgorithmConstraints(
            AlgorithmConstraints constraints, Certificate[] chain,
            String variant) {

        if (constraintsDisabled) {
            return true;
        }

        AlgorithmChecker checker = new AlgorithmChecker(constraints, variant);
        try {
            checker.init(false);
        } catch (CertPathValidatorException cpve) {
            // unlikely to happen
            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine(
                        "Cannot initialize algorithm constraints checker",
                        cpve);
            }

            return false;
        }

        // It is a forward checker, so we need to check from trust to target.
        for (int i = chain.length - 1; i >= 0; i--) {
            Certificate cert = chain[i];
            try {
                // We don't care about the unresolved critical extensions.
                checker.check(cert, Collections.emptySet());
            } catch (CertPathValidatorException cpve) {
                if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                    SSLLogger.fine("Certificate does not conform to " +
                            "algorithm constraints", cert, cpve);
                }

                return false;
            }
        }

        return true;
    }

    protected boolean isConstraintsDisabled() {
        return "true".equals(System.getProperty(
                "jdk.tls.keymanager.disableConstraintsChecking"));
    }

    // enum for the result of the extension check
    // NOTE: the order of the constants is important as they are used
    // for sorting, i.e. OK is best, followed by EXPIRED and EXTENSION_MISMATCH
    enum CheckResult {
        OK,                     // ok or not checked
        INSENSITIVE,            // server name indication insensitive
        EXPIRED,                // extensions valid but cert expired
        EXTENSION_MISMATCH,     // extensions invalid (expiration not checked)
    }

    // enum for the type of certificate check we want to perform
    // (client or server)
    // also includes the check code itself
    enum CheckType {

        // enum constant for "no check" (currently not used)
        NONE(Collections.emptySet()),

        // enum constant for "tls client" check
        // valid EKU for TLS client: any, tls_client
        CLIENT(new HashSet<>(List.of(
                KnownOIDs.anyExtendedKeyUsage.value(),
                KnownOIDs.clientAuth.value()
        ))),

        // enum constant for "tls server" check
        // valid EKU for TLS server: any, tls_server, ns_sgc, ms_sgc
        SERVER(new HashSet<>(List.of(
                KnownOIDs.anyExtendedKeyUsage.value(),
                KnownOIDs.serverAuth.value(),
                KnownOIDs.NETSCAPE_ExportApproved.value(),
                KnownOIDs.MICROSOFT_ExportApproved.value()
        )));

        // set of valid EKU values for this type
        final Set<String> validEku;

        CheckType(Set<String> validEku) {
            this.validEku = validEku;
        }

        private static boolean getBit(boolean[] keyUsage, int bit) {
            return (bit < keyUsage.length) && keyUsage[bit];
        }

        // Check if this certificate is appropriate for this type of use
        // first check extensions, if they match, check expiration.
        //
        // Note: we may want to move this code into the sun.security.validator
        // package
        CheckResult check(X509Certificate cert, Date date,
                List<SNIServerName> serverNames, String idAlgorithm) {

            if (this == NONE) {
                return CheckResult.OK;
            }

            // check extensions
            try {
                // check extended key usage
                List<String> certEku = cert.getExtendedKeyUsage();
                if ((certEku != null) &&
                        Collections.disjoint(validEku, certEku)) {
                    // if extension is present and does not contain any of
                    // the valid EKU OIDs, return extension_mismatch
                    return CheckResult.EXTENSION_MISMATCH;
                }

                // check key usage
                boolean[] ku = cert.getKeyUsage();
                if (ku != null) {
                    String algorithm = cert.getPublicKey().getAlgorithm();
                    boolean supportsDigitalSignature = getBit(ku, 0);
                    switch (algorithm) {
                        case "RSA":
                            // require either signature bit
                            // or if server also allow key encipherment bit
                            if (!supportsDigitalSignature) {
                                if (this == CLIENT || !getBit(ku, 2)) {
                                    return CheckResult.EXTENSION_MISMATCH;
                                }
                            }
                            break;
                        case "RSASSA-PSS":
                            if (!supportsDigitalSignature && (this == SERVER)) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            break;
                        case "DSA":
                            // require signature bit
                            if (!supportsDigitalSignature) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            break;
                        case "DH":
                            // require keyagreement bit
                            if (!getBit(ku, 4)) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            break;
                        case "EC":
                            // require signature bit
                            if (!supportsDigitalSignature) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            // For servers, also require key agreement.
                            // This is not totally accurate as the keyAgreement
                            // bit is only necessary for static ECDH key
                            // exchange and not ephemeral ECDH. We leave it in
                            // for now until there are signs that this check
                            // causes problems for real world EC certificates.
                            if (this == SERVER && !getBit(ku, 4)) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            break;
                    }
                }
            } catch (CertificateException e) {
                // extensions unparseable, return failure
                return CheckResult.EXTENSION_MISMATCH;
            }

            try {
                cert.checkValidity(date);
            } catch (CertificateException e) {
                return CheckResult.EXPIRED;
            }

            if (serverNames != null && !serverNames.isEmpty()) {
                for (SNIServerName serverName : serverNames) {
                    if (serverName.getType() ==
                            StandardConstants.SNI_HOST_NAME) {
                        if (!(serverName instanceof SNIHostName)) {
                            try {
                                serverName =
                                        new SNIHostName(serverName.getEncoded());
                            } catch (IllegalArgumentException iae) {
                                // unlikely to happen, just in case ...
                                if (SSLLogger.isOn &&
                                        SSLLogger.isOn("keymanager")) {
                                    SSLLogger.fine(
                                            "Illegal server name: " + serverName);
                                }

                                return CheckResult.INSENSITIVE;
                            }
                        }
                        String hostname =
                                ((SNIHostName)serverName).getAsciiName();

                        try {
                            X509TrustManagerImpl.checkIdentity(hostname,
                                    cert, idAlgorithm);
                        } catch (CertificateException e) {
                            if (SSLLogger.isOn &&
                                    SSLLogger.isOn("keymanager")) {
                                SSLLogger.fine(
                                        "Certificate identity does not match " +
                                                "Server Name Indication (SNI): " +
                                                hostname);
                            }
                            return CheckResult.INSENSITIVE;
                        }

                        break;
                    }
                }
            }

            return CheckResult.OK;
        }

        public String getValidator() {
            if (this == CLIENT) {
                return Validator.VAR_TLS_CLIENT;
            } else if (this == SERVER) {
                return Validator.VAR_TLS_SERVER;
            }
            return Validator.VAR_GENERIC;
        }
    }
}
