/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8051498
 * @summary JEP 244: TLS Application-Layer Protocol Negotiation Extension
 * @run main/othervm SSLSocketAlpnTest h2          h2          h2
 * @run main/othervm SSLSocketAlpnTest h2          h2,http/1.1 h2
 * @run main/othervm SSLSocketAlpnTest h2,http/1.1 h2,http/1.1 h2
 * @run main/othervm SSLSocketAlpnTest http/1.1,h2 h2,http/1.1 http/1.1
 * @run main/othervm SSLSocketAlpnTest h4,h3,h2    h1,h2       h2
 * @run main/othervm SSLSocketAlpnTest EMPTY       h2,http/1.1 NONE
 * @run main/othervm SSLSocketAlpnTest h2          EMPTY       NONE
 * @run main/othervm SSLSocketAlpnTest H2          h2          ERROR
 * @run main/othervm SSLSocketAlpnTest h2          http/1.1    ERROR
 * @author Brad Wetmore
 */
import java.io.*;
import javax.net.ssl.*;

public class SSLSocketAlpnTest {

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
    static boolean separateServerThread = false;

    /*
     * Where do we find the keystores?
     */
    static String pathToStores = "../etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;

    static String[] serverAPs;
    static String[] clientAPs;
    static String expectedAP;

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
        SSLServerSocketFactory sslssf
                = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket sslServerSocket
                = (SSLServerSocket) sslssf.createServerSocket(serverPort);

        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();

        SSLParameters sslp = sslSocket.getSSLParameters();

        /*
         * The default ciphersuite ordering from the SSLContext may not
         * reflect "h2" ciphersuites as being preferred, additionally the
         * client may not send them in an appropriate order. We could resort
         * the suite list if so desired.
         */
        String[] suites = sslp.getCipherSuites();
        sslp.setCipherSuites(suites);
        sslp.setUseCipherSuitesOrder(true);  // Set server side order

        // Set the ALPN selection.
        sslp.setApplicationProtocols(serverAPs);
        sslSocket.setSSLParameters(sslp);

        sslSocket.startHandshake();

        String ap = sslSocket.getApplicationProtocol();
        System.out.println("Application Protocol: \"" + ap + "\"");

        if (ap == null) {
            throw new Exception(
                "Handshake was completed but null was received");
        }
        if (expectedAP.equals("NONE")) {
            if (!ap.isEmpty()) {
                throw new Exception("Expected no ALPN value");
            } else {
                System.out.println("No ALPN value negotiated, as expected");
            }
        } else if (!expectedAP.equals(ap)) {
            throw new Exception(expectedAP +
                " ALPN value not available on negotiated connection");
        }

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

        SSLSocketFactory sslsf
                = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket sslSocket
                = (SSLSocket) sslsf.createSocket("localhost", serverPort);

        SSLParameters sslp = sslSocket.getSSLParameters();

        /*
         * The default ciphersuite ordering from the SSLContext may not
         * reflect "h2" ciphersuites as being preferred, additionally the
         * client may not send them in an appropriate order. We could resort
         * the suite list if so desired.
         */
        String[] suites = sslp.getCipherSuites();
        sslp.setCipherSuites(suites);
        sslp.setUseCipherSuitesOrder(true);  // Set server side order

        // Set the ALPN selection.
        sslp.setApplicationProtocols(clientAPs);
        sslSocket.setSSLParameters(sslp);

        sslSocket.startHandshake();

        /*
         * Check that the resulting connection meets our defined ALPN
         * criteria.  If we were connecting to a non-JSSE implementation,
         * the server might have negotiated something we shouldn't accept.
         *
         * We were expecting H2 from server, let's make sure the
         * conditions match.
         */
        String ap = sslSocket.getApplicationProtocol();
        System.out.println("Application Protocol: \"" + ap + "\"");

        if (ap == null) {
            throw new Exception(
                "Handshake was completed but null was received");
        }
        if (expectedAP.equals("NONE")) {
            if (!ap.isEmpty()) {
                throw new Exception("Expected no ALPN value");
            } else {
                System.out.println("No ALPN value negotiated, as expected");
            }
        } else if (!expectedAP.equals(ap)) {
            throw new Exception(expectedAP +
                " ALPN value not available on negotiated connection");
        }

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();

        sslSocket.close();
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
        String keyFilename
                = System.getProperty("test.src", ".") + "/" + pathToStores
                + "/" + keyStoreFile;
        String trustFilename
                = System.getProperty("test.src", ".") + "/" + pathToStores
                + "/" + trustStoreFile;

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);

        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        // Validate parameters
        if (args.length != 3) {
            throw new Exception("Invalid number of test parameters");
        }
        serverAPs = convert(args[0]);
        clientAPs = convert(args[1]);
        expectedAP = args[2];

        /*
         * Start the tests.
         */
        try {
            new SSLSocketAlpnTest();
        } catch (SSLHandshakeException she) {
            if (args[2].equals("ERROR")) {
                System.out.println("Caught the expected exception: " + she);
            } else {
                throw she;
            }
        }

        System.out.println("Test Passed.");
    }

    /*
     * Convert a comma-separated list into an array of strings.
     */
    private static String[] convert(String list) {
        String[] strings;

        if (list.equals("EMPTY")) {
            return new String[0];
        }

        if (list.indexOf(',') > 0) {
            strings = list.split(",");
        } else {
            strings = new String[]{ list };
        }

        return strings;
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    SSLSocketAlpnTest() throws Exception {
        Exception startException = null;
        try {
            if (separateServerThread) {
                startServer(true);
                startClient(false);
            } else {
                startClient(true);
                startServer(false);
            }
        } catch (Exception e) {
            startException = e;
        }

        /*
         * Wait for other side to close down.
         */
        if (separateServerThread) {
            if (serverThread != null) {
                serverThread.join();
            }
        } else {
            if (clientThread != null) {
                clientThread.join();
            }
        }

        /*
         * When we get here, the test is pretty much over.
         * Which side threw the error?
         */
        Exception local;
        Exception remote;

        if (separateServerThread) {
            remote = serverException;
            local = clientException;
        } else {
            remote = clientException;
            local = serverException;
        }

        Exception exception = null;

        /*
         * Check various exception conditions.
         */
        if ((local != null) && (remote != null)) {
            // If both failed, return the curthread's exception.
            local.initCause(remote);
            exception = local;
        } else if (local != null) {
            exception = local;
        } else if (remote != null) {
            exception = remote;
        } else if (startException != null) {
            exception = startException;
        }

        /*
         * If there was an exception *AND* a startException,
         * output it.
         */
        if (exception != null) {
            if (exception != startException && startException != null) {
                exception.addSuppressed(startException);
            }
            throw exception;
        }

        // Fall-through: no exception to throw!
    }

    void startServer(boolean newThread) throws Exception {
        if (newThread) {
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
                @Override
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
            try {
                doClientSide();
            } catch (Exception e) {
                clientException = e;
            }
        }
    }
}
