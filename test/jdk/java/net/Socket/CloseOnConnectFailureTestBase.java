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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base class containing tests against <em>real sockets</em> and verifying the socket close after connect failures.
 *
 * @see CloseOnOioConnectFailureTest
 * @see CloseOnNioConnectFailureTest
 */
abstract class CloseOnConnectFailureTestBase {

    private static final int DEAD_SERVER_PORT = 0xDEAD;

    private static final InetSocketAddress REFUSING_SOCKET_ADDRESS = Utils.refusingEndpoint();

    private static final InetSocketAddress UNRESOLVED_ADDRESS =
            InetSocketAddress.createUnresolved("no.such.host", DEAD_SERVER_PORT);

    @Test
    void verifyUnresolvedAddress() {
        assertTrue(UNRESOLVED_ADDRESS.isUnresolved());
    }

    @Test
    void verifyUnboundSocket() throws IOException {
        try (Socket socket = createUnboundSocket()) {
            assertFalse(socket.isBound());
            assertFalse(socket.isConnected());
        }
    }

    @Test
    void verifyBoundSocket() throws IOException {
        try (Socket socket = createBoundSocket()) {
            assertTrue(socket.isBound());
            assertFalse(socket.isConnected());
        }
    }

    @Test
    @SuppressWarnings("resource")
    void verifyConnectedSocket() throws IOException {
        SocketAddress serverSocketAddress = serverSocket().getLocalSocketAddress();
        try (Socket socket = createConnectedSocket(serverSocketAddress)) {
            assertTrue(socket.isBound());
            assertTrue(socket.isConnected());
        }
    }

    @Test
    void socketShouldBeClosedWhenConnectFailsUsingUnboundSocket() throws IOException {
        try (Socket socket = createUnboundSocket()) {
            assertThrows(IOException.class, () -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    @Test
    void socketShouldBeClosedWhenConnectFailsUsingBoundSocket() throws IOException {
        try (Socket socket = createBoundSocket()) {
            assertThrows(IOException.class, () -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    @Test
    @SuppressWarnings("resource")
    void socketShouldNotBeClosedWhenConnectFailsUsingConnectedSocket() throws IOException {
        SocketAddress serverSocketAddress = serverSocket().getLocalSocketAddress();
        try (Socket socket = createConnectedSocket(serverSocketAddress)) {
            // OIO and NIO differ in how they fail on re-connection attempts on an already connected socket.
            // Hence, we delegate that check:
            assertReconnectFailure(() -> socket.connect(REFUSING_SOCKET_ADDRESS));
            assertFalse(socket.isClosed());
        }
    }

    @Test
    void socketShouldBeClosedWhenConnectWithUnresolvedAddressFailsUsingUnboundSocket() throws IOException {
        try (Socket socket = createUnboundSocket()) {
            assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    @Test
    void socketShouldBeClosedWhenConnectWithUnresolvedAddressFailsUsingBoundSocket() throws IOException {
        try (Socket socket = createBoundSocket()) {
            assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertTrue(socket.isClosed());
        }
    }

    @Test
    @SuppressWarnings("resource")
    void socketShouldNotBeClosedWhenConnectWithUnresolvedAddressFailsUsingConnectedSocket() throws IOException {
        SocketAddress serverSocketAddress = serverSocket().getLocalSocketAddress();
        try (Socket socket = createConnectedSocket(serverSocketAddress)) {
            assertThrows(IOException.class, () -> socket.connect(UNRESOLVED_ADDRESS));
            assertFalse(socket.isClosed());
        }
    }

    abstract ServerSocket serverSocket();

    abstract Socket createUnboundSocket() throws IOException;

    abstract Socket createBoundSocket() throws IOException;

    abstract Socket createConnectedSocket(SocketAddress address) throws IOException;

    abstract void assertReconnectFailure(Executable executable);

}
