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
 * @summary Test implementations of ExecutorService.invokeAll/invokeAny
 * @run junit InvokeTest
 */

import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import static java.lang.Thread.State.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class InvokeTest {

    private static ScheduledExecutorService scheduler;

    @BeforeAll
    static void setup() throws Exception {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterAll
    static void shutdown() {
        scheduler.shutdown();
    }

    private static Stream<ExecutorService> executors() {
        return Stream.of(
                Executors.newCachedThreadPool(),
                Executors.newVirtualThreadPerTaskExecutor(),
                new ForkJoinPool()
        );
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
            String result = executor.invokeAny(List.of(task1, task2));
            assertTrue(Set.of("foo", "bar").contains(result));
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
            Callable<String> task1 = () -> "foo";

            var task2Started = new AtomicBoolean();
            var task2Interrupted = new CountDownLatch(1);
            Callable<String> task2 = () -> {
                task2Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    task2Interrupted.countDown();
                }
                return null;
            };

            String result = executor.invokeAny(List.of(task1, task2));
            assertEquals("foo", result);

            // if task2 started then it should have been interrupted
            if (task2Started.get()) {
                task2Interrupted.await();
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
                executor.invokeAny(List.of(task1, task2));
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
                executor.invokeAny(List.of(task1, task2));
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
            String result = executor.invokeAny(List.of(task1, task2));
            assertEquals("foo", result);
        }
    }

    /**
     * Test invokeAny where some, not all, tasks complete normally. The first
     * task to complete normally is delayed.
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
            String result = executor.invokeAny(List.of(task1, task2));
            assertEquals("foo", result);
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
            String result = executor.invokeAny(List.of(task1, task2), 1, TimeUnit.MINUTES);
            assertTrue(Set.of("foo", "bar").contains(result));
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
            Callable<String> task1 = () -> "foo";

            var task2Started = new AtomicBoolean();
            var task2Interrupted = new CountDownLatch(1);
            Callable<String> task2 = () -> {
                task2Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    task2Interrupted.countDown();
                }
                return null;
            };

            String result = executor.invokeAny(List.of(task1, task2), 1, TimeUnit.MINUTES);
            assertEquals("foo", result);

            // if task2 started then it should have been interrupted
            if (task2Started.get()) {
                task2Interrupted.await();
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
            var task1Started = new AtomicBoolean();
            var task1Interrupted = new CountDownLatch(1);
            Callable<String> task1 = () -> {
                task1Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    task1Interrupted.countDown();
                }
                return null;
            };

            var task2Started = new AtomicBoolean();
            var task2Interrupted = new CountDownLatch(1);
            Callable<String> task2 = () -> {
                task2Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    task2Interrupted.countDown();
                }
                return null;
            };

            // invokeAny should throw TimeoutException
            assertThrows(TimeoutException.class,
                    () -> executor.invokeAny(List.of(task1, task2), 100, TimeUnit.MILLISECONDS));

            // tasks that started should be interrupted
            if (task1Started.get()) {
                task1Interrupted.await();
            }
            if (task2Started.get()) {
                task2Interrupted.await();
            }
        }
    }

    /**
     * Test invokeAny with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyWithInterruptSet(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> {
                Thread.sleep(Duration.ofMinutes(1));
                return "foo";
            };
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMinutes(1));
                return "bar";
            };
            Thread.currentThread().interrupt();
            try {
                executor.invokeAny(List.of(task1, task2));
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
            var task1Started = new AtomicBoolean();
            var task1Interrupted = new CountDownLatch(1);
            Callable<String> task1 = () -> {
                task1Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    task1Interrupted.countDown();
                }
                return null;
            };

            var task2Started = new AtomicBoolean();
            var task2Interrupted = new CountDownLatch(1);
            Callable<String> task2 = () -> {
                task2Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    task2Interrupted.countDown();
                }
                return null;
            };

            scheduleInterruptAt("invokeAny");
            try {
                executor.invokeAny(List.of(task1, task2));
                fail("invokeAny did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // clear interrupt
            }

            // tasks that started should be interrupted
            if (task1Started.get()) {
                task1Interrupted.await();
            }
            if (task2Started.get()) {
                task2Interrupted.await();
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
                () -> executor.invokeAny(List.of(task1, task2)));
    }

    /**
     * Test invokeAny with empty collection.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyEmpty1(ExecutorService executor) throws Exception {
        try (executor) {
            assertThrows(IllegalArgumentException.class, () -> executor.invokeAny(List.of()));
        }
    }

    /**
     * Test timed-invokeAny with empty collection.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyEmpty2(ExecutorService executor) throws Exception {
        try (executor) {
            assertThrows(IllegalArgumentException.class,
                    () -> executor.invokeAny(List.of(), 1, TimeUnit.MINUTES));
        }
    }

    /**
     * Test invokeAny with null.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyNull1(ExecutorService executor)throws Exception {
        try (executor) {
            assertThrows(NullPointerException.class, () -> executor.invokeAny(null));
        }
    }

    /**
     * Test invokeAny with null element
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAnyNull2(ExecutorService executor)throws Exception {
        try (executor) {
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

            List<Future<String>> futures = executor.invokeAll(List.of(task1, task2));
            assertTrue(futures.size() == 2);

            // check results
            List<String> results = futures.stream().map(Future::resultNow).toList();
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

            List<Future<String>> futures = executor.invokeAll(List.of(task1, task2));
            assertTrue(futures.size() == 2);

            // check results
            Throwable e1 = assertThrows(ExecutionException.class, () -> futures.get(0).get());
            assertTrue(e1.getCause() instanceof FooException);
            Throwable e2 = assertThrows(ExecutionException.class, () -> futures.get(1).get());
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

            List<Future<String>> futures = executor.invokeAll(List.of(task1, task2), 1, TimeUnit.MINUTES);
            assertTrue(futures.size() == 2);

            // check results
            List<String> results = futures.stream().map(Future::resultNow).toList();
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
            Callable<String> task1 = () -> "foo";

            var task2Started = new AtomicBoolean();
            var task2Interrupted = new CountDownLatch(1);
            Callable<String> task2 = () -> {
                task2Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    task2Interrupted.countDown();
                }
                return null;
            };

            List<Future<String>> futures = executor.invokeAll(List.of(task1, task2), 1, TimeUnit.SECONDS);
            assertTrue(futures.size() == 2);

            // task1 should be done
            assertTrue(futures.get(0).isDone());

            // task2 should be cancelled and interrupted
            assertTrue(futures.get(1).isCancelled());

            // if task2 started then it should have been interrupted
            if (task2Started.get()) {
                task2Interrupted.await();
            }
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
                executor.invokeAll(List.of(task1, task2), 1, TimeUnit.MINUTES);
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

            var task2Started = new AtomicBoolean();
            var task2Interrupted = new CountDownLatch(1);
            Callable<String> task2 = () -> {
                task2Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    task2Interrupted.countDown();
                }
                return null;
            };

            scheduleInterruptAt("invokeAll");
            try {
                executor.invokeAll(List.of(task1, task2));
                fail("invokeAll did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // clear interrupt
            }

            // if task2 started then it should have been interrupted
            if (task2Started.get()) {
                task2Interrupted.await();
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

            var task2Started = new AtomicBoolean();
            var task2Interrupted = new CountDownLatch(1);
            Callable<String> task2 = () -> {
                task2Started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    task2Interrupted.countDown();
                }
                return null;
            };

            scheduleInterruptAt("invokeAll");
            try {
                executor.invokeAll(List.of(task1, task2), 1, TimeUnit.MINUTES);
                fail("invokeAll did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // clear interrupt
            }

            // if task2 started then it should have been interrupted
            if (task2Started.get()) {
                task2Interrupted.await();
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
                () -> executor.invokeAll(List.of(task1, task2)));
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllAfterShutdown2(ExecutorService executor) throws Exception {
        executor.shutdown();

        Callable<String> task1 = () -> "foo";
        Callable<String> task2 = () -> "bar";
        assertThrows(RejectedExecutionException.class,
                () -> executor.invokeAll(List.of(task1, task2), 1, TimeUnit.SECONDS));
    }

    /**
     * Test invokeAll with empty collection.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllEmpty1(ExecutorService executor) throws Exception {
        try (executor) {
            List<Future<Object>> list = executor.invokeAll(List.of());
            assertTrue(list.size() == 0);
        }
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllEmpty2(ExecutorService executor) throws Exception {
        try (executor) {
            List<Future<Object>> list = executor.invokeAll(List.of(), 1, TimeUnit.SECONDS);
            assertTrue(list.size() == 0);
        }
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllNull1(ExecutorService executor)throws Exception {
        try (executor) {
            assertThrows(NullPointerException.class, () -> executor.invokeAll(null));
        }
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllNull2(ExecutorService executor)throws Exception {
        try (executor) {
            List<Callable<String>> tasks = new ArrayList<>();
            tasks.add(() -> "foo");
            tasks.add(null);
            assertThrows(NullPointerException.class, () -> executor.invokeAll(tasks));
        }
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllNull3(ExecutorService executor)throws Exception {
        try (executor) {
            assertThrows(NullPointerException.class,
                    () -> executor.invokeAll(null, 1, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllNull4(ExecutorService executor)throws Exception {
        try (executor) {
            Callable<String> task = () -> "foo";
            assertThrows(NullPointerException.class,
                    () -> executor.invokeAll(List.of(task), 1, null));
        }
    }

    @ParameterizedTest
    @MethodSource("executors")
    void testInvokeAllNull5(ExecutorService executor)throws Exception {
        try (executor) {
            List<Callable<String>> tasks = new ArrayList<>();
            tasks.add(() -> "foo");
            tasks.add(null);
            assertThrows(NullPointerException.class,
                    () -> executor.invokeAll(tasks, 1, TimeUnit.SECONDS));
        }
    }

    /**
     * Schedules the current thread to be interrupted when it waits (timed or untimed)
     * at the given method name.
     */
    private void scheduleInterruptAt(String methodName) {
        Thread target = Thread.currentThread();
        scheduler.submit(() -> {
            try {
                boolean found = false;
                while (!found) {
                    Thread.State state = target.getState();
                    assertTrue(state != TERMINATED);
                    if ((state == WAITING || state == TIMED_WAITING)
                            && contains(target.getStackTrace(), methodName)) {
                        found = true;
                    } else {
                        Thread.sleep(20);
                    }
                }
                target.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Returns true if the given stack trace contains an element for the given method name.
     */
    private boolean contains(StackTraceElement[] stack, String methodName) {
        return Arrays.stream(stack)
                .anyMatch(e -> methodName.equals(e.getMethodName()));
    }
}
