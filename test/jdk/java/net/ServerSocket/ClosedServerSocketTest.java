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
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/*
 * @test
 * @summary verifies that the APIs on java.net.ServerSocket throw expected exceptions
 *          when invoked on a closed ServerSocket
 * @run junit ClosedServerSocketTest
 */
public class ClosedServerSocketTest {

    private static final InetAddress loopback = InetAddress.getLoopbackAddress();
    private static final InetSocketAddress loopbackEphemeral = new InetSocketAddress(loopback, 0);

    @FunctionalInterface
    private interface ServerSocketOp {
        void apply(ServerSocket ss) throws IOException;
    }


    static Stream<Arguments> ioExceptionOperations() {
        return Stream.of(
                Arguments.of("accept()", (ServerSocketOp) ss -> {
                    var _ = ss.accept();
                }),
                Arguments.of("bind()", (ServerSocketOp) ss -> {
                    ss.bind(loopbackEphemeral);
                }),
                Arguments.of("bind(SocketAddress, int)", (ServerSocketOp) ss -> {
                    ss.bind(loopbackEphemeral, 10);
                }),
                Arguments.of("getOption()", (ServerSocketOp) ss -> {
                    var _ = ss.getOption(StandardSocketOptions.SO_RCVBUF);
                }),
                Arguments.of("getSoTimeout()", (ServerSocketOp) ss -> {
                    var _ = ss.getSoTimeout();
                }),
                Arguments.of("setOption()", (ServerSocketOp) ss -> {
                    var _ = ss.setOption(StandardSocketOptions.SO_RCVBUF, 1024);
                })
        );
    }

    static Stream<Arguments> socketExceptionOperations() {
        return Stream.of(
                Arguments.of("getReceiveBufferSize()", (ServerSocketOp) ss -> {
                    var _ = ss.getReceiveBufferSize();
                }),
                Arguments.of("getReuseAddress()", (ServerSocketOp) ss -> {
                    var _ = ss.getReuseAddress();
                }),
                Arguments.of("setReceiveBufferSize()", (ServerSocketOp) ss -> {
                    ss.setReceiveBufferSize(1024);
                }),
                Arguments.of("setReuseAddress()", (ServerSocketOp) ss -> {
                    ss.setReuseAddress(false);
                }),
                Arguments.of("setSoTimeout()", (ServerSocketOp) ss -> {
                    ss.setSoTimeout(1000);
                })
        );
    }

    static Stream<Arguments> noExceptionOperations() {
        return Stream.of(
                Arguments.of("close()", (ServerSocketOp) ss -> {
                    ss.close();
                }),
                Arguments.of("getInetAddress()", (ServerSocketOp) ss -> {
                    var _ = ss.getInetAddress();
                }),
                Arguments.of("getLocalPort()", (ServerSocketOp) ss -> {
                    var _ = ss.getLocalPort();
                }),
                Arguments.of("getLocalSocketAddress()", (ServerSocketOp) ss -> {
                    var _ = ss.getLocalSocketAddress();
                }),
                Arguments.of("isBound()", (ServerSocketOp) ss -> {
                    var _ = ss.isBound();
                }),
                Arguments.of("isClosed()", (ServerSocketOp) ss -> {
                    var _ = ss.isClosed();
                }),
                Arguments.of("supportedOptions()", (ServerSocketOp) ss -> {
                    var _ = ss.supportedOptions();
                })
        );
    }

    /**
     * Verifies that various operations that specify to throw an IOException on a
     * closed ServerSocket, do indeed throw it.
     */
    @ParameterizedTest
    @MethodSource("ioExceptionOperations")
    public void testIOExceptionThrown(final String opName, final ServerSocketOp op)
            throws Exception {
        test(IOException.class, false, opName, op);
    }

    /**
     * Verifies that various operations that specify to throw a SocketOperation on a
     * closed ServerSocket, do indeed throw it.
     */
    @ParameterizedTest
    @MethodSource("socketExceptionOperations")
    public void testSocketExceptionThrown(final String opName, final ServerSocketOp op)
            throws Exception {
        test(SocketException.class, true, opName, op);
    }

    /**
     * Verifies that various operations that aren't expected to throw an exception on a
     * closed ServerSocket, complete normally.
     */
    @ParameterizedTest
    @MethodSource("noExceptionOperations")
    public void testNoExceptionThrown(final String opName, final ServerSocketOp op)
            throws Exception {
        try (final ServerSocket ss = new ServerSocket()) {
            // close and then invoke the operation on the ServerSocket
            ss.close();
            op.apply(ss);
        }
    }

    private static void test(final Class<? extends Exception> expectedExceptionType,
                             final boolean exactType,
                             final String opName, final ServerSocketOp op) throws Exception {
        try (final ServerSocket ss = new ServerSocket()) {
            // close and then invoke the operation on the ServerSocket
            ss.close();
            if (exactType) {
                assertThrowsExactly(expectedExceptionType,
                        () -> op.apply(ss), opName + " when already closed didn't throw "
                                + expectedExceptionType.getName());
            } else {
                assertThrows(expectedExceptionType,
                        () -> op.apply(ss), opName + " when already closed didn't throw "
                                + expectedExceptionType.getName());
            }
        }
    }
}
