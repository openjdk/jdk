/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8343791
 * @summary verifies that `connect()` failures throw expected exception and leave both `Socket` and the underlying
 *          `SocketImpl` at the same expected state
 * @library /test/lib
 * @run junit ConnectFailTest
 */
class ConnectFailTest {

    private static final int DEAD_SERVER_PORT = 0xDEAD;

    private static final InetSocketAddress REFUSING_SOCKET_ADDRESS = Utils.refusingEndpoint();

    private static final InetSocketAddress UNRESOLVED_ADDRESS =
            InetSocketAddress.createUnresolved("no.such.host", DEAD_SERVER_PORT);

    @Test
    void testUnresolvedAddress() {
        assertTrue(UNRESOLVED_ADDRESS.isUnresolved());
    }

    /**
     * Verifies socket is closed when {@code unboundSocket.connect()} fails.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testUnboundSocket(Socket socket) throws IOException {
        try (socket) {
            assertFalse(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    /**
     * Verifies socket is closed when {@code boundSocket.connect()} fails.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testBoundSocket(Socket socket) throws IOException {
        try (socket) {
            socket.bind(new InetSocketAddress(0));
            assertTrue(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    /**
     * Verifies socket is not closed when {@code `connectedSocket.connect()`} fails.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testConnectedSocket(Socket socket) throws Throwable {
        try (socket) {
            withEphemeralServerSocket(serverSocket -> {
                SocketAddress serverSocketAddress = serverSocket.getLocalSocketAddress();
                socket.connect(serverSocketAddress);
                assertTrue(socket.isBound());
                assertTrue(socket.isConnected());
                SocketException exception = assertThrows(
                        SocketException.class,
                        () -> socket.connect(REFUSING_SOCKET_ADDRESS));
                assertEquals("already connected", exception.getMessage());
                assertFalse(socket.isClosed());
            });
        }
    }

    /**
     * Verifies socket is closed when {@code unboundSocket.connect(unresolvedAddress)} fails.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testUnboundSocketWithUnresolvedAddress(Socket socket) throws IOException {
        try (socket) {
            assertFalse(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(UnknownHostException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    /**
     * Verifies socket is closed when {@code boundSocket.connect(unresolvedAddress)} fails.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testBoundSocketWithUnresolvedAddress(Socket socket) throws IOException {
        try (socket) {
            socket.bind(new InetSocketAddress(0));
            assertTrue(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(UnknownHostException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    /**
     * Verifies socket is not closed when {@code connectedSocket.connect(unresolvedAddress)} fails.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testConnectedSocketWithUnresolvedAddress(Socket socket) throws Throwable {
        try (socket) {
            withEphemeralServerSocket(serverSocket -> {
                SocketAddress serverSocketAddress = serverSocket.getLocalSocketAddress();
                socket.connect(serverSocketAddress);
                assertTrue(socket.isBound());
                assertTrue(socket.isConnected());
                assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
                assertFalse(socket.isClosed());
            });
        }
    }

    static List<Socket> sockets() throws Exception {
        Socket socket = new Socket();
        @SuppressWarnings("resource")
        Socket channelSocket = SocketChannel.open().socket();
        return List.of(socket, channelSocket);
    }

    private static void withEphemeralServerSocket(ThrowingConsumer<ServerSocket> serverSocketConsumer)
            throws Throwable {
        @SuppressWarnings("resource")   // We'll use `shutdownNow()`
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            // Accept connections in the background to avoid blocking the caller
            executorService.submit(() -> continuouslyAcceptConnections(serverSocket));
            serverSocketConsumer.accept(serverSocket);
        } finally {
            executorService.shutdownNow();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void continuouslyAcceptConnections(ServerSocket serverSocket) {
        System.err.println("[Test socket server] Starting accepting connections");
        while (true) {
            try {

                // Accept the connection
                Socket clientSocket = serverSocket.accept();
                System.err.format(
                        "[Test socket server] Accepted port %d to port %d%n",
                        ((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getPort(),
                        clientSocket.getLocalPort());

                // Instead of directly closing the socket, we try to read some to block. Directly closing
                // the socket will invalidate the client socket tests checking the established connection
                // status.
                try (clientSocket; InputStream inputStream = clientSocket.getInputStream()) {
                    inputStream.read();
                } catch (IOException _) {
                    // Do nothing
                }

            } catch (IOException _) {
                break;
            }

        }
        System.err.println("[Test socket server] Stopping accepting connections");
    }

}
