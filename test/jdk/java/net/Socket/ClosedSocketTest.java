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
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/*
 * @test
 * @summary verifies that the APIs on java.net.Socket throw expected exceptions
 *          when invoked on a closed socket
 * @run junit ClosedSocketTest
 */
public class ClosedSocketTest {

    private static final InetAddress loopback = InetAddress.getLoopbackAddress();
    private static final InetSocketAddress loopbackEphemeral = new InetSocketAddress(loopback, 0);

    @FunctionalInterface
    private interface SocketOp {
        void apply(Socket s) throws IOException;
    }


    static Stream<Arguments> ioExceptionOperations() {
        return Stream.of(
                Arguments.of("bind()", (SocketOp) s -> {
                    s.bind(loopbackEphemeral);
                }),
                Arguments.of("connect()", (SocketOp) s -> {
                    // connect() will never get to the stage of attempting
                    // a connection against this port
                    final int dummyPort = 12345;
                    s.connect(new InetSocketAddress(loopback, dummyPort));
                }),
                Arguments.of("connect(SocketAddress, int)", (SocketOp) s -> {
                    // connect() will never get to the stage of attempting
                    // a connection against this port
                    final int dummyPort = 12345;
                    s.connect(new InetSocketAddress(loopback, dummyPort), 10);
                }),
                Arguments.of("getOption()", (SocketOp) s -> {
                    var _ = s.getOption(StandardSocketOptions.SO_RCVBUF);
                }),
                Arguments.of("getOutputStream()", (SocketOp) s -> {
                    var _ = s.getOutputStream();
                }),
                Arguments.of("shutdownInput()", (SocketOp) s -> {
                    s.shutdownInput();
                }),
                Arguments.of("shutdownOutput()", (SocketOp) s -> {
                    s.shutdownOutput();
                })
        );
    }

    static Stream<Arguments> socketExceptionOperations() {
        return Stream.of(
                Arguments.of("getKeepAlive()", (SocketOp) s -> {
                    var _ = s.getKeepAlive();
                }),
                Arguments.of("getOOBInline()", (SocketOp) s -> {
                    var _ = s.getOOBInline();
                }),
                Arguments.of("getReceiveBufferSize()", (SocketOp) s -> {
                    var _ = s.getReceiveBufferSize();
                }),
                Arguments.of("getReuseAddress()", (SocketOp) s -> {
                    var _ = s.getReuseAddress();
                }),
                Arguments.of("getSendBufferSize()", (SocketOp) s -> {
                    var _ = s.getSendBufferSize();
                }),
                Arguments.of("getSoLinger()", (SocketOp) s -> {
                    var _ = s.getSoLinger();
                }),
                Arguments.of("getSoTimeout()", (SocketOp) s -> {
                    var _ = s.getSoTimeout();
                }),
                Arguments.of("getTcpNoDelay()", (SocketOp) s -> {
                    var _ = s.getTcpNoDelay();
                }),
                Arguments.of("getTrafficClass()", (SocketOp) s -> {
                    var _ = s.getTrafficClass();
                }),
                Arguments.of("setKeepAlive()", (SocketOp) s -> {
                    s.setKeepAlive(false);
                }),
                Arguments.of("setOOBInline()", (SocketOp) s -> {
                    s.setOOBInline(false);
                }),
                Arguments.of("setOption()", (SocketOp) s -> {
                    s.setOption(StandardSocketOptions.SO_RCVBUF, 1024);
                }),
                Arguments.of("setReceiveBufferSize()", (SocketOp) s -> {
                    s.setReceiveBufferSize(1024);
                }),
                Arguments.of("setReuseAddress()", (SocketOp) s -> {
                    s.setReuseAddress(false);
                }),
                Arguments.of("setSendBufferSize()", (SocketOp) s -> {
                    s.setSendBufferSize(1024);
                }),
                Arguments.of("setSoLinger()", (SocketOp) s -> {
                    s.setSoLinger(false, 0);
                }),
                Arguments.of("setSoTimeout()", (SocketOp) s -> {
                    s.setSoTimeout(1000);
                }),
                Arguments.of("setTcpNoDelay()", (SocketOp) s -> {
                    s.setTcpNoDelay(false);
                }),
                Arguments.of("setTrafficClass()", (SocketOp) s -> {
                    s.setTrafficClass(123);
                })
        );
    }

    static Stream<Arguments> noExceptionOperations() {
        return Stream.of(
                Arguments.of("close()", (SocketOp) s -> {
                    s.close();
                }),
                Arguments.of("getInetAddress()", (SocketOp) s -> {
                    var _ = s.getInetAddress();
                }),
                Arguments.of("getLocalAddress()", (SocketOp) s -> {
                    var _ = s.getLocalAddress();
                }),
                Arguments.of("getLocalPort()", (SocketOp) s -> {
                    var _ = s.getLocalPort();
                }),
                Arguments.of("getLocalSocketAddress()", (SocketOp) s -> {
                    var _ = s.getLocalSocketAddress();
                }),
                Arguments.of("getPort()", (SocketOp) s -> {
                    var _ = s.getPort();
                }),
                Arguments.of("getRemoteSocketAddress()", (SocketOp) s -> {
                    var _ = s.getRemoteSocketAddress();
                }),
                Arguments.of("isBound()", (SocketOp) s -> {
                    var _ = s.isBound();
                }),
                Arguments.of("isClosed()", (SocketOp) s -> {
                    var _ = s.isClosed();
                }),
                Arguments.of("isConnected()", (SocketOp) s -> {
                    var _ = s.isConnected();
                }),
                Arguments.of("isInputShutdown()", (SocketOp) s -> {
                    var _ = s.isInputShutdown();
                }),
                Arguments.of("isOutputShutdown()", (SocketOp) s -> {
                    var _ = s.isOutputShutdown();
                }),
                Arguments.of("supportedOptions()", (SocketOp) s -> {
                    var _ = s.supportedOptions();
                })
        );
    }

    /**
     * Verifies that various operations that specify to throw an IOException on a closed socket,
     * do indeed throw it.
     */
    @ParameterizedTest
    @MethodSource("ioExceptionOperations")
    public void testIOExceptionThrown(final String opName, final SocketOp op)
            throws Exception {
        test(IOException.class, false, opName, op);
    }

    /**
     * Verifies that various operations that specify to throw a SocketOperation on a closed socket,
     * do indeed throw it.
     */
    @ParameterizedTest
    @MethodSource("socketExceptionOperations")
    public void testSocketExceptionThrown(final String opName, final SocketOp op)
            throws Exception {
        test(SocketException.class, true, opName, op);
    }

    /**
     * Verifies that various operations that aren't expected to throw an exception on a
     * closed socket, complete normally.
     */
    @ParameterizedTest
    @MethodSource("noExceptionOperations")
    public void testNoExceptionThrown(final String opName, final SocketOp op)
            throws Exception {
        try (final Socket s = new Socket()) {
            // close and then invoke the operation on the socket
            s.close();
            op.apply(s);
        }
    }

    private static void test(final Class<? extends Exception> expectedExceptionType,
                             final boolean exactType,
                             final String opName, final SocketOp op) throws Exception {
        try (final Socket s = new Socket()) {
            // close and then invoke the operation on the socket
            s.close();
            if (exactType) {
                assertThrowsExactly(expectedExceptionType,
                        () -> op.apply(s), opName + " when already closed didn't throw "
                                + expectedExceptionType.getName());
            } else {
                assertThrows(expectedExceptionType,
                        () -> op.apply(s), opName + " when already closed didn't throw "
                                + expectedExceptionType.getName());
            }
        }
    }
}
