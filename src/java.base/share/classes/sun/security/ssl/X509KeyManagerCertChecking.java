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
import java.security.Principal;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.StandardConstants;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.security.auth.x500.X500Principal;
import sun.security.provider.certpath.AlgorithmChecker;
import sun.security.util.KnownOIDs;
import sun.security.validator.Validator;

/*
 * Layer that adds algorithm constraints and certificate checking functionality
 * to a key manager:
 * 1) Check against peer supported certificate signature algorithms (sent with
 *    "signature_algorithms_cert" TLS extension).
 * 2) Check against local TLS algorithm constraints ("java.security" config
 *    file).
 * 3) Mark alias results based on validity period and certificate extensions,
 *    so results can be sorted to find the best match. See "CheckResult" and
 *    "EntryStatus" for details.
 */

abstract class X509KeyManagerCertChecking extends X509ExtendedKeyManager {

    // Indicates whether we should skip the certificate checks.
    private final boolean checksDisabled;

    protected X509KeyManagerCertChecking() {
        checksDisabled = isCheckingDisabled();
    }

    abstract boolean isCheckingDisabled();

    // Entry point to do all certificate checks.
    protected EntryStatus checkAlias(int keyStoreIndex, String alias,
            Certificate[] chain, Date verificationDate, List<KeyType> keyTypes,
            Set<X500Principal> issuerSet, CheckType checkType,
            AlgorithmConstraints constraints,
            List<SNIServerName> requestedServerNames, String idAlgorithm) {

        // --- Mandatory checks ---

        if ((chain == null) || (chain.length == 0)) {
            return null;
        }

        for (Certificate cert : chain) {
            if (!(cert instanceof X509Certificate)) {
                // Not an X509Certificate, ignore this alias
                return null;
            }
        }

        // Check key type, get key type index.
        int keyIndex = -1;
        int j = 0;

        for (KeyType keyType : keyTypes) {
            if (keyType.matches(chain)) {
                keyIndex = j;
                break;
            }
            j++;
        }

        if (keyIndex == -1) {
            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine("Ignore alias " + alias
                        + ": key algorithm does not match");
            }
            return null;
        }

        // Check issuers
        if (issuerSet != null && !issuerSet.isEmpty()) {
            boolean found = false;
            for (Certificate cert : chain) {
                X509Certificate xcert = (X509Certificate) cert;
                if (issuerSet.contains(xcert.getIssuerX500Principal())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                    SSLLogger.fine(
                            "Ignore alias " + alias
                                    + ": issuers do not match");
                }
                return null;
            }
        }

        // --- Optional checks, depending on "checksDisabled" toggle ---

