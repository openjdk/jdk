/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=platform
 * @summary Basic tests for new thread-per-task executors
 * @enablePreview
 * @run junit/othervm -DthreadFactory=platform ThreadPerTaskExecutorTest
 */

/*
 * @test id=virtual
 * @enablePreview
 * @run junit/othervm -DthreadFactory=virtual ThreadPerTaskExecutorTest
 */

import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.util.concurrent.Future.State.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class ThreadPerTaskExecutorTest {
    // long running interruptible task
    private static final Callable<Void> SLEEP_FOR_A_DAY = () -> {
        Thread.sleep(Duration.ofDays(1));
        return null;
    };

    private static ScheduledExecutorService scheduler;
    private static List<ThreadFactory> threadFactories;

    @BeforeAll
    static void setup() throws Exception {
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // thread factories
        String value = System.getProperty("threadFactory");
        List<ThreadFactory> list = new ArrayList<>();
        if (value == null || value.equals("platform"))
            list.add(Thread.ofPlatform().factory());
        if (value == null || value.equals("virtual"))
            list.add(Thread.ofVirtual().factory());
        assertTrue(list.size() > 0, "No thread factories for tests");
        threadFactories = list;
    }

    @AfterAll
    static void shutdown() {
        scheduler.shutdown();
    }

    private static Stream<ThreadFactory> factories() {
        return threadFactories.stream();
    }

    private static Stream<ExecutorService> executors() {
        return threadFactories.stream()
                .map(f -> Executors.newThreadPerTaskExecutor(f));
    }

    /**
     * Schedules a thread to be interrupted after the given delay.
     */
    private void scheduleInterrupt(Thread thread, Duration delay) {
        long millis = delay.toMillis();
        scheduler.schedule(thread::interrupt, millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Test that a thread is created for each task.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testThreadPerTask(ThreadFactory factory) throws Exception {
        final int NUM_TASKS = 100;
        AtomicInteger threadCount = new AtomicInteger();

        ThreadFactory wrapper = task -> {
            threadCount.addAndGet(1);
            return factory.newThread(task);
        };

        var futures = new ArrayList<Future<Integer>>();
        ExecutorService executor = Executors.newThreadPerTaskExecutor(wrapper);
        try (executor) {
            for (int i=0; i<NUM_TASKS; i++) {
                int result = i;
                Future<Integer> future = executor.submit(() -> result);
                futures.add(future);
            }
        }

        assertTrue(executor.isTerminated());
        assertEquals(NUM_TASKS, threadCount.get());
        for (int i=0; i<NUM_TASKS; i++) {
            Future<Integer> future = futures.get(i);
            assertEquals((int) future.get(), i);
        }
    }

    /**
     * Test that newThreadPerTaskExecutor uses the specified thread factory.
     */
    @Test
    void testThreadFactory() throws Exception {
        var ref1 = new AtomicReference<Thread>();
        var ref2 = new AtomicReference<Thread>();
        ThreadFactory factory = task -> {
            assertTrue(ref1.get() == null);
            Thread thread = new Thread(task);
            ref1.set(thread);
            return thread;
        };
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            executor.submit(() -> ref2.set(Thread.currentThread()));
        }
        Thread thread1 = ref1.get();   // Thread created by thread factory
        Thread thread2 = ref2.get();   // Thread that executed task
        assertTrue(thread1 == thread2);
    }

    /**
     * Test shutdown.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testShutdown(ExecutorService executor) throws Exception {
        try (executor) {
            assertFalse(executor.isShutdown());
            assertFalse(executor.isTerminated());
            assertFalse(executor.awaitTermination(10, TimeUnit.MILLISECONDS));

            Future<?> result = executor.submit(SLEEP_FOR_A_DAY);
            try {
                executor.shutdown();
                assertTrue(executor.isShutdown());
                assertFalse(executor.isTerminated());
                assertFalse(executor.awaitTermination(500, TimeUnit.MILLISECONDS));
            } finally {
                result.cancel(true);
            }
        }
    }

    /**
     * Test shutdownNow.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testShutdownNow(ExecutorService executor) throws Exception {
        try (executor) {
            assertFalse(executor.isShutdown());
            assertFalse(executor.isTerminated());
            assertFalse(executor.awaitTermination(10, TimeUnit.MILLISECONDS));

            Future<?> result = executor.submit(SLEEP_FOR_A_DAY);
            try {
                List<Runnable> tasks = executor.shutdownNow();
                assertTrue(executor.isShutdown());
                assertTrue(tasks.isEmpty());

                Throwable e = assertThrows(ExecutionException.class, result::get);
                assertTrue(e.getCause() instanceof InterruptedException);

                assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
                assertTrue(executor.isTerminated());
            } finally {
                result.cancel(true);
            }
        }
    }

    /**
     * Test close with no threads running.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testClose1(ExecutorService executor) throws Exception {
        executor.close();
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10,  TimeUnit.MILLISECONDS));
    }

    /**
     * Test close with threads running.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testClose2(ExecutorService executor) throws Exception {
        Future<String> future;
        try (executor) {
            future = executor.submit(() -> {
                Thread.sleep(Duration.ofMillis(50));
                return "foo";
            });
        }
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10,  TimeUnit.MILLISECONDS));
        assertEquals(future.get(), "foo");   // task should complete
    }

    /**
     * Invoke close with interrupt status set, should cancel task.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testClose3(ExecutorService executor) throws Exception {
        Future<?> future;
        try (executor) {
            future = executor.submit(SLEEP_FOR_A_DAY);
            Thread.currentThread().interrupt();
        } finally {
            assertTrue(Thread.interrupted());  // clear interrupt
        }

        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10,  TimeUnit.MILLISECONDS));
        assertThrows(ExecutionException.class, future::get);
    }

    /**
     * Interrupt thread blocked in close.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testClose4(ExecutorService executor) throws Exception {
        Future<?> future;
        try (executor) {
            future = executor.submit(SLEEP_FOR_A_DAY);
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
        } finally {
            assertTrue(Thread.interrupted());
        }
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10,  TimeUnit.MILLISECONDS));
        assertThrows(ExecutionException.class, future::get);
    }

    /**
     * Close executor that is already closed.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testClose5(ExecutorService executor) throws Exception {
        executor.close();
        executor.close(); // already closed
    }

    /**
     * Test awaitTermination when not shutdown.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testAwaitTermination1(ExecutorService executor) throws Exception {
        assertFalse(executor.awaitTermination(100, TimeUnit.MILLISECONDS));
        executor.close();
        assertTrue(executor.awaitTermination(100, TimeUnit.MILLISECONDS));
    }

    /**
     * Test awaitTermination with task running.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testAwaitTermination2(ExecutorService executor) throws Exception {
        Phaser barrier = new Phaser(2);
        Future<?> result = executor.submit(barrier::arriveAndAwaitAdvance);
        try {
            executor.shutdown();
            assertFalse(executor.awaitTermination(100, TimeUnit.MILLISECONDS));
            barrier.arriveAndAwaitAdvance();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            result.cancel(true);
        }
    }

    /**
     * Test submit when the Executor is shutdown but not terminated.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitAfterShutdown(ExecutorService executor) throws Exception {
        Phaser barrier = new Phaser(2);
        try (executor) {
            // submit task to prevent executor from terminating
            executor.submit(barrier::arriveAndAwaitAdvance);
            try {
                executor.shutdown();
                assertTrue(executor.isShutdown() && !executor.isTerminated());
                assertThrows(RejectedExecutionException.class,
                             () -> executor.submit(() -> {  }));
            } finally {
                barrier.arriveAndAwaitAdvance();
            }
        }
    }

    /**
     * Test submit when the Executor is terminated.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testSubmitAfterTermination(ExecutorService executor) throws Exception {
        executor.shutdown();
        assertTrue(executor.isShutdown() && executor.isTerminated());
        assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> {}));
    }

    /**
     * Test submit with null.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testSubmitNulls1(ThreadFactory factory) {
        var executor = Executors.newThreadPerTaskExecutor(factory);
        assertThrows(NullPointerException.class, () -> executor.submit((Runnable) null));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testSubmitNulls2(ThreadFactory factory) {
        var executor = Executors.newThreadPerTaskExecutor(factory);
        assertThrows(NullPointerException.class, () -> executor.submit((Callable<String>) null));
    }

    /**
     * Test invokeAny where all tasks complete normally.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAny1(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> "bar";
            String result = executor.invokeAny(Set.of(task1, task2));
            assertTrue("foo".equals(result) || "bar".equals(result));
        }
    }

    /**
     * Test invokeAny where all tasks complete normally. The completion of the
     * first task should cancel remaining tasks.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAny2(ExecutorService executor) throws Exception {
        try (executor) {
            AtomicBoolean task2Started = new AtomicBoolean();
            AtomicReference<Throwable> task2Exception = new AtomicReference<>();
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                task2Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (Exception e) {
                    task2Exception.set(e);
                }
                return "bar";
            };
            String result = executor.invokeAny(Set.of(task1, task2));
            assertTrue("foo".equals(result));

            // if task2 started then the sleep should have been interrupted
            if (task2Started.get()) {
                Throwable exc;
                while ((exc = task2Exception.get()) == null) {
                    Thread.sleep(20);
                }
                assertTrue(exc instanceof InterruptedException);
            }
        }
    }

    /**
     * Test invokeAny where all tasks complete with exception.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAny3(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            Callable<String> task1 = () -> { throw new FooException(); };
            Callable<String> task2 = () -> { throw new FooException(); };
            try {
                executor.invokeAny(Set.of(task1, task2));
                fail("invokeAny did not throw");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                assertTrue(cause instanceof FooException);
            }
        }
    }

    /**
     * Test invokeAny where all tasks complete with exception. The completion
     * of the last task is delayed.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAny4(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            Callable<String> task1 = () -> { throw new FooException(); };
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMillis(50));
                throw new FooException();
            };
            try {
                executor.invokeAny(Set.of(task1, task2));
                fail("invokeAny did not throw");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                assertTrue(cause instanceof FooException);
            }
        }
    }

    /**
     * Test invokeAny where some, not all, tasks complete normally.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAny5(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> { throw new FooException(); };
            String result = executor.invokeAny(Set.of(task1, task2));
            assertTrue("foo".equals(result));
        }
    }

    /**
     * Test invokeAny where some, not all, tasks complete normally. The
     * completion of the first task to complete normally is delayed.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAny6(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            Callable<String> task1 = () -> {
                Thread.sleep(Duration.ofMillis(50));
                return "foo";
            };
            Callable<String> task2 = () -> { throw new FooException(); };
            String result = executor.invokeAny(Set.of(task1, task2));
            assertTrue("foo".equals(result));
        }
    }

    /**
     * Test timed-invokeAny where all tasks complete normally before the timeout.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyWithTimeout1(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> "bar";
            String result = executor.invokeAny(Set.of(task1, task2), 1, TimeUnit.MINUTES);
            assertTrue("foo".equals(result) || "bar".equals(result));
        }
    }

    /**
     * Test timed-invokeAny where one task completes normally before the timeout.
     * The remaining tests should be cancelled.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyWithTimeout2(ExecutorService executor) throws Exception {
        try (executor) {
            AtomicBoolean task2Started = new AtomicBoolean();
            AtomicReference<Throwable> task2Exception = new AtomicReference<>();
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                task2Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (Exception e) {
                    task2Exception.set(e);
                }
                return "bar";
            };
            String result = executor.invokeAny(Set.of(task1, task2), 1, TimeUnit.MINUTES);
            assertTrue("foo".equals(result));

            // if task2 started then the sleep should have been interrupted
            if (task2Started.get()) {
                Throwable exc;
                while ((exc = task2Exception.get()) == null) {
                    Thread.sleep(20);
                }
                assertTrue(exc instanceof InterruptedException);
            }
        }
    }

    /**
     * Test timed-invokeAny where timeout expires before any task completes.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyWithTimeout3(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> {
                Thread.sleep(Duration.ofMinutes(1));
                return "foo";
            };
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMinutes(2));
                return "bar";
            };
            assertThrows(TimeoutException.class,
                         () -> executor.invokeAny(Set.of(task1, task2), 1, TimeUnit.SECONDS));
        }
    }

    /**
     * Test invokeAny where timeout expires after some tasks have completed
     * with exception.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyWithTimeout4(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            Callable<String> task1 = () -> { throw new FooException(); };
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMinutes(2));
                return "bar";
            };
            assertThrows(TimeoutException.class,
                         () -> executor.invokeAny(Set.of(task1, task2), 1, TimeUnit.SECONDS));
        }
    }

    /**
     * Test invokeAny with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyWithInterruptSet(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> "bar";
            Thread.currentThread().interrupt();
            try {
                executor.invokeAny(Set.of(task1, task2));
                fail("invokeAny did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // clear interrupt
            }
        }
    }

    /**
     * Test interrupting a thread blocked in invokeAny.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInterruptInvokeAny(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> {
                Thread.sleep(Duration.ofMinutes(1));
                return "foo";
            };
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMinutes(2));
                return "bar";
            };
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            try {
                executor.invokeAny(Set.of(task1, task2));
                fail("invokeAny did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // clear interrupt
            }
        }
    }

    /**
     * Test invokeAny after ExecutorService has been shutdown.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyAfterShutdown(ExecutorService executor) throws Exception {
        executor.shutdown();
        Callable<String> task1 = () -> "foo";
        Callable<String> task2 = () -> "bar";
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAny(Set.of(task1, task2)));
    }

    /**
     * Test invokeAny with empty collection.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInvokeAnyEmpty1(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(IllegalArgumentException.class, () -> executor.invokeAny(Set.of()));
        }
    }

    /**
     * Test timed-invokeAny with empty collection.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInvokeAnyEmpty2(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(IllegalArgumentException.class,
                         () -> executor.invokeAny(Set.of(), 1, TimeUnit.MINUTES));
        }
    }

    /**
     * Test invokeAny with null.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInvokeAnyNull1(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(NullPointerException.class, () -> executor.invokeAny(null));
        }
    }

    /**
     * Test invokeAny with null element
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInvokeAnyNull2(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            List<Callable<String>> list = new ArrayList<>();
            list.add(() -> "foo");
            list.add(null);
            assertThrows(NullPointerException.class, () -> executor.invokeAny(null));
        }
    }

    /**
     * Test invokeAll where all tasks complete normally.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAll1(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMillis(50));
                return "bar";
            };

            List<Future<String>> list = executor.invokeAll(List.of(task1, task2));

            // list should have two elements, both should be done
            assertTrue(list.size() == 2);
            boolean notDone = list.stream().anyMatch(r -> !r.isDone());
            assertFalse(notDone);

            // check results
            List<String> results = list.stream().map(Future::resultNow).collect(Collectors.toList());
            assertEquals(results, List.of("foo", "bar"));
        }
    }

    /**
     * Test invokeAll where all tasks complete with exception.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAll2(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            class BarException extends Exception { }
            Callable<String> task1 = () -> { throw new FooException(); };
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMillis(50));
                throw new BarException();
            };

            List<Future<String>> list = executor.invokeAll(List.of(task1, task2));

            // list should have two elements, both should be done
            assertTrue(list.size() == 2);
            boolean notDone = list.stream().anyMatch(r -> !r.isDone());
            assertFalse(notDone);

            // check results
            Throwable e1 = assertThrows(ExecutionException.class, () -> list.get(0).get());
            assertTrue(e1.getCause() instanceof FooException);
            Throwable e2 = assertThrows(ExecutionException.class, () -> list.get(1).get());
            assertTrue(e2.getCause() instanceof BarException);
        }
    }

    /**
     * Test invokeAll where all tasks complete normally before the timeout expires.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAll3(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMillis(50));
                return "bar";
            };

            List<Future<String>> list = executor.invokeAll(List.of(task1, task2), 1, TimeUnit.DAYS);

            // list should have two elements, both should be done
            assertTrue(list.size() == 2);
            boolean notDone = list.stream().anyMatch(r -> !r.isDone());
            assertFalse(notDone);

            // check results
            List<String> results = list.stream().map(Future::resultNow).collect(Collectors.toList());
            assertEquals(results, List.of("foo", "bar"));
        }
    }

    /**
     * Test invokeAll where some tasks do not complete before the timeout expires.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAll4(ExecutorService executor) throws Exception {
        try (executor) {
            AtomicReference<Exception> exc = new AtomicReference<>();
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                try {
                    Thread.sleep(Duration.ofDays(1));
                    return "bar";
                } catch (Exception e) {
                    exc.set(e);
                    throw e;
                }
            };

            var list = executor.invokeAll(List.of(task1, task2), 2, TimeUnit.SECONDS);

            // list should have two elements, both should be done
            assertTrue(list.size() == 2);
            boolean notDone = list.stream().anyMatch(r -> !r.isDone());
            assertFalse(notDone);

            // check results
            assertEquals(list.get(0).get(), "foo");
            assertTrue(list.get(1).isCancelled());

            // task2 should be interrupted
            Exception e;
            while ((e = exc.get()) == null) {
                Thread.sleep(50);
            }
            assertTrue(e instanceof InterruptedException);
        }
    }

    /**
     * Test invokeAll with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllInterrupt1(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMinutes(1));
                return "bar";
            };

            Thread.currentThread().interrupt();
            try {
                executor.invokeAll(List.of(task1, task2));
                fail("invokeAll did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // clear interrupt
            }
        }
    }

    /**
     * Test timed-invokeAll with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllInterrupt3(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMinutes(1));
                return "bar";
            };

            Thread.currentThread().interrupt();
            try {
                executor.invokeAll(List.of(task1, task2), 1, TimeUnit.SECONDS);
                fail("invokeAll did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // clear interrupt
            }
        }
    }

    /**
     * Test interrupt with thread blocked in invokeAll.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllInterrupt4(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            DelayedResult<String> task2 = new DelayedResult("bar", Duration.ofMinutes(1));
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            try {
                executor.invokeAll(Set.of(task1, task2));
                fail("invokeAll did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());

                // task2 should have been interrupted
                while (!task2.isDone()) {
                    Thread.sleep(Duration.ofMillis(100));
                }
                assertTrue(task2.exception() instanceof InterruptedException);
            } finally {
                Thread.interrupted(); // clear interrupt
            }
        }
    }

    /**
     * Test interrupt with thread blocked in timed-invokeAll.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllInterrupt6(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            DelayedResult<String> task2 = new DelayedResult("bar", Duration.ofMinutes(1));
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            try {
                executor.invokeAll(Set.of(task1, task2), 1, TimeUnit.DAYS);
                fail("invokeAll did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());

                // task2 should have been interrupted
                while (!task2.isDone()) {
                    Thread.sleep(Duration.ofMillis(100));
                }
                assertTrue(task2.exception() instanceof InterruptedException);
            } finally {
                Thread.interrupted(); // clear interrupt
            }
        }
    }

    /**
     * Test invokeAll after ExecutorService has been shutdown.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllAfterShutdown1(ExecutorService executor) throws Exception {
        executor.shutdown();

        Callable<String> task1 = () -> "foo";
        Callable<String> task2 = () -> "bar";
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAll(Set.of(task1, task2)));
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllAfterShutdown2(ExecutorService executor) throws Exception {
        executor.shutdown();

        Callable<String> task1 = () -> "foo";
        Callable<String> task2 = () -> "bar";
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAll(Set.of(task1, task2), 1, TimeUnit.SECONDS));
    }

    /**
     * Test invokeAll with empty collection.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllEmpty1(ExecutorService executor) throws Exception {
        try (executor) {
            List<Future<Object>> list = executor.invokeAll(Set.of());
            assertTrue(list.size() == 0);
        }
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllEmpty2(ExecutorService executor) throws Exception {
        try (executor) {
            List<Future<Object>> list = executor.invokeAll(Set.of(), 1, TimeUnit.SECONDS);
            assertTrue(list.size() == 0);
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testInvokeAllNull1(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(NullPointerException.class, () -> executor.invokeAll(null));
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testInvokeAllNull2(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            List<Callable<String>> tasks = new ArrayList<>();
            tasks.add(() -> "foo");
            tasks.add(null);
            assertThrows(NullPointerException.class, () -> executor.invokeAll(tasks));
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testInvokeAllNull3(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(NullPointerException.class,
                         () -> executor.invokeAll(null, 1, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testInvokeAllNull4(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            Callable<String> task = () -> "foo";
            assertThrows(NullPointerException.class,
                         () -> executor.invokeAll(List.of(task), 1, null));
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testInvokeAllNull5(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            List<Callable<String>> tasks = new ArrayList<>();
            tasks.add(() -> "foo");
            tasks.add(null);
            assertThrows(NullPointerException.class,
                         () -> executor.invokeAll(tasks, 1, TimeUnit.SECONDS));
        }
    }

    /**
     * Test ThreadFactory that does not produce any threads
     */
    @Test
    void testNoThreads1() throws Exception {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(task -> null);
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
    }

    @Test
    void testNoThreads2() throws Exception {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(task -> null);
        assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> "foo"));
    }

    @Test
    void testNoThreads3() throws Exception {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(task -> null);
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAll(List.of(() -> "foo")));
    }

    @Test
    void testNoThreads4() throws Exception {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(task -> null);
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAny(List.of(() -> "foo")));
    }

    @Test
    void testNull() {
        assertThrows(NullPointerException.class,
                     () -> Executors.newThreadPerTaskExecutor(null));
    }

    // -- supporting classes --

    static class DelayedResult<T> implements Callable<T> {
        final T result;
        final Duration delay;
        volatile boolean done;
        volatile Exception exception;
        DelayedResult(T result, Duration delay) {
            this.result = result;
            this.delay = delay;
        }
        public T call() throws Exception {
            try {
                Thread.sleep(delay);
                return result;
            } catch (Exception e) {
                this.exception = e;
                throw e;
            } finally {
                done = true;
            }
        }
        boolean isDone() {
            return done;
        }
        Exception exception() {
            return exception;
        }
    }
}
