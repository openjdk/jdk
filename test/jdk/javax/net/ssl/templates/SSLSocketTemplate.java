/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
// Please run in othervm mode.  SunJSSE does not support dynamic system
// properties, no way to re-use system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 8161106 8170329
 * @modules jdk.crypto.ec
 * @summary Improve SSLSocket test template
 * @run main/othervm SSLSocketTemplate
 */

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Template to help speed your client/server tests.
 *
 * Two examples that use this template:
 * test/jdk/sun/security/ssl/ServerHandshaker/AnonCipherWithWantClientAuth.java
 * test/jdk/sun/net/www/protocol/https/HttpsClient/ServerIdentityTest.java
 */
public class SSLSocketTemplate extends SSLContextTemplate {

    /*
     * ==================
     * Run the test case.
     */
    public static void main(String[] args) throws Exception {
        (new SSLSocketTemplate()).run();
    }

    /*
     * Run the test case.
     */
    public void run() throws Exception {
        bootup();
    }

    /*
     * Define the server side application of the test for the specified socket.
     */
    protected void runServerApplication(SSLSocket socket) throws Exception {
        // here comes the test logic
        InputStream sslIS = socket.getInputStream();
        OutputStream sslOS = socket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();
    }

    /*
     * Define the client side application of the test for the specified socket.
     * This method is used if the returned value of
     * isCustomizedClientConnection() is false.
     *
     * @param socket may be null is no client socket is generated.
     *
     * @see #isCustomizedClientConnection()
     */
    protected void runClientApplication(SSLSocket socket) throws Exception {
        InputStream sslIS = socket.getInputStream();
        OutputStream sslOS = socket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();
    }

    /*
     * Define the client side application of the test for the specified
     * server port.  This method is used if the returned value of
     * isCustomizedClientConnection() is true.
     *
     * Note that the client need to connect to the server port by itself
     * for the actual message exchange.
     *
     * @see #isCustomizedClientConnection()
     */
    protected void runClientApplication(int serverPort) throws Exception {
        // blank
    }

    /*
     * Does the client side use customized connection other than
     * explicit Socket.connect(), for example, URL.openConnection()?
     */
    protected boolean isCustomizedClientConnection() {
        return false;
    }

    /*
     * Configure the client side socket.
     */
    protected void configureClientSocket(SSLSocket socket) {
    }

    /*
     * Configure the server side socket.
     */
    protected void configureServerSocket(SSLServerSocket socket) {
    }

    /*
     * =============================================
     * Define the client and server side operations.
     *
     * If the client or server is doing some kind of object creation
     * that the other side depends on, and that thread prematurely
     * exits, you may experience a hang.  The test harness will
     * terminate all hung threads after its timeout has expired,
     * currently 3 minutes by default, but you might try to be
     * smart about it....
     */

    /*
     * Is the server ready to serve?
     */
    protected final CountDownLatch serverCondition = new CountDownLatch(1);

    /*
     * Is the client ready to handshake?
     */
    protected final CountDownLatch clientCondition = new CountDownLatch(1);

    /*
     * What's the server port?  Use any free port by default
     */
    protected volatile int serverPort = 0;

    /*
     * What's the server address?  null means binding to the wildcard.
     */
    protected volatile InetAddress serverAddress = null;

