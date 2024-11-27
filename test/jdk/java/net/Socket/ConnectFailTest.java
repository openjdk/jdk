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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

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
    void verifyUnresolvedAddress() {
        assertTrue(UNRESOLVED_ADDRESS.isUnresolved());
    }

    // Socket should be closed when `unboundSocket.connect()` fails ////////////////////////////////////////////////////

    @Test
    void unboundSocketShouldBeClosedWhenConnectFails() throws Exception {
        unboundSocketShouldBeClosedWhenConnectFails(ConnectFailTest::createUnboundSocket);
    }

    @Test
    void unboundNioSocketShouldBeClosedWhenConnectFails() throws Exception {
        unboundSocketShouldBeClosedWhenConnectFails(ConnectFailTest::createUnboundNioSocket);
    }

    private static void unboundSocketShouldBeClosedWhenConnectFails(
            ThrowingSupplier<Socket> unboundSocketFactory)
            throws Exception {
        try (Socket socket = unboundSocketFactory.get()) {
            assertFalse(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    // Socket should be closed when `boundSocket.connect()` fails //////////////////////////////////////////////////////

    @Test
    void boundSocketShouldBeClosedWhenConnectFails() throws Exception {
        boundSocketShouldBeClosedWhenConnectFails(
                ConnectFailTest::createBoundSocket);
    }

    @Test
    void boundNioSocketShouldBeClosedWhenConnectFails() throws Exception {
        boundSocketShouldBeClosedWhenConnectFails(
                ConnectFailTest::createBoundNioSocket);
    }

    private static void boundSocketShouldBeClosedWhenConnectFails(
            ThrowingSupplier<Socket> boundSocketFactory)
            throws Exception {
        try (Socket socket = boundSocketFactory.get()) {
            assertTrue(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    // Socket should *NOT* be closed when `connectedSocket.connect()` fails ////////////////////////////////////////////

    @Test
    void connectedSocketShouldNotBeClosedWhenConnectFails() throws Exception {
        connectedSocketShouldNotBeClosedWhenConnectFails(
                ConnectFailTest::createConnectedSocket,
                runnable -> {
                    SocketException exception = assertThrows(SocketException.class, runnable::run);
                    assertEquals("already connected", exception.getMessage());
                });
    }

    @Test
    void connectedNioSocketShouldNotBeClosedWhenConnectFails() throws Exception {
        connectedSocketShouldNotBeClosedWhenConnectFails(
                ConnectFailTest::createConnectedNioSocket,
                runnable -> assertThrows(AlreadyConnectedException.class, runnable::run));
    }

    private static void connectedSocketShouldNotBeClosedWhenConnectFails(
            ThrowingFunction<SocketAddress, Socket> connectedSocketFactory,
            Consumer<ThrowingRunnable> reconnectFailureVerifier)
            throws Exception {
        ServerSocketTestUtil.withEphemeralServerSocket(serverSocket -> {
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

    // Socket should be closed when `unboundSocket.connect(unresolvedAddress)` fails ///////////////////////////////////

    @Test
    void unboundSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails() throws Exception {
        unboundSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails(
                ConnectFailTest::createUnboundSocket);
    }

    private static Socket createUnboundSocket() {
        return new Socket();
    }

    @Test
    void unboundNioSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails() throws Exception {
        unboundSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails(
                ConnectFailTest::createUnboundNioSocket);
    }

    @SuppressWarnings("resource")
    private static Socket createUnboundNioSocket() throws IOException {
        return SocketChannel.open().socket();
    }

    private static void unboundSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails(
            ThrowingSupplier<Socket> unboundSocketFactory)
            throws Exception {
        try (Socket socket = unboundSocketFactory.get()) {
            assertFalse(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    // Socket should be closed when `boundSocket.connect(unresolvedAddress)` fails /////////////////////////////////////

    @Test
    void boundSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails() throws Exception {
        boundSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails(
                ConnectFailTest::createBoundSocket);
    }

    private static Socket createBoundSocket() throws IOException {
        Socket socket = new Socket();
        socket.bind(new InetSocketAddress(0));
        return socket;
    }

    @Test
    void boundNioSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails() throws Exception {
        boundSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails(
                ConnectFailTest::createBoundNioSocket);
    }

    @SuppressWarnings("resource")
    private static Socket createBoundNioSocket() throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.bind(new InetSocketAddress(0));
        return channel.socket();
    }

    private static void boundSocketShouldBeClosedWhenConnectWithUnresolvedAddressFails(
            ThrowingSupplier<Socket> boundSocketFactory)
            throws Exception {
        try (Socket socket = boundSocketFactory.get()) {
            assertTrue(socket.isBound());
            assertFalse(socket.isConnected());
            assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    // Socket should *NOT* be closed when `connectedSocket.connect(unresolvedAddress)` fails ///////////////////////////

    @Test
    void connectedSocketShouldNotBeClosedWhenConnectWithUnresolvedAddressFails() throws Exception {
        connectedSocketShouldNotBeClosedWhenConnectWithUnresolvedAddressFails(
                ConnectFailTest::createConnectedSocket);
    }

    private static Socket createConnectedSocket(SocketAddress address) throws IOException {
        InetSocketAddress inetAddress = (InetSocketAddress) address;
        return new Socket(inetAddress.getAddress(), inetAddress.getPort());
    }

    @Test
    void connectedNioSocketShouldNotBeClosedWhenConnectWithUnresolvedAddressFails() throws Exception {
        connectedSocketShouldNotBeClosedWhenConnectWithUnresolvedAddressFails(
                ConnectFailTest::createConnectedNioSocket);
    }

    @SuppressWarnings("resource")
    private static Socket createConnectedNioSocket(SocketAddress address) throws IOException {
        return SocketChannel.open(address).socket();
    }

    private static void connectedSocketShouldNotBeClosedWhenConnectWithUnresolvedAddressFails(
            ThrowingFunction<SocketAddress, Socket> connectedSocketFactory)
            throws Exception {
        ServerSocketTestUtil.withEphemeralServerSocket(serverSocket -> {
            SocketAddress serverSocketAddress = serverSocket.getLocalSocketAddress();
            try (Socket socket = connectedSocketFactory.apply(serverSocketAddress)) {
                assertTrue(socket.isBound());
                assertTrue(socket.isConnected());
                assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
                assertFalse(socket.isClosed());
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;

    }

    @FunctionalInterface
    private interface ThrowingSupplier<V> {

        V get() throws Exception;

    }

    @FunctionalInterface
    private interface ThrowingFunction<I, O> {

        O apply(I input) throws Exception;

    }

}
