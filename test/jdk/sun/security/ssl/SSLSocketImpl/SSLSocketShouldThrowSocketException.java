/*
 * Copyright (c) 2017, 2020, Amazon and/or its affiliates. All rights reserved.
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
 * @bug 8214339
 * @summary When a SocketException is thrown by the underlying layer, It
 *      should be thrown as is and not be transformed to an SSLException.
 * @run main/othervm SSLSocketShouldThrowSocketException
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.security.*;
import javax.net.ssl.*;

public class SSLSocketShouldThrowSocketException {

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

    /*
     * Where do we find the keystores?
     */
    static String pathToStores = "../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Was the client responsible for closing the socket
     */
    volatile static boolean clientClosed = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;

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
     * The server accepts 2 requests, The first request does not send
     * back a handshake message. The second request sends back a
     * handshake message.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLServerSocketFactory sslssf =
            (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket sslServerSocket =
            (SSLServerSocket) sslssf.createServerSocket(serverPort);

        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        System.err.println("Server accepting: " + System.nanoTime());
        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        System.err.println("Server accepted: " + System.nanoTime());

        System.err.println("Server accepting: " + System.nanoTime());
        sslSocket = (SSLSocket) sslServerSocket.accept();
        sslSocket.startHandshake();
        System.err.println("Server accepted: " + System.nanoTime());

        while (!clientClosed) {
            Thread.sleep(500);
        }
    }

    Socket initilizeClientSocket() throws Exception {
        /*
         * Wait for server to get started.
         */
        System.out.println("waiting on server");
        while (!serverReady) {
            Thread.sleep(50);
        }
        Thread.sleep(500);
        System.out.println("server ready");

        Socket baseSocket = new Socket("localhost", serverPort);
        baseSocket.setSoTimeout(1000);
        return baseSocket;
    }

    SSLSocket initilizeSSLSocket(Socket baseSocket) throws Exception {
        SSLSocketFactory sslsf =
            (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket sslSocket = (SSLSocket)
            sslsf.createSocket(baseSocket, "localhost", serverPort, false);
        return sslSocket;
    }

    /*
     * The client should throw a SocketException without wrapping it
     * during the handshake process.
     */
    void doClientSideHandshakeClose() throws Exception {

        Socket baseSocket = initilizeClientSocket();
        SSLSocket sslSocket = initilizeSSLSocket(baseSocket);

        Thread aborter = new Thread() {
            @Override
            public void run() {

                try {
                    Thread.sleep(10);
                    System.err.println("Closing the client socket : " + System.nanoTime());
                    baseSocket.close();
                } catch (Exception ieo) {
                    ieo.printStackTrace();
                }
            }
        };

        aborter.start();

        try {
            // handshaking
            System.err.println("Client starting handshake: " + System.nanoTime());
            sslSocket.startHandshake();
            throw new Exception("Start handshake did not throw an exception");
        } catch (SocketException se) {
            System.err.println("Caught Expected SocketException");
        }

        aborter.join();

    }

    volatile static boolean handshakeCompleted = false;

    /*
     * The client should throw SocketException without wrapping it
     * while waiting to read data from the socket.
     */
    void doClientSideDataClose() throws Exception {

        Socket baseSocket = initilizeClientSocket();
        SSLSocket sslSocket = initilizeSSLSocket(baseSocket);

        handshakeCompleted = false;

        Thread aborter = new Thread() {
            @Override
            public void run() {

                try {
                    while (!handshakeCompleted) {
                        Thread.sleep(10);
                    }
                    System.err.println("Closing the client socket : " + System.nanoTime());
                    baseSocket.close();
                } catch (Exception ieo) {
                    ieo.printStackTrace();
                }
            }
        };

        aborter.start();

        try {
            // handshaking
            System.err.println("Client starting handshake: " + System.nanoTime());
            sslSocket.startHandshake();
            handshakeCompleted = true;
            System.err.println("Reading data from server");
            BufferedReader is = new BufferedReader(
                    new InputStreamReader(sslSocket.getInputStream()));
            String data = is.readLine();
            throw new Exception("Start handshake did not throw an exception");
        } catch (SocketException se) {
            System.err.println("Caught Expected SocketException");
        }

        aborter.join();

    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;

    volatile byte[] serverDigest = null;

    public static void main(String[] args) throws Exception {
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

        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Start the tests.
         */
        new SSLSocketShouldThrowSocketException();
    }

    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the server side.
     */
    SSLSocketShouldThrowSocketException() throws Exception {
        startServer();
        startClient();

        clientClosed = true;
        System.err.println("Client closed: " + System.nanoTime());

        /*
         * Wait for other side to close down.
         */
        serverThread.join();

        /*
         * When we get here, the test is pretty much over.
         *
         * If the main thread excepted, that propagates back
         * immediately.  If the other thread threw an exception, we
         * should report back.
         */
        if (serverException != null) {
            System.out.print("Server Exception:");
            throw serverException;
        }
    }

    void startServer() throws Exception {
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
                    System.err.println(e);
                    serverReady = true;
                    serverException = e;
                }
            }
        };
        serverThread.start();
    }

    void startClient() throws Exception {
        doClientSideHandshakeClose();
        doClientSideDataClose();
    }
}
