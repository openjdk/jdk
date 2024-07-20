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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @summary verifies that the APIs on java.net.Socket and java.net.ServerSocket
 *          throw the specified exceptions
 * @run junit SocketBasicExceptionsTest
 */
public class SocketBasicExceptionsTest {

    private static final InetAddress loopback = InetAddress.getLoopbackAddress();

    /**
     * Verifies that the ServerSocket.bind() throws IOException when already bound or closed
     */
    @Test
    public void testServerSocketBindException() throws Exception {
        final InetSocketAddress bindAddr = new InetSocketAddress(loopback, 0);
        try (final ServerSocket ss = new ServerSocket()) {
            ss.bind(bindAddr);
            // try binding again, must fail
            assertThrows(IOException.class, () -> ss.bind(bindAddr),
                    "ServerSocket.bind() when already bound didn't throw IOException");
            assertThrows(IOException.class, () -> ss.bind(bindAddr, 10),
                    "ServerSocket.bind() when already bound didn't throw IOException");
            // now close and try to bind, that too must fail
            ss.close();
            assertThrows(IOException.class, () -> ss.bind(bindAddr),
                    "ServerSocket.bind() when already closed didn't throw IOException");
            assertThrows(IOException.class, () -> ss.bind(bindAddr, 10),
                    "ServerSocket.bind() when already closed didn't throw IOException");
        }
    }

    /**
     * Verifies that the ServerSocket.accept() throws IOException when not bound or already closed
     */
    @Test
    public void testServerSocketAcceptException() throws Exception {
        final InetSocketAddress bindAddr = new InetSocketAddress(loopback, 0);
        try (final ServerSocket ss = new ServerSocket()) {
            // try accept() without being bound, must fail
            assertThrows(IOException.class, ss::accept,
                    "ServerSocket.accept() when not bound didn't throw IOException");
            // now bind before closing
            ss.bind(bindAddr);
            // now close and try to accept(), must fail
            ss.close();
            assertThrows(IOException.class, ss::accept,
                    "ServerSocket.accept() when already closed didn't throw IOException");
        }
    }

    /**
     * Verifies that the Socket.bind() throws IOException when already bound or closed
     */
    @Test
    public void testSocketBindException() throws Exception {
        final InetSocketAddress bindAddr = new InetSocketAddress(loopback, 0);
        try (final Socket s = new Socket()) {
            s.bind(bindAddr);
            // try binding again, must fail
            assertThrows(IOException.class, () -> s.bind(bindAddr),
                    "Socket.bind() when already bound didn't throw IOException");
            // now close and try to bind, that too must fail
            s.close();
            assertThrows(IOException.class, () -> s.bind(bindAddr),
                    "Socket.bind() when already closed didn't throw IOException");
        }
    }

    /**
     * Verifies that the Socket.connect() throws IOException when already connected or closed
     */
    @Test
    public void testSocketConnectException() throws Exception {
        try (final ServerSocket ss = new ServerSocket(0, 0, loopback);
             final Socket s = new Socket()) {
            final Thread connAcceptor = new Thread(() -> {
                try {
                    try (final Socket acceptedSocket = ss.accept()) {
                        System.out.println("accepted connection from " + acceptedSocket);
                    }
                } catch (IOException ioe) {
                    System.err.println("ignoring exception in server acceptor thread: " + ioe);
                    ioe.printStackTrace();
                }
            });
            connAcceptor.setDaemon(true);
            connAcceptor.start();
            // establish connection to the server
            final SocketAddress serverAddr = ss.getLocalSocketAddress();
            System.out.println("establishing connection to " + serverAddr);
            s.connect(serverAddr);
            // try connecting again, must fail
            assertThrows(IOException.class, () -> s.connect(serverAddr),
                    "Socket.connect() when already connected didn't throw IOException");
            assertThrows(IOException.class, () -> s.connect(serverAddr, 10),
                    "Socket.connect() when already connected didn't throw IOException");
            // now close and try to connect, that too must fail
            s.close();
            assertThrows(IOException.class, () -> s.connect(serverAddr),
                    "Socket.connect() when already closed didn't throw IOException");
            assertThrows(IOException.class, () -> s.connect(serverAddr, 10),
                    "Socket.connect() when already closed didn't throw IOException");
        }
    }
}