        // Check the algorithm constraints
        if (constraints != null &&
                !conformsToAlgorithmConstraints(constraints, chain,
                        checkType.getValidator())) {

            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine("Ignore alias " + alias +
                        ": certificate chain does not conform to " +
                        "algorithm constraints");
            }
            return null;
        }

        // Endpoint certificate check
        CheckResult checkResult = certificateCheck(checkType,
                (X509Certificate) chain[0],
                verificationDate == null ? new Date() : verificationDate,
                requestedServerNames, idAlgorithm);

        return new EntryStatus(
                keyStoreIndex, keyIndex, alias, chain, checkResult);
    }

    // Gets algorithm constraints of the socket.
    protected AlgorithmConstraints getAlgorithmConstraints(Socket socket) {

        if (checksDisabled) {
            return null;
        }

        if (socket != null && socket.isConnected() &&
                socket instanceof SSLSocket sslSocket) {

            SSLSession session = sslSocket.getHandshakeSession();

            if (session != null) {
                if (ProtocolVersion.useTLS12PlusSpec(session.getProtocol())) {
                    String[] peerSupportedSignAlgs = null;

                    if (session instanceof ExtendedSSLSession extSession) {
                        // Peer supported certificate signature algorithms
                        // sent with "signature_algorithms_cert" TLS extension.
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

        if (checksDisabled) {
            return null;
        }

        if (engine != null) {
            SSLSession session = engine.getHandshakeSession();
            if (session != null) {
                if (ProtocolVersion.useTLS12PlusSpec(session.getProtocol())) {
                    String[] peerSupportedSignAlgs = null;

                    if (session instanceof ExtendedSSLSession extSession) {
                        // Peer supported certificate signature algorithms
                        // sent with "signature_algorithms_cert" TLS extension.
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

    // Algorithm constraints check.
    private boolean conformsToAlgorithmConstraints(
            AlgorithmConstraints constraints, Certificate[] chain,
            String variant) {

        if (checksDisabled) {
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

    // Certificate check.
    private CheckResult certificateCheck(
            CheckType checkType, X509Certificate cert, Date date,
            List<SNIServerName> serverNames, String idAlgorithm) {
        return checksDisabled ? CheckResult.OK
                : checkType.check(cert, date, serverNames, idAlgorithm);
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

        // Check if this certificate is appropriate for this type of use.
        // First check extensions, if they match then check expiration.
        // NOTE: `conformsToAlgorithmConstraints` call above also does some
        // basic keyUsage checks.
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
                            // require key agreement bit
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
                                serverName = new SNIHostName(
                                        serverName.getEncoded());
                            } catch (IllegalArgumentException iae) {
                                // unlikely to happen, just in case ...
                                if (SSLLogger.isOn &&
                                        SSLLogger.isOn("keymanager")) {
                                    SSLLogger.fine("Illegal server name: "
                                            + serverName);
                                }

                                return CheckResult.INSENSITIVE;
                            }
                        }
                        String hostname =
                                ((SNIHostName) serverName).getAsciiName();

                        try {
                            X509TrustManagerImpl.checkIdentity(hostname,
                                    cert, idAlgorithm);
                        } catch (CertificateException e) {
                            if (SSLLogger.isOn &&
                                    SSLLogger.isOn("keymanager")) {
                                SSLLogger.fine(
                                        "Certificate identity does not match "
                                                + "Server Name Indication (SNI): "
                                                + hostname);
                            }
                            return CheckResult.INSENSITIVE;
                        }

                        break;
                    }
                }
            }

            return CheckResult.OK;
        }

        String getValidator() {
            if (this == CLIENT) {
                return Validator.VAR_TLS_CLIENT;
            } else if (this == SERVER) {
                return Validator.VAR_TLS_SERVER;
            }
            return Validator.VAR_GENERIC;
        }
    }

    // A candidate match.
    // Identifies the entry by key store index and alias
    // and includes the result of the certificate check.
    protected static class EntryStatus implements Comparable<EntryStatus> {

        final int keyStoreIndex;
        final int keyIndex;
        final String alias;
        final CheckResult checkResult;

        EntryStatus(int keyStoreIndex, int keyIndex, String alias,
                Certificate[] chain, CheckResult checkResult) {
            this.keyStoreIndex = keyStoreIndex;
            this.keyIndex = keyIndex;
            this.alias = alias;
            this.checkResult = checkResult;
        }

        @Override
        public int compareTo(EntryStatus other) {
            int result = this.checkResult.compareTo(other.checkResult);
            return (result == 0) ? (this.keyIndex - other.keyIndex) : result;
        }

        @Override
        public String toString() {
            String s = alias + " (verified: " + checkResult + ")";
            if (keyStoreIndex == 0) {
                return s;
            } else {
                return "KeyStore #" + keyStoreIndex + ", alias: " + s;
            }
        }
    }

    // Class to help verify that the public key algorithm (and optionally
    // the signature algorithm) of a certificate matches what we need.
    protected static class KeyType {

        final String keyAlgorithm;

        // In TLS 1.2, the signature algorithm  has been obsoleted by the
        // supported_signature_algorithms, and the certificate type no longer
        // restricts the algorithm used to sign the certificate.
        //
        // However, because we don't support certificate type checking other
        // than rsa_sign, dss_sign and ecdsa_sign, we don't have to check the
        // protocol version here.
        final String sigKeyAlgorithm;

        KeyType(String algorithm) {
            int k = algorithm.indexOf('_');
            if (k == -1) {
                keyAlgorithm = algorithm;
                sigKeyAlgorithm = null;
            } else {
                keyAlgorithm = algorithm.substring(0, k);
                sigKeyAlgorithm = algorithm.substring(k + 1);
            }
        }

        boolean matches(Certificate[] chain) {
            if (!chain[0].getPublicKey().getAlgorithm().equals(keyAlgorithm)) {
                return false;
            }
            if (sigKeyAlgorithm == null) {
                return true;
            }
            if (chain.length > 1) {
                // if possible, check the public key in the issuer cert
                return sigKeyAlgorithm.equals(
                        chain[1].getPublicKey().getAlgorithm());
            } else {
                // Check the signature algorithm of the certificate itself.
                // Look for the "withRSA" in "SHA1withRSA", etc.
                X509Certificate issuer = (X509Certificate) chain[0];
                String sigAlgName =
                        issuer.getSigAlgName().toUpperCase(Locale.ENGLISH);
                String pattern =
                        "WITH" + sigKeyAlgorithm.toUpperCase(Locale.ENGLISH);
                return sigAlgName.endsWith(pattern);
            }
        }
    }

    // Make a list of key types.
    protected static List<KeyType> getKeyTypes(String... keyTypes) {
        if ((keyTypes == null) ||
                (keyTypes.length == 0) || (keyTypes[0] == null)) {
            return null;
        }
        List<KeyType> list = new ArrayList<>(keyTypes.length);
        for (String keyType : keyTypes) {
            list.add(new KeyType(keyType));
        }
        return list;
    }

    // Make a set out of the array.
    protected static Set<X500Principal> getIssuerSet(Principal[] issuers) {

        if (issuers != null && issuers.length != 0) {
            Set<X500Principal> ret = new HashSet<>(issuers.length);

            for (Principal p : issuers) {
                if (p instanceof X500Principal) {
                    ret.add((X500Principal) p);
                } else {
                    // Normally, this will never happen but try to recover if
                    // it does.
                    try {
                        ret.add(new X500Principal(p.getName()));
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            return ret.isEmpty() ? null : ret;
        } else {
            return null;
        }
    }
}
