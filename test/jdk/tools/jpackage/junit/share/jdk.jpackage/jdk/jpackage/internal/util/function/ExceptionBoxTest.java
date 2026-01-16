/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import jdk.jpackage.internal.util.Slot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ExceptionBoxTest {

    @Test
    public void test_unbox_RuntimeException() {
        var ex = new RuntimeException();
        assertSame(ex, ExceptionBox.unbox(ex));
    }

    @Test
    public void test_unbox_Exception() {
        var ex = new Exception();
        assertSame(ex, ExceptionBox.unbox(ex));
    }

    @Test
    public void test_unbox_InvocationTargetException() {
        var ex = new Exception();
        assertSame(ex, ExceptionBox.unbox(new InvocationTargetException(ex)));
    }

    @Test
    public void test_unbox_ExceptionBox() {
        var ex = new Exception("foo");
        // There is no way to directly instantiate ExceptionBox, use a workaround.
        var box = assertThrowsExactly(ExceptionBox.class, () -> {
            throw ExceptionBox.toUnchecked(ex);
        });
        assertSame(ex, ExceptionBox.unbox(box));
    }

    @Test
    public void test_unbox_Error() {
        var err = new Error("On Fire!");
        var thrown = assertThrowsExactly(Error.class, () -> {
            ExceptionBox.unbox(err);
        });
        assertSame(err, thrown);
    }

    @Test
    public void test_reachedUnreachable() {
        var err = ExceptionBox.reachedUnreachable();
        assertEquals("Reached unreachable!", err.getMessage());
    }

    @Test
    public void test_toUnchecked_RuntimeException() {
        assertToUnchecked(new RuntimeException(), true);
    }

    @Test
    public void test_toUnchecked_Exception() {
        assertToUnchecked(new Exception(), false);
    }

    @Test
    public void test_toUnchecked_ExceptionBox() {
        // There is no way to directly instantiate ExceptionBox, use a workaround.
        var box = assertThrowsExactly(ExceptionBox.class, () -> {
            throw ExceptionBox.toUnchecked(new Exception("foo"));
        });
        assertToUnchecked(box, true);
    }

    @Test
    public void test_toUnchecked_InterruptedException() throws InterruptedException {

        var workerThreadReadyToWait = Slot.<Boolean>createEmpty();

        var workerThreadInterruptedExceptionCaught = Slot.<Boolean>createEmpty();

        var workerThreadException = new AtomicReference<Throwable>();

        var thread = Thread.ofVirtual().uncaughtExceptionHandler((Thread _, Throwable e) -> {
            trace("unexpected exception: %s", e);
            workerThreadException.set(e);
        }).start(() -> {
            try {
                var lock = new Object();
                synchronized (lock) {
                    synchronized (workerThreadReadyToWait) {
                        workerThreadReadyToWait.set(true);
                        workerThreadReadyToWait.notify();
                    }
                    trace("wait");
                    lock.wait();
                }
            } catch (InterruptedException iex) {
                trace("interrupted state cleared");
                synchronized (workerThreadInterruptedExceptionCaught) {
                    workerThreadInterruptedExceptionCaught.set(true);
                    trace("notify about to interrupt itself");
                    workerThreadInterruptedExceptionCaught.notify();
                }
                trace("before toUnchecked()");
                var box = assertThrowsExactly(ExceptionBox.class, () -> {
                    throw ExceptionBox.toUnchecked(iex);
                });
                assertSame(iex, box.getCause());
            }
        });

        // Wait until the worker thread gets to the point
        // when interrupting it will cause InterruptedException.
        synchronized (workerThreadReadyToWait) {
            while (workerThreadReadyToWait.find().isEmpty()) {
                workerThreadReadyToWait.wait();
            }
        }

        trace("interrupt %s", thread);
        thread.interrupt();

        // Wait until the worker thread catches an InterruptedException.
        synchronized (workerThreadInterruptedExceptionCaught) {
            while (workerThreadInterruptedExceptionCaught.find().isEmpty()) {
                trace("wait for %s to catch InterruptedException", thread);
                workerThreadInterruptedExceptionCaught.wait();
            }
        }

        // Block waiting when ExceptionBox.toUnchecked()
        // called in the worker thread will interrupt the worker thread.
        while (!thread.isInterrupted()) {
            trace("wait %s is interrupted", thread);
            Thread.sleep(100);
        }

        trace("join interrupted %s", thread);
        thread.join();

        assertNull(workerThreadException.get());
    }

    @ParameterizedTest
    @EnumSource(InvocationTargetExceptionType.class)
    public void test_rethrowUnchecked_InvocationTargetException(InvocationTargetExceptionType type)
            throws NoSuchMethodException, SecurityException, IllegalAccessException {

        var m = ExceptionBoxTest.class.getMethod(type.methodName);

        try {
            m.invoke(null);
        } catch (InvocationTargetException ex) {
            var cause = assertThrows(type.expectedThrownType, () -> {
                throw ExceptionBox.toUnchecked(ex);
            });
            assertSame(ex.getCause(), type.expectedThrowableGetter.apply(cause));
        }
    }

    public enum InvocationTargetExceptionType {
        CHECKED("throwIOException", t -> {
            return t.getCause();
        }, ExceptionBox.class),
        UNCHECKED("throwNPE", x -> x, RuntimeException.class),
        ERROR("throwError", x -> x, Error.class),
        ;

        InvocationTargetExceptionType(
                String methodName,
                UnaryOperator<Throwable> expectedThrowableGetter,
                Class<? extends Throwable> expectedThrownType) {
            this.methodName = Objects.requireNonNull(methodName);
            this.expectedThrownType = Objects.requireNonNull(expectedThrownType);
            this.expectedThrowableGetter = Objects.requireNonNull(expectedThrowableGetter);
        }

        final String methodName;
        final Class<? extends Throwable> expectedThrownType;
        final UnaryOperator<Throwable> expectedThrowableGetter;
    }

    public static void throwIOException() throws IOException {
        throw new IOException("foo");
    }

    public static void throwNPE() {
        throw new NullPointerException("foo");
    }

    public static void throwError() {
        throw new Error("Kaput!");
    }

    private static void assertToUnchecked(Exception cause, boolean asis) {
        Class<? extends Throwable> expectedType;
        if (asis) {
            expectedType = cause.getClass();
        } else {
            expectedType = ExceptionBox.class;
        }
        var unchecked = ExceptionBox.toUnchecked(cause);
        if (asis) {
            assertSame(cause, unchecked);
        } else {
            assertSame(cause, unchecked.getCause());
        }
    }

    private void trace(String format, Object... args) {
        Objects.requireNonNull(format);
        System.out.println(String.format("[%s]: %s", Thread.currentThread(), String.format(format, args)));
    }
}
