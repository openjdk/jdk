/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7113275
 * @summary compatibility issue with MD2 trust anchor and old X509TrustManager
 * @library /test/jdk/java/security/testlibrary
 * @modules java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 *          java.base/sun.security.validator
 *          java.base/sun.security.x509
 * @build CertificateBuilder
 * @run main/othervm MD2InTrustAnchor PKIX TLSv1.1
 * @run main/othervm MD2InTrustAnchor SunX509 TLSv1.1
 * @run main/othervm MD2InTrustAnchor PKIX TLSv1.2
 * @run main/othervm MD2InTrustAnchor SunX509 TLSv1.2
 */
import java.io.InputStream;
import java.io.OutputStream;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import sun.security.testlibrary.CertificateBuilder;
import sun.security.util.KnownOIDs;

public class MD2InTrustAnchor {

    private final X509Certificate caCertificate;
    private final X509Certificate targetCert;
    private final KeyPair targetKeys;

    private static final char[] PASSPHRASE = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    private static final CountDownLatch sync = new CountDownLatch(1);

    /*
     * Turn on SSL debugging?
     */
    private static final boolean DEBUG = Boolean.getBoolean("test.debug");

    public MD2InTrustAnchor() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);

        KeyPair caKey = kpg.generateKeyPair();
        caCertificate = CertificateBuilder.createCACertificateBuilder(
            "C=US, O=Java, OU=SunJSSE Test Serivce", caKey)
            .addKeyUsageExt(new boolean[]{false, false, false, false, false, true, true, false, false})
            .build(null, caKey.getPrivate(), "MD2withRSA");

        targetKeys = kpg.generateKeyPair();
        targetCert = CertificateBuilder.createClientCertificateBuilder(
            "C=US, O=Java, OU=SunJSSE Test Serivce, CN=localhost",
            targetKeys.getPublic(), caKey.getPublic())
            .addKeyUsageExt(new boolean[]{true, true, true, false, true, false, false, false, false})
            .addExtendedKeyUsageExt(
                    List.of(KnownOIDs.serverAuth.value(), KnownOIDs.clientAuth.value(),
                            KnownOIDs.codeSigning.value()))
            .build(caCertificate, caKey.getPrivate(), "MD5withRSA");

        if (DEBUG) {
            CertificateBuilder.printCertificate(caCertificate, System.out);
            CertificateBuilder.printCertificate(targetCert, System.out);
        }
    }

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    private void doServerSide() throws Exception {
        SSLContext context = generateSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        try (SSLServerSocket sslServerSocket
                = (SSLServerSocket) sslssf.createServerSocket(serverPort)) {
            sslServerSocket.setNeedClientAuth(true);
            serverPort = sslServerSocket.getLocalPort();
            /*
            * Signal Client, we're ready for his connect.
             */
            System.out.println("Signal server ready");
            sync.countDown();

            System.out.println("Waiting for client connection");
            try (SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept()) {
                InputStream sslIS = sslSocket.getInputStream();
                OutputStream sslOS = sslSocket.getOutputStream();

                sslIS.read();
                sslOS.write('A');
                sslOS.flush();
            }
        }
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    private void doClientSide() throws Exception {

        /*
         * Wait for server to get started.
         */
        System.out.println("Waiting for server ready");
        sync.await();

        SSLContext context = generateSSLContext();
        SSLSocketFactory sslsf = context.getSocketFactory();

        System.out.println("Connect to server on port: " + serverPort);
        try (SSLSocket sslSocket
                = (SSLSocket) sslsf.createSocket("localhost", serverPort)) {
            // enable the specified TLS protocol
            sslSocket.setEnabledProtocols(new String[]{tlsProtocol});

            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslOS.write('B');
            sslOS.flush();
            sslIS.read();
        }
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */
    private static String tmAlgorithm;        // trust manager
    private static String tlsProtocol;        // trust manager

    private static void parseArguments(String[] args) {
        tmAlgorithm = args[0];
        tlsProtocol = args[1];
    }

    private SSLContext generateSSLContext() throws Exception {

        // generate certificate from cert string
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        ks.setCertificateEntry("RSA Export Signer", caCertificate);

        // It's not allowed to send MD2 signed certificate to peer,
        // even it may be a trusted certificate. Then we will not
        // place the trusted certficate in the chain.
        Certificate[] chain = new Certificate[1];
        chain[0] = targetCert;

        // import the key entry.
        ks.setKeyEntry("Whatever", targetKeys.getPrivate(), PASSPHRASE, chain);

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmAlgorithm);
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance(tlsProtocol);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init(ks, PASSPHRASE);

        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ctx;
    }

    // use any free port by default
    private volatile int serverPort = 0;

    private volatile Exception serverException = null;

    public static void main(String[] args) throws Exception {
        // MD5 is used in this test case, don't disable MD5 algorithm.
        Security.setProperty("jdk.certpath.disabledAlgorithms",
                "MD2, RSA keySize < 1024");
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DH keySize < 768");

        if (DEBUG) {
            System.setProperty("javax.net.debug", "all");
        }

        /*
         * Get the customized arguments.
         */
        parseArguments(args);
        /*
         * Start the tests.
         */
        new MD2InTrustAnchor().runTest();
    }

    private Thread serverThread = null;

    /*
     * Used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    public void runTest() throws Exception {
        startServerThread();
        doClientSide();

        /*
         * Wait for other side to close down.
         */
        serverThread.join();

        if (serverException != null) {
            throw serverException;
        }
    }

    private void startServerThread() {
        serverThread = new Thread() {
            @Override
            public void run() {
                try {
                    doServerSide();
                } catch (Exception e) {
                    /*
                     * Our server thread just died.
                     *
                     * Release the client, if not active already...
                     */
                    System.err.println("Server died...");
                    e.printStackTrace(System.out);
                    serverException = e;
                    sync.countDown();
                }
            }
        };

        serverThread.start();
    }
}
