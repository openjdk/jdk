/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Utils.runAndCheckException;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import jdk.test.lib.security.CertificateBuilder;

/*
 * @test
 * @bug 8345277
 * @summary TLS1.2 clients using ecdsa_secp256r1_sha256 signature scheme
 *          cannot connect to JDK servers with secp384r1 certificates
 *
 * @comment RFC 8446 Section 4.2.3:
 * ECDSA signature schemes align with TLS 1.2's ECDSA hash/signature
 * pairs.  However, the old semantics did not constrain the signing
 * curve.  If TLS 1.2 is negotiated, implementations MUST be prepared
 * to accept a signature that uses any curve that they advertised in
 * the "supported_groups" extension.
 *
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /javax/net/ssl/templates
 *          /test/lib
 *
 * @run main TLSCurveMismatch
 */

public class TLSCurveMismatch extends SSLSocketTemplate {

    private static final String KEY_ALGORITHM = "EC";
    // This is a signing curve that differs from the one in TLSv1.3
    // signature scheme.
    private static final String SIGNING_KEY_CURVE = "secp384r1";
    // We use SHA256withECDSA certificate signature so it matches the
    // signature scheme's algorithm in "signature_algorithms_cert" extension.
    private static final String CERT_SIG_ALG = "SHA256withECDSA";
    private static final String CLIENT_SIG_SCHEME =
            "ecdsa_secp256r1_sha256";
    private static final String SIG_SCHEME_CURVE = "secp256r1";
    private static Instant NOW;

    private final String protocol;
    private final String[] namedGroups;

    private X509Certificate trustedCert;
    private X509Certificate serverCert;
    private KeyPair serverKeys;

    TLSCurveMismatch(String protocol, String[] namedGroups) throws Exception {
        super();
        this.protocol = protocol;
        this.namedGroups = namedGroups;
        setupCertificates();
    }

    public static void main(String[] args) throws Exception {
        NOW = Instant.now();

        // TLSv1.2 with a curve mismatch should run fine.
        new TLSCurveMismatch("TLSv1.2",
                new String[]{SIGNING_KEY_CURVE, SIG_SCHEME_CURVE}).run();

        // When a signing key curve is not provided by the client in
        // "supported_groups" extension, we fail due to check in
        // X509Authentication#createServerPossession().
        runAndCheckException(() -> new TLSCurveMismatch(
                        "TLSv1.2", new String[]{SIG_SCHEME_CURVE}).run(),
                serverEx -> {
                    assertTrue(serverEx instanceof SSLHandshakeException);
                    assertEquals(serverEx.getMessage(),
                            "(handshake_failure) no cipher suites "
                                    + "in common");
                });

        // TLSv1.3 should always fail because of EC curve mismatch between the
        // signature scheme and a certificate key.
        runAndCheckException(() -> new TLSCurveMismatch("TLSv1.3",
                        new String[]{SIGNING_KEY_CURVE, SIG_SCHEME_CURVE})
                        .run(),
                serverEx -> {
                    assertTrue(serverEx instanceof SSLException);
                    assertEquals(serverEx.getMessage(),
                            "(internal_error) No supported CertificateVerify "
                                    + "signature algorithm for "
                                    + KEY_ALGORITHM + " key");
                });
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters params = socket.getSSLParameters();
        params.setNamedGroups(namedGroups);
        params.setSignatureSchemes(new String[]{CLIENT_SIG_SCHEME});
        socket.setSSLParameters(params);
    }

    @Override
    protected SSLContext createClientSSLContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("Trusted Cert", trustedCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);
        SSLContext ctx = SSLContext.getInstance(protocol);
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    @Override
    protected SSLContext createServerSSLContext() throws Exception {
        // Create a key store
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);

        // Import the trusted cert
        ks.setCertificateEntry("TLS Signer", trustedCert);

        // Import the key entry.
        final char[] passphrase = "passphrase".toCharArray();
        ks.setKeyEntry("Whatever", serverKeys.getPrivate(), passphrase,
                new Certificate[]{serverCert});

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        // Create SSL context
        SSLContext ctx = SSLContext.getInstance(protocol);
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    private void setupCertificates()
            throws Exception {
        var kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        kpg.initialize(new ECGenParameterSpec(SIGNING_KEY_CURVE));
        var caKeys = kpg.generateKeyPair();
        serverKeys = kpg.generateKeyPair();

        trustedCert = createTrustedCert(caKeys);

        serverCert = customCertificateBuilder(
                "O=Server-Org, L=Some-City, ST=Some-State, C=US",
                serverKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .addKeyUsageExt(new boolean[]{true})
                .build(trustedCert, caKeys.getPrivate(),
                        CERT_SIG_ALG);
    }

    private static X509Certificate createTrustedCert(KeyPair caKeys)
            throws CertificateException, IOException {
        return customCertificateBuilder(
                "O=CA-Org, L=Some-City, ST=Some-State, C=US",
                caKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(true, true, 1)
                .addKeyUsageExt(new boolean[]{
                        false, false, false, false, false, true, true})
                .build(null, caKeys.getPrivate(), CERT_SIG_ALG);
    }

    private static CertificateBuilder customCertificateBuilder(
            String subjectName, PublicKey publicKey, PublicKey caKey)
            throws CertificateException, IOException {
        return new CertificateBuilder()
                .setSubjectName(subjectName)
                .setPublicKey(publicKey)
                .setNotBefore(
                        Date.from(NOW.minus(1, ChronoUnit.HOURS)))
                .setNotAfter(Date.from(NOW.plus(1, ChronoUnit.HOURS)))
                .setSerialNumber(BigInteger.valueOf(
                        new SecureRandom().nextLong(1000000) + 1))
                .addSubjectKeyIdExt(publicKey)
                .addAuthorityKeyIdExt(caKey);
    }
}
