/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for new thread-per-task executors
 * @compile --enable-preview -source ${jdk.version} ThreadPerTaskExecutorTest.java
 * @run testng/othervm/timeout=300 --enable-preview ThreadPerTaskExecutorTest
 */

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static java.util.concurrent.Future.State.*;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ThreadPerTaskExecutorTest {
    // long running interruptible task
    private static final Callable<Void> SLEEP_FOR_A_DAY = () -> {
        Thread.sleep(Duration.ofDays(1));
        return null;
    };

    private ScheduledExecutorService scheduler;

    @BeforeClass
    public void setUp() throws Exception {
        ThreadFactory factory = (task) -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    @AfterClass
    public void tearDown() {
        scheduler.shutdown();
    }

    @DataProvider(name = "factories")
    public Object[][] factories() {
        return new Object[][] {
            { Executors.defaultThreadFactory(), },
            { Thread.ofVirtual().factory(), },
        };
    }

    @DataProvider(name = "executors")
    public Object[][] executors() {
        var defaultThreadFactory = Executors.defaultThreadFactory();
        var virtualThreadFactory = Thread.ofVirtual().factory();
        return new Object[][] {
            { Executors.newThreadPerTaskExecutor(defaultThreadFactory), },
            { Executors.newThreadPerTaskExecutor(virtualThreadFactory), },
        };
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
    @Test(dataProvider = "factories")
    public void testThreadPerTask(ThreadFactory factory) throws Exception {
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
        assertEquals(threadCount.get(), NUM_TASKS);
        for (int i=0; i<NUM_TASKS; i++) {
            Future<Integer> future = futures.get(i);
            assertEquals((int) future.get(), i);
        }
    }

    /**
     * Test that newThreadPerTaskExecutor uses the specified thread factory.
     */
    @Test
    public void testThreadFactory() throws Exception {
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
    @Test(dataProvider = "executors")
    public void testShutdown(ExecutorService executor) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testShutdownNow(ExecutorService executor) throws Exception {
        try (executor) {
            assertFalse(executor.isShutdown());
            assertFalse(executor.isTerminated());
            assertFalse(executor.awaitTermination(10, TimeUnit.MILLISECONDS));

            Future<?> result = executor.submit(SLEEP_FOR_A_DAY);
            try {
                List<Runnable> tasks = executor.shutdownNow();
                assertTrue(executor.isShutdown());
                assertTrue(tasks.isEmpty());

                Throwable e = expectThrows(ExecutionException.class, result::get);
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
    @Test(dataProvider = "executors")
    public void testClose1(ExecutorService executor) throws Exception {
        executor.close();
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10,  TimeUnit.MILLISECONDS));
    }

    /**
     * Test close with threads running.
     */
    @Test(dataProvider = "executors")
    public void testClose2(ExecutorService executor) throws Exception {
        Future<String> future;
        try (executor) {
            future = executor.submit(() -> {
                Thread.sleep(Duration.ofMillis(500));
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
    @Test(dataProvider = "executors")
    public void testClose3(ExecutorService executor) throws Exception {
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
        expectThrows(ExecutionException.class, future::get);
    }

    /**
     * Interrupt thread blocked in close.
     */
    @Test(dataProvider = "executors")
    public void testClose4(ExecutorService executor) throws Exception {
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
        expectThrows(ExecutionException.class, future::get);
    }

    /**
     * Close executor that is already closed.
     */
    @Test(dataProvider = "executors")
    public void testClose5(ExecutorService executor) throws Exception {
        executor.close();
        executor.close(); // already closed
    }

    /**
     * Test awaitTermination when not shutdown.
     */
    @Test(dataProvider = "executors")
    public void testAwaitTermination1(ExecutorService executor) throws Exception {
        assertFalse(executor.awaitTermination(100, TimeUnit.MILLISECONDS));
        executor.close();
        assertTrue(executor.awaitTermination(100, TimeUnit.MILLISECONDS));
    }

    /**
     * Test awaitTermination with task running.
     */
    @Test(dataProvider = "executors")
    public void testAwaitTermination2(ExecutorService executor) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testSubmitAfterShutdown(ExecutorService executor) throws Exception {
        Phaser barrier = new Phaser(2);
        try (executor) {
            // submit task to prevent executor from terminating
            executor.submit(barrier::arriveAndAwaitAdvance);
            try {
                executor.shutdown();
                assertTrue(executor.isShutdown() && !executor.isTerminated());
                expectThrows(RejectedExecutionException.class,
                             () -> executor.submit(() -> {  }));
            } finally {
                barrier.arriveAndAwaitAdvance();
            }
        }
    }

    /**
     * Test submit when the Executor is terminated.
     */
    @Test(dataProvider = "executors")
    public void testSubmitAfterTermination(ExecutorService executor) throws Exception {
        executor.shutdown();
        assertTrue(executor.isShutdown() && executor.isTerminated());
        expectThrows(RejectedExecutionException.class, () -> executor.submit(() -> {}));
    }

    /**
     * Test submit with null.
     */
    @Test(dataProvider = "factories")
    public void testSubmitNulls1(ThreadFactory factory) {
        var executor = Executors.newThreadPerTaskExecutor(factory);
        assertThrows(NullPointerException.class, () -> executor.submit((Runnable) null));
    }

    @Test(dataProvider = "factories")
    public void testSubmitNulls2(ThreadFactory factory) {
        var executor = Executors.newThreadPerTaskExecutor(factory);
        assertThrows(NullPointerException.class, () -> executor.submit((Callable<String>) null));
    }

    /**
     * Test invokeAny where all tasks complete normally.
     */
    @Test(dataProvider = "executors")
    public void testInvokeAny1(ExecutorService executor) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testInvokeAny2(ExecutorService executor) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testInvokeAny3(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            Callable<String> task1 = () -> { throw new FooException(); };
            Callable<String> task2 = () -> { throw new FooException(); };
            try {
                executor.invokeAny(Set.of(task1, task2));
                fail();
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
    @Test(dataProvider = "executors")
    public void testInvokeAny4(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            Callable<String> task1 = () -> { throw new FooException(); };
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMillis(500));
                throw new FooException();
            };
            try {
                executor.invokeAny(Set.of(task1, task2));
                fail();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                assertTrue(cause instanceof FooException);
            }
        }
    }

    /**
     * Test invokeAny where some, not all, tasks complete normally.
     */
    @Test(dataProvider = "executors")
    public void testInvokeAny5(ExecutorService executor) throws Exception {
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
     * completion of the last task is delayed.
     */
    @Test(dataProvider = "executors")
    public void testInvokeAny6(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            Callable<String> task1 = () -> {
                Thread.sleep(Duration.ofMillis(500));
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
    @Test(dataProvider = "executors")
    public void testInvokeAnyWithTimeout1(ExecutorService executor) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testInvokeAnyWithTimeout2(ExecutorService executor) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testInvokeAnyWithTimeout3(ExecutorService executor) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testInvokeAnyWithTimeout4(ExecutorService executor) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testInvokeAnyWithInterruptSet(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> "bar";
            Thread.currentThread().interrupt();
            try {
                executor.invokeAny(Set.of(task1, task2));
                fail();
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
    @Test(dataProvider = "executors")
    public void testInterruptInvokeAny(ExecutorService executor) throws Exception {
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
                fail();
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
    @Test(dataProvider = "executors")
    public void testInvokeAnyAfterShutdown(ExecutorService executor) throws Exception {
        executor.shutdown();
        Callable<String> task1 = () -> "foo";
        Callable<String> task2 = () -> "bar";
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAny(Set.of(task1, task2)));
    }

    /**
     * Test invokeAny with empty collection.
     */
    @Test(dataProvider = "factories")
    public void testInvokeAnyEmpty1(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(IllegalArgumentException.class, () -> executor.invokeAny(Set.of()));
        }
    }

    /**
     * Test timed-invokeAny with empty collection.
     */
    @Test(dataProvider = "factories")
    public void testInvokeAnyEmpty2(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(IllegalArgumentException.class,
                         () -> executor.invokeAny(Set.of(), 1, TimeUnit.MINUTES));
        }
    }

    /**
     * Test invokeAny with null.
     */
    @Test(dataProvider = "factories")
    public void testInvokeAnyNull1(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(NullPointerException.class, () -> executor.invokeAny(null));
        }
    }

    /**
     * Test invokeAny with null element
     */
    @Test(dataProvider = "factories")
    public void testInvokeAnyNull2(ThreadFactory factory) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testInvokeAll1(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMillis(500));
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
    @Test(dataProvider = "executors")
    public void testInvokeAll2(ExecutorService executor) throws Exception {
        try (executor) {
            class FooException extends Exception { }
            class BarException extends Exception { }
            Callable<String> task1 = () -> { throw new FooException(); };
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMillis(500));
                throw new BarException();
            };

            List<Future<String>> list = executor.invokeAll(List.of(task1, task2));

            // list should have two elements, both should be done
            assertTrue(list.size() == 2);
            boolean notDone = list.stream().anyMatch(r -> !r.isDone());
            assertFalse(notDone);

            // check results
            Throwable e1 = expectThrows(ExecutionException.class, () -> list.get(0).get());
            assertTrue(e1.getCause() instanceof FooException);
            Throwable e2 = expectThrows(ExecutionException.class, () -> list.get(1).get());
            assertTrue(e2.getCause() instanceof BarException);
        }
    }

    /**
     * Test invokeAll where all tasks complete normally before the timeout expires.
     */
    @Test(dataProvider = "executors")
    public void testInvokeAll3(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMillis(500));
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
    @Test(dataProvider = "executors")
    public void testInvokeAll4(ExecutorService executor) throws Exception {
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
    @Test(dataProvider = "executors")
    public void testInvokeAllInterrupt1(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMinutes(1));
                return "bar";
            };

            Thread.currentThread().interrupt();
            try {
                executor.invokeAll(List.of(task1, task2));
                fail();
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
    @Test(dataProvider = "executors")
    public void testInvokeAllInterrupt3(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> {
                Thread.sleep(Duration.ofMinutes(1));
                return "bar";
            };

            Thread.currentThread().interrupt();
            try {
                executor.invokeAll(List.of(task1, task2), 1, TimeUnit.SECONDS);
                fail();
            } catch (InterruptedException expected) {
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted(); // clear interrupt
            }
        }
    }

    /**
     * Test interrupt with thread blocked in invokeAll
     */
    @Test(dataProvider = "executors")
    public void testInvokeAllInterrupt4(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            DelayedResult<String> task2 = new DelayedResult("bar", Duration.ofMinutes(1));
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            try {
                executor.invokeAll(Set.of(task1, task2));
                fail();
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
     * Test interrupt with thread blocked in timed-invokeAll
     */
    @Test(dataProvider = "executors")
    public void testInvokeAllInterrupt6(ExecutorService executor) throws Exception {
        try (executor) {
            Callable<String> task1 = () -> "foo";
            DelayedResult<String> task2 = new DelayedResult("bar", Duration.ofMinutes(1));
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            try {
                executor.invokeAll(Set.of(task1, task2), 1, TimeUnit.DAYS);
                fail();
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
    @Test(dataProvider = "executors")
    public void testInvokeAllAfterShutdown1(ExecutorService executor) throws Exception {
        executor.shutdown();

        Callable<String> task1 = () -> "foo";
        Callable<String> task2 = () -> "bar";
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAll(Set.of(task1, task2)));
    }

    @Test(dataProvider = "executors")
    public void testInvokeAllAfterShutdown2(ExecutorService executor) throws Exception {
        executor.shutdown();

        Callable<String> task1 = () -> "foo";
        Callable<String> task2 = () -> "bar";
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAll(Set.of(task1, task2), 1, TimeUnit.SECONDS));
    }

    /**
     * Test invokeAll with empty collection.
     */
    @Test(dataProvider = "executors")
    public void testInvokeAllEmpty1(ExecutorService executor) throws Exception {
        try (executor) {
            List<Future<Object>> list = executor.invokeAll(Set.of());
            assertTrue(list.size() == 0);
        }
    }

    @Test(dataProvider = "executors")
    public void testInvokeAllEmpty2(ExecutorService executor) throws Exception {
        try (executor) {
            List<Future<Object>> list = executor.invokeAll(Set.of(), 1, TimeUnit.SECONDS);
            assertTrue(list.size() == 0);
        }
    }

    @Test(dataProvider = "factories")
    public void testInvokeAllNull1(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(NullPointerException.class, () -> executor.invokeAll(null));
        }
    }

    @Test(dataProvider = "factories")
    public void testInvokeAllNull2(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            List<Callable<String>> tasks = new ArrayList<>();
            tasks.add(() -> "foo");
            tasks.add(null);
            assertThrows(NullPointerException.class, () -> executor.invokeAll(tasks));
        }
    }

    @Test(dataProvider = "factories")
    public void testInvokeAllNull3(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            assertThrows(NullPointerException.class,
                         () -> executor.invokeAll(null, 1, TimeUnit.SECONDS));
        }
    }

    @Test(dataProvider = "factories")
    public void testInvokeAllNull4(ThreadFactory factory) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            Callable<String> task = () -> "foo";
            assertThrows(NullPointerException.class,
                         () -> executor.invokeAll(List.of(task), 1, null));
        }
    }

    @Test(dataProvider = "factories")
    public void testInvokeAllNull5(ThreadFactory factory) throws Exception {
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
    public void testNoThreads1() throws Exception {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(task -> null);
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
    }

    @Test
    public void testNoThreads2() throws Exception {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(task -> null);
        assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> "foo"));
    }

    @Test
    public void testNoThreads3() throws Exception {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(task -> null);
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAll(List.of(() -> "foo")));
    }

    @Test
    public void testNoThreads4() throws Exception {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(task -> null);
        assertThrows(RejectedExecutionException.class,
                     () -> executor.invokeAny(List.of(() -> "foo")));
    }

    @Test
    public void testNull() {
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
