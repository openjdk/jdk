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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.List;

import static java.net.InetAddress.getLoopbackAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8343791 8346017
 * @summary verifies that `connect()` failures throw the expected exception and leave socket in the expected state
 * @library /test/lib /java/net/Socks
 * @build SocksServer
 * @run junit ConnectFailTest
 */
class ConnectFailTest {

    private static final int DEAD_SERVER_PORT = 0xDEAD;

    private static final InetSocketAddress REFUSING_SOCKET_ADDRESS = Utils.refusingEndpoint();

    private static final InetSocketAddress UNRESOLVED_ADDRESS =
            InetSocketAddress.createUnresolved("no.such.host", DEAD_SERVER_PORT);

    private static final String SOCKS_AUTH_USERNAME = "foo";

    private static final String SOCKS_AUTH_PASSWORD = "bar";

    private static SocksServer SOCKS_SERVER;

    private static Proxy SOCKS_PROXY;

    @BeforeAll
    static void initAuthenticator() {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SOCKS_AUTH_USERNAME, SOCKS_AUTH_PASSWORD.toCharArray());
            }
        });
    }

    @BeforeAll
    static void initSocksServer() throws IOException {
        SOCKS_SERVER = new SocksServer(0);
        SOCKS_SERVER.addUser(SOCKS_AUTH_USERNAME, SOCKS_AUTH_PASSWORD);
        SOCKS_SERVER.start();
        InetSocketAddress proxyAddress = new InetSocketAddress(getLoopbackAddress(), SOCKS_SERVER.getPort());
        SOCKS_PROXY = new Proxy(Proxy.Type.SOCKS, proxyAddress);
    }

    @AfterAll
    static void stopSocksServer() {
        SOCKS_SERVER.close();
    }

    /**
     * Verifies that an unbound socket is closed when {@code connect()} fails.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testUnboundSocket(SocketArg socketArg) throws IOException {
        try (Socket socket = socketArg.socket) {
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
    void testBoundSocket(SocketArg socketArg) throws IOException {
        try (Socket socket = socketArg.socket) {
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
    void testConnectedSocket(SocketArg socketArg) throws Throwable {
        try (Socket socket = socketArg.socket;
             ServerSocket serverSocket = createEphemeralServerSocket()) {
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
     * Delegates to {@link #testUnconnectedSocketWithUnresolvedAddress(boolean, SocketArg)} using an unbound socket.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testUnboundSocketWithUnresolvedAddress(SocketArg socketArg) throws IOException {
        try (Socket socket = socketArg.socket) {
            assertFalse(socket.isBound());
            assertFalse(socket.isConnected());
            testUnconnectedSocketWithUnresolvedAddress(false, socketArg);
        }
    }

    /**
     * Delegates to {@link #testUnconnectedSocketWithUnresolvedAddress(boolean, SocketArg)} using a bound socket.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testBoundSocketWithUnresolvedAddress(SocketArg socketArg) throws IOException {
        try (Socket socket = socketArg.socket) {
            socket.bind(new InetSocketAddress(0));
            testUnconnectedSocketWithUnresolvedAddress(true, socketArg);
        }
    }

    /**
     * Verifies the behaviour of an unconnected socket when {@code connect()} is invoked using an unresolved address.
     */
    private static void testUnconnectedSocketWithUnresolvedAddress(boolean bound, SocketArg socketArg) throws IOException {
        Socket socket = socketArg.socket;
        assertEquals(bound, socket.isBound());
        assertFalse(socket.isConnected());
        if (socketArg.proxied) {
            try (ServerSocket serverSocket = createEphemeralServerSocket()) {
                InetSocketAddress unresolvedAddress =
                        InetSocketAddress.createUnresolved("localhost", serverSocket.getLocalPort());
                socket.connect(unresolvedAddress);
                try (Socket _ = serverSocket.accept()) {
                    assertTrue(socket.isBound());
                    assertTrue(socket.isConnected());
                    assertFalse(socket.isClosed());
                }
            }
        } else {
            assertThrows(UnknownHostException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    /**
     * Verifies that a connected socket is not closed when {@code connect()} is invoked using an unresolved address.
     */
    @ParameterizedTest
    @MethodSource("sockets")
    void testConnectedSocketWithUnresolvedAddress(SocketArg socketArg) throws Throwable {
        try (Socket socket = socketArg.socket;
             ServerSocket serverSocket = createEphemeralServerSocket()) {
            socket.connect(serverSocket.getLocalSocketAddress());
            try (Socket _ = serverSocket.accept()) {
                assertTrue(socket.isBound());
                assertTrue(socket.isConnected());
                SocketException exception = assertThrows(
                        SocketException.class,
                        () -> socket.connect(UNRESOLVED_ADDRESS));
                assertEquals("Already connected", exception.getMessage());
                assertFalse(socket.isClosed());
            }
        }
    }

    static List<SocketArg> sockets() throws Exception {
        Socket socket = new Socket();
        Socket proxiedSocket = new Socket(SOCKS_PROXY);
        @SuppressWarnings("resource")
        Socket channelSocket = SocketChannel.open().socket();
        return List.of(
                new SocketArg(socket, false),
                new SocketArg(proxiedSocket, true),
                new SocketArg(channelSocket, false));
    }

    private record SocketArg(Socket socket, boolean proxied) {}

    private static ServerSocket createEphemeralServerSocket() throws IOException {
        return new ServerSocket(0, 0, getLoopbackAddress());
    }

}
