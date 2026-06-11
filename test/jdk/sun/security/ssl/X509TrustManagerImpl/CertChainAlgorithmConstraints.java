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
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import jdk.test.lib.security.CertificateBuilder;
import sun.security.provider.certpath.SunCertPathBuilderException;
import sun.security.validator.ValidatorException;
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
 *          java.base/sun.security.validator
 *          java.base/sun.security.provider.certpath
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm CertChainAlgorithmConstraints RSASSA-PSS RSASSA-PSS Rsa_pss_pss_Sha384 true
 * @run main/othervm CertChainAlgorithmConstraints RSASSA-PSS RSASSA-PSS RsaSsa-Pss true
 * @run main/othervm CertChainAlgorithmConstraints RSA RSASSA-PSS rsa_pss_Rsae_sha384 true
 * @run main/othervm CertChainAlgorithmConstraints RSA RSASSA-PSS Rsa true
 * @run main/othervm CertChainAlgorithmConstraints RSA RSASSA-PSS RSASSA-pSS true
 * @run main/othervm CertChainAlgorithmConstraints RSA SHA384withRSA rsa_pkcs1_Sha384 true
 * @run main/othervm CertChainAlgorithmConstraints EC SHA384withECDSA Ecdsa_Secp384r1_sha384 true
 * @run main/othervm CertChainAlgorithmConstraints RSA SHA384withRSA SHA384withRsA true
 * @run main/othervm CertChainAlgorithmConstraints RSASSA-PSS RSASSA-PSS rsa_pss_rsae_sha384 false
 * @run main/othervm CertChainAlgorithmConstraints RSA RSASSA-PSS rsa_pss_pss_sha384 false
 * @run main/othervm CertChainAlgorithmConstraints RSASSA-PSS RSASSA-PSS rsa_pss_pss_sha256 false
 * @run main/othervm CertChainAlgorithmConstraints RSASSA-PSS RSASSA-PSS rsa_pss_pss_sha512 false
 * @run main/othervm CertChainAlgorithmConstraints RSASSA-PSS RSASSA-PSS RSA false
 * @run main/othervm CertChainAlgorithmConstraints RSA RSASSA-PSS rsa_pss_rsae_sha512 false
 * @run main/othervm CertChainAlgorithmConstraints RSA SHA384withRSA rsa_pkcs1_sha256 false
 * @run main/othervm CertChainAlgorithmConstraints EC SHA384withECDSA ecdsa_secp256r1_sha256 false
 * @run main/othervm CertChainAlgorithmConstraints EC SHA384withECDSA SHA512withECDSA false
 */

// Testing that an algorithm can be disabled with both a PKIXCertPathValidator
// and a CertPathBuilder code paths. It is somewhat common for JSSE to fall
// back to using a CertPathBuilder to find a valid chain.
public class CertChainAlgorithmConstraints extends SSLEngineTemplate {

    private final String keyAlg;
    private final String certSigAlg;
    private final boolean fail;

    private X509Certificate trustedCert;
    private X509Certificate serverCert;
    private X509Certificate linkCert1;
    private X509Certificate linkCert2;

    protected CertChainAlgorithmConstraints(
            String keyAlg, String certSigAlg, boolean fail)
            throws Exception {
        super();
        this.keyAlg = keyAlg;
        this.certSigAlg = certSigAlg;
        this.fail = fail;
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

        new CertChainAlgorithmConstraints(keyAlg, certSigAlg, fail).run();
    }

    // Run things in TLS handshake order.
    protected void run() throws Exception {

        // Produce client_hello
        clientEngine.wrap(clientOut, cTOs);
        cTOs.flip();

        // Consume client_hello.
        serverEngine.unwrap(cTOs, serverIn);
        runDelegatedTasks(serverEngine);

        // Produce server_hello.
        serverEngine.wrap(serverOut, sTOc);
        sTOc.flip();

        // Now that we have a Handshake session attached to the serverEngine,
        // do the check.
        checkChain(serverEngine);
    }

    protected void checkChain(SSLEngine engine) throws Exception {

        // Create a key store.
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);

        // Import the trusted cert.
        ks.setCertificateEntry("Trusted", trustedCert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        // Init TrustManager with a Key Store.
        tmf.init(ks);

        // Generate a mixed-up certificate chain.
        X509Certificate[] mixedUpChain = new X509Certificate[3];
        mixedUpChain[0] = linkCert2;
        // Put EE cert between 2 link certs to mix up the chain.
        mixedUpChain[1] = serverCert;
        mixedUpChain[2] = linkCert1;

        // Generate a valid certificate chain - we should get the same
        // results with it but a different code path to be used.
        X509Certificate[] validChain = new X509Certificate[3];
        validChain[0] = serverCert;
        validChain[1] = linkCert2;
        validChain[2] = linkCert1;

        var tm = (X509ExtendedTrustManager) tmf.getTrustManagers()[0];

        if (fail) {
            // Mixed-up chain: CertPathBuilder code path.
            runAndCheckException(
                    () -> tm.checkServerTrusted(mixedUpChain, "RSA", engine),
                    ex -> {
                        assertTrue(ex instanceof ValidatorException);
                        assertTrue(
                                ex.getCause() instanceof SunCertPathBuilderException);
                        assertEquals(ex.getMessage(), "PKIX path "
                                + "building failed: "
                                + "sun.security.provider.certpath."
                                + "SunCertPathBuilderException: unable to find "
                                + "valid certification path to requested target");
                    });

            // Valid chain: PKIXCertPathValidator code path.
            runAndCheckException(
                    () -> tm.checkServerTrusted(validChain, "RSA", engine),
                    ex -> {
                        assertTrue(ex instanceof ValidatorException);
                        assertTrue(
                                ex.getCause() instanceof CertPathValidatorException);
                        assertTrue(ex.getMessage().startsWith("PKIX path "
                                + "validation failed: java.security.cert."
                                + "CertPathValidatorException: Algorithm "
                                + "constraints check failed on "
                                + certSigAlg + " signature and "
                                + keyAlg + " key"));
                    });
        } else {
            tm.checkServerTrusted(mixedUpChain, "RSA", engine);
            tm.checkServerTrusted(validChain, "RSA", engine);
        }
    }

    // Certificate-building helper methods.

    private void setupCertificates() throws Exception {
        var kpg = KeyPairGenerator.getInstance(keyAlg);
        var caKeys = kpg.generateKeyPair();
        var serverKeys = kpg.generateKeyPair();
        var linkKeys1 = kpg.generateKeyPair();
        var linkKeys2 = kpg.generateKeyPair();

        this.trustedCert = createTrustedCert(caKeys, certSigAlg);

        this.linkCert1 = customCertificateBuilder(
                "O=Link1, L=Some-City, ST=Some-State, C=US",
                linkKeys1.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(true, true, -1)
                .build(trustedCert, caKeys.getPrivate(), certSigAlg);

        this.linkCert2 = customCertificateBuilder(
                "O=Link2, L=Some-City, ST=Some-State, C=US",
                linkKeys2.getPublic(), linkKeys1.getPublic())
                .addBasicConstraintsExt(true, true, -1)
                .build(linkCert1, linkKeys1.getPrivate(), certSigAlg);

        this.serverCert = customCertificateBuilder(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US",
                serverKeys.getPublic(), linkKeys2.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(linkCert2, linkKeys2.getPrivate(),
                        certSigAlg);
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