    /*
     * Define the server side of the test.
     */
    protected void doServerSide() throws Exception {
        // kick start the server side service
        SSLContext context = createServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        InetAddress serverAddress = this.serverAddress;
        SSLServerSocket sslServerSocket = serverAddress == null ?
                (SSLServerSocket)sslssf.createServerSocket(serverPort)
                : (SSLServerSocket)sslssf.createServerSocket();
        if (serverAddress != null) {
            sslServerSocket.bind(new InetSocketAddress(serverAddress, serverPort));
        }
        configureServerSocket(sslServerSocket);
        serverPort = sslServerSocket.getLocalPort();

        // Signal the client, the server is ready to accept connection.
        serverCondition.countDown();

        // Try to accept a connection in 30 seconds.
        SSLSocket sslSocket;
        try {
            sslServerSocket.setSoTimeout(30000);
            sslSocket = (SSLSocket)sslServerSocket.accept();
        } catch (SocketTimeoutException ste) {
            // Ignore the test case if no connection within 30 seconds.
            System.out.println(
                "No incoming client connection in 30 seconds. " +
                "Ignore in server side.");
            return;
        } finally {
            sslServerSocket.close();
        }

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
            boolean clientIsReady =
                    clientCondition.await(30L, TimeUnit.SECONDS);

            if (clientIsReady) {
                // Run the application in server side.
                runServerApplication(sslSocket);
            } else {    // Otherwise, ignore
                // We don't actually care about plain socket connections
                // for TLS communication testing generally.  Just ignore
                // the test if the accepted connection is not linked to
                // the expected client or the client connection timeout
                // in 30 seconds.
                System.out.println(
                        "The client is not the expected one or timeout. " +
                        "Ignore in server side.");
            }
        } finally {
            sslSocket.close();
        }
    }

    /*
     * Define the client side of the test.
     */
    protected void doClientSide() throws Exception {

        // Wait for server to get started.
        //
        // The server side takes care of the issue if the server cannot
        // get started in 90 seconds.  The client side would just ignore
        // the test case if the serer is not ready.
        boolean serverIsReady =
                serverCondition.await(90L, TimeUnit.SECONDS);
        if (!serverIsReady) {
            System.out.println(
                    "The server is not ready yet in 90 seconds. " +
                    "Ignore in client side.");
            return;
        }

        if (isCustomizedClientConnection()) {
            // Signal the server, the client is ready to communicate.
            clientCondition.countDown();

            // Run the application in client side.
            runClientApplication(serverPort);

            return;
        }

        SSLContext context = createClientSSLContext();
        SSLSocketFactory sslsf = context.getSocketFactory();

        try (SSLSocket sslSocket = (SSLSocket)sslsf.createSocket()) {
            try {
                configureClientSocket(sslSocket);
                InetAddress serverAddress = this.serverAddress;
                InetSocketAddress connectAddress = serverAddress == null
                        ? new InetSocketAddress(InetAddress.getLoopbackAddress(), serverPort)
                        : new InetSocketAddress(serverAddress, serverPort);
                sslSocket.connect(connectAddress, 15000);
            } catch (IOException ioe) {
                // The server side may be impacted by naughty test cases or
                // third party routines, and cannot accept connections.
                //
                // Just ignore the test if the connection cannot be
                // established.
                System.out.println(
                        "Cannot make a connection in 15 seconds. " +
                        "Ignore in client side.");
                return;
            }

            // OK, here the client and server get connected.

            // Signal the server, the client is ready to communicate.
            clientCondition.countDown();

            // There is still a chance in theory that the server thread may
            // wait client-ready timeout and then quit.  The chance should
            // be really rare so we don't consider it until it becomes a
            // real problem.

            // Run the application in client side.
            runClientApplication(sslSocket);
        }
    }

    /*
     * =================================================
     * Stuffs to boot up the client-server mode testing.
     */
    private Thread clientThread = null;
    private Thread serverThread = null;
    private volatile Exception serverException = null;
    private volatile Exception clientException = null;

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    private final boolean separateServerThread;

    public SSLSocketTemplate() {
        this(false);
    }

    public SSLSocketTemplate(boolean sepSrvThread) {
        this.separateServerThread = sepSrvThread;
    }

    /*
     * Boot up the testing, used to drive remainder of the test.
     */
    private void bootup() throws Exception {
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
            local.addSuppressed(remote);
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

    private void startServer(boolean newThread) throws Exception {
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
                        logException("Server died", e);
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            try {
                doServerSide();
            } catch (Exception e) {
                logException("Server failed", e);
                serverException = e;
            }
        }
    }

    private void startClient(boolean newThread) {
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
                        logException("Client died", e);
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            try {
                doClientSide();
            } catch (Exception e) {
                logException("Client failed", e);
                clientException = e;
            }
        }
    }

    private synchronized void logException(String prefix, Throwable cause) {
        System.out.println(prefix + ": " + cause);
        cause.printStackTrace(System.out);
    }

}
