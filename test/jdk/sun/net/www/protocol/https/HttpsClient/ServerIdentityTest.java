/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 4328195
 * @summary Need to include the alternate subject DN for certs,
 *          https should check for this
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm ServerIdentityTest dns localhost
 * @run main/othervm ServerIdentityTest ip 127.0.0.1
 *
 * @author Yingxian Wang
 */

import static jdk.test.lib.Asserts.fail;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.UnknownHostException;
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
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import jdk.test.lib.security.CertificateBuilder;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;

public final class ServerIdentityTest extends SSLSocketTemplate {

    private static String hostname;
    private static SSLContext serverContext;

    /*
     * Run the test case.
     */
    public static void main(String[] args) throws Exception {
        // Get the customized arguments.
        initialize(args);

        new ServerIdentityTest().run();
    }

    ServerIdentityTest() throws UnknownHostException {
        serverAddress = InetAddress.getByName(hostname);
    }

    @Override
    protected boolean isCustomizedClientConnection() {
        return true;
    }

    @Override
    protected void runServerApplication(SSLSocket socket) throws Exception {
        InputStream sslIS = socket.getInputStream();
        sslIS.read();
        BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream()));
        bw.write("HTTP/1.1 200 OK\r\n\r\n\r\n");
        bw.flush();
        socket.getSession().invalidate();
    }

    @Override
    protected void runClientApplication(int serverPort) throws Exception {
        URL url = new URL(
                "https://" + hostname + ":" + serverPort + "/index.html");

        HttpURLConnection urlc = null;
        InputStream is = null;
        try {
            urlc = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            is = urlc.getInputStream();
        } finally {
            if (is != null) {
                is.close();
            }
            if (urlc != null) {
                urlc.disconnect();
            }
        }
    }

    @Override
    protected SSLContext createServerSSLContext() throws Exception {
        return serverContext;
    }

    private static void initialize(String[] args) throws Exception {
        String mode = args[0];
        hostname = args[1];

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        KeyPair caKeys = kpg.generateKeyPair();
        KeyPair serverKeys = kpg.generateKeyPair();
        KeyPair clientKeys = kpg.generateKeyPair();

        CertificateBuilder serverCertificateBuilder = customCertificateBuilder(
                "CN=server, O=Some-Org, L=Some-City, ST=Some-State, C=US",
                serverKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1);

        if (mode.equalsIgnoreCase("dns")) {
            serverCertificateBuilder.addSubjectAltNameDNSExt(List.of(hostname));
        } else if (mode.equalsIgnoreCase("ip")) {
            serverCertificateBuilder.addSubjectAltNameIPExt(List.of(hostname));
        } else {
            fail("Unknown mode: " + mode);
        }

        X509Certificate trustedCert = createTrustedCert(caKeys);

        X509Certificate serverCert = serverCertificateBuilder.build(
                trustedCert, caKeys.getPrivate(), "SHA256WithRSA");

        X509Certificate clientCert = customCertificateBuilder(
                "CN=localhost, OU=SSL-Client, O=Some-Org, L=Some-City, ST=Some-State, C=US",
                clientKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(false, false, -1)
                .build(trustedCert, caKeys.getPrivate(), "SHA256WithRSA");

        serverContext = getSSLContext(
                trustedCert, serverCert, serverKeys.getPrivate());

        SSLContext clientContext = getSSLContext(
                trustedCert, clientCert, clientKeys.getPrivate());

        HttpsURLConnection.setDefaultSSLSocketFactory(
                clientContext.getSocketFactory());
    }

    private static SSLContext getSSLContext(
            X509Certificate trustedCertificate, X509Certificate keyCertificate,
            PrivateKey privateKey)
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

        // Using PKIX TrustManager
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        // Using PKIX KeyManager
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");

        // create SSL context
        SSLContext ctx = SSLContext.getInstance("TLS");
        kmf.init(ks, passphrase);
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
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
                .build(null, caKeys.getPrivate(), "SHA256WithRSA");
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
