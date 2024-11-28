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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8343791
 * @summary verifies the socket state after `connect()` failures
 * @library /test/lib
 * @run junit/othervm --add-opens java.base/java.net=ALL-UNNAMED ConnectFailTest
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
     * @param socket an unbound socket
     */
    @ParameterizedTest
    @MethodSource("boundSockets")
    void testBoundSocket(Socket socket) throws IOException {
        try (socket) {
            assertTrue(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    @Test
    void testConnectedSocket() throws Throwable {
        testConnectedSocket(
                ConnectFailTest::createConnectedSocket,
                executable -> {
                    SocketException exception = assertThrows(SocketException.class, executable);
                    assertEquals("already connected", exception.getMessage());
                });
    }

    @Test
    void testConnectedNioSocket() throws Throwable {
        testConnectedSocket(
                ConnectFailTest::createConnectedNioSocket,
                executable -> assertThrows(AlreadyConnectedException.class, executable));
    }

    /**
     * Verifies socket is not closed when {@code `connectedSocket.connect()`} fails.
     * @param connectedSocketFactory a connected socket factory
     * @param reconnectFailureVerifier a consumer verifying the thrown reconnect failure
     */
    private static void testConnectedSocket(
            Function<SocketAddress, Socket> connectedSocketFactory,
            Consumer<Executable> reconnectFailureVerifier)
            throws Throwable {
        withEphemeralServerSocket(serverSocket -> {
            SocketAddress serverSocketAddress = serverSocket.getLocalSocketAddress();
            try (Socket socket = connectedSocketFactory.apply(serverSocketAddress)) {
                assertTrue(socket.isBound());
                assertTrue(socket.isConnected());
                // `Socket` and `SocketChannel` differ in how they fail on re-connection attempts on an already
                // connected socket. Hence, we delegate this particular check:
                reconnectFailureVerifier.accept(() -> socket.connect(REFUSING_SOCKET_ADDRESS));
                assertFalse(socket.isClosed());
            }
        });
    }

    /**
     * Verifies socket is closed when {@code unboundSocket.connect(unresolvedAddress)} fails.
     * @param socket an unbound socket
     */
    @ParameterizedTest
    @MethodSource("unboundSockets")
    void testUnboundSocketWithUnresolvedAddress(Socket socket) throws IOException {
        try (socket) {
            assertFalse(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    @SuppressWarnings("resource")
    static List<Socket> unboundSockets() throws IOException {
        return List.of(
                new Socket(),
                SocketChannel.open().socket());
    }

    /**
     * Verifies socket is closed when {@code boundSocket.connect(unresolvedAddress)} fails.
     * @param socket a bound socket
     */
    @ParameterizedTest
    @MethodSource("boundSockets")
    void testBoundSocketWithUnresolvedAddress(Socket socket) throws IOException {
        try (socket) {
            assertTrue(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    @SuppressWarnings("resource")
    static List<Socket> boundSockets() throws IOException {
        List<Socket> sockets = new ArrayList<>();
        // Socket
        {
            Socket socket = new Socket();
            socket.bind(new InetSocketAddress(0));
            sockets.add(socket);
        }
        // NIO Socket
        {
            SocketChannel channel = SocketChannel.open();
            channel.bind(new InetSocketAddress(0));
            sockets.add(channel.socket());
        }
        return sockets;
    }

    /**
     * Verifies socket is not closed when {@code connectedSocket.connect(unresolvedAddress)} fails.
     * @param connectedSocketFactory a connected socket factory
     */
    @ParameterizedTest
    @MethodSource("connectedSocketFactories")
    void testConnectedSocketWithUnresolvedAddress(
            Function<SocketAddress, Socket> connectedSocketFactory)
            throws Throwable {
        withEphemeralServerSocket(serverSocket -> {
            SocketAddress serverSocketAddress = serverSocket.getLocalSocketAddress();
            try (Socket socket = connectedSocketFactory.apply(serverSocketAddress)) {
                assertTrue(socket.isBound());
                assertTrue(socket.isConnected());
                assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
                assertFalse(socket.isClosed());
            }
        });
    }

    static List<Function<SocketAddress, Socket>> connectedSocketFactories() {
        return List.of(
                ConnectFailTest::createConnectedSocket,
                ConnectFailTest::createConnectedNioSocket);
    }

    private static Socket createConnectedSocket(SocketAddress address) {
        InetSocketAddress inetAddress = (InetSocketAddress) address;
        try {
            return new Socket(inetAddress.getAddress(), inetAddress.getPort());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @SuppressWarnings("resource")
    private static Socket createConnectedNioSocket(SocketAddress address) {
        try {
            return SocketChannel.open(address).socket();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void withEphemeralServerSocket(ThrowingConsumer<ServerSocket> serverSocketConsumer)
            throws Throwable {
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
             ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            // Accept connections in the background to avoid blocking the caller
            executorService.submit(() -> acceptConnections(serverSocket));
            serverSocketConsumer.accept(serverSocket);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void acceptConnections(ServerSocket serverSocket) {
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
