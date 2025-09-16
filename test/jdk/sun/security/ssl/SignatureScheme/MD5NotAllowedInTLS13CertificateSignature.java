/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8350807
 * @summary Certificates using MD5 algorithm that are disabled by default are
 *          incorrectly allowed in TLSv1.3 when re-enabled.
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm MD5NotAllowedInTLS13CertificateSignature
 */

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Utils.runAndCheckException;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import jdk.test.lib.security.CertificateBuilder;
import jdk.test.lib.security.SecurityUtils;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;

public class MD5NotAllowedInTLS13CertificateSignature extends
        SSLSocketTemplate {

    private final String protocol;
    private X509Certificate trustedCert;
    private X509Certificate serverCert;
    private X509Certificate clientCert;
    private KeyPair serverKeys;
    private KeyPair clientKeys;

    protected MD5NotAllowedInTLS13CertificateSignature(String protocol)
            throws Exception {
        super();
        this.protocol = protocol;
        setupCertificates();
    }

    public static void main(String[] args) throws Exception {
        // MD5 is disabled by default in java.security config file,
        // re-enable it for our test.
        SecurityUtils.removeFromDisabledAlgs(
                "jdk.certpath.disabledAlgorithms", List.of("MD5"));
        SecurityUtils.removeFromDisabledAlgs(
                "jdk.tls.disabledAlgorithms", List.of("MD5withRSA"));

        // Should fail on TLSv1.3 and up.
        runAndCheckException(
                // The conditions to reproduce the bug being fixed only met when
                // 'TLS' is specified, i.e. when older versions of protocol are
                // supported besides TLSv1.3.
                () -> new MD5NotAllowedInTLS13CertificateSignature("TLS").run(),
                serverEx -> {
                    Throwable clientEx = serverEx.getSuppressed()[0];
                    assertTrue(clientEx instanceof SSLHandshakeException);
                    assertEquals(clientEx.getMessage(), "(bad_certificate) "
                            + "PKIX path validation failed: "
                            + "java.security.cert.CertPathValidatorException: "
                            + "Algorithm constraints check failed on signature"
                            + " algorithm: MD5withRSA");
                });

        // Should run fine on TLSv1.2.
        new MD5NotAllowedInTLS13CertificateSignature("TLSv1.2").run();
    }

    @Override
    public SSLContext createServerSSLContext() throws Exception {
        return getSSLContext(
                trustedCert, serverCert, serverKeys.getPrivate(), protocol);
    }

    @Override
    public SSLContext createClientSSLContext() throws Exception {
        return getSSLContext(
                trustedCert, clientCert, clientKeys.getPrivate(), protocol);
    }

    private static SSLContext getSSLContext(
            X509Certificate trustedCertificate, X509Certificate keyCertificate,
            PrivateKey privateKey, String protocol)
            throws Exception {

        // create a key store
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);

        // import the trusted cert
        ks.setCertificateEntry("TLS Signer", trustedCertificate);

        // generate certificate chain
        Certificate[] chain = new Certificate[2];
        chain[0] = keyCertificate;
        chain[1] = trustedCertificate;

        // import the key entry.
        final char[] passphrase = "passphrase".toCharArray();
        ks.setKeyEntry("Whatever", privateKey, passphrase, chain);

        // Using PKIX TrustManager - this is where MD5 signature check is done.
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        // create SSL context
        SSLContext ctx = SSLContext.getInstance(protocol);

        // Disable KeyManager's algorithm constraints checking,
        // so we check against local supported signature
        // algorithms which constitutes the fix being tested.
        System.setProperty(
                "jdk.tls.SunX509KeyManager.certChecking", "false");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ctx;
    }

    // Certificate-building helper methods.
    // Certificates are signed with signature using MD5WithRSA algorithm.

    private void setupCertificates() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        KeyPair caKeys = kpg.generateKeyPair();
        this.serverKeys = kpg.generateKeyPair();
        this.clientKeys = kpg.generateKeyPair();

        this.trustedCert = createTrustedCert(caKeys);

        this.serverCert = customCertificateBuilder(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US",
                serverKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), "MD5WithRSA");

        this.clientCert = customCertificateBuilder(
                "CN=localhost, OU=SSL-Client, O=Some-Org, L=Some-City, ST=Some-State, C=US",
                clientKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), "MD5WithRSA");
    }

    private static X509Certificate createTrustedCert(KeyPair caKeys)
            throws Exception {
        SecureRandom random = new SecureRandom();

        KeyIdentifier kid = new KeyIdentifier(caKeys.getPublic());
        GeneralNames gns = new GeneralNames();
        GeneralName name = new GeneralName(new X500Name(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US"));
        gns.add(name);
        BigInteger serialNumber = BigInteger.valueOf(
                random.nextLong(1000000) + 1);
        return customCertificateBuilder(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US",
                caKeys.getPublic(), caKeys.getPublic())
                .setSerialNumber(serialNumber)
                .addExtension(new AuthorityKeyIdentifierExtension(kid, gns,
                        new SerialNumber(serialNumber)))
                .addBasicConstraintsExt(true, true, -1)
                .build(null, caKeys.getPrivate(), "MD5WithRSA");
    }

    private static CertificateBuilder customCertificateBuilder(
            String subjectName, PublicKey publicKey, PublicKey caKey)
            throws CertificateException, IOException {
        SecureRandom random = new SecureRandom();

        CertificateBuilder builder = new CertificateBuilder()
                .setSubjectName(subjectName)
                .setPublicKey(publicKey)
                .setNotBefore(
                        Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .setNotAfter(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .setSerialNumber(
                        BigInteger.valueOf(random.nextLong(1000000) + 1))
                .addSubjectKeyIdExt(publicKey)
                .addAuthorityKeyIdExt(caKey);
        builder.addKeyUsageExt(
                new boolean[]{true, true, true, true, true, true});

        return builder;
    }

}
