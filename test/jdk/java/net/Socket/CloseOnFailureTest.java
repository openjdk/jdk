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
import org.junit.function.ThrowingRunnable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketImplFactory;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8343791
 * @summary verifies the socket is closed on `connect()` failures
 * @library /test/lib
 * @run junit/othervm --add-opens java.base/java.net=ALL-UNNAMED CloseOnFailureTest
 */
class CloseOnFailureTest {

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

    private static final class MockSocketImpl extends ThrowingSocketImpl {

        private final AtomicInteger closeInvocationCounter = new AtomicInteger(0);

        private final Exception bindException;

        private final Exception connectException;

        private MockSocketImpl(Exception bindException, Exception connectException) {
            this.bindException = bindException;
            this.connectException = connectException;
        }

        @Override
        protected void create(boolean stream) {}

        @Override
        protected void bind(InetAddress host, int port) throws IOException {
            throwIfPresent(bindException);
        }

        @Override
        protected void connect(SocketAddress address, int timeoutMillis) throws IOException {
            throwIfPresent(connectException);
        }

        private void throwIfPresent(Exception exception) throws IOException {
            if (exception != null) {
                switch (exception) {
                    case IOException error -> throw error;
                    case RuntimeException error -> throw error;
                    default -> throw new IllegalStateException(
                            "unknown exception type: " + exception.getClass().getCanonicalName());
                }
            }
        }

        @Override
        protected void close() {
            closeInvocationCounter.incrementAndGet();
        }

    }

    @ParameterizedTest
    @MethodSource("ctorShouldCloseOnFailuresTestCases")
    @SuppressWarnings("resource")
    void ctorShouldCloseOnFailures(TestCase testCase) throws Throwable {

        // Create a socket using the mock `SocketImpl` configured to fail
        withSocketImplFactory(() -> testCase.socketImpl, () -> {

            // Trigger the failure
            Exception exception = assertThrows(Exception.class, () -> {
                // Address and port are mostly ineffective.
                // They just need to be _valid enough_ to reach to the point where both `SocketImpl#bind()` and `SocketImpl#connect()` are invoked.
                // Failure will be triggered by the injected `SocketImpl`.
                InetAddress serverAddress = InetAddress.getLoopbackAddress();
                new Socket(serverAddress, DEAD_SERVER_PORT, null, 0);
            });

            // Run verifications
            testCase.caughtExceptionVerifier.accept(exception);
            testCase.socketImplVerifier.run();

        });

    }

    static List<TestCase> ctorShouldCloseOnFailuresTestCases() {
        return List.of(
                TestCase.BindFailureFactory.iOExceptionTestCase(),
                TestCase.ConnectFailureFactory.iOExceptionTestCase(),
                TestCase.ConnectFailureFactory.illegalArgumentExceptionTestCase(1));
    }

    @Test
    void connectShouldCloseOnUnresolvedAddress() throws IOException {
        MockSocketImpl socketImpl = new MockSocketImpl(null, null);
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
    void connectShouldCloseOnFailures(TestCase testCase) throws Throwable {

        // Create a socket using the mock `SocketImpl` configured to fail
        try (Socket socket = new Socket(testCase.socketImpl) {}) {

            // Trigger the failure
            Exception exception = assertThrows(Exception.class, () -> {
                SocketAddress address = Utils.refusingEndpoint();
                // Address and timeout are mostly ineffective.
                // They just need to be _valid enough_ to reach to the `SocketImpl#connect()` invocation.
                // Failure will be triggered by the injected `SocketImpl`.
                socket.connect(address, 10_000);
            });

            // Run verifications
            testCase.caughtExceptionVerifier.accept(exception);
            testCase.socketImplVerifier.run();
            testCase.socketVerifier.accept(socket);

        }

    }

    static List<TestCase> connectShouldCloseOnFailuresTestCases() {
        return List.of(
                TestCase.ConnectFailureFactory.iOExceptionTestCase(),
                TestCase.ConnectFailureFactory.illegalArgumentExceptionTestCase(0));
    }

    private record TestCase(
            String description,
            MockSocketImpl socketImpl,
            ThrowingConsumer<Exception> caughtExceptionVerifier,
            ThrowingRunnable socketImplVerifier,
            ThrowingConsumer<Socket> socketVerifier) {

        private static final String EXCEPTION_MESSAGE = "intentional test failure";

        private static final class BindFailureFactory {

            private static TestCase iOExceptionTestCase() {
                Exception bindException = new IOException(EXCEPTION_MESSAGE);
                MockSocketImpl socketImpl = new MockSocketImpl(bindException, null);
                String description = String.format(
                        "%s.%s",
                        BindFailureFactory.class.getSimpleName(),
                        bindException.getClass().getSimpleName());
                return new TestCase(
                        description,
                        socketImpl,
                        caughtException -> assertSame(bindException, caughtException),
                        () -> assertEquals(1, socketImpl.closeInvocationCounter.get()),
                        _ -> {});
            }

        }

        private static final class ConnectFailureFactory {

            private static TestCase iOExceptionTestCase() {
                Exception connectException = new IOException(EXCEPTION_MESSAGE);
                MockSocketImpl socketImpl = new MockSocketImpl(null, connectException);
                String description = String.format(
                        "%s.%s",
                        ConnectFailureFactory.class.getSimpleName(),
                        connectException.getClass().getSimpleName());
                return new TestCase(
                        description,
                        socketImpl,
                        caughtException -> assertSame(connectException, caughtException),
                        () -> assertEquals(1, socketImpl.closeInvocationCounter.get()),
                        _ -> {});
            }

            private static TestCase illegalArgumentExceptionTestCase(int expectedCloseInvocationCount) {
                Exception connectException = new IllegalArgumentException(EXCEPTION_MESSAGE);
                MockSocketImpl socketImpl = new MockSocketImpl(null, connectException);
                String description = String.format(
                        "%s.%s",
                        ConnectFailureFactory.class.getSimpleName(),
                        connectException.getClass().getSimpleName());
                return new TestCase(
                        description,
                        socketImpl,
                        caughtException -> assertSame(connectException, caughtException),
                        () -> assertEquals(expectedCloseInvocationCount, socketImpl.closeInvocationCounter.get()),
                        _ -> {
                        });
            }

        }

        @Override
        public String toString() {
            return description;
        }

    }

}
