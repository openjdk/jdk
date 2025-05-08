/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6618387
 * @summary SSL client sessions do not close cleanly. A TCP reset occurs
 *      instead of a close_notify alert.
 * @library /test/lib
 *
 * @run main/othervm -Dtest.separateThreads=true CloseKeepAliveCached false
 * @run main/othervm -Dtest.separateThreads=true CloseKeepAliveCached true
 *
 * @comment SunJSSE does not support dynamic system properties, no way to re-use
 *    system properties in samevm/agentvm mode.
 *    if "MainThread, called close()" found, the test passed. Otherwise,
 *    if "Keep-Alive-Timer: called close()", the test failed.
 */

import jdk.test.lib.Asserts;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

public class CloseKeepAliveCached {
    public static final String CLOSE_THE_SSL_CONNECTION_PASSIVE = "close the SSL connection (passive)";

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
    static boolean separateServerThread = Boolean.getBoolean("test.separateThreads");

    /*
     * Where do we find the keystores?
     */
    static String pathToStores = "../../../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    private SSLServerSocket sslServerSocket = null;

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLServerSocketFactory sslSsf =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        sslServerSocket =
                (SSLServerSocket) sslSsf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;
        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();

        // getting input and output streams
        InputStream is = sslSocket.getInputStream();
        BufferedReader r = new BufferedReader(
                new InputStreamReader(is));
        PrintStream out = new PrintStream(
                new BufferedOutputStream(
                        sslSocket.getOutputStream()));

        for (int i = 0; i < 3 && !sslSocket.isClosed(); i++) {
            // read request
            String x;
            while ((x = r.readLine()) != null) {
                if (x.isEmpty()) {
                    break;
                }
            }

            /* send the response headers and body */
            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Keep-Alive: timeout=15, max=100\r\n");
            out.print("Connection: Keep-Alive\r\n");
            out.print("Content-Type: text/html; charset=iso-8859-1\r\n");
            out.print("Content-Length: 9\r\n");
            out.print("\r\n");
            out.print("Testing\r\n");
            out.flush();

            Thread.sleep(50);
        }
        sslSocket.close();
        sslServerSocket.close();
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

        HostnameVerifier reservedHV =
                HttpsURLConnection.getDefaultHostnameVerifier();
        try {
            HttpsURLConnection http;

            /* establish http connection to server */
            URL url = new URI("https://localhost:" + serverPort + "/").toURL();
            HttpsURLConnection.setDefaultHostnameVerifier(new NameVerifier());
            http = (HttpsURLConnection) url.openConnection();
            InputStream is = http.getInputStream();
            while (is.read() != -1) ;
            is.close();

            url = new URI("https://localhost:" + serverPort + "/").toURL();
            http = (HttpsURLConnection) url.openConnection();
            is = http.getInputStream();
            while (is.read() != -1) ;

            // if inputstream.close() called, the http.disconnect() will
            // not able to close the cached connection. If application
            // want to close the keep-alive cached connection immediately
            // with httpURLConnection.disconnect(), they should not call
            // inputstream.close() explicitly, the
            // httpURLConnection.disconnect() will do it internally.
            // is.close();

            // close the connection, sending close_notify to peer.
            // otherwise, the connection will be closed by Finalizer or
            // Keep-Alive-Timer if timeout.
            http.disconnect();
        } catch (IOException ioex) {
            if (sslServerSocket != null) {
                sslServerSocket.close();
            }
            throw ioex;
        } finally {
            HttpsURLConnection.setDefaultHostnameVerifier(reservedHV);
        }
    }

    static class NameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
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

    public static void main(String[] args) throws Exception {
        separateServerThread = Boolean.parseBoolean(args[0]);
        System.out.printf("separateServerThread: %s%n", separateServerThread);

        String keyFilename =
                System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + keyStoreFile;
        String trustFilename =
                System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + trustStoreFile;

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);

        System.setProperty("javax.net.debug", "all");

        // setting up the error stream for further analysis
        var errorCapture = new ByteArrayOutputStream();
        var errorStream = new PrintStream(errorCapture);
        var originalErr = System.err; // saving the initial error stream, so it can be restored
        System.setErr(errorStream);

        /*
         * Start the tests.
         */
        try {
            new CloseKeepAliveCached();
        } finally {
            // this will allow the error stream to be printed in case of an exception inside for debugging purposes
            System.setErr(originalErr);
            if (Boolean.getBoolean("test.debug")) {
                System.err.println(errorCapture);
            }
        }

        // Parses the captured error stream, which is used by debug, to find out who closed the SSL connection
        // example of pass: javax.net.ssl|DEBUG|91|MainThread|...|close the SSL connection (passive)
        // example of fail: javax.net.ssl|DEBUG|C1|Keep-Alive-Timer|...|close the SSL connection (passive)
        var isTestPassed = false;
        for (final String line : errorCapture.toString().split("\n")) {
            if (line.contains(CLOSE_THE_SSL_CONNECTION_PASSIVE) &&
                line.contains("MainThread")) {

                System.out.println("close was called by the MainThread: ");
                System.out.println(line);

                isTestPassed = true;
                break;
            } else if (line.contains(CLOSE_THE_SSL_CONNECTION_PASSIVE) &&
                       line.contains("Keep-Alive-Timer")) {

                System.out.println("close was called by the Keep-Alive-Timer: ");
                System.out.println(line);

                throw new RuntimeException("SSL connection was closed by the Keep-Alive-Timer. Should have been MainThread");
            }
        }

        Asserts.assertTrue(isTestPassed, "Test pass result");
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    CloseKeepAliveCached() throws Exception {
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
