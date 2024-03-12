/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8274736 8277970
 * @summary Concurrent read/close of SSLSockets causes SSLSessions to be
 *          invalidated unnecessarily
 * @library /javax/net/ssl/templates
 * @run main/othervm NoInvalidateSocketException TLSv1.3
 * @run main/othervm NoInvalidateSocketException TLSv1.2
 * @run main/othervm -Djdk.tls.client.enableSessionTicketExtension=false
 *      NoInvalidateSocketException TLSv1.2
 */



import java.io.*;
import javax.net.ssl.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class NoInvalidateSocketException extends SSLSocketTemplate {
    private static final int ITERATIONS = 10;

    // This controls how long the main thread waits before closing the socket.
    // This may need tweaking for different environments to get the timing
    // right.
    private static final int CLOSE_DELAY = 10;

    private static SSLContext clientSSLCtx;
    private static SSLSocket theSSLSocket;
    private static SSLSession theSSLSession;
    private static InputStream theInputStream;
    private static String theSSLSocketHashCode;
    private static SSLSession lastSSLSession;
    private static final List<SSLSocket> serverCleanupList = new ArrayList<>();
    private static String tlsVersion = null;

    private static int invalidSessCount = 0;
    private static volatile boolean readFromSocket = false;
    private static volatile boolean finished = false;

    public static void main(String[] args) throws Exception {
        if (System.getProperty("javax.net.debug") == null) {
            System.setProperty("javax.net.debug", "session");
        }

        if (args != null && args.length >= 1) {
            tlsVersion = args[0];
        }

        new NoInvalidateSocketException(true).run();
        if (invalidSessCount > 0) {
            throw new RuntimeException("One or more sessions were improperly " +
                    "invalidated.");
        }
    }

    public NoInvalidateSocketException(boolean sepSrvThread) {
        super(sepSrvThread);
    }

    @Override
    public boolean isCustomizedClientConnection() {
        return true;
    }

    @Override
    public void runClientApplication(int serverPort) {
        Thread.currentThread().setName("Main Client Thread");

        // Create the SSLContext we'll use for client sockets for the
        // duration of the test.
        try {
            clientSSLCtx = createClientSSLContext();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create client ctx", e);
        }

        // Create the reader thread
        ReaderThread readerThread = new ReaderThread();
        readerThread.setName("Client Reader Thread");
        readerThread.start();

        try {
            for (int i = 0; i < ITERATIONS; i++) {
                openSSLSocket();
                doHandshake();
                getInputStream();
                getAndCompareSession();

                // Perform the Close/Read MT collision
                readCloseMultiThreaded();

                // Check to make sure that the initially negotiated session
                // remains intact.
                isSessionValid();

                lastSSLSession = theSSLSession;

                // Insert a short gap between iterations
                Thread.sleep(1000);
                System.out.println();
            }
        } catch (Exception e) {
            logToConsole("Unexpected Exception: " + e);
        } finally {
            // Tell the reader thread to finish
            finished = true;
        }
    }

    private void readCloseMultiThreaded() throws IOException,
            InterruptedException {
        // Tell the reader thread to start trying to read from this
        // socket
        readFromSocket = true;

        // Short pause to give the reader thread time to start
        // reading.
        if (CLOSE_DELAY > 0) {
            Thread.sleep(CLOSE_DELAY);
        }

        // The problem happens when the reader thread tries to read
        // from the socket while this thread is in the close() call
        closeSSLSocket();

        // Pause to give the reader thread time to discover that the
        // socket is closed and throw a SocketException
        Thread.sleep(500);
    }

    private class ReaderThread extends Thread {
        public void run() {
            // This thread runs in a tight loop until
            // readFromSocket == true
            while (!finished) {
                if (readFromSocket) {
                    int result = 0;
                    try {
                        // If the timing is just
                        // right, this will throw a SocketException
                        // and the SSLSession will be
                        // invalidated.
                        result = readFromSSLSocket();
                    } catch (Exception e) {
                        logToConsole("Exception reading from SSLSocket@" +
                                theSSLSocketHashCode + ": " + e);
                        e.printStackTrace(System.out);

                        // Stop trying to read from
                        // the socket now
                        readFromSocket = false;
                    }

                    if (result == -1) {
                        logToConsole("Reached end of stream reading from " +
                                "SSLSocket@" + theSSLSocketHashCode);

                        // Stop trying to read from
                        // the socket now
                        readFromSocket = false;
                    }
                }
            }
        }
    }

    private void openSSLSocket() throws IOException {
        theSSLSocket = (SSLSocket)clientSSLCtx.getSocketFactory().
                createSocket(serverAddress, serverPort);
        if (tlsVersion != null) {
            theSSLSocket.setEnabledProtocols(new String[] { tlsVersion });
        }
        theSSLSocketHashCode = String.format("%08x", theSSLSocket.hashCode());
        logToConsole("Opened SSLSocket@" + theSSLSocketHashCode);
    }

    private void doHandshake() throws IOException {
        logToConsole("Started handshake on SSLSocket@" +
                theSSLSocketHashCode);
        theSSLSocket.startHandshake();
        logToConsole("Finished handshake on SSLSocket@" +
                theSSLSocketHashCode);
    }

    private void getInputStream() throws IOException {
        theInputStream = theSSLSocket.getInputStream();
    }

    private void getAndCompareSession() {
        theSSLSession = theSSLSocket.getSession();

        // Have we opened a new session or re-used the last one?
        if (lastSSLSession == null ||
                !theSSLSession.equals(lastSSLSession)) {
            logToConsole("*** OPENED NEW SESSION ***: " +
                    theSSLSession);
        } else {
            logToConsole("*** RE-USING PREVIOUS SESSION ***: " +
                    theSSLSession + ")");
        }
    }

    private void closeSSLSocket() throws IOException {
        logToConsole("Closing SSLSocket@" + theSSLSocketHashCode);
        theSSLSocket.close();
        logToConsole("Closed SSLSocket@" + theSSLSocketHashCode);
    }

    private int readFromSSLSocket() throws Exception {
        logToConsole("Started reading from SSLSocket@" +
                theSSLSocketHashCode);
        int result = theInputStream.read();
        logToConsole("Finished reading from SSLSocket@" +
                theSSLSocketHashCode + ": result = " + result);
        return result;
    }

    private void isSessionValid() {
        // Is the session still valid?
        if (theSSLSession.isValid()) {
            logToConsole("*** " + theSSLSession + " IS VALID ***");
        } else {
            logToConsole("*** " + theSSLSession + " IS INVALID ***");
            invalidSessCount++;
        }
    }

    private static void logToConsole(String s) {
        System.out.println(System.nanoTime() + ": " +
                Thread.currentThread().getName() + ": " + s);
    }

    @Override
    public void doServerSide() throws Exception {
        Thread.currentThread().setName("Server Listener Thread");
        SSLContext context = createServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        InetAddress serverAddress = this.serverAddress;
        SSLServerSocket sslServerSocket = serverAddress == null ?
                (SSLServerSocket)sslssf.createServerSocket(serverPort)
                : (SSLServerSocket)sslssf.createServerSocket();
        if (serverAddress != null) {
            sslServerSocket.bind(new InetSocketAddress(serverAddress,
                    serverPort));
        }
        configureServerSocket(sslServerSocket);
        serverPort = sslServerSocket.getLocalPort();
        logToConsole("Listening on " + sslServerSocket.getLocalSocketAddress());

        // Signal the client, the server is ready to accept connection.
        serverCondition.countDown();

        // Try to accept a connection in 5 seconds.
        // We will do this in a loop until the client flips the
        // finished variable to true
        SSLSocket sslSocket;

        int timeoutCount = 0;
        try {
            do {
                try {
                    sslSocket = (SSLSocket) sslServerSocket.accept();
                    timeoutCount = 0;   // Reset the timeout counter;
                    logToConsole("Accepted connection from " +
                            sslSocket.getRemoteSocketAddress());

                    // Add the socket to the cleanup list so it can get
                    // closed at the end of the test
                    serverCleanupList.add(sslSocket);

                    boolean clientIsReady =
                            clientCondition.await(30L, TimeUnit.SECONDS);
                    if (clientIsReady) {
                        // Handle the connection in a new thread
                        ServerHandlerThread sht = null;
                        try {
                             sht = new ServerHandlerThread(sslSocket);
                             sht.start();
                        } finally {
                            if (sht != null) {
                                sht.join();
                            }
                        }
                    }
                } catch (SocketTimeoutException ste) {
                    timeoutCount++;
                    // If we are finished then we can return, otherwise
                    // check if we've timed out too many times (an exception
                    // case).  One way or the other we will exit eventually.
                    if (finished) {
                        return;
                    } else if (timeoutCount >= 3) {
                        logToConsole("Server accept timeout exceeded");
                        throw ste;
                    }
                }
            } while (!finished);
        } finally {
            sslServerSocket.close();
            // run through the server cleanup list and close those sockets
            // as well.
            for (SSLSocket sock : serverCleanupList) {
                try {
                    if (sock != null) {
                        sock.close();
                    }
                } catch (IOException ioe) {
                    // Swallow these close failures as the server itself
                    // is shutting down anyway.
                }
            }
        }
    }

    @Override
    public void configureServerSocket(SSLServerSocket socket) {
        try {
            socket.setReuseAddress(true);
            socket.setSoTimeout(5000);
        } catch (SocketException se) {
            // Rethrow as unchecked to satisfy the override signature
            throw new RuntimeException(se);
        }
    }

    @Override
    public void runServerApplication(SSLSocket sslSocket) {
        Thread.currentThread().setName("Server Reader Thread");
        SSLSocket sock = null;
        sock = sslSocket;
        try {
            BufferedReader is = new BufferedReader(
                    new InputStreamReader(sock.getInputStream()));
            PrintWriter os = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(sock.getOutputStream())));

            // Only handle a single burst of data
            char[] buf = new char[1024];
            int dataRead = is.read(buf);
            logToConsole(String.format("Received: %d bytes of data\n",
                    dataRead));

            os.println("Received connection from client");
            os.flush();
        } catch (IOException ioe) {
            // Swallow these exceptions for this test
        }
    }

    private class ServerHandlerThread extends Thread {
        SSLSocket sock;
        ServerHandlerThread(SSLSocket socket) {
            this.sock = Objects.requireNonNull(socket, "Illegal null socket");
        }

        @Override
        public void run() {
            try {
                runServerApplication(sock);
            } catch (Exception exc) {
                // Wrap inside an unchecked exception to satisfy Runnable
                throw new RuntimeException(exc);
            }
        }

        void close() {
            try {
                sock.close();
            } catch (IOException e) {
                // swallow this exception
            }
        }
    }
}
