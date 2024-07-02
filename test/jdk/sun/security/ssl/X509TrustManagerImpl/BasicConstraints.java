/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7166570
 * @summary JSSE certificate validation has started to fail for
 *     certificate chains
 * @library /test/jdk/java/security/testlibrary
 * @modules java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 *          java.base/sun.security.validator
 *          java.base/sun.security.x509
 * @build CertificateBuilder
 * @run main/othervm BasicConstraints PKIX
 * @run main/othervm BasicConstraints SunX509
 */

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.*;
import java.io.*;
import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.cert.*;
import java.security.spec.*;
import sun.security.testlibrary.CertificateBuilder;

public class BasicConstraints extends TMBase {
    private final X509Certificate trustedCertificate;

    private final X509Certificate caSignerCertificate;

    private final X509Certificate certIssuerCertificate;

    private final X509Certificate serverCertificate;
    private final KeyPair serverKeyPair;

    private final X509Certificate clientCertificate;
    private final KeyPair clientKeyPair;

    static char[] passphrase = "passphrase".toCharArray();


    /*
     * Turn on SSL debugging?
     */
    static boolean debug = Boolean.getBoolean("test.debug");

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = getSSLContext(true);
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        SSLServerSocket sslServerSocket =
                (SSLServerSocket) sslssf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();
        SSLSocket sslSocket = null;
        try {
            /*
             * Signal Client, we're ready for his connect.
             */
            serverReady = true;

            sslSocket = (SSLSocket) sslServerSocket.accept();
            sslSocket.setNeedClientAuth(true);

            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslIS.read();
            sslOS.write(85);
            sslOS.flush();
        } finally {
            if (sslSocket != null) {
                sslSocket.close();
            }
            sslServerSocket.close();
        }
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {
        /*
         * Wait for server to get started.
         */
        while (!serverReady) {
            Thread.sleep(50);
        }

        SSLContext context = getSSLContext(false);
        SSLSocketFactory sslsf = context.getSocketFactory();

        SSLSocket sslSocket =
                (SSLSocket) sslsf.createSocket("localhost", serverPort);
        try {
            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslOS.write(280);
            sslOS.flush();
            sslIS.read();
        } finally {
            sslSocket.close();
        }
    }

    // get the ssl context
    private SSLContext getSSLContext(boolean isServer) throws Exception {

        // generate certificate from cert string
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        ks.setCertificateEntry("SunJSSE Test Serivce", trustedCertificate);

        // import the certificate chain and key
        Certificate[] chain = new Certificate[3];
        chain[2] = caSignerCertificate;
        chain[1] = certIssuerCertificate;

        PKCS8EncodedKeySpec priKeySpec = null;
        if (isServer) {
            chain[0] = serverCertificate;
            ks.setKeyEntry("End Entity", serverKeyPair.getPrivate(), passphrase, chain);
        } else {
            chain[0] = clientCertificate;
            ks.setKeyEntry("End Entity", clientKeyPair.getPrivate(), passphrase, chain);
        }

        // check the certification path
        PKIXParameters paras = new PKIXParameters(ks);
        paras.setRevocationEnabled(false);
        CertPath path = cf.generateCertPath(Arrays.asList(chain));
        CertPathValidator cv = CertPathValidator.getInstance("PKIX");
        cv.validate(path, paras);

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmAlgorithm);
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance("TLS");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init(ks, passphrase);

        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        ks = null;

        return ctx;
    }

    private static String tmAlgorithm;        // trust manager

    private static void parseArguments(String[] args) {
        tmAlgorithm = args[0];
    }


    // use any free port by default
    volatile int serverPort = 0;

    public static void main(String args[]) throws Exception {
        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Get the customized arguments.
         */
        parseArguments(args);

        /*
         * Start the tests.
         */
        new BasicConstraints().run();
    }

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    BasicConstraints() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);

        KeyPair trustedKeyPair = kpg.generateKeyPair();
        trustedCertificate = CertificateBuilder.newSelfSignedCA(
            "C = US, O = Java, OU = SunJSSE Test Serivce", trustedKeyPair)
            .build(null, trustedKeyPair.getPrivate(), "SHA256withRSA");

        KeyPair caSignerKeyPair = kpg.generateKeyPair();
        caSignerCertificate = CertificateBuilder.newEndEntity(
            "C = US, O = Java, OU = SunJSSE Test Serivce, CN = casigner",
            caSignerKeyPair.getPublic(), trustedKeyPair.getPublic())
            .addKeyUsageExt(new boolean[]{false, false, false, false, false, true, true, false, false})
            .addBasicConstraintsExt(true, true, -1)
            .build(trustedCertificate, trustedKeyPair.getPrivate(), "SHA256withRSA");

        KeyPair certIssuerKeyPair = kpg.generateKeyPair();
        certIssuerCertificate = CertificateBuilder.newEndEntity(
            "C = US, O = Java, OU = SunJSSE Test Serivce, CN = certissuer",
            certIssuerKeyPair.getPublic(), caSignerKeyPair.getPublic())
            .addKeyUsageExt(new boolean[]{false, false, false, false, false, true, true, false, false})
            .addBasicConstraintsExt(true, true, -1)
            .build(caSignerCertificate, caSignerKeyPair.getPrivate(), "SHA256withRSA");

        serverKeyPair = kpg.generateKeyPair();
        serverCertificate = CertificateBuilder.newServerCertificateBuilder(
            "C = US, O = Java, OU = SunJSSE Test Serivce, CN = localhost",
            serverKeyPair.getPublic(), certIssuerKeyPair.getPublic())
            .build(certIssuerCertificate, certIssuerKeyPair.getPrivate(), "SHA256withRSA");

        clientKeyPair = kpg.generateKeyPair();
        clientCertificate = CertificateBuilder.newClientCertificateBuilder(
            "C = US, O = Java, OU = SunJSSE Test Serivce, CN = InterOp Tester",
            clientKeyPair.getPublic(), certIssuerKeyPair.getPublic())
            .build(certIssuerCertificate, certIssuerKeyPair.getPrivate(),"SHA256withRSA");
    }
}
