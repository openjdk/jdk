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
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import jdk.test.lib.security.CertificateBuilder;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;


/*
 * @test
 * @bug 8367104
 * @summary Check for RSASSA-PSS parameters when validating certificates
 *          against algorithm constraints.
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm RsaSsaPssConstraints RSASSA-PSS RSASSA-PSS Rsa_pss_pss_Sha384 true
 * @run main/othervm RsaSsaPssConstraints RSASSA-PSS RSASSA-PSS RsaSsa-Pss true
 * @run main/othervm RsaSsaPssConstraints RSA RSASSA-PSS rsa_pss_Rsae_sha384 true
 * @run main/othervm RsaSsaPssConstraints RSA RSASSA-PSS Rsa true
 * @run main/othervm RsaSsaPssConstraints RSA RSASSA-PSS RSASSA-pSS true
 * @run main/othervm RsaSsaPssConstraints RSA SHA384withRSA rsa_pkcs1_Sha384 true
 * @run main/othervm RsaSsaPssConstraints EC SHA384withECDSA Ecdsa_Secp384r1_sha384 true
 * @run main/othervm RsaSsaPssConstraints RSA SHA384withRSA SHA384withRsA true
 * @run main/othervm RsaSsaPssConstraints RSASSA-PSS RSASSA-PSS rsa_pss_rsae_sha384 false
 * @run main/othervm RsaSsaPssConstraints RSA RSASSA-PSS rsa_pss_pss_sha384 false
 * @run main/othervm RsaSsaPssConstraints RSASSA-PSS RSASSA-PSS rsa_pss_pss_sha256 false
 * @run main/othervm RsaSsaPssConstraints RSASSA-PSS RSASSA-PSS rsa_pss_pss_sha512 false
 * @run main/othervm RsaSsaPssConstraints RSASSA-PSS RSASSA-PSS RSA false
 * @run main/othervm RsaSsaPssConstraints RSA RSASSA-PSS rsa_pss_rsae_sha512 false
 * @run main/othervm RsaSsaPssConstraints RSA SHA384withRSA rsa_pkcs1_sha256 false
 * @run main/othervm RsaSsaPssConstraints EC SHA384withECDSA ecdsa_secp256r1_sha256 false
 * @run main/othervm RsaSsaPssConstraints EC SHA384withECDSA SHA512withECDSA false
 */

public class RsaSsaPssConstraints extends SSLSocketTemplate {

    private final String protocol;
    private final String keyAlg;
    private final String certSigAlg;
    private X509Certificate trustedCert;
    private X509Certificate serverCert;
    private X509Certificate clientCert;
    private KeyPair serverKeys;
    private KeyPair clientKeys;

    protected RsaSsaPssConstraints(
            String protocol, String keyAlg,
            String certSigAlg) throws Exception {
        super();
        this.protocol = protocol;
        this.keyAlg = keyAlg;
        this.certSigAlg = certSigAlg;
        setupCertificates();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new RuntimeException("Wrong number of arguments");
        }

        String keyAlg = args[0];
        String certSigAlg = args[1];
        String constraintAlgo = args[2];
        boolean fail = Boolean.parseBoolean(args[3]);

        // Note: CertificateBuilder generates RSASSA-PSS certificate
        // signature using SHA-384 digest algorithm by default.
        Security.setProperty("jdk.tls.disabledAlgorithms",
                constraintAlgo + " usage CertificateSignature");

        for (String protocol : new String[]{"TLSv1.3", "TLSv1.2"}) {
            var test = new RsaSsaPssConstraints(protocol, keyAlg, certSigAlg);

            final String errorMsg = protocol.equals("TLSv1.2") ?
                    "no cipher suites in common" :
                    "No available authentication scheme";

            if (fail) {
                runAndCheckException(test::run,
                        serverEx -> {
                            assertTrue(
                                    serverEx instanceof SSLHandshakeException);
                            assertEquals(serverEx.getMessage(),
                                    "(handshake_failure) " + errorMsg);
                        });
            } else {
                test.run();
            }
        }

        // Disable KeyManager's algorithm constraints checking and
        // check against TrustManager's local supported signature
        // algorithms on the client side.
        System.setProperty(
                "jdk.tls.SunX509KeyManager.certChecking", "false");

        for (String protocol : new String[]{"TLSv1.3", "TLSv1.2"}) {
            var test = new RsaSsaPssConstraints(protocol, keyAlg, certSigAlg);

            if (fail) {
                runAndCheckException(test::run,
                        serverEx -> {
                            Throwable clientEx = serverEx.getSuppressed()[0];
                            assertTrue(clientEx instanceof SSLHandshakeException
                                    || serverEx instanceof SSLHandshakeException);
                        });
            } else {
                test.run();
            }
        }
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

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        // create SSL context
        SSLContext ctx = SSLContext.getInstance(protocol);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ctx;
    }

    // Certificate-building helper methods.

    private void setupCertificates() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyAlg);
        KeyPair caKeys = kpg.generateKeyPair();
        this.serverKeys = kpg.generateKeyPair();
        this.clientKeys = kpg.generateKeyPair();

        this.trustedCert = createTrustedCert(caKeys, certSigAlg);

        this.serverCert = customCertificateBuilder(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US",
                serverKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), certSigAlg);

        this.clientCert = customCertificateBuilder(
                "CN=localhost, OU=SSL-Client, O=Some-Org, L=Some-City, ST=Some-State, C=US",
                clientKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), certSigAlg);
    }

    private static X509Certificate createTrustedCert(
            KeyPair caKeys, String certSigAlg)
            throws Exception {
        SecureRandom random = new SecureRandom();

        KeyIdentifier kid = new KeyIdentifier(caKeys.getPublic());
        GeneralNames gns = new GeneralNames();
        GeneralName name = new GeneralName(new X500Name(
                "O=Trusted-Org, L=Some-City, ST=Some-State, C=US"));
        gns.add(name);
        BigInteger serialNumber = BigInteger.valueOf(
                random.nextLong(1000000) + 1);
        return customCertificateBuilder(
                name.toString(),
                caKeys.getPublic(), caKeys.getPublic())
                .setSerialNumber(serialNumber)
                .addExtension(new AuthorityKeyIdentifierExtension(kid, gns,
                        new SerialNumber(serialNumber)))
                .addBasicConstraintsExt(true, true, -1)
                .build(null, caKeys.getPrivate(), certSigAlg);
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
