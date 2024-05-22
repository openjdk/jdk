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
 * @bug 7068321
 * @summary Support TLS Server Name Indication (SNI) Extension in JSSE Server
 * @library /test/jdk/java/security/testlibrary
 * @modules java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 *          java.base/sun.security.validator
 *          java.base/sun.security.x509
 * @build CertificateBuilder
 * @run main/othervm SSLSocketSNISensitive PKIX www.example.com
 * @run main/othervm SSLSocketSNISensitive SunX509 www.example.com
 * @run main/othervm SSLSocketSNISensitive PKIX www.example.net
 * @run main/othervm SSLSocketSNISensitive SunX509 www.example.net
 * @run main/othervm SSLSocketSNISensitive PKIX www.invalid.com
 * @run main/othervm SSLSocketSNISensitive SunX509 www.invalid.com
 */

import java.security.*;
import java.util.*;
import java.io.*;
import javax.net.ssl.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import sun.security.testlibrary.CertificateBuilder;

// Note: this test case works only on TLS 1.2 and prior versions because of
// the use of MD5withRSA signed certificate.
public class SSLSocketSNISensitive {
    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = false;
    static X509Certificate caCertificate;
    static X509Certificate[] serverCerts;
    static KeyPair[] serverKeys;
    static X509Certificate[] clientCerts;
    static KeyPair[] clientKeys;

    static char passphrase[] = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = Boolean.getBoolean("test.debug");

