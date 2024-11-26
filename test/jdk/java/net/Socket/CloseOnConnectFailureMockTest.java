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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8343791
 * @summary using a `SocketImpl` mock, verifies that the socket is closed on `connect()` failures
 * @library /test/lib
 * @run junit/othervm --add-opens java.base/java.net=ALL-UNNAMED CloseOnConnectFailureMockTest
 */
class CloseOnConnectFailureMockTest {

    private static final int DEAD_SERVER_PORT = 0xDEAD;

    private static final InetSocketAddress REFUSING_SOCKET_ADDRESS = Utils.refusingEndpoint();

    @Test
    void connectShouldCloseOnUnresolvedAddress() throws IOException {
        CloseOnFailureMockTestBase.MockSocketImpl socketImpl =
                new CloseOnFailureMockTestBase.MockSocketImpl(null, null);
        try (Socket socket = new Socket(socketImpl) {}) {
            InetSocketAddress address = InetSocketAddress.createUnresolved("no.such.host", DEAD_SERVER_PORT);
            assertTrue(address.isUnresolved());
            assertThrows(
                    UnknownHostException.class,
                    () -> socket.connect(address, 10_000),
                    () -> address.getHostName() + " is unresolved");
        }
    }

    @ParameterizedTest
    @MethodSource("connectShouldCloseOnFailuresTestCases")
    void connectShouldCloseOnFailures(CloseOnFailureMockTestBase.TestCase testCase) throws Throwable {

        // Create a socket using the mock `SocketImpl` configured to fail
        try (Socket socket = new Socket(testCase.socketImpl()) {}) {

            // Trigger the failure
            Exception exception = assertThrows(Exception.class, () -> {
                // Address and timeout are mostly ineffective.
                // They just need to be _valid enough_ to reach to the `SocketImpl#connect()` invocation.
                // Failure will be triggered by the injected `SocketImpl`.
                socket.connect(REFUSING_SOCKET_ADDRESS, 10_000);
            });

            // Run verifications
            testCase.caughtExceptionVerifier().accept(exception);
            testCase.socketImplVerifier().run();
            testCase.socketVerifier().accept(socket);

        }

    }

    static List<CloseOnFailureMockTestBase.TestCase> connectShouldCloseOnFailuresTestCases() {
        return List.of(
                CloseOnFailureMockTestBase.TestCase.ConnectFailureFactory.iOExceptionTestCase(),
                CloseOnFailureMockTestBase.TestCase.ConnectFailureFactory.illegalArgumentExceptionTestCase(0));
    }

}
