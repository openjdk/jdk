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
import static jdk.test.lib.security.SecurityUtils.countSubstringOccurrences;
import static jdk.test.lib.security.SecurityUtils.runAndGetLog;

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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import jdk.test.lib.security.CertificateBuilder;

/*
 * @test
 * @bug 8372526
 * @summary Check CompressedCertificate message cache.
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm CompressedCertMsgCache
 */

public class CompressedCertMsgCache extends SSLSocketTemplate {

    private static X509Certificate trustedCert;
    private static X509Certificate serverCert;
    private static X509Certificate clientCert;
    private static KeyPair serverKeys;
    private static KeyPair clientKeys;
    private static SSLContext serverSslContext;
    private static SSLContext clientSslContext;

    public static void main(String[] args) throws Exception {

        // Complete 3 handshakes with the same SSLContext.
        String log = runAndGetLog(() -> {
            try {
                setupCertificates();
                serverSslContext = getSSLContext(trustedCert, serverCert,
                        serverKeys.getPrivate(), "TLSv1.3");
                clientSslContext = getSSLContext(trustedCert, clientCert,
                        clientKeys.getPrivate(), "TLSv1.3");

                new CompressedCertMsgCache().run();
                new CompressedCertMsgCache().run();
                new CompressedCertMsgCache().run();
            } catch (Exception _) {
            }
        });

        // The same CompressedCertificate message must be cached only once.
        assertEquals(1, countSubstringOccurrences(log,
                "Caching CompressedCertificate message"));

        // Make sure CompressedCertificate message is produced 3 times.
        assertEquals(3, countSubstringOccurrences(log,
                "Produced CompressedCertificate handshake message"));

        // Make sure CompressedCertificate message is consumed 3 times.
        assertEquals(3, countSubstringOccurrences(log,
                "Consuming CompressedCertificate handshake message"));
    }

    @Override
    public SSLContext createServerSSLContext() throws Exception {
        return serverSslContext;
    }

    @Override
    public SSLContext createClientSSLContext() throws Exception {
        return clientSslContext;
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

    private static void setupCertificates() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        KeyPair caKeys = kpg.generateKeyPair();
        serverKeys = kpg.generateKeyPair();
        clientKeys = kpg.generateKeyPair();

        trustedCert = createTrustedCert(caKeys);

        serverCert = customCertificateBuilder(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US",
                serverKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), "SHA256withECDSA");

        clientCert = customCertificateBuilder(
                "CN=localhost, OU=SSL-Client, ST=Some-State, C=US",
                clientKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), "SHA256withECDSA");
    }

    private static X509Certificate createTrustedCert(KeyPair caKeys)
            throws Exception {
        return customCertificateBuilder(
                "O=CA-Org, L=Some-City, ST=Some-State, C=US",
                caKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(true, true, 1)
                .build(null, caKeys.getPrivate(), "SHA256withECDSA");
    }

    private static CertificateBuilder customCertificateBuilder(
            String subjectName, PublicKey publicKey, PublicKey caKey)
            throws CertificateException, IOException {
        return new CertificateBuilder()
                .setSubjectName(subjectName)
                .setPublicKey(publicKey)
                .setNotBefore(
                        Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .setNotAfter(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .setSerialNumber(BigInteger.valueOf(
                        new SecureRandom().nextLong(1000000) + 1))
                .addSubjectKeyIdExt(publicKey)
                .addAuthorityKeyIdExt(caKey)
                .addKeyUsageExt(new boolean[]{
                        true, true, true, true, true, true, true});
    }
}
