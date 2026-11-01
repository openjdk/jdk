/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8240533
 * @summary Check that DatagramSocket, MulticastSocket and DatagramSocketAdaptor
 *          throw expected Exception when connecting to port 0
 * @run junit/othervm ${test.main.class}
 */

public class ConnectPortZero {
    private static InetAddress loopbackAddr, wildcardAddr;
    private static final Class<SocketException> SE = SocketException.class;
    private static final Class<UncheckedIOException> UCIOE = UncheckedIOException.class;

    @BeforeAll
    public static void setUp() throws IOException {
        loopbackAddr = InetAddress.getLoopbackAddress();
        wildcardAddr = new InetSocketAddress(0).getAddress();
    }

    public static List<Arguments> testCases() throws IOException {
        // Note that Closeable arguments passed to a ParameterizedTest are automatically
        // closed by JUnit. We do not want to rely on this, but we do need to
        // create a new set of sockets for each invocation of this method, so that
        // the next test method invoked doesn't get a closed socket.
        return List.of(
                Arguments.of(new DatagramSocket(),            loopbackAddr),
                Arguments.of(DatagramChannel.open().socket(), loopbackAddr),
                Arguments.of(new MulticastSocket(),           loopbackAddr),
                Arguments.of(new DatagramSocket(),            wildcardAddr),
                Arguments.of(DatagramChannel.open().socket(), wildcardAddr),
                Arguments.of(new MulticastSocket(),           wildcardAddr)
        );
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void testConnect(DatagramSocket socket, InetAddress addr) {
        try (var ds = socket) {
            assertFalse(ds.isConnected());
            assertFalse(ds.isClosed());
            Throwable t = assertThrows(UCIOE, () -> ds.connect(addr, 0));
            assertSame(SE, t.getCause().getClass());
            assertFalse(ds.isConnected());
            assertFalse(ds.isClosed());
            assertThrows(SE, () -> ds
                    .connect(new InetSocketAddress(addr, 0)));
            assertFalse(ds.isConnected());
            assertFalse(ds.isClosed());
        }
    }
}
