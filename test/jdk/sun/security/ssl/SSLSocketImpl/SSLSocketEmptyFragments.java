/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8182621
 * @summary Verify JSSE rejects empty Handshake, Alert, and ChangeCipherSpec messages.
 * @library /javax/net/ssl/templates
 * @run main SSLSocketEmptyFragments
 */

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SSLSocketEmptyFragments extends SSLContextTemplate {
    private static final boolean DEBUG = Boolean.getBoolean("test.debug");
    private static final byte HANDSHAKE_TYPE = 22;
    private static final byte ALERT_TYPE = 21;
    private static final byte CHANGE_CIPHERSPEC_TYPE = 20;

    private static final byte[] INVALID_ALERT = {ALERT_TYPE, 3, 3, 0, 0};

    private static final byte[] INVALID_HANDSHAKE = {HANDSHAKE_TYPE, 3, 3, 0, 0};
    private static final int SERVER_WAIT_SEC = 5;
    private static final String TLSv13 = "TLSv1.3";
    private static final String TLSv12 = "TLSv1.2";

    private final String protocol;

    public SSLSocketEmptyFragments(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Creates a socket connection between an SSLSocket (server-side) and
     * a plain Socket on the client. Then runs the given executor to execute
     * the test.
     *
     * @param consumer runs the test-specific code. The SSLSocket argument is the
     *                 server-side of the connection. The Socket is the client-side.
     */
    private void executeSingleThreadedTest(BiConsumer<SSLSocket, Socket> consumer)
            throws Exception {
        SSLContext ctx = createServerSSLContext();
        SSLServerSocketFactory factory = ctx.getServerSocketFactory();

        try(ExecutorService executors = Executors.newFixedThreadPool(1);
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket()) {
            serverSocket.bind(null);
            int port = serverSocket.getLocalPort();
            InetAddress address = serverSocket.getInetAddress();

            Future<SSLSocket> serverThread = executors.submit(
                    () -> (SSLSocket)serverSocket.accept());

            try (Socket client = new Socket(address, port);
                SSLSocket sslSocket = serverThread.get(SERVER_WAIT_SEC, TimeUnit.SECONDS)) {
                consumer.accept(sslSocket, client);
            }
        }
    }

    private static void testEmptyHandshakeRecord(SSLSocket server, Socket client) {
        log("Sending bad handshake packet to server...");

        try {
            OutputStream os = client.getOutputStream();
            os.write(INVALID_HANDSHAKE);
            os.flush();
            log("Reading data from client.");
            // try to read some data to start the SSL handshake
            server.getInputStream().read();
            throw new RuntimeException(
                    "Reading bad packet did not throw expected exception.");
        } catch (SSLProtocolException exc) {
            log("Caught expected SSLProtocolException.");
        } catch (IOException exc) {
            throw new RuntimeException("Unexpected IOException thrown by socket operations", exc);
        }
    }


    private static void testEmptyAlertNotHandshaking(SSLSocket server, Socket client) {
        log("Sending empty alert packet before handshaking starts.");

        try {
            OutputStream os = client.getOutputStream();
            os.write(INVALID_ALERT);
            os.flush();
            log("Reading data from client.");
            // try to read some data to start the SSL handshake
            server.getInputStream().read();
            throw new RuntimeException(
                    "Reading bad packet did not throw expected exception.");
        } catch (SSLHandshakeException exc) {
            log("Caught expected SSLHandshakeException.");
            if (!server.isClosed()) {
                throw new RuntimeException("Server socket was not closed after exception.");
            }

        } catch (IOException exc) {
            throw new RuntimeException("Unexpected IOException thrown by socket operations.", exc);
        }
    }

    /**
     * Runs a test where the server -- in a separate thread -- accepts a connection
     * and attempts to read from the remote side. Tests are successful if the
     * server thread returns true.
     *
     * @param clientConsumer Client-side test code that injects bad packets into the TLS handshake.
     * @param expectedException The exception that should be thrown by the server
     */
    private void executeMultiThreadedTest(Consumer<Socket> clientConsumer,
                              final Class<?> expectedException) throws Exception {
        SSLContext serverContext = createServerSSLContext();
        SSLServerSocketFactory factory = serverContext.getServerSocketFactory();

        try(ExecutorService threadPool = Executors.newFixedThreadPool(1);
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket()) {
            serverSocket.bind(null);
            int port = serverSocket.getLocalPort();
            InetAddress address = serverSocket.getInetAddress();

            Future<Boolean> serverThread = threadPool.submit(() -> {
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                    log("Server reading data from client.");
                    socket.getInputStream().read();
                    log("The expected exception was not thrown.");
                    return false;

                } catch (Exception exc) {
                    if (expectedException.isAssignableFrom(exc.getClass())) {
                        log("Server thread received expected SSLProtocolException");
                        return true;
                    } else {
                        log("Server thread threw an unexpected exception: " + exc);
                        throw exc;
                    }
                }
            });

            try(Socket socket = new Socket(address, port)) {
                clientConsumer.accept(socket);
                log("waiting for server to exit.");

                // wait for the server to exit, which should be quick if the test passes.
                if (!serverThread.get(SERVER_WAIT_SEC, TimeUnit.SECONDS)) {
                    throw new RuntimeException(
                            "The server side of the connection did not throw the expected exception");
                }
            }
        }
    }

    /**
     * Performs the client side of the TLS handshake, sending and receiving
     * packets over the given socket.
     * @param socket Connected socket to the server side.
     * @throws IOException
     */
    private void testEmptyAlertDuringHandshake(Socket socket) {
        log("**** Testing empty alert during handshake");

        try {
            SSLEngine engine = createClientSSLContext().createSSLEngine();
            engine.setUseClientMode(true);
            SSLSession session = engine.getSession();

            int appBufferMax = session.getApplicationBufferSize();
            int netBufferMax = session.getPacketBufferSize();

            ByteBuffer clientIn = ByteBuffer.allocate(appBufferMax + 50);
            ByteBuffer clientToServer = ByteBuffer.allocate(appBufferMax + 50);
            ByteBuffer serverToClient = ByteBuffer.allocate(netBufferMax + 50);
            ByteBuffer clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());

            wrap(engine, clientOut, clientToServer);
            runDelegatedTasks(engine);
            clientToServer.flip();

            OutputStream socketOut = socket.getOutputStream();
            byte [] outbound = new byte[netBufferMax];
            clientToServer.get(outbound, 0, clientToServer.limit());
            socketOut.write(outbound, 0, clientToServer.limit());
            socketOut.flush();

            processServerResponse(engine, clientIn, socket.getInputStream());

            log("Sending invalid alert packet!");
            socketOut.write(new byte[]{ALERT_TYPE, 3, 3, 0, 0});
            socketOut.flush();

        } catch (Exception exc){
            throw new RuntimeException("An error occurred running the test.", exc);
        }
    }

    /**
     * Performs TLS handshake until the client (this method) needs to send the
     * ChangeCipherSpec message. Then we send a packet with a zero-length fragment.
     */
    private void testEmptyChangeCipherSpecMessage(Socket socket) {
        log("**** Testing invalid ChangeCipherSpec message");

        try {
            socket.setSoTimeout(500);
            SSLEngine engine = createClientSSLContext().createSSLEngine();
            engine.setUseClientMode(true);
            SSLSession session = engine.getSession();
            int appBufferMax = session.getApplicationBufferSize();

            ByteBuffer clientIn = ByteBuffer.allocate(appBufferMax + 50);
            ByteBuffer clientToServer = ByteBuffer.allocate(appBufferMax + 50);

            ByteBuffer clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());

            OutputStream outputStream = socket.getOutputStream();

            boolean foundCipherSpecMsg = false;

            byte[] outbound = new byte[8192];
            do {
                wrap(engine, clientOut, clientToServer);
                runDelegatedTasks(engine);
                clientToServer.flip();

                if(clientToServer.get(0) == CHANGE_CIPHERSPEC_TYPE) {
                    foundCipherSpecMsg = true;
                    break;
                }
                clientToServer.get(outbound, 0, clientToServer.limit());
                debug("Writing " + clientToServer.limit() + " bytes to the server.");
                outputStream.write(outbound, 0, clientToServer.limit());
                outputStream.flush();

                processServerResponse(engine, clientIn, socket.getInputStream());

                clientToServer.clear();

            } while(engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED);

            if (!foundCipherSpecMsg) {
                throw new RuntimeException("Didn't intercept the ChangeCipherSpec message.");
            } else {
                log("Sending invalid ChangeCipherSpec message");
                outputStream.write(new byte[]{CHANGE_CIPHERSPEC_TYPE, 3, 3, 0, 0});
                outputStream.flush();
            }

        } catch (Exception exc) {
            throw new RuntimeException("An error occurred running the test.", exc);
        }
    }

    /**
     * Processes TLS handshake messages received from the server.
     */
    private static void processServerResponse(SSLEngine engine, ByteBuffer clientIn,
                                      InputStream inputStream) throws IOException {
        byte [] inbound = new byte[8192];
        ByteBuffer serverToClient = ByteBuffer.allocate(
                engine.getSession().getApplicationBufferSize() + 50);

        while(engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
            log("reading data from server.");
            int len = inputStream.read(inbound);
            if (len == -1) {
                throw new IOException("Could not read from server.");
            }

            dumpBytes(inbound, len);

            serverToClient.put(inbound, 0, len);
            serverToClient.flip();

            // unwrap packets in a loop because we sometimes get multiple
            // TLS messages in one read() operation.
            do {
                unwrap(engine, serverToClient, clientIn);
                runDelegatedTasks(engine);
                log("Status after running tasks: " + engine.getHandshakeStatus());
            } while (serverToClient.hasRemaining());
            serverToClient.compact();
        }
    }

    private static SSLEngineResult wrap(SSLEngine engine, ByteBuffer src, ByteBuffer dst) throws SSLException {
        log("Wrapping...");
        SSLEngineResult result = engine.wrap(src, dst);
        logEngineStatus(engine, result);
        return result;
    }

    private static SSLEngineResult unwrap(SSLEngine engine, ByteBuffer src, ByteBuffer dst) throws SSLException {
        log("Unwrapping");
        SSLEngineResult result = engine.unwrap(src, dst);
        logEngineStatus(engine, result);
        return result;
    }

    protected static void runDelegatedTasks(SSLEngine engine) {
        if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                log("    running delegated task...");
                runnable.run();
            }
            SSLEngineResult.HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                throw new RuntimeException(
                        "handshake shouldn't need additional tasks");
            }
        }
    }


    @Override
    protected ContextParameters getClientContextParameters() {
        return getContextParameters();
    }

    @Override
    protected ContextParameters getServerContextParameters() {
        return getContextParameters();
    }

    private ContextParameters getContextParameters() {
        return new ContextParameters(protocol, "PKIX", "NewSunX509");
    }
    
    private static void log(String message) {
        System.out.println(message);
        System.out.flush();
    }

    private static void dumpBytes(byte[] buffer, int length) {
        int totalLength = Math.min(buffer.length, length);
        StringBuffer sb = new StringBuffer();
        int counter = 0;
        for (int idx = 0; idx < totalLength ; ++idx) {
            sb.append(String.format("%02x ", buffer[idx]));
            if (++counter == 16) {
                sb.append("\n");
                counter = 0;
            }
        }
        debug(sb.toString());
    }

    private static void debug(String message) {
        if (DEBUG) {
            log(message);
        }
    }

    private static FileWriter fw;

    private static void logEngineStatus(
            SSLEngine engine, SSLEngineResult result) {
        debug("\tResult Status    : " + result.getStatus());
        debug("\tResult HS Status : " + result.getHandshakeStatus());
        debug("\tEngine HS Status : " + engine.getHandshakeStatus());
        debug("\tisInboundDone()  : " + engine.isInboundDone());
        debug("\tisOutboundDone() : " + engine.isOutboundDone());
        debug("\tMore Result      : " + result);
    }


    public static void main(String [] args) throws Exception {
        SSLSocketEmptyFragments tests = new SSLSocketEmptyFragments(TLSv12);

        tests.executeSingleThreadedTest(
                SSLSocketEmptyFragments::testEmptyHandshakeRecord);
        tests.executeSingleThreadedTest(
                SSLSocketEmptyFragments::testEmptyAlertNotHandshaking);
        tests.executeMultiThreadedTest(tests::testEmptyAlertDuringHandshake, SSLHandshakeException.class);
        tests.executeMultiThreadedTest(tests::testEmptyChangeCipherSpecMessage, SSLProtocolException.class);

        tests = new SSLSocketEmptyFragments(TLSv13);
        tests.executeSingleThreadedTest(
                SSLSocketEmptyFragments::testEmptyHandshakeRecord);
        tests.executeSingleThreadedTest(
                SSLSocketEmptyFragments::testEmptyAlertNotHandshaking);
    }
}
