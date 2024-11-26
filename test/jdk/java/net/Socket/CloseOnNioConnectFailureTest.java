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

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SocketChannel;

import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8343791
 * @summary verifies the socket obtained from `SocketChannel.open().socket()` (i.e., `SocketAdaptor`) is closed on `connect()` failures
 * @library /test/lib
 * @run junit/othervm --add-opens java.base/java.net=ALL-UNNAMED CloseOnNioConnectFailureTest
 */
@SuppressWarnings("resource")
class CloseOnNioConnectFailureTest extends CloseOnConnectFailureTestBase {

    @RegisterExtension
    static final ServerSocketExtension SERVER_SOCKET_EXTENSION = new ServerSocketExtension();

    @Override
    ServerSocket serverSocket() {
        return SERVER_SOCKET_EXTENSION.serverSocket;
    }

    Socket createUnboundSocket() throws IOException {
        return SocketChannel.open().socket();
    }

    Socket createBoundSocket() throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.bind(new InetSocketAddress(0));
        return channel.socket();
    }

    Socket createConnectedSocket(SocketAddress address) throws IOException {
        return SocketChannel.open(address).socket();
    }

    @Override
    void assertReconnectFailure(Executable executable) {
        assertThrows(AlreadyConnectedException.class, executable);
    }

}
