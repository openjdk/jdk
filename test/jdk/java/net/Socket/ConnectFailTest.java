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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8343791
 * @summary verifies that `connect()` failures throw the expected exception and leave socket in the expected state
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
     * Verifies that an unbound socket is closed when {@code connect()} fails.
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
     * Verifies that a bound socket is closed when {@code connect()} fails.
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
     * Verifies that a connected socket is not closed when {@code connect()} fails.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testConnectedSocket(Socket socket) throws Throwable {
        try (socket; ServerSocket serverSocket = createEphemeralServerSocket()) {
            socket.connect(serverSocket.getLocalSocketAddress());
            try (Socket _ = serverSocket.accept()) {
                assertTrue(socket.isBound());
                assertTrue(socket.isConnected());
                SocketException exception = assertThrows(
                        SocketException.class,
                        () -> socket.connect(REFUSING_SOCKET_ADDRESS));
                assertEquals("Already connected", exception.getMessage());
                assertFalse(socket.isClosed());
            }
        }
    }

    /**
     * Verifies that an unbound socket is closed when {@code connect()} is invoked using an unresolved address.
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
     * Verifies that a bound socket is closed when {@code connect()} is invoked using an unresolved address.
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
     * Verifies that a connected socket is not closed when {@code connect()} is invoked using an unresolved address.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testConnectedSocketWithUnresolvedAddress(Socket socket) throws Throwable {
        try (socket; ServerSocket serverSocket = createEphemeralServerSocket()) {
            socket.connect(serverSocket.getLocalSocketAddress());
            try (Socket _ = serverSocket.accept()) {
                assertTrue(socket.isBound());
                assertTrue(socket.isConnected());
                assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
                assertFalse(socket.isClosed());
            }
        }
    }

    static List<Socket> sockets() throws Exception {
        Socket socket = new Socket();
        @SuppressWarnings("resource")
        Socket channelSocket = SocketChannel.open().socket();
        return List.of(socket, channelSocket);
    }

    private static ServerSocket createEphemeralServerSocket() throws IOException {
        return new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
    }

}
