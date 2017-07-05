/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Helper class for JSSE tests.
 *
 * Please run in othervm mode.  SunJSSE does not support dynamic system
 * properties, no way to re-use system properties in samevm/agentvm mode.
 */
public class SSLTest {

    public static final String TEST_SRC = System.getProperty("test.src", ".");

    /*
     * Where do we find the keystores?
     */
    public static final String PATH_TO_STORES = "../etc";
    public static final String KEY_STORE_FILE = "keystore";
    public static final String TRUST_STORE_FILE = "truststore";
    public static final String PASSWORD = "passphrase";

    public static final int FREE_PORT = 0;

    // in seconds
    public static final long CLIENT_SIGNAL_TIMEOUT = 30L;
    public static final long SERVER_SIGNAL_TIMEOUT = 90L;

    // in millis
    public static final int CLIENT_TIMEOUT = 15000;
    public static final int SERVER_TIMEOUT = 30000;

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    private boolean separateServerThread = false;

    /*
     * What's the server port?  Use any free port by default
     */
    private volatile int serverPort;

    private volatile Exception serverException;
    private volatile Exception clientException;

    private Thread clientThread;
    private Thread serverThread;

    private Peer serverPeer;
    private Peer clientPeer;

    private Application serverApplication;
    private Application clientApplication;

    private SSLContext context;

    /*
     * Is the server ready to serve?
     */
    private final CountDownLatch serverReadyCondition = new CountDownLatch(1);

    /*
     * Is the client ready to handshake?
     */
    private final CountDownLatch clientReadyCondition = new CountDownLatch(1);

    /*
     * Is the server done?
     */
    private final CountDownLatch serverDoneCondition = new CountDownLatch(1);

    /*
     * Is the client done?
     */
    private final CountDownLatch clientDoneCondition = new CountDownLatch(1);

    /*
     * Public API.
     */

    public static interface Peer {
        void run(SSLTest test) throws Exception;
    }

    public static interface Application {
        void run(SSLSocket socket, SSLTest test) throws Exception;
    }

    public static void debug() {
        debug("ssl");
    }

    public static void debug(String mode) {
        System.setProperty("javax.net.debug", mode);
    }

