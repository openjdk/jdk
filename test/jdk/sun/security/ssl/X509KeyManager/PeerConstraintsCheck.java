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
import java.net.Socket;
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
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import jdk.test.lib.security.CertificateBuilder;
import jdk.test.lib.security.SecurityUtils;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;

/*
 * @test
 * @bug 8353113
 * @summary Peer supported certificate signature algorithms are not being
 *          checked with default SunX509 key manager
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm PeerConstraintsCheck false SunX509
 * @run main/othervm PeerConstraintsCheck true SunX509
 * @run main/othervm PeerConstraintsCheck false PKIX
 * @run main/othervm PeerConstraintsCheck true PKIX
 */

public class PeerConstraintsCheck extends SSLSocketTemplate {

    private static final String CLIENT_CERT_SIG_ALG = "SHA384withRSA";
    private static final String SERVER_CERT_SIG_ALG = "SHA256withRSA";
    private static final String TRUSTED_CERT_SIG_ALG = "SHA512withRSA";

    private final String kmAlg;
    private X509Certificate trustedCert;
    private X509Certificate serverCert;
    private X509Certificate clientCert;
    private KeyPair serverKeys;
    private KeyPair clientKeys;

    protected PeerConstraintsCheck(String kmAlg) throws Exception {
        super();
        this.kmAlg = kmAlg;
        setupCertificates();
    }

    public static void main(String[] args) throws Exception {
        // Make sure only server's certificate signature algorithm is disabled.
        SecurityUtils.removeFromDisabledAlgs("jdk.certpath.disabledAlgorithms",
                List.of(CLIENT_CERT_SIG_ALG, TRUSTED_CERT_SIG_ALG));

        SecurityUtils.removeFromDisabledAlgs("jdk.tls.disabledAlgorithms",
                List.of(CLIENT_CERT_SIG_ALG, TRUSTED_CERT_SIG_ALG));

        SecurityUtils.addToDisabledTlsAlgs(SERVER_CERT_SIG_ALG);

        String disabled = args[0];
        String kmAlg = args[1];

        System.setProperty(
                "jdk.tls.keymanager.disableConstraintsChecking", disabled);

        if ("true".equals(disabled)) {
            new PeerConstraintsCheck(kmAlg).run();
        } else {
            runAndCheckException(
                    () -> new PeerConstraintsCheck(kmAlg).run(),
                    ex -> {
                        assertTrue(ex instanceof javax.net.ssl.SSLHandshakeException);
                        assertEquals(ex.getMessage(), "(handshake_failure) "
                                + "No available authentication scheme");
                    }
            );
        }
    }

    @Override
    public SSLContext createServerSSLContext() throws Exception {
        return getSSLContext(
                trustedCert, serverCert, serverKeys.getPrivate(), kmAlg);
    }

    @Override
    public SSLContext createClientSSLContext() throws Exception {
        return getSSLContext(
                trustedCert, clientCert, clientKeys.getPrivate(), kmAlg);
    }

    private static SSLContext getSSLContext(
            X509Certificate trustedCertificate, X509Certificate keyCertificate,
            PrivateKey privateKey, String kmAlg)
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

        // create SSL context
        SSLContext ctx = SSLContext.getInstance("TLS");

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmAlg);
        kmf.init(ks, passphrase);

        // Use custom trust-all TrustManager so we perform only KeyManager's
        // constraints check.
        X509ExtendedTrustManager[] trustAll = new X509ExtendedTrustManager[]{
                new X509ExtendedTrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain,
                            String authType, Socket socket)
                            throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain,
                            String authType, Socket socket)
                            throws CertificateException {
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain,
                            String authType, SSLEngine engine)
                            throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain,
                            String authType, SSLEngine engine)
                            throws CertificateException {
                    }

                    public void checkClientTrusted(X509Certificate[] chain,
                            String authType) throws CertificateException {
                    }

                    public void checkServerTrusted(X509Certificate[] chain,
                            String authType) throws CertificateException {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        ctx.init(kmf.getKeyManagers(), trustAll, null);

        return ctx;
    }

    // Certificate-building helper methods.

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
                .build(trustedCert, caKeys.getPrivate(), SERVER_CERT_SIG_ALG);

        this.clientCert = customCertificateBuilder(
                "CN=localhost, OU=SSL-Client, O=Some-Org, L=Some-City, ST=Some-State, C=US",
                clientKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), CLIENT_CERT_SIG_ALG);
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
                .build(null, caKeys.getPrivate(), TRUSTED_CERT_SIG_ALG);
    }

    private static CertificateBuilder customCertificateBuilder(
            String subjectName, PublicKey publicKey, PublicKey caKey)
            throws CertificateException, IOException {
        SecureRandom random = new SecureRandom();

        CertificateBuilder builder = new CertificateBuilder()
                .setSubjectName(subjectName)
                .setPublicKey(publicKey)
                .setNotAfter(
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