    static void setupCertificates() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);

        KeyPair caKey = kpg.generateKeyPair();
        caCertificate = CertificateBuilder.newSelfSignedCA(
                "C=US, O=Java, OU=SunJSSE Test Serivce", caKey)
                .build(null, caKey.getPrivate(), "MD5withRSA");

        KeyPair trustedKeysA = kpg.generateKeyPair();
        X509Certificate trustedCertA = CertificateBuilder.newEndEntity(
                "C=US, O=Java, OU=SunJSSE Test Serivce, CN=www.example.com",
                trustedKeysA.getPublic(), caKey.getPublic())
                .build(caCertificate, caKey.getPrivate(), "MD5withRSA");

        KeyPair trustedKeysB = kpg.generateKeyPair();
        X509Certificate trustedCertB = CertificateBuilder.newEndEntity(
                "C=US, O=Java, OU=SunJSSE Test Serivce, CN=www.example.net",
                trustedKeysB.getPublic(), caKey.getPublic())
                .build(caCertificate, caKey.getPrivate(), "MD5withRSA");

        KeyPair trustedKeysC = kpg.generateKeyPair();
        X509Certificate trustedCertC = CertificateBuilder.newEndEntity(
                "C=US, O=Java, OU=SunJSSE Test Serivce, CN=www.invalid.com",
                trustedKeysC.getPublic(), caKey.getPublic())
                .build(caCertificate, caKey.getPrivate(), "MD5withRSA");

        serverCerts = new X509Certificate[]{trustedCertA, trustedCertB, trustedCertC};
        serverKeys = new KeyPair[]{trustedKeysA, trustedKeysB, trustedKeysC};

        KeyPair trustedKeysD = kpg.generateKeyPair();
        X509Certificate trustedCertD = CertificateBuilder.newEndEntity(
                "C=US, O=Java, OU=SunJSSE Test Serivce, CN=InterOp Tester",
                trustedKeysD.getPublic(), caKey.getPublic())
                .build(caCertificate, caKey.getPrivate(), "MD5withRSA");

        clientCerts = new X509Certificate[]{trustedCertD};
        clientKeys = new KeyPair[]{trustedKeysD};

        if (debug) {
            System.err.println("CA Certificate:");
            CertificateBuilder.printCertificate(caCertificate, System.err);
            System.err.println("Server Certificate A:");
            CertificateBuilder.printCertificate(trustedCertA, System.err);
            System.err.println("Server Certificate B:");
            CertificateBuilder.printCertificate(trustedCertB, System.err);
            CertificateBuilder.printCertificate(trustedCertC, System.err);
            System.err.println("Client Certificate:");
            CertificateBuilder.printCertificate(trustedCertD, System.err);
        }
    }

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = generateSSLContext(false);
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        SSLServerSocket sslServerSocket =
            (SSLServerSocket)sslssf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept();
        try {
            sslSocket.setSoTimeout(5000);
            sslSocket.setSoLinger(true, 5);

            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslIS.read();
            sslOS.write('A');
            sslOS.flush();

            SSLSession session = sslSocket.getSession();
            checkCertificate(session.getLocalCertificates(),
                                                clientRequestedHostname);
        } finally {
            sslSocket.close();
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

        SSLContext context = generateSSLContext(true);
        SSLSocketFactory sslsf = context.getSocketFactory();

        SSLSocket sslSocket =
            (SSLSocket)sslsf.createSocket("localhost", serverPort);

        SNIHostName serverName = new SNIHostName(clientRequestedHostname);
        List<SNIServerName> serverNames = new ArrayList<>(1);
        serverNames.add(serverName);
        SSLParameters params = sslSocket.getSSLParameters();
        params.setServerNames(serverNames);
        sslSocket.setSSLParameters(params);

        try {
            sslSocket.setSoTimeout(5000);
            sslSocket.setSoLinger(true, 5);

            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslOS.write('B');
            sslOS.flush();
            sslIS.read();

            SSLSession session = sslSocket.getSession();
            checkCertificate(session.getPeerCertificates(),
                                                clientRequestedHostname);
        } finally {
            sslSocket.close();
        }
    }

    private static void checkCertificate(Certificate[] certs,
            String hostname) throws Exception {
        if (certs != null && certs.length != 0) {
            X509Certificate x509Cert = (X509Certificate)certs[0];

            String subject = x509Cert.getSubjectX500Principal().getName();

            if (!subject.contains(hostname)) {
                throw new Exception(
                        "Not the expected certificate: " + subject);
            }
        }
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */
    private static String tmAlgorithm;             // trust manager
    private static String clientRequestedHostname; // server name indication

    private static void parseArguments(String[] args) {
        tmAlgorithm = args[0];
        clientRequestedHostname = args[1];
    }

    private static SSLContext generateSSLContext(boolean isClient)
            throws Exception {

        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        ks.setCertificateEntry("RSA Export Signer", caCertificate);

        X509Certificate[] certs;
        KeyPair[] keys;
        if (isClient) {
            certs = clientCerts;
            keys = clientKeys;
        } else {
            certs = serverCerts;
            keys = serverKeys;
        }

        for (int i = 0; i < certs.length; i++) {
            // generate the private key.
            Certificate[] chain = new Certificate[2];
            chain[0] = certs[i];
            chain[1] = caCertificate;

            // import the key entry.
            ks.setKeyEntry("key-entry-" + i, keys[i].getPrivate(), passphrase, chain);
        }

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmAlgorithm);
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init(ks, passphrase);

        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ctx;
    }

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    public static void main(String[] args) throws Exception {
        // MD5 is used in this test case, don't disable MD5 algorithm.
        Security.setProperty("jdk.certpath.disabledAlgorithms",
                "MD2, RSA keySize < 1024");
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DH keySize < 768");

        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Get the customized arguments.
         */
        parseArguments(args);

        setupCertificates();

        /*
         * Start the tests.
         */
        new SSLSocketSNISensitive();
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    SSLSocketSNISensitive() throws Exception {
        try {
            if (separateServerThread) {
                startServer(true);
                startClient(false);
            } else {
                startClient(true);
                startServer(false);
            }
        } catch (Exception e) {
            // swallow for now.  Show later
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
         * Which side threw the error?
         */
        Exception local;
        Exception remote;
        String whichRemote;

        if (separateServerThread) {
            remote = serverException;
            local = clientException;
            whichRemote = "server";
        } else {
            remote = clientException;
            local = serverException;
            whichRemote = "client";
        }

        /*
         * If both failed, return the curthread's exception, but also
         * print the remote side Exception
         */
        if ((local != null) && (remote != null)) {
            System.out.println(whichRemote + " also threw:");
            remote.printStackTrace();
            System.out.println();
            throw local;
        }

        if (remote != null) {
            throw remote;
        }

        if (local != null) {
            throw local;
        }
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
                        System.err.println("Server died, because of " + e);
                        serverReady = true;
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            try {
                doServerSide();
            } catch (Exception e) {
                serverException = e;
            } finally {
                serverReady = true;
            }
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
                        System.err.println("Client died, because of " + e);
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            try {
                doClientSide();
            } catch (Exception e) {
                clientException = e;
            }
        }
    }
}