    public static void setup(String keyFilename, String trustFilename,
            String password) {

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", password);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", password);
    }

    public static void setup() throws Exception {
        String keyFilename = TEST_SRC + "/" + PATH_TO_STORES + "/"
                + KEY_STORE_FILE;
        String trustFilename = TEST_SRC + "/" + PATH_TO_STORES + "/"
                + TRUST_STORE_FILE;

        setup(keyFilename, trustFilename, PASSWORD);
    }

    public static void print(String message, Throwable... errors) {
        synchronized (System.out) {
            System.out.println(message);
            Arrays.stream(errors).forEach(e -> e.printStackTrace(System.out));
        }
    }

    public static KeyStore loadJksKeyStore(String filename, String password)
            throws Exception {

        return loadKeyStore(filename, password, "JKS");
    }

    public static KeyStore loadKeyStore(String filename, String password,
            String type) throws Exception {

        KeyStore keystore = KeyStore.getInstance(type);
        try (FileInputStream fis = new FileInputStream(filename)) {
            keystore.load(fis, password.toCharArray());
        }
        return keystore;
    }

    // Try to accept a connection in 30 seconds.
    public static SSLSocket accept(SSLServerSocket sslServerSocket)
            throws IOException {

        return accept(sslServerSocket, SERVER_TIMEOUT);
    }

    public static SSLSocket accept(SSLServerSocket sslServerSocket, int timeout)
            throws IOException {

        try {
            sslServerSocket.setSoTimeout(timeout);
            return (SSLSocket) sslServerSocket.accept();
        } catch (SocketTimeoutException ste) {
            sslServerSocket.close();
            return null;
        }
    }

    public SSLTest setSeparateServerThread(boolean separateServerThread) {
        this.separateServerThread = separateServerThread;
        return this;
    }

    public SSLTest setServerPort(int serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    public int getServerPort() {
        return serverPort;
    }

    public SSLTest setSSLContext(SSLContext context) {
        this.context = context;
        return this;
    }

    public SSLContext getSSLContext() {
        return context;
    }

    public SSLServerSocketFactory getSSLServerSocketFactory() {
        if (context != null) {
            return context.getServerSocketFactory();
        }

        return (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
    }

    public SSLSocketFactory getSSLSocketFactory() {
        if (context != null) {
            return context.getSocketFactory();
        }

        return (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    public void signalServerReady() {
        serverReadyCondition.countDown();
    }

    public void signalServerDone() {
        serverDoneCondition.countDown();
    }

    public boolean waitForClientSignal(long timeout, TimeUnit unit)
            throws InterruptedException {

        return clientReadyCondition.await(timeout, unit);
    }

    public boolean waitForClientSignal() throws InterruptedException {
        return waitForClientSignal(CLIENT_SIGNAL_TIMEOUT, TimeUnit.SECONDS);
    }

    public boolean waitForClientDone(long timeout, TimeUnit unit)
            throws InterruptedException {

        return clientDoneCondition.await(timeout, unit);
    }

    public boolean waitForClientDone() throws InterruptedException {
        return waitForClientDone(CLIENT_SIGNAL_TIMEOUT, TimeUnit.SECONDS);
    }

    public void signalClientReady() {
        clientReadyCondition.countDown();
    }

    public void signalClientDone() {
        clientDoneCondition.countDown();
    }

    public boolean waitForServerSignal(long timeout, TimeUnit unit)
            throws InterruptedException {

        return serverReadyCondition.await(timeout, unit);
    }

    public boolean waitForServerSignal() throws InterruptedException {
        return waitForServerSignal(SERVER_SIGNAL_TIMEOUT, TimeUnit.SECONDS);
    }

    public boolean waitForServerDone(long timeout, TimeUnit unit)
            throws InterruptedException {

        return serverDoneCondition.await(timeout, unit);
    }

    public boolean waitForServerDone() throws InterruptedException {
        return waitForServerDone(SERVER_SIGNAL_TIMEOUT, TimeUnit.SECONDS);
    }

    public SSLTest setServerPeer(Peer serverPeer) {
        this.serverPeer = serverPeer;
        return this;
    }

    public Peer getServerPeer() {
        return serverPeer;
    }

    public SSLTest setServerApplication(Application serverApplication) {
        this.serverApplication = serverApplication;
        return this;
    }

    public Application getServerApplication() {
        return serverApplication;
    }

    public SSLTest setClientPeer(Peer clientPeer) {
        this.clientPeer = clientPeer;
        return this;
    }

    public Peer getClientPeer() {
        return clientPeer;
    }

    public SSLTest setClientApplication(Application clientApplication) {
        this.clientApplication = clientApplication;
        return this;
    }

    public Application getClientApplication() {
        return clientApplication;
    }

    public void runTest() throws Exception {
        if (separateServerThread) {
            startServer(true, this);
            startClient(false, this);
            serverThread.join();
        } else {
            startClient(true, this);
            startServer(false, this);
            clientThread.join();
        }

        if (clientException != null || serverException != null) {
            throw new RuntimeException("Test failed");
        }
    }

    public SSLTest() {
        serverPeer = (test) -> doServerSide(test);
        clientPeer = (test) -> doClientSide(test);
        serverApplication = (socket, test) -> runServerApplication(socket);
        clientApplication = (socket, test) -> runClientApplication(socket);
    }

    /*
     * Private part.
     */


    /*
     * Define the server side of the test.
     */
    private static void doServerSide(SSLTest test) throws Exception {
        SSLServerSocket sslServerSocket;

        // kick start the server side service
        SSLServerSocketFactory sslssf = test.getSSLServerSocketFactory();
        sslServerSocket = (SSLServerSocket)sslssf.createServerSocket(FREE_PORT);

        test.setServerPort(sslServerSocket.getLocalPort());
        print("Server is listening on port " + test.getServerPort());

        // Signal the client, the server is ready to accept connection.
        test.signalServerReady();

        // Try to accept a connection in 30 seconds.
        SSLSocket sslSocket = accept(sslServerSocket);
        if (sslSocket == null) {
            // Ignore the test case if no connection within 30 seconds.
            print("No incoming client connection in 30 seconds. "
                    + "Ignore in server side.");
            return;
        }
        print("Server accepted connection");

        // handle the connection
        try {
            // Is it the expected client connection?
            //
            // Naughty test cases or third party routines may try to
            // connection to this server port unintentionally.  In
            // order to mitigate the impact of unexpected client
            // connections and avoid intermittent failure, it should
            // be checked that the accepted connection is really linked
            // to the expected client.
            boolean clientIsReady = test.waitForClientSignal();

            if (clientIsReady) {
                // Run the application in server side.
                print("Run server application");
                test.getServerApplication().run(sslSocket, test);
            } else {    // Otherwise, ignore
                // We don't actually care about plain socket connections
                // for TLS communication testing generally.  Just ignore
                // the test if the accepted connection is not linked to
                // the expected client or the client connection timeout
                // in 30 seconds.
                print("The client is not the expected one or timeout. "
                        + "Ignore in server side.");
            }
        } finally {
            sslSocket.close();
            sslServerSocket.close();
        }

        test.signalServerDone();
    }

    /*
     * Define the server side application of the test for the specified socket.
     */
    private static void runServerApplication(SSLSocket socket)
            throws Exception {

        // here comes the test logic
        InputStream sslIS = socket.getInputStream();
        OutputStream sslOS = socket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();
    }

    /*
     * Define the client side of the test.
     */
    private static void doClientSide(SSLTest test) throws Exception {

        // Wait for server to get started.
        //
        // The server side takes care of the issue if the server cannot
        // get started in 90 seconds.  The client side would just ignore
        // the test case if the serer is not ready.
        boolean serverIsReady = test.waitForServerSignal();
        if (!serverIsReady) {
            print("The server is not ready yet in 90 seconds. "
                    + "Ignore in client side.");
            return;
        }

        SSLSocketFactory sslsf = test.getSSLSocketFactory();
        try (SSLSocket sslSocket = (SSLSocket)sslsf.createSocket()) {
            try {
                sslSocket.connect(
                        new InetSocketAddress("localhost",
                                test.getServerPort()), CLIENT_TIMEOUT);
                print("Client connected to server");
            } catch (IOException ioe) {
                // The server side may be impacted by naughty test cases or
                // third party routines, and cannot accept connections.
                //
                // Just ignore the test if the connection cannot be
                // established.
                print("Cannot make a connection in 15 seconds. "
                        + "Ignore in client side.", ioe);
                return;
            }

            // OK, here the client and server get connected.

            // Signal the server, the client is ready to communicate.
            test.signalClientReady();

            // There is still a chance in theory that the server thread may
            // wait client-ready timeout and then quit.  The chance should
            // be really rare so we don't consider it until it becomes a
            // real problem.

            // Run the application in client side.
            print("Run client application");
            test.getClientApplication().run(sslSocket, test);
        }

        test.signalClientDone();
    }

    /*
     * Define the client side application of the test for the specified socket.
     */
    private static void runClientApplication(SSLSocket socket)
            throws Exception {

        InputStream sslIS = socket.getInputStream();
        OutputStream sslOS = socket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();
    }

    private void startServer(boolean newThread, SSLTest test) throws Exception {
        if (newThread) {
            serverThread = new Thread() {
                @Override
                public void run() {
                    try {
                        serverPeer.run(test);
                    } catch (Exception e) {
                        /*
                         * Our server thread just died.
                         *
                         * Release the client, if not active already...
                         */
                        print("Server died ...", e);
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            try {
                serverPeer.run(test);
            } catch (Exception e) {
                print("Server failed ...", e);
                serverException = e;
            }
        }
    }

    private void startClient(boolean newThread, SSLTest test) throws Exception {
        if (newThread) {
            clientThread = new Thread() {
                @Override
                public void run() {
                    try {
                        clientPeer.run(test);
                    } catch (Exception e) {
                        /*
                         * Our client thread just died.
                         */
                        print("Client died ...", e);
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            try {
                clientPeer.run(test);
            } catch (Exception e) {
                print("Client failed ...", e);
                clientException = e;
            }
        }
    }
}
