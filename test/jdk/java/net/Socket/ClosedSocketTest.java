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
import java.net.Socket;
import java.net.SocketException;
import java.net.StandardSocketOptions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary verifies that the APIs on java.net.Socket throw expected exceptions
 *          when invoked on a closed socket
 * @run junit ClosedSocketTest
 */
public class ClosedSocketTest {

    private static final InetAddress loopback = InetAddress.getLoopbackAddress();
    private static final InetSocketAddress loopbackEphemeral = new InetSocketAddress(loopback, 0);

    /**
     * Verifies that various operations that specify to throw an IOException on a closed socket,
     * do indeed throw it.
     */
    @Test
    public void testIOExceptionThrown() throws Exception {
        try (final Socket s = new Socket()) {
            // close and then invoke the operation on the socket
            s.close();
            assertTrue(s.isClosed(), "socket isn't closed");
            assertThrows(IOException.class, () -> s.bind(loopbackEphemeral),
                    "bind() when already closed didn't throw IOException");
            // connect() will never get to the stage of attempting
            // a connection against this port
            final int dummyPort = 12345;
            assertThrows(IOException.class,
                    () -> s.connect(new InetSocketAddress(loopback, dummyPort)),
                    "connect() when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    () -> s.connect(new InetSocketAddress(loopback, dummyPort), 10),
                    "connect(SocketAddress, int) when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    () -> s.getOption(StandardSocketOptions.SO_RCVBUF),
                    "getOption() when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    s::getOutputStream,
                    "getOutputStream() when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    s::shutdownInput,
                    "shutdownInput() when already closed didn't throw IOException");
            assertThrows(IOException.class,
                    s::shutdownOutput,
                    "shutdownOutput() when already closed didn't throw IOException");
        }
    }

    /**
     * Verifies that various operations that specify to throw a SocketOperation on a closed socket,
     * do indeed throw it.
     */
    @Test
    public void testSocketExceptionThrown() throws Exception {
        try (final Socket s = new Socket()) {
            // close and then invoke the operations on the socket
            s.close();
            assertTrue(s.isClosed(), "socket isn't closed");
            assertThrowsExactly(SocketException.class,
                    s::getKeepAlive,
                    "getKeepAlive() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    s::getOOBInline,
                    "getOOBInline() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    s::getReceiveBufferSize,
                    "getReceiveBufferSize() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    s::getReuseAddress,
                    "getReuseAddress() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    s::getSendBufferSize,
                    "getSendBufferSize() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    s::getSoLinger,
                    "getSoLinger() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    s::getSoTimeout,
                    "getSoTimeout() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    s::getTcpNoDelay,
                    "getTcpNoDelay() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    s::getTrafficClass,
                    "getTrafficClass() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setKeepAlive(false),
                    "setKeepAlive() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setOOBInline(false),
                    "setOOBInline() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setOption(StandardSocketOptions.SO_RCVBUF, 1024),
                    "setOption() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setReceiveBufferSize(1024),
                    "setReceiveBufferSize() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setReuseAddress(false),
                    "setReuseAddress() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setSendBufferSize(1024),
                    "setSendBufferSize() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setSoLinger(false, 0),
                    "setSoLinger() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setSoTimeout(1000),
                    "setSoTimeout() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setTcpNoDelay(false),
                    "setTcpNoDelay() when already closed didn't throw SocketException");
            assertThrowsExactly(SocketException.class,
                    () -> s.setTrafficClass(123),
                    "setTrafficClass() when already closed didn't throw SocketException");
        }
    }

    /**
     * Verifies that various operations that aren't expected to throw an exception on a
     * closed socket, complete normally.
     */
    @Test
    public void testNoExceptionThrown() throws Exception {
        try (final Socket s = new Socket()) {
            // close and then invoke various operation on the socket and don't expect an exception
            s.close();
            assertTrue(s.isClosed(), "socket isn't closed");
            s.getInetAddress();
            s.getLocalAddress();
            s.getLocalPort();
            s.getLocalSocketAddress();
            s.getPort();
            s.getRemoteSocketAddress();
            s.isBound();
            s.isConnected();
            s.isInputShutdown();
            s.isOutputShutdown();
            s.supportedOptions();
        }
    }
}
