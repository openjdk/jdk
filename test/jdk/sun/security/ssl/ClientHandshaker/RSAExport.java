/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 6690018
 * @summary RSAClientKeyExchange NullPointerException
 * @library /test/jdk/java/security/testlibrary
 * @modules java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 *          java.base/sun.security.validator
 *          java.base/sun.security.x509
 * @build CertificateBuilder
 * @run main/othervm RSAExport
 */



import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import sun.security.testlibrary.CertificateBuilder;

public class RSAExport {

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;


    static char[] passphrase = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    static boolean debug = Boolean.getBoolean("test.debug");

    /*
     * If the client or server is doing some kind of object creation
     * that the other side depends on, and that thread prematurely
     * exits, you may experience a hang.  The test harness will
     * terminate all hung threads after its timeout has expired,
     * currently 3 minutes by default, but you might try to be
     * smart about it....
     */

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLServerSocketFactory sslssf =
                getSSLContext(true).getServerSocketFactory();
        SSLServerSocket sslServerSocket =
                (SSLServerSocket) sslssf.createServerSocket(serverPort);

        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for this connect.
         */
        serverReady = true;

        // Enable RSA_EXPORT cipher suites only.
        try {
            String enabledSuites[] = {
                "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA"};
            sslServerSocket.setEnabledCipherSuites(enabledSuites);
        } catch (IllegalArgumentException iae) {
            // ignore the exception a cipher suite is unsupported.
        }

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();

        sslSocket.close();
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

        SSLSocketFactory sslsf =
                getSSLContext(false).getSocketFactory();
        SSLSocket sslSocket = (SSLSocket)
                sslsf.createSocket("localhost", serverPort);

        // Enable RSA_EXPORT cipher suites only.
        try {
            String enabledSuites[] = {
                "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA"};
            sslSocket.setEnabledCipherSuites(enabledSuites);
        } catch (IllegalArgumentException iae) {
            // ignore the exception a cipher suite is unsupported.
        }

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();

        sslSocket.close();
    }

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    private final X509Certificate caCertificate;
    private final X509Certificate serverCertificate;
    private final KeyPair serverKeys;

    public static void main(String[] args) throws Exception {
        // reset the security property to make sure that the algorithms
        // and keys used in this test are not disabled.
        Security.setProperty("jdk.certpath.disabledAlgorithms", "MD2");
        Security.setProperty("jdk.tls.disabledAlgorithms", "MD2");

        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Start the tests.
         */
        new RSAExport().run();
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    RSAExport() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);

        KeyPair caKeys = kpg.generateKeyPair();
        caCertificate = CertificateBuilder.newSelfSignedCA(
            "C = US, ST = Some-State, O = Some Org, CN = Someone", caKeys)
            .build(null, caKeys.getPrivate(), "MD5withRSA");

        kpg.initialize(512);
        serverKeys = kpg.generateKeyPair();
        serverCertificate = CertificateBuilder.newEndEntity(
            "C = US, ST = Some-State, O = Some Org, CN = SomeoneExport",
            serverKeys.getPublic(), caKeys.getPublic())
            .build(caCertificate, caKeys.getPrivate(), "SHA1withRSA");

        if (debug) {
            System.err.println("CA CERTIFICATE");
            CertificateBuilder.printCertificate(caCertificate, System.err);

            System.err.println("SERVER CERTIFICATE");
            CertificateBuilder.printCertificate(serverCertificate, System.err);
        }
    }

    void run() throws Exception {
        if (separateServerThread) {
            startServer(true);
            startClient(false);
        } else {
            startClient(true);
            startServer(false);
        }

        /*
         * Wait for other side to close down.
         */
        if (separateServerThread) {
            serverThread.join();
        } else {
            clientThread.join();
        }

        /*
         * When we get here, the test is pretty much over.
         *
         * If the main thread excepted, that propagates back
         * immediately.  If the other thread threw an exception, we
         * should report back.
         */
        if (serverException != null)
            throw serverException;
        if (clientException != null)
            throw clientException;
    }

    void startServer(boolean newThread) throws Exception {
        if (newThread) {
            serverThread = new Thread() {
                public void run() {
                    try {
                        doServerSide();
                    } catch (Exception e) {
                        /*
                         * Our server thread just died.
                         *
                         * Release the client, if not active already...
                         */
                        System.err.println("Server died..." + e);
                        serverReady = true;
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            doServerSide();
        }
    }

    void startClient(boolean newThread) throws Exception {
        if (newThread) {
            clientThread = new Thread() {
                public void run() {
                    try {
                        doClientSide();
                    } catch (Exception e) {
                        /*
                         * Our client thread just died.
                         */
                        System.err.println("Client died...");
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            doClientSide();
        }
    }

    // Get the SSL context
    private SSLContext getSSLContext(boolean authnRequired) throws Exception {
        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        // import the trusted cert
        ks.setCertificateEntry("RSA Export Signer", caCertificate);

        if (authnRequired) {
            Certificate[] chain = new Certificate[2];
            chain[0] = serverCertificate;
            chain[1] = caCertificate;

            // import the key entry.
            ks.setKeyEntry("RSA Export", serverKeys.getPrivate(), passphrase, chain);
        }

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance("TLS");
        if (authnRequired) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);

            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else {
            ctx.init(null, tmf.getTrustManagers(), null);
        }

        return ctx;
    }
}
