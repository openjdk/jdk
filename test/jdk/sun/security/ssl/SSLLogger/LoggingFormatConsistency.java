/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8211227
 * @library ../../
 * @library /test/lib
 * @summary Tests for consistency in logging format of TLS Versions
 * @run main/othervm LoggingFormatConsistency
 */

/*
 * This test runs in another process so we can monitor the debug
 * results. The OutputAnalyzer must see correct debug output to return a
 * success.
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.security.SecurityUtils;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

public class LoggingFormatConsistency {
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

    /*
     * Is the connection ready to close?
     */
    volatile static boolean closeReady = false;

    /*
     * Turn on SSL debugging?
     */

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    Thread clientThread = null;
    Thread serverThread = null;

    private static final String pathToStores = "../../../../javax/net/ssl/etc";
    private static final String keyStoreFile = "keystore";
    private static final String trustStoreFile = "truststore";
    private static final String password = "passphrase";

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            // A non-empty set of arguments occurs when the "runTest" argument
            // is passed to the test via ProcessTools::executeTestJvm.
            //
            // This is done because an OutputAnalyzer is unable to read
            // the output of the current running JVM, and must therefore create
            // a test JVM. When this case occurs, it will inherit all specified
            // JVM properties (keyStore, trustStore, tls protocols, etc.)
            new LoggingFormatConsistency();
        } else {
            // We are in the main JVM that the test is being ran in.
            var keyStoreFileName = System.getProperty("test.src", "./") + "/" + pathToStores + "/" + keyStoreFile;
            var trustStoreFileName = System.getProperty("test.src", "./") + "/" + pathToStores + "/" + trustStoreFile;

            // Setting up JVM system properties
            var keyStoreArg = "-Djavax.net.ssl.keyStore=" + keyStoreFileName;
            var keyStorePassword = "-Djavax.net.ssl.keyStorePassword=" + password;
            var trustStoreArg = "-Djavax.net.ssl.trustStore=" + trustStoreFileName;
            var trustStorePassword = "-Djavax.net.ssl.trustStorePassword=" + password;
            var testSrc = "-Dtest.src=" + System.getProperty("test.src");
            var javaxNetDebug = "-Djavax.net.debug=all";

            var correctTlsVersionsFormat = new String[]{"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"};
            var incorrectTLSVersionsFormat = new String[]{"TLS10", "TLS11", "TLS12", "TLS13"};

            for (int i = 0; i < correctTlsVersionsFormat.length; i++) {
                String expectedTLSVersion = correctTlsVersionsFormat[i];
                String incorrectTLSVersion = incorrectTLSVersionsFormat[i];

                System.out.println("TESTING " + expectedTLSVersion);
                String activeTLSProtocol = "-Djdk.tls.client.protocols=" + expectedTLSVersion;
                var output = ProcessTools.executeTestJvm(
                        testSrc,
                        keyStoreArg,
                        keyStorePassword,
                        trustStoreArg,
                        trustStorePassword,
                        activeTLSProtocol,
                        javaxNetDebug,
                        "LoggingFormatConsistency",
                        "runTest"); // Ensuring args.length is greater than 0

                output.shouldContain(expectedTLSVersion);
                output.shouldNotContain(incorrectTLSVersion);
            }
        }
    }

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    LoggingFormatConsistency() throws Exception {
        // Test depends on these being enabled
        SecurityUtils.removeFromDisabledTlsAlgs("TLSv1", "TLSv1.1");
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
        if (serverException != null) {
            throw serverException;
        }
        if (clientException != null) {
            throw clientException;
        }
    }

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {

        SSLServerSocketFactory sslServerSocketFactory = SSLContext.getDefault().getServerSocketFactory();

        InetAddress localHost = InetAddress.getByName("localhost");
        InetSocketAddress address = new InetSocketAddress(localHost, serverPort);

        SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket();
        sslServerSocket.bind(address);
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for its connect.
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
        while (!serverReady) {
            Thread.sleep(50);
        }

        HttpsURLConnection http = null;

        /* establish http connection to server */
        URL url = new URL("https://localhost:" + serverPort+"/");
        System.out.println("url is "+url.toString());

        try {
            http = (HttpsURLConnection)url.openConnection(Proxy.NO_PROXY);

            int responseCode = http.getResponseCode();
            System.out.println("respCode = " + responseCode);
        } finally {
            if (http != null) {
                http.disconnect();
            }
            closeReady = true;
        }
    }

    void startServer(boolean newThread) throws Exception {
        if (newThread) {
            serverThread = new Thread(() -> {
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
            });
            serverThread.start();
        } else {
            doServerSide();
        }
    }

    void startClient(boolean newThread) throws Exception {
        if (newThread) {
            clientThread = new Thread(() -> {
                try {
                    doClientSide();
                } catch (Exception e) {
                    /*
                     * Our client thread just died.
                     */
                    System.err.println("Client died...");
                    clientException = e;
                }
            });
            clientThread.start();
        } else {
            doClientSide();
        }
    }
}
