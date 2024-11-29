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

import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketImpl;
import java.net.SocketImplFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @summary verifies that the socket is closed on constructor failures
 * @modules java.base/java.net:+open
 * @run junit CtorFailTest
 */
class CtorFailTest {

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

    private static void withSocketImplFactory(SocketImplFactory newFactory, Executable executable) throws Throwable {
        SocketImplFactory oldFactory = (SocketImplFactory) SOCKET_IMPL_FACTORY_HANDLE.getAndSet(newFactory);
        try {
            executable.execute();
        } finally {
            SOCKET_IMPL_FACTORY_HANDLE.set(oldFactory);
        }
    }

    @ParameterizedTest
    @MethodSource("socketImpls")
    @SuppressWarnings("resource")
    void test(MockSocketImpl socketImpl) throws Throwable {
        withSocketImplFactory(() -> socketImpl, () -> {

            // Trigger the failure
            Exception caughtException = assertThrows(Exception.class, () -> {
                // Address and port are mostly ineffective.
                // They just need to be _valid enough_ to reach to the point where both `SocketImpl#bind()` and `SocketImpl#connect()` are invoked.
                // Failure will be triggered by the injected `SocketImpl`.
                InetAddress serverAddress = InetAddress.getLoopbackAddress();
                new Socket(serverAddress, DEAD_SERVER_PORT, null, 0);
            });

            // Run verifications
            Exception expectedException = socketImpl.bindException != null
                    ? socketImpl.bindException
                    : socketImpl.connectException;
            assertSame(expectedException, caughtException);
            assertEquals(1, socketImpl.closeInvocationCounter.get());

        });
    }

    static List<MockSocketImpl> socketImpls() {
        String exceptionMessage = "intentional test failure";
        IOException checkedException = new IOException(exceptionMessage);
        IllegalArgumentException uncheckedException = new IllegalArgumentException(exceptionMessage);
        return List.of(
                new MockSocketImpl(checkedException, null),
                new MockSocketImpl(null, checkedException),
                new MockSocketImpl(uncheckedException, null),
                new MockSocketImpl(null, uncheckedException));
    }

    private static final class MockSocketImpl extends SocketImpl {

        private final AtomicInteger closeInvocationCounter = new AtomicInteger(0);

        private final Exception bindException;

        private final Exception connectException;

        private MockSocketImpl(Exception bindException, Exception connectException) {
            this.bindException = bindException;
            this.connectException = connectException;
        }

        @Override
        protected void create(boolean stream) {
            // Do nothing
        }

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
        public String toString() {
            record MockSocket(Exception bindException, Exception connectException, int closeInvocationCounter) {}
            return new MockSocket(bindException, connectException, closeInvocationCounter.get()).toString();
        }

        // Rest of the `SocketImpl` methods should not be used, hence overriding them to throw `UOE`

        @Override
        protected void close() {
            closeInvocationCounter.incrementAndGet();
        }

        @Override
        protected void connect(String host, int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void connect(InetAddress address, int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void listen(int backlog) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void accept(SocketImpl impl) {
            throw new UnsupportedOperationException();

        }

        @Override
        protected InputStream getInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected int available() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void sendUrgentData(int data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setOption(int optID, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getOption(int optID) {
            throw new UnsupportedOperationException();
        }

    }

}
