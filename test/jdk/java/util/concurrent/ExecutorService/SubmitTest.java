/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test implementations of ExecutorService.submit/execute
 * @run junit SubmitTest
 */

import java.time.Duration;
import java.util.concurrent.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SubmitTest {

    private static Stream<ExecutorService> executors() {
        return Stream.of(
                Executors.newCachedThreadPool(),
                Executors.newVirtualThreadPerTaskExecutor(),
                new ForkJoinPool()
        );
    }

    /**
     * Test submit(Runnable) executes the task.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitRunnable(ExecutorService executor) throws Exception {
        try (executor) {
            var latch = new CountDownLatch(1);
            Future<?> future = executor.submit(latch::countDown);
            latch.await();
            assertNull(future.get());
        }
    }

    /**
     * Test submit(Runnable) throws if executor is shutdown.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitRunnableAfterShutdown(ExecutorService executor) {
        executor.shutdown();
        assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> { }));
    }

    /**
     * Test task submitted with submit(Runnable) is not interrupted by cancel(false).
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitRunnableWithCancelFalse(ExecutorService executor) throws Exception {
        try (executor) {
            var started = new CountDownLatch(1);
            var stop = new CountDownLatch(1);
            var done = new CountDownLatch(1);
            Future<?> future = executor.submit(() -> {
                started.countDown();
                try {
                    stop.await();
                } catch (InterruptedException e) {
                    // ignore
                } finally {
                    done.countDown();
                }
            });

            // wait for task to start
            started.await();

            // cancel(false), task should not be interrupted
            future.cancel(false);
            assertFalse(done.await(500, TimeUnit.MILLISECONDS));

            // let task finish
            stop.countDown();
        }
    }

    /**
     * Test task submitted with submit(Runnable) is interrupted by cancel(true).
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitRunnableWithCancelTrue(ExecutorService executor) throws Exception {
        try (executor) {
            var started = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            Future<?> future = executor.submit(() -> {
                started.countDown();
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            });

            // wait for task to start
            started.await();

            // cancel(true), task should be interrupted
            future.cancel(true);
            interrupted.await();
        }
    }

    /**
     * Test task submitted with submit(Runnable) is interrupted if executor is
     * stopped with shutdownNow.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitRunnableWithShutdownNow(ExecutorService executor) throws Exception {
        try (executor) {
            var started = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            Future<?> future = executor.submit(() -> {
                started.countDown();
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            });

            // wait for task to start
            started.await();

            // shutdown forcefully, task should be interrupted
            executor.shutdownNow();
            interrupted.await();
        }
    }

    /**
     * Test submit(Runnable) throws if task is null.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitRunnableNull(ExecutorService executor) {
        try (executor) {
            Runnable nullTask = null;
            assertThrows(NullPointerException.class, () -> executor.submit(nullTask));
            assertThrows(NullPointerException.class, () -> executor.submit(nullTask, Void.class));
        }
    }

    //

    /**
     * Test submit(Callable) executes the task.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitCallable(ExecutorService executor) throws Exception {
        try (executor) {
            var latch = new CountDownLatch(1);
            Future<String> future = executor.submit(() -> {
                latch.countDown();
                return "foo";
            });
            latch.await();
            assertEquals("foo", future.get());
        }
    }

    /**
     * Test submit(Callable) throws if executor is shutdown.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitCallableAfterShutdown(ExecutorService executor) {
        executor.shutdown();
        assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> null));
    }

    /**
     * Test task submitted with submit(Callable) is not interrupted by cancel(false).
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitCallableWithCancelFalse(ExecutorService executor) throws Exception {
        try (executor) {
            var started = new CountDownLatch(1);
            var stop = new CountDownLatch(1);
            var done = new CountDownLatch(1);
            Future<Void> future = executor.submit(() -> {
                started.countDown();
                try {
                    stop.await();
                } finally {
                    done.countDown();
                }
                return null;
            });

            // wait for task to start
            started.await();

            // cancel(false), task should not be interrupted
            future.cancel(false);
            assertFalse(done.await(500, TimeUnit.MILLISECONDS));

            // let task finish
            stop.countDown();
        }
    }

    /**
     * Test task submitted with submit(Callable) is interrupted by cancel(true).
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitCallableWithCancelTrue(ExecutorService executor) throws Exception {
        try (executor) {
            var started = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            Future<Void> future = executor.submit(() -> {
                started.countDown();
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
                return null;
            });

            // wait for task to start
            started.await();

            // cancel(true), task should be interrupted
            future.cancel(true);
            interrupted.await();
        }
    }

    /**
     * Test task submitted with submit(Callable) is interrupted if executor is
     * stopped with shutdownNow.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitCallableWithShutdownNow(ExecutorService executor) throws Exception {
        try (executor) {
            var started = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            Future<Void> future = executor.submit(() -> {
                started.countDown();
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
                return null;
            });

            // wait for task to start
            started.await();

            // shutdown forcefully, task should be interrupted
            executor.shutdownNow();
            interrupted.await();
        }
    }

    /**
     * Test submit(Callable) throws if task is null.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitCallableNull(ExecutorService executor) {
        try (executor) {
            Callable<Void> nullTask = null;
            assertThrows(NullPointerException.class, () -> executor.submit(nullTask));
        }
    }

    //

    /**
     * Test execute(Runnable) executes the task.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testExecute(ExecutorService executor) throws Exception {
        try (executor) {
            var latch = new CountDownLatch(1);
            executor.execute(latch::countDown);
            latch.await();
        }
    }

    /**
     * Test execute(Runnable) throws if executor is shutdown.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testExecuteAfterShutdown(ExecutorService executor) {
        executor.shutdown();
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
    }

    /**
     * Test task submitted with execute(Runnable) is interrupted if executor is
     * stopped with shutdownNow.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testExecuteWithShutdownNow(ExecutorService executor) throws Exception {
        try (executor) {
            var started = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            executor.execute(() -> {
                started.countDown();
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            });

            // wait for task to start
            started.await();

            // shutdown forcefully, task should be interrupted
            executor.shutdownNow();
            interrupted.await();
        }
    }

    /**
     * Test execute(Runnable) throws if task is null.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testExecuteNull(ExecutorService executor) {
        try (executor) {
            Runnable nullTask = null;
            assertThrows(NullPointerException.class, () -> executor.execute(nullTask));
        }
    }
}
