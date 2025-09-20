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
 * @bug 8365820
 * @summary Apply certificate scope constraints to algorithms in
 *          "signature_algorithms" extension when
 *          "signature_algorithms_cert" extension is not being sent.
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DisableCertSignAlgsExtForServerTLS13
 */

import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Utils.runAndCheckException;

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketException;
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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManagerFactory;
import jdk.test.lib.security.CertificateBuilder;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;

/*
 * Test disabled signature_algorithms_cert extension on the server side.
 *
 * CertificateRequest's extensions are encrypted in TLSv1.3. So we can't verify
 * the content of the CertificateRequest's signature_algorithms extension
 * directly like we do it for extensions in ClientHello message.
 * Instead, we run a TLS handshake and check that certificate scope
 * constraints are being applied to algorithms in "signature_algorithms"
 * extension when "signature_algorithms_cert" extension is not being sent.
 */

public class DisableCertSignAlgsExtForServerTLS13 extends SSLSocketTemplate {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String SERVER_CERT_SIG_ALG = "RSASSA-PSS";
    // SHA256withRSA signature algorithm is not allowed for certificate
    // signatures per TLSv1.3 spec. This is regardless of
    // jdk.tls.disabledAlgorithms configuration.
    private static final String CLIENT_CERT_SIG_ALG = "SHA256withRSA";
    private static final String TRUSTED_CERT_SIG_ALG = "RSASSA-PSS";

    private final String protocol;
    private X509Certificate trustedCert;
    private X509Certificate serverCert;
    private X509Certificate clientCert;
    private KeyPair serverKeys;
    private KeyPair clientKeys;

    protected DisableCertSignAlgsExtForServerTLS13(
            String protocol) throws Exception {
        super();
        this.protocol = protocol;
        setupCertificates();
    }

    public static void main(String[] args) throws Exception {
        // Disable signature_algorithms_cert extension on the server side.
        System.setProperty("jdk.tls.server.disableExtensions",
                "signature_algorithms_cert");

        // Should run fine on TLSv1.2 because SHA256withRSA signature algorithm
        // is allowed for certificates in TLSv1.2.
        new DisableCertSignAlgsExtForServerTLS13("TLSv1.2").run();

        // Should fail for "TLSv1.3" and "TLS". It fails for "TLS" because the
        // protocol is already negotiated when server sends CertificateRequest
        // message.
        for (String protocol : new String[]{"TLS", "TLSv1.3"}) {
            runAndCheckException(
                    () -> new DisableCertSignAlgsExtForServerTLS13(
                            protocol).run(),
                    localEx -> {
                        Throwable remoteEx = localEx.getSuppressed()[0];

                        for (Throwable ex :
                                new Throwable[]{localEx, remoteEx}) {
                            assertTrue((ex instanceof SSLHandshakeException
                                    && ex.getMessage()
                                    .contains("(certificate_required)")
                                    // Sometimes we can get SocketException
                                    // instead, depends on network setup.
                                    || ex instanceof SocketException));
                        }
                    });
        }
    }

    @Override
    protected void configureServerSocket(SSLServerSocket sslServerSocket) {
        // Require a conforming certificate for the client.
        SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        sslParameters.setNeedClientAuth(true);
        sslServerSocket.setSSLParameters(sslParameters);
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
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KEY_ALGORITHM);
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
