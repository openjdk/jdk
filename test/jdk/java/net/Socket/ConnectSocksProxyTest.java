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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import static java.net.InetAddress.getLoopbackAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8346017
 * @summary Verifies the `connect()` behaviour of a SOCKS proxy socket. In particular, that passing a resolvable
 *          unresolved address doesn't throw an exception.
 * @library /test/lib /java/net/Socks
 * @build SocksServer
 * @run junit ConnectSocksProxyTest
 */
class ConnectSocksProxyTest {

    // Implementation Note: Explicitly binding on the loopback address to avoid potential unstabilities.

    private static final int DEAD_SERVER_PORT = 0xDEAD;

    private static final InetSocketAddress REFUSING_SOCKET_ADDRESS = Utils.refusingEndpoint();

    private static final InetSocketAddress UNRESOLVED_ADDRESS =
            InetSocketAddress.createUnresolved("no.such.host", DEAD_SERVER_PORT);

    private static final String PROXY_AUTH_USERNAME = "foo";

    private static final String PROXY_AUTH_PASSWORD = "bar";

    private static SocksServer PROXY_SERVER;

    private static Proxy PROXY;

    @BeforeAll
    static void initAuthenticator() {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(PROXY_AUTH_USERNAME, PROXY_AUTH_PASSWORD.toCharArray());
            }
        });
    }

    @BeforeAll
    static void initProxyServer() throws IOException {
        PROXY_SERVER = new SocksServer(getLoopbackAddress(), 0, false);
        PROXY_SERVER.addUser(PROXY_AUTH_USERNAME, PROXY_AUTH_PASSWORD);
        PROXY_SERVER.start();
        InetSocketAddress proxyAddress = new InetSocketAddress(getLoopbackAddress(), PROXY_SERVER.getPort());
        PROXY = new Proxy(Proxy.Type.SOCKS, proxyAddress);
    }

    @AfterAll
    static void stopProxyServer() {
        PROXY_SERVER.close();
    }

    @Test
    void testUnresolvedAddress() {
        assertTrue(UNRESOLVED_ADDRESS.isUnresolved());
    }

    /**
     * Verifies that an unbound socket is closed when {@code connect()} fails.
     */
    @Test
    void testUnboundSocket() throws IOException {
        try (Socket socket = createProxiedSocket()) {
            assertFalse(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    /**
     * Verifies that a bound socket is closed when {@code connect()} fails.
     */
    @Test
    void testBoundSocket() throws IOException {
        try (Socket socket = createProxiedSocket()) {
            socket.bind(new InetSocketAddress(getLoopbackAddress(), 0));
            assertTrue(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    /**
     * Verifies that a connected socket is not closed when {@code connect()} fails.
     */
    @Test
    void testConnectedSocket() throws Throwable {
        try (Socket socket = createProxiedSocket();
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
     * Delegates to {@link #testUnconnectedSocketWithUnresolvedAddress(boolean, Socket)} using an unbound socket.
     */
    @Test
    void testUnboundSocketWithUnresolvedAddress() throws IOException {
        try (Socket socket = createProxiedSocket()) {
            assertFalse(socket.isBound());
            assertFalse(socket.isConnected());
            testUnconnectedSocketWithUnresolvedAddress(false, socket);
        }
    }

    /**
     * Delegates to {@link #testUnconnectedSocketWithUnresolvedAddress(boolean, Socket)} using a bound socket.
     */
    @Test
    void testBoundSocketWithUnresolvedAddress() throws IOException {
        try (Socket socket = createProxiedSocket()) {
            socket.bind(new InetSocketAddress(getLoopbackAddress(), 0));
            testUnconnectedSocketWithUnresolvedAddress(true, socket);
        }
    }

    /**
     * Verifies the behaviour of an unconnected socket when {@code connect()} is invoked using an unresolved address.
     */
    private static void testUnconnectedSocketWithUnresolvedAddress(boolean bound, Socket socket) throws IOException {
        assertEquals(bound, socket.isBound());
        assertFalse(socket.isConnected());
        try (ServerSocket serverSocket = createEphemeralServerSocket()) {
            InetSocketAddress unresolvedAddress = InetSocketAddress.createUnresolved(
                    getLoopbackAddress().getHostAddress(),
                    serverSocket.getLocalPort());
            socket.connect(unresolvedAddress);
            try (Socket _ = serverSocket.accept()) {
                assertTrue(socket.isBound());
                assertTrue(socket.isConnected());
                assertFalse(socket.isClosed());
            }
        }
    }

    /**
     * Verifies that a connected socket is not closed when {@code connect()} is invoked using an unresolved address.
     */
    @Test
    void testConnectedSocketWithUnresolvedAddress() throws Throwable {
        try (Socket socket = createProxiedSocket();
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

    private static Socket createProxiedSocket() {
        return new Socket(PROXY);
    }

    private static ServerSocket createEphemeralServerSocket() throws IOException {
        return new ServerSocket(0, 0, getLoopbackAddress());
    }

}
