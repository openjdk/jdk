/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test Future's default methods
 * @library ../lib
 * @run junit DefaultMethods
 */

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import static java.util.concurrent.Future.State.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class DefaultMethods {

    static Stream<ExecutorService> executors() {
        return Stream.of(
                // ensures that default close method is tested
                new DelegatingExecutorService(Executors.newCachedThreadPool()),

                // executors that may return a Future that overrides the methods
                Executors.newCachedThreadPool(),
                Executors.newVirtualThreadPerTaskExecutor(),
                new ForkJoinPool()
        );
    }

    /**
     * Test methods when the task has not completed.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testRunningTask(ExecutorService executor) {
        try (executor) {
            var latch = new CountDownLatch(1);
            Future<?> future = executor.submit(() -> { latch.await(); return null; });
            try {
                assertTrue(future.state() == RUNNING);
                assertThrows(IllegalStateException.class, future::resultNow);
                assertThrows(IllegalStateException.class, future::exceptionNow);
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * Test methods when the task has already completed with a result.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testCompletedTask1(ExecutorService executor) {
        try (executor) {
            Future<String> future = executor.submit(() -> "foo");
            awaitDone(future);
            assertTrue(future.state() == SUCCESS);
            assertEquals("foo", future.resultNow());
            assertThrows(IllegalStateException.class, future::exceptionNow);
        }
    }

    /**
     * Test methods when the task has already completed with null.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testCompletedTask2(ExecutorService executor) {
        try (executor) {
            Future<String> future = executor.submit(() -> null);
            awaitDone(future);
            assertTrue(future.state() == SUCCESS);
            assertNull(future.resultNow());
            assertThrows(IllegalStateException.class, future::exceptionNow);
        }
    }

    /**
     * Test methods when the task has completed with an exception.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testFailedTask(ExecutorService executor) {
        try (executor) {
            Future<?> future = executor.submit(() -> { throw new ArithmeticException(); });
            awaitDone(future);
            assertTrue(future.state() == FAILED);
            assertThrows(IllegalStateException.class, future::resultNow);
            Throwable ex = future.exceptionNow();
            assertTrue(ex instanceof ArithmeticException);
        }
    }

    /**
     * Test methods when the task has been cancelled (mayInterruptIfRunning=false)
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testCancelledTask1(ExecutorService executor) {
        try (executor) {
            var latch = new CountDownLatch(1);
            Future<?> future = executor.submit(() -> { latch.await(); return null; });
            future.cancel(false);
            try {
                assertTrue(future.state() == CANCELLED);
                assertThrows(IllegalStateException.class, future::resultNow);
                assertThrows(IllegalStateException.class, future::exceptionNow);
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * Test methods when the task has been cancelled (mayInterruptIfRunning=true)
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testCancelledTask2(ExecutorService executor) {
        try (executor) {
            var latch = new CountDownLatch(1);
            Future<?> future = executor.submit(() -> { latch.await(); return null; });
            future.cancel(true);
            try {
                assertTrue(future.state() == CANCELLED);
                assertThrows(IllegalStateException.class, future::resultNow);
                assertThrows(IllegalStateException.class, future::exceptionNow);
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * Test CompletableFuture with the task has not completed.
     */
    @Test
    void testCompletableFuture1() {
        var future = new CompletableFuture<String>();
        assertTrue(future.state() == RUNNING);
        assertThrows(IllegalStateException.class, future::resultNow);
        assertThrows(IllegalStateException.class, future::exceptionNow);
    }

    /**
     * Test CompletableFuture with the task that completed with result.
     */
    @Test
    void testCompletableFuture2() {
        var future = new CompletableFuture<String>();
        future.complete("foo");
        assertTrue(future.state() == SUCCESS);
        assertEquals("foo", future.resultNow());
        assertThrows(IllegalStateException.class, future::exceptionNow);
    }

    /**
     * Test CompletableFuture with the task that completed with null.
     */
    @Test
    void testCompletableFuture3() {
        var future = new CompletableFuture<String>();
        future.complete(null);
        assertTrue(future.state() == SUCCESS);
        assertNull(future.resultNow());
        assertThrows(IllegalStateException.class, future::exceptionNow);
    }

    /**
     * Test CompletableFuture with the task that completed with exception.
     */
    @Test
    void testCompletableFuture4() {
        var future = new CompletableFuture<String>();
        future.completeExceptionally(new ArithmeticException());
        assertTrue(future.state() == FAILED);
        assertThrows(IllegalStateException.class, future::resultNow);
        Throwable ex = future.exceptionNow();
        assertTrue(ex instanceof ArithmeticException);
    }

    /**
     * Test CompletableFuture with the task that was cancelled.
     */
    @Test
    void testCompletableFuture5() {
        var future = new CompletableFuture<String>();
        future.cancel(false);
        assertTrue(future.state() == CANCELLED);
        assertThrows(IllegalStateException.class, future::resultNow);
        assertThrows(IllegalStateException.class, future::exceptionNow);
    }

    /**
     * Waits for the future to be done.
     */
    private static void awaitDone(Future<?> future) {
        boolean interrupted = false;
        while (!future.isDone()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
