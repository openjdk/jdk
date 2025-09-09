/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary X509 certificate hostname checking is broken in JDK1.6.0_10
 * @bug 6766775
 * @library /test/lib
 * @run main/othervm IPIdentities
 * @author Xuelei Fan
 */


import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;

import java.security.cert.Certificate;
import java.math.BigInteger;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.security.CertificateBuilder;
import jdk.test.lib.security.CertificateBuilder.KeyUsage;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SerialNumber;
import sun.security.x509.X500Name;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;


public class IPIdentities {

    /*
     * =============================================================
     * Set the various variables needed for the tests, then
     * specify what tests to run on each side.
     */

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;

    static X509Certificate trustedCert;

    static X509Certificate serverCert;

    static X509Certificate clientCert;

    static KeyPair serverKeys;
    static KeyPair clientKeys;

    static char passphrase[] = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Is the connection ready to close?
     */
    volatile static boolean closeReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = Boolean.getBoolean("test.debug");

    private SSLServerSocket sslServerSocket = null;

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = getSSLContext(trustedCert, serverCert,
            serverKeys, passphrase);
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        // doClientSide() connects to the loopback address
        InetAddress loopback = InetAddress.getLoopbackAddress();
        InetSocketAddress address = new InetSocketAddress(loopback, serverPort);

        sslServerSocket =
            (SSLServerSocket) sslssf.createServerSocket();
        sslServerSocket.bind(address);
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        sslSocket.setNeedClientAuth(true);

        PrintStream out =
                new PrintStream(sslSocket.getOutputStream());

        try {
            // ignore request data

            // send the response
            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Content-Type: text/html; charset=iso-8859-1\r\n");
            out.print("Content-Length: "+ 9 +"\r\n");
            out.print("\r\n");
            out.print("Testing\r\n");
            out.flush();
        } finally {
             // close the socket
             while (!closeReady) {
                 Thread.sleep(50);
             }

             System.out.println("Server closing socket");
             sslSocket.close();
             serverReady = false;
        }

    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {
        SSLContext reservedSSLContext = SSLContext.getDefault();
        try {
            SSLContext context = getSSLContext(trustedCert, clientCert,
                    clientKeys, passphrase);
            SSLContext.setDefault(context);

            /*
             * Wait for server to get started.
             */
            while (!serverReady) {
                Thread.sleep(50);
            }

            HttpsURLConnection http = null;

            /* establish http connection to server */
            URL url = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(serverPort)
                .path("/")
                .toURL();
            System.out.println("url is "+url.toString());

            try {
                http = (HttpsURLConnection)url.openConnection(Proxy.NO_PROXY);

                int respCode = http.getResponseCode();
                System.out.println("respCode = "+respCode);
            } finally {
                if (http != null) {
                    http.disconnect();
                }
                closeReady = true;
            }
        } finally {
            SSLContext.setDefault(reservedSSLContext);
        }
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    private static X509Certificate createTrustedCert(KeyPair caKeys) throws Exception {
        SecureRandom random = new SecureRandom();

        KeyIdentifier kid = new KeyIdentifier(caKeys.getPublic());
        GeneralNames gns = new GeneralNames();
        GeneralName name = new GeneralName(new X500Name(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US"));
        gns.add(name);
        BigInteger serialNumber = BigInteger.valueOf(random.nextLong(1000000)+1);
        return CertificateBuilder.newCertificateBuilder(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US",
                caKeys.getPublic(), caKeys.getPublic())
                .setSerialNumber(serialNumber)
                .addExtension(new AuthorityKeyIdentifierExtension(kid, gns,
                        new SerialNumber(serialNumber)))
                .addBasicConstraintsExt(true, true, -1)
                .setOneHourValidity()
                .build(null, caKeys.getPrivate(), "MD5WithRSA");
    }

    private static void setupCertificates() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        KeyPair caKeys = kpg.generateKeyPair();
        serverKeys = kpg.generateKeyPair();
        clientKeys = kpg.generateKeyPair();

        trustedCert = createTrustedCert(caKeys);
        if (debug) {
            System.out.println("----------- Trusted Cert -----------");
            CertificateBuilder.printCertificate(trustedCert, System.out);
        }

        serverCert = CertificateBuilder.newCertificateBuilder(
                "O=Some-Org, L=Some-City, ST=Some-State, C=US",
                serverKeys.getPublic(), caKeys.getPublic(),
                KeyUsage.DIGITAL_SIGNATURE, KeyUsage.NONREPUDIATION, KeyUsage.KEY_ENCIPHERMENT)
                .addBasicConstraintsExt(false, false, -1)
                .addExtension(CertificateBuilder.createIPSubjectAltNameExt(true, "127.0.0.1"))
                .setOneHourValidity()
                .build(trustedCert, caKeys.getPrivate(), "MD5WithRSA");
        if (debug) {
            System.out.println("----------- Server Cert -----------");
            CertificateBuilder.printCertificate(serverCert, System.out);
        }

        clientCert = CertificateBuilder.newCertificateBuilder(
                "CN=localhost, OU=SSL-Client, O=Some-Org, L=Some-City, ST=Some-State, C=US",
                clientKeys.getPublic(), caKeys.getPublic(),
                KeyUsage.DIGITAL_SIGNATURE, KeyUsage.NONREPUDIATION, KeyUsage.KEY_ENCIPHERMENT)
                .addExtension(CertificateBuilder.createIPSubjectAltNameExt(true, "127.0.0.1"))
                .addBasicConstraintsExt(false, false, -1)
                .setOneHourValidity()
                .build(trustedCert, caKeys.getPrivate(), "MD5WithRSA");
        if (debug) {
            System.out.println("----------- Client Cert -----------");
            CertificateBuilder.printCertificate(clientCert, System.out);
        }
    }

    public static void main(String args[]) throws Exception {
        // MD5 is used in this test case, don't disable MD5 algorithm.
        Security.setProperty("jdk.certpath.disabledAlgorithms",
                "MD2, RSA keySize < 1024");
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DH keySize < 768");

        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        setupCertificates();

        /*
         * Start the tests.
         */
        new IPIdentities();
    }

    Thread clientThread = null;
    Thread serverThread = null;
    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    IPIdentities() throws Exception {
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
                        System.err.println("Server died...");
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

    // get the ssl context
    private static SSLContext getSSLContext(X509Certificate trustedCert,
            X509Certificate keyCert, KeyPair key, char[] passphrase) throws Exception {

        // create a key store
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);

        // import the trused cert
        ks.setCertificateEntry("RSA Export Signer", trustedCert);

        if (keyCert != null) {
            Certificate[] chain = new Certificate[2];
            chain[0] = keyCert;
            chain[1] = trustedCert;

            // import the key entry.
            ks.setKeyEntry("Whatever", key.getPrivate(), passphrase, chain);
        }

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");

        if (keyCert != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);

            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else {
            ctx.init(null, tmf.getTrustManagers(), null);
        }

        return ctx;
    }

}
