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

import org.junit.function.ThrowingRunnable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketImplFactory;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8343791
 * @summary using a `SocketImpl` mock, verifies that the socket is closed on constructor failures
 * @library /test/lib
 * @run junit/othervm --add-opens java.base/java.net=ALL-UNNAMED CloseOnCtorFailureMockTest
 */
class CloseOnCtorFailureMockTest {

    private static final VarHandle SOCKET_IMPL_FACTORY_HANDLE = createSocketImplFactoryHandle();

    private static final int DEAD_SERVER_PORT = 0xDEAD;

    private static VarHandle createSocketImplFactoryHandle() {
        try {
            Field field = Socket.class.getDeclaredField("factory");
            field.setAccessible(true);
            return MethodHandles
                    .privateLookupIn(Socket.class, MethodHandles.lookup())
                    .findStaticVarHandle(Socket.class, "factory", SocketImplFactory.class);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void withSocketImplFactory(SocketImplFactory newFactory, ThrowingRunnable runnable) throws Throwable {
        SocketImplFactory oldFactory = (SocketImplFactory) SOCKET_IMPL_FACTORY_HANDLE.getAndSet(newFactory);
        try {
            runnable.run();
        } finally {
            SOCKET_IMPL_FACTORY_HANDLE.set(oldFactory);
        }
    }

    @ParameterizedTest
    @MethodSource("ctorShouldCloseOnFailuresTestCases")
    @SuppressWarnings("resource")
    void ctorShouldCloseOnFailures(CloseOnFailureMockTestBase.TestCase testCase) throws Throwable {

        // Create a socket using the mock `SocketImpl` configured to fail
        withSocketImplFactory(testCase::socketImpl, () -> {

            // Trigger the failure
            Exception exception = assertThrows(Exception.class, () -> {
                // Address and port are mostly ineffective.
                // They just need to be _valid enough_ to reach to the point where both `SocketImpl#bind()` and `SocketImpl#connect()` are invoked.
                // Failure will be triggered by the injected `SocketImpl`.
                InetAddress serverAddress = InetAddress.getLoopbackAddress();
                new Socket(serverAddress, DEAD_SERVER_PORT, null, 0);
            });

            // Run verifications
            testCase.caughtExceptionVerifier().accept(exception);
            testCase.socketImplVerifier().run();

        });

    }

    static List<CloseOnFailureMockTestBase.TestCase> ctorShouldCloseOnFailuresTestCases() {
        return List.of(
                CloseOnFailureMockTestBase.TestCase.BindFailureFactory.iOExceptionTestCase(),
                CloseOnFailureMockTestBase.TestCase.ConnectFailureFactory.iOExceptionTestCase(),
                CloseOnFailureMockTestBase.TestCase.ConnectFailureFactory.illegalArgumentExceptionTestCase(1));
    }

}
