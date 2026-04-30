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

import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Utils.runAndCheckException;

import java.io.IOException;
import java.math.BigInteger;
import java.net.IDN;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import jdk.test.lib.security.CertificateBuilder;
import sun.security.util.DerValue;
import sun.security.x509.DNSName;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.SubjectAlternativeNameExtension;

/*
 * @test
 * @summary Test host wildcard matching as part of a TLS validation of the
 * server's identity
 *
 * @library /test/lib
 *          /javax/net/ssl/templates
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 *
 * @run main SNIWildcardMatching
 */

public final class SNIWildcardMatching extends SSLSocketTemplate {

    private static final String KEY_ALGORITHM = "EC";
    private static final String CERT_SIG_ALG = "SHA256withECDSA";

    private static final String[][] VALID_VALUES = new String[][]{
            {"secret.foo.com", "secret.foo.com"},
            {"secret.foo.com", "*.foo.com"},
            {"secret.foo.com", "s*.foo.com"},
            {"secret.foo.com", "*t.foo.com"},
            {"secret.foo.com", "s**.foo.com"},
            {"secret.foo.com", "s*t.foo.com"},
            {"secret.foo.com", "test.foo.com,*.foo.com"},
            {"公司.江利子.net", "secret.foo.com,*.江利子.net"}
    };

    private static final String[][] INVALID_VALUES = new String[][]{
            {"secret.foo.com", "secret.*.com"},
            {"secret.foo.com", "*.*.com"},
            {"foo", "*"},
            {"foo.com", "*.foo.com"},
            {"bar.secret.foo.com", "*.foo.com"},
            {"secret.foo.com", "secret1.foo.com"},
            {"公司.江利子.net", "*公司.*.net"},
            {"公司.江利子.example.net", "*.example.net"}
    };

    private X509Certificate trustedCert;
    private X509Certificate serverCert;
    private KeyPair serverKeys;

    private final String protocol;
    private final List<String> serverSANs;
    private final SNIHostName sniHostName;

    SNIWildcardMatching(String sni, String sans,
            String protocol) throws Exception {
        super();
        this.protocol = protocol;
        serverSANs = Arrays.stream(sans.split(",")).toList();
        sniHostName = new SNIHostName(sni);
        setupCertificates();
    }

    public static void main(String[] args) throws Exception {

        for (String protocol : new String[]{"TLSv1.3", "TLSv1.2"}) {
            for (var v : VALID_VALUES) {
                try {
                    new SNIWildcardMatching(v[0], v[1], protocol).run();
                } catch (Exception e) {
                    System.err.println("Error running "
                            + Arrays.toString(v) + ": " + e.getMessage());
                }
            }

            for (var v : INVALID_VALUES) {
                runAndCheckException(() -> new SNIWildcardMatching(
                                v[0], v[1], protocol).run(),
                        // Either client or server can throw first, check both.
                        peer1 -> {
                            var peer2 = peer1.getSuppressed()[0];
                            assertTrue(peer1 instanceof SSLHandshakeException
                                    || peer2 instanceof SSLHandshakeException);
                        });
            }
        }
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters params = socket.getSSLParameters();
        // Set SNI to check against SANs.
        params.setServerNames(List.of(sniHostName));
        // Client won't throw unless EIA is set.
        params.setEndpointIdentificationAlgorithm("HTTPS");
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

    private void setupCertificates() throws Exception {
        var kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        var caKeys = kpg.generateKeyPair();
        serverKeys = kpg.generateKeyPair();

        trustedCert = createTrustedCert(caKeys);

        GeneralNames gNames = new GeneralNames();
        for (String name : serverSANs) {
            gNames.add(new GeneralName(new DNSName(new DerValue(
                    DerValue.tag_IA5String, IDN.toASCII(name)))));
        }

        serverCert = customCertificateBuilder(
                "O=Server-Org, L=Some-City, ST=Some-State, C=US",
                serverKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                // Add SAN extension to the server cert.
                .addExtension(new SubjectAlternativeNameExtension(
                        false, gNames))
                .build(trustedCert, caKeys.getPrivate(),
                        CERT_SIG_ALG);
    }

    private static X509Certificate createTrustedCert(KeyPair caKeys)
            throws CertificateException, IOException {
        return customCertificateBuilder(
                "O=CA-Org, L=Some-City, ST=Some-State, C=US",
                caKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(true, true, 1)
                .build(null, caKeys.getPrivate(), CERT_SIG_ALG);
    }

    private static CertificateBuilder customCertificateBuilder(
            String subjectName, PublicKey publicKey, PublicKey caKey)
            throws IOException, CertificateException {
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
