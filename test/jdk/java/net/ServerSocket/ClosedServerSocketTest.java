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
import java.net.SocketException;
import java.net.StandardSocketOptions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary verifies that the APIs on java.net.ServerSocket throw expected exceptions
 *          when invoked on a closed ServerSocket
 * @run junit ClosedServerSocketTest
 */
public class ClosedServerSocketTest {

    private static final InetAddress loopback = InetAddress.getLoopbackAddress();
    private static final InetSocketAddress loopbackEphemeral = new InetSocketAddress(loopback, 0);

    /**
     * Verifies that various operations that specify to throw an IOException on a
     * closed ServerSocket, do indeed throw it.
     */
    @Test
    public void testIOExceptionThrown() throws Exception {
        try (final ServerSocket ss = new ServerSocket()) {
            // close and then invoke the operations on the ServerSocket
            ss.close();
            assertTrue(ss.isClosed(), "ServerSocket isn't closed");
            assertThrows(IOException.class,
                    ss::accept,
                    "accept() when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    () -> ss.bind(loopbackEphemeral),
                    "bind() when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    () -> ss.bind(loopbackEphemeral, 10),
                    "bind(SocketAddress, int) when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    () -> ss.getOption(StandardSocketOptions.SO_RCVBUF),
                    "getOption() when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    ss::getSoTimeout,
                    "getSoTimeout() when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    () -> ss.setOption(StandardSocketOptions.SO_RCVBUF, 1024),
                    "setOption() when already closed didn't throw IOException");
        }
    }

    /**
     * Verifies that various operations that specify to throw a SocketOperation on a
     * closed ServerSocket, do indeed throw it.
     */
    @Test
    public void testSocketExceptionThrown() throws Exception {
        try (final ServerSocket ss = new ServerSocket()) {
            // close and then invoke the operations on the ServerSocket
            ss.close();
            assertTrue(ss.isClosed(), "ServerSocket isn't closed");
            assertThrowsExactly(SocketException.class,
                    ss::getReceiveBufferSize,
                    "getReceiveBufferSize() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    ss::getReuseAddress,
                    "getReuseAddress() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> ss.setReceiveBufferSize(1024),
                    "setReceiveBufferSize() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> ss.setReuseAddress(false),
                    "setReuseAddress() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> ss.setSoTimeout(1000),
                    "setSoTimeout() when already closed didn't throw SocketException");
        }
    }

    /**
     * Verifies that various operations that aren't expected to throw an exception on a
     * closed ServerSocket, complete normally.
     */
    @Test
    public void testNoExceptionThrown() throws Exception {
        try (final ServerSocket ss = new ServerSocket()) {
            // close and then invoke the operations on the ServerSocket
            ss.close();
            assertTrue(ss.isClosed(), "ServerSocket isn't closed");
            ss.getInetAddress();
            ss.getLocalPort();
            ss.getLocalSocketAddress();
            ss.isBound();
            ss.supportedOptions();
        }
    }
}
