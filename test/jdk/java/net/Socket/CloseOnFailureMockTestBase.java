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
import org.junit.jupiter.api.function.ThrowingConsumer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Base class containing tests against <em>mocked sockets</em> and verifying the socket close after connect failures.
 *
 * @see CloseOnConnectFailureMockTest
 * @see CloseOnCtorFailureMockTest
 */
final class CloseOnFailureMockTestBase {

    static final class MockSocketImpl extends ThrowingSocketImpl {

        private final AtomicInteger closeInvocationCounter = new AtomicInteger(0);

        private final Exception bindException;

        private final Exception connectException;

        MockSocketImpl(Exception bindException, Exception connectException) {
            this.bindException = bindException;
            this.connectException = connectException;
        }

        @Override
        protected void create(boolean stream) {
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
        protected void close() {
            closeInvocationCounter.incrementAndGet();
        }

    }

    record TestCase(
            String description,
            MockSocketImpl socketImpl,
            ThrowingConsumer<Exception> caughtExceptionVerifier,
            ThrowingRunnable socketImplVerifier,
            ThrowingConsumer<Socket> socketVerifier) {

        private static final String EXCEPTION_MESSAGE = "intentional test failure";

        static final class BindFailureFactory {

            static CloseOnFailureMockTestBase.TestCase iOExceptionTestCase() {
                Exception bindException = new IOException(EXCEPTION_MESSAGE);
                MockSocketImpl socketImpl = new MockSocketImpl(bindException, null);
                String description = String.format(
                        "%s.%s",
                        BindFailureFactory.class.getSimpleName(),
                        bindException.getClass().getSimpleName());
                return new CloseOnFailureMockTestBase.TestCase(
                        description,
                        socketImpl,
                        caughtException -> assertSame(bindException, caughtException),
                        () -> assertEquals(1, socketImpl.closeInvocationCounter.get()),
                        _ -> {
                        });
            }

        }

        static final class ConnectFailureFactory {

            static CloseOnFailureMockTestBase.TestCase iOExceptionTestCase() {
                Exception connectException = new IOException(EXCEPTION_MESSAGE);
                MockSocketImpl socketImpl = new MockSocketImpl(null, connectException);
                String description = String.format(
                        "%s.%s",
                        ConnectFailureFactory.class.getSimpleName(),
                        connectException.getClass().getSimpleName());
                return new CloseOnFailureMockTestBase.TestCase(
                        description,
                        socketImpl,
                        caughtException -> assertSame(connectException, caughtException),
                        () -> assertEquals(1, socketImpl.closeInvocationCounter.get()),
                        _ -> {
                        });
            }

            static CloseOnFailureMockTestBase.TestCase illegalArgumentExceptionTestCase(int expectedCloseInvocationCount) {
                Exception connectException = new IllegalArgumentException(EXCEPTION_MESSAGE);
                MockSocketImpl socketImpl = new MockSocketImpl(null, connectException);
                String description = String.format(
                        "%s.%s",
                        ConnectFailureFactory.class.getSimpleName(),
                        connectException.getClass().getSimpleName());
                return new CloseOnFailureMockTestBase.TestCase(
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
