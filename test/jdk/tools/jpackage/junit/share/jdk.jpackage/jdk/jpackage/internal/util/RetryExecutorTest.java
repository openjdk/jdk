/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import jdk.jpackage.internal.util.RetryExecutor.Context;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.internal.util.function.ThrowingFunction;
import jdk.jpackage.internal.util.function.ThrowingSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class RetryExecutorTest {

    @Test
    public void test_defaults() {

        var executor = new AttemptCounter<Void, Exception>(context -> {
            throw new AttemptFailedException();
        });

        var defaultTimeout = Duration.ofSeconds(2);
        var defaultAttemptCount = 5;

        var timeout = Slot.<Duration>createEmpty();

        assertThrowsExactly(AttemptFailedException.class, new RetryExecutor<Void, Exception>(Exception.class)
                .setExecutable(executor)
                .setSleepFunction(t -> {
                    assertEquals(defaultTimeout, t);
                    timeout.set(t);
                    return;
                })::execute);

        assertEquals(defaultTimeout, timeout.get());
        assertEquals(defaultAttemptCount, executor.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, -4})
    public void test_N_attempts_fail(int maxAttemptsCount) throws AttemptFailedException {

        var retry = new RetryExecutor<String, AttemptFailedException>(AttemptFailedException.class)
                .setMaxAttemptsCount(maxAttemptsCount)
                .setAttemptTimeout(null)
                .setExecutable(context -> {
                    if (context.attempt() == (maxAttemptsCount - 1)) {
                        assertTrue(context.isLastAttempt());
                    } else {
                        assertFalse(context.isLastAttempt());
                    }
                    throw new AttemptFailedException("Attempt: " + context.attempt());
                });

        if (maxAttemptsCount <= 0) {
            assertNull(retry.execute());
        } else {
            var ex = assertThrowsExactly(AttemptFailedException.class, retry::execute);
            assertEquals("Attempt: " + (maxAttemptsCount - 1), ex.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    public void test_N_attempts_last_succeed(int maxAttemptsCount) throws AttemptFailedException {
        test_N_attempts_M_succeed(maxAttemptsCount, maxAttemptsCount - 1, false);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    public void test_N_attempts_first_succeed(int maxAttemptsCount) throws AttemptFailedException {
        test_N_attempts_M_succeed(maxAttemptsCount, 0, false);
    }

    @Test
    public void test_N_attempts_2nd_succeed() throws AttemptFailedException {
        test_N_attempts_M_succeed(4, 1, false);
    }

    @Test
    public void test_N_attempts_2nd_succeed_unchecked() throws AttemptFailedException {
        test_N_attempts_M_succeed(4, 1, true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_null_executor(boolean dynamic) {
        var retry = new RetryExecutor<Void, AttemptFailedException>(AttemptFailedException.class)
                .setAttemptTimeout(null).setMaxAttemptsCount(1000);

        if (dynamic) {
            int maxAttemptsCount = 3;
            var executor = new AttemptCounter<Void, AttemptFailedException>(context -> {
                assertTrue(context.attempt() <= (maxAttemptsCount - 1));
                if (context.attempt() == (maxAttemptsCount - 1)) {
                    context.executor().setExecutable((ThrowingSupplier<Void, AttemptFailedException>)null);
                }
                throw new AttemptFailedException("foo");
            });

            retry.setExecutable(executor);

            var ex = assertThrowsExactly(IllegalStateException.class, retry::execute);
            assertEquals("No executable", ex.getMessage());
            assertEquals(3, executor.count());
        } else {
            var ex = assertThrowsExactly(IllegalStateException.class, retry::execute);
            assertEquals("No executable", ex.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_unexpected_exception(boolean executeUnchecked) {
        var cause = new UnsupportedOperationException("foo");

        var executor = new AttemptCounter<Void, IOException>(context -> {
            assertEquals(0, context.attempt());
            throw cause;
        });

        var retry = new RetryExecutor<Void, IOException>(IOException.class).setExecutable(executor)
                .setMaxAttemptsCount(10).setAttemptTimeout(null);

        UnsupportedOperationException ex;
        if (executeUnchecked) {
            ex = assertThrowsExactly(UnsupportedOperationException.class, retry::executeUnchecked);
        } else {
            ex = assertThrowsExactly(UnsupportedOperationException.class, retry::execute);
        }
        assertSame(cause, ex);
        assertEquals(1, executor.count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_dynamic(boolean abort) {
        int maxAttemptsCount = 4;

        var secondExecutor = new AttemptCounter<String, AttemptFailedException>(context -> {
            throw new AttemptFailedException("bar");
        });

        var firstExecutor = new AttemptCounter<String, AttemptFailedException>(context -> {
            assertTrue(context.attempt() <= (maxAttemptsCount - 1));
            if (context.attempt() == (maxAttemptsCount - 1)) {
                if (abort) {
                    context.executor().setMaxAttemptsCount(maxAttemptsCount);
                } else {
                    // Let it go two more times.
                    context.executor().setMaxAttemptsCount(maxAttemptsCount + 2);
                }
                context.executor().setExecutable(secondExecutor);
            }
            throw new AttemptFailedException("foo");
        });

        var retry = new RetryExecutor<String, AttemptFailedException>(AttemptFailedException.class)
                .setExecutable(firstExecutor)
                .setMaxAttemptsCount(1000000)
                .setAttemptTimeout(null);

        var ex = assertThrowsExactly(AttemptFailedException.class, retry::execute);
        if (abort) {
            assertEquals("foo", ex.getMessage());
            assertEquals(0, secondExecutor.count());
        } else {
            assertEquals("bar", ex.getMessage());
            assertEquals(2, secondExecutor.count());
        }
        assertEquals(maxAttemptsCount, firstExecutor.count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_supplier_executor(boolean isNull) throws Exception {
        var retry = new RetryExecutor<String, Exception>(Exception.class).setMaxAttemptsCount(1);
        if (isNull) {
            retry.setExecutable((ThrowingSupplier<String, Exception>)null);
            var ex = assertThrowsExactly(IllegalStateException.class, retry::execute);
            assertEquals("No executable", ex.getMessage());
        } else {
            retry.setExecutable(() -> "Hello");
            assertEquals("Hello", retry.execute());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_executeUnchecked_fail(boolean withExceptionMapper) throws AttemptFailedException {
        var retry = new RetryExecutor<String, AttemptFailedException>(AttemptFailedException.class).setExecutable(() -> {
            throw new AttemptFailedException("kaput!");
        }).setMaxAttemptsCount(1);

        Class<? extends Exception> expectedExceptionType;
        if (withExceptionMapper) {
            retry.setExceptionMapper((AttemptFailedException ex) -> {
                assertEquals("kaput!", ex.getMessage());
                return new UncheckedAttemptFailedException(ex);
            });
            expectedExceptionType = UncheckedAttemptFailedException.class;
        } else {
            expectedExceptionType = ExceptionBox.class;
        }

        var ex = assertThrowsExactly(expectedExceptionType, retry::executeUnchecked);
        assertEquals(AttemptFailedException.class, ex.getCause().getClass());
        assertEquals("kaput!", ex.getCause().getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_setSleepFunction(boolean withTimeout) {

        var timeout = Slot.<Duration>createEmpty();

        assertDoesNotThrow(new RetryExecutor<Void, AttemptFailedException>(AttemptFailedException.class)
                .setMaxAttemptsCount(2)
                .mutate(retry -> {
                    if (withTimeout) {
                        retry.setAttemptTimeout(Duration.ofDays(100));
                    } else {
                        retry.setAttemptTimeout(null);
                    }
                })
                .setExecutable(context -> {
                    if (context.isLastAttempt()) {
                        return null;
                    } else {
                        throw new AttemptFailedException();
                    }
                })
                .setSleepFunction(timeout::set)::execute);

        assertEquals(withTimeout, timeout.find().isPresent());
        if (withTimeout) {
            assertEquals(Duration.ofDays(100), timeout.get());
        }
    }

    private static void test_N_attempts_M_succeed(int maxAttempts, int failedAttempts, boolean unchecked) throws AttemptFailedException {

        var countingExecutor = new AttemptCounter<String, AttemptFailedException>(context -> {
            if (context.attempt() == failedAttempts) {
                return "You made it!";
            } else {
                throw new AttemptFailedException();
            }
        });

        var retry = new RetryExecutor<String, AttemptFailedException>(AttemptFailedException.class)
                .setMaxAttemptsCount(maxAttempts)
                .setAttemptTimeout(null)
                .setExecutable(countingExecutor);

        assertEquals("You made it!", unchecked ? retry.execute() : retry.executeUnchecked());
        assertEquals(failedAttempts, countingExecutor.count() - 1);
    }

    private static final class AttemptCounter<T, E extends Exception> implements ThrowingFunction<Context<RetryExecutor<T, E>>, T, E> {

        AttemptCounter(ThrowingFunction<Context<RetryExecutor<T, E>>, T, E> impl) {
            this.impl = Objects.requireNonNull(impl);
        }

        @Override
        public T apply(Context<RetryExecutor<T, E>> context) throws E {
            counter++;
            return impl.apply(context);
        }

        int count() {
            return counter;
        }

        private int counter;
        private final ThrowingFunction<Context<RetryExecutor<T, E>>, T, E> impl;
    }

    private static final class AttemptFailedException extends Exception {

        AttemptFailedException(String msg) {
            super(msg);
        }

        AttemptFailedException() {
        }

        private static final long serialVersionUID = 1L;
    }

    private static final class UncheckedAttemptFailedException extends RuntimeException {

        UncheckedAttemptFailedException(AttemptFailedException ex) {
            super(ex);
        }

        private static final long serialVersionUID = 1L;
    }
}
