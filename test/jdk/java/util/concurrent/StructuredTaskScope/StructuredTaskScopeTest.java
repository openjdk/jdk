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
 * @test id=platform
 * @bug 8284199 8296779 8306647
 * @summary Basic tests for StructuredTaskScope
 * @enablePreview
 * @run junit/othervm -DthreadFactory=platform StructuredTaskScopeTest
 */

/*
 * @test id=virtual
 * @enablePreview
 * @run junit/othervm -DthreadFactory=virtual StructuredTaskScopeTest
 */

import java.time.Duration;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.TaskHandle;
import java.util.concurrent.StructuredTaskScope.ShutdownOnSuccess;
import java.util.concurrent.StructuredTaskScope.ShutdownOnFailure;
import java.util.concurrent.StructureViolationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class StructuredTaskScopeTest {
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

    /**
     * Test that fork creates a new thread for each task.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkCreatesThread(ThreadFactory factory) throws Exception {
        Set<Long> tids = ConcurrentHashMap.newKeySet();
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            for (int i = 0; i < 100; i++) {
                scope.fork(() -> {
                    tids.add(Thread.currentThread().threadId());
                    return null;
                });
            }
            scope.join();
        }
        assertEquals(100, tids.size());
    }

    /**
     * Test that fork creates a new virtual thread for each task.
     */
    @Test
    void testForkCreateVirtualThread() throws Exception {
        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        try (var scope = new StructuredTaskScope<Object>()) {
            for (int i = 0; i < 100; i++) {
                scope.fork(() -> {
                    threads.add(Thread.currentThread());
                    return null;
                });
            }
            scope.join();
        }
        assertEquals(100, threads.size());
        threads.forEach(t -> assertTrue(t.isVirtual()));
    }

    /**
     * Test that fork creates a new thread with the given thread factory.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkUsesFactory(ThreadFactory factory) throws Exception {
        var count = new AtomicInteger();
        ThreadFactory countingFactory = task -> {
            count.incrementAndGet();
            return factory.newThread(task);
        };
        try (var scope = new StructuredTaskScope<Object>(null, countingFactory)) {
            for (int i = 0; i < 100; i++) {
                scope.fork(() -> null);
            }
            scope.join();
        }
        assertEquals(100, count.get());
    }

    /**
     * Test fork is confined to threads in the scope "tree".
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkConfined(ThreadFactory factory) throws Exception {
        try (var scope1 = new StructuredTaskScope<Boolean>();
             var scope2 = new StructuredTaskScope<Boolean>()) {

            // thread in scope1 cannot fork thread in scope2
            TaskHandle<Boolean> handle1 = scope1.fork(() -> {
                assertThrows(WrongThreadException.class, () -> {
                    scope2.fork(() -> null);
                });
                return true;
            });

            // thread in scope2 can fork thread in scope1
            TaskHandle<Boolean> handle2 = scope2.fork(() -> {
                scope1.fork(() -> null);
                return true;
            });

            scope2.join();
            scope1.join();

            assertTrue(handle1.get());
            assertTrue(handle2.get());

            // random thread cannot fork
            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<Void> future = pool.submit(() -> {
                    assertThrows(WrongThreadException.class, () -> {
                        scope1.fork(() -> null);
                    });
                    assertThrows(WrongThreadException.class, () -> {
                        scope2.fork(() -> null);
                    });
                    return null;
                });
                future.get();
            }
        }
    }

    /**
     * Test fork after join.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkAfterJoin(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<String>(null, factory)) {
            scope.join();
            TaskHandle<String> handle = scope.fork(() -> "foo");
            scope.join();
            assertEquals(TaskHandle.State.SUCCESS, handle.state());
            assertEquals("foo", handle.get());
            assertThrows(IllegalStateException.class, handle::exception);
        }
    }

    /**
     * Test fork after scope is shutdown.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkAfterShutdown(ThreadFactory factory) throws Exception {
        var executed = new AtomicBoolean();
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            scope.shutdown();
            TaskHandle<String> handle = scope.fork(() -> {
                executed.set(true);
                return null;
            });
            assertEquals(TaskHandle.State.CANCELLED, handle.state());
            assertThrows(IllegalStateException.class, handle::get);
            assertThrows(IllegalStateException.class, handle::exception);
        }
        assertFalse(executed.get());
    }

    /**
     * Test fork after scope is closed.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkAfterClose(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            scope.close();
            assertThrows(IllegalStateException.class, () -> scope.fork(() -> null));
        }
    }

    /**
     * Test fork when the thread factory rejects creating a thread.
     */
    @Test
    void testForkRejectedExecutionException() throws Exception {
        ThreadFactory factory = task -> null;
        try (var scope = new StructuredTaskScope(null, factory)) {
            assertThrows(RejectedExecutionException.class, () -> scope.fork(() -> null));
            scope.join();
        }
    }

    /**
     * Test join with no threads.
     */
    @Test
    void testJoinWithNoThreads() throws Exception {
        try (var scope = new StructuredTaskScope()) {
            scope.join();
        }
    }

    /**
     * Test join with threads running.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinWithThreads(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope(null, factory)) {
            TaskHandle<String> handle = scope.fork(() -> {
                Thread.sleep(Duration.ofMillis(50));
                return "foo";
            });
            scope.join();
            assertEquals("foo", handle.get());
        }
    }

    /**
     * Test join is owner confined.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinConfined(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Boolean>()) {

            // thread in scope cannot join
            TaskHandle<Boolean> handle = scope.fork(() -> {
                assertThrows(WrongThreadException.class, () -> { scope.join(); });
                return true;
            });

            scope.join();

            assertTrue(handle.get());

            // random thread cannot join
            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<Void> future = pool.submit(() -> {
                    assertThrows(WrongThreadException.class, scope::join);
                    return null;
                });
                future.get();
            }
        }
    }

    /**
     * Test join with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptJoin1(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope(null, factory)) {
            var latch = new CountDownLatch(1);

            TaskHandle<String> handle = scope.fork(() -> {
                latch.await();
                return "foo";
            });

            // join should throw
            Thread.currentThread().interrupt();
            try {
                scope.join();
                fail("join did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());   // interrupt status should be clear
            } finally {
                // let task continue
                latch.countDown();
            }

            // join should complete
            scope.join();
            assertEquals("foo", handle.get());
        }
    }

    /**
     * Test interrupt of thread blocked in join.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptJoin2(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope(null, factory)) {
            var latch = new CountDownLatch(1);

            TaskHandle<String> handle = scope.fork(() -> {
                latch.await();
                return "foo";
            });

            // join should throw
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            try {
                scope.join();
                fail("join did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());   // interrupt status should be clear
            } finally {
                // let task continue
                latch.countDown();
            }

            // join should complete
            scope.join();
            assertEquals("foo", handle.get());
        }
    }

    /**
     * Test join when scope is shutdown.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinWithShutdown1(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<String>(null, factory)) {
            var interrupted = new CountDownLatch(1);
            var finish = new CountDownLatch(1);

            TaskHandle<String> handle = scope.fork(() -> {
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
                finish.await();
                return "foo";
            });

            scope.shutdown();      // should interrupt task

            interrupted.await();

            scope.join();

            // signal task to finish
            finish.countDown();
        }
    }

    /**
     * Test shutdown when owner is blocked in join.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinWithShutdown2(ThreadFactory factory) throws Exception {
        class MyScope<T> extends StructuredTaskScope<T> {
            MyScope(ThreadFactory factory) {
                super(null, factory);
            }
            @Override
            protected void handleComplete(TaskHandle<T> handle) {
                shutdown();
            }
        }

        try (var scope = new MyScope<String>(factory)) {
            TaskHandle<String> handle1 = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return "foo";
            });
            TaskHandle<String> handle2 = scope.fork(() -> {
                Thread.sleep(Duration.ofMillis(50));
                return "bar";
            });

            scope.join();

            // task1 should be cancelled
            assertEquals(TaskHandle.State.CANCELLED, handle1.state());

            // task2 should have completed successfully
            assertEquals(TaskHandle.State.SUCCESS, handle2.state());
            assertEquals("bar", handle2.get());
        }
    }

    /**
     * Test join after scope is closed.
     */
    @Test
    void testJoinAfterClose() throws Exception {
        try (var scope = new StructuredTaskScope()) {
            scope.join();
            scope.close();
            assertThrows(IllegalStateException.class, () -> scope.join());
            assertThrows(IllegalStateException.class, () -> scope.joinUntil(Instant.now()));
        }
    }

    /**
     * Test joinUntil, threads finish before deadline expires.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinUntil1(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<String>(null, factory)) {
            TaskHandle<String> handle = scope.fork(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(2));
                } catch (InterruptedException e) { }
                return "foo";
            });

            long startMillis = millisTime();
            scope.joinUntil(Instant.now().plusSeconds(30));
            expectDuration(startMillis, /*min*/1900, /*max*/20_000);
            assertEquals("foo", handle.get());
        }
    }

    /**
     * Test joinUntil, deadline expires before threads finish.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinUntil2(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            TaskHandle<Void> handle = scope.fork(() -> {
                Thread.sleep(Duration.ofSeconds(30));
                return null;
            });

            long startMillis = millisTime();
            try {
                scope.joinUntil(Instant.now().plusSeconds(2));
            } catch (TimeoutException e) {
                expectDuration(startMillis, /*min*/1900, /*max*/20_000);
            }
            assertEquals(TaskHandle.State.RUNNING, handle.state());
        }
    }

    /**
     * Test joinUntil many times.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinUntil3(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<String>(null, factory)) {
            TaskHandle<String> handle = scope.fork(() -> {
                Thread.sleep(Duration.ofSeconds(30));
                return null;
            });

            for (int i = 0; i < 3; i++) {
                try {
                    scope.joinUntil(Instant.now().plusMillis(50));
                    fail("joinUntil did not throw");
                } catch (TimeoutException expected) {
                    assertEquals(TaskHandle.State.RUNNING, handle.state());
                }
            }
        }
    }

    /**
     * Test joinUntil with a deadline that has already expired.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinUntil4(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            TaskHandle<Void> handle = scope.fork(() -> {
                Thread.sleep(Duration.ofSeconds(30));
                return null;
            });

            // now
            try {
                scope.joinUntil(Instant.now());
                fail("joinUntil did not throw");
            } catch (TimeoutException expected) {
                assertEquals(TaskHandle.State.RUNNING, handle.state());
            }

            // in the past
            try {
                scope.joinUntil(Instant.now().minusSeconds(1));
                fail("joinUntil did not throw");
            } catch (TimeoutException expected) {
                assertEquals(TaskHandle.State.RUNNING, handle.state());
            }
        }
    }

    /**
     * Test joinUntil with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptJoinUntil1(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<String>(null, factory)) {
            var latch = new CountDownLatch(1);

            TaskHandle<String> handle = scope.fork(() -> {
                latch.await();
                return "foo";
            });

            // joinUntil should throw
            Thread.currentThread().interrupt();
            try {
                scope.joinUntil(Instant.now().plusSeconds(30));
                fail("joinUntil did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());   // interrupt status should be clear
            } finally {
                // let task continue
                latch.countDown();
            }

            // join should complete
            scope.join();
            assertEquals("foo", handle.get());
        }
    }

    /**
     * Test interrupt of thread blocked in joinUntil.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptJoinUntil2(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope(null, factory)) {
            var latch = new CountDownLatch(1);

            TaskHandle<String> handle = scope.fork(() -> {
                latch.await();
                return "foo";
            });

            // joinUntil should throw
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            try {
                scope.joinUntil(Instant.now().plusSeconds(10));
                fail("joinUntil did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());   // interrupt status should be clear
            } finally {
                // let task continue
                latch.countDown();
            }

            // join should complete
            scope.join();
            assertEquals("foo", handle.get());
        }
    }

    /**
     * Test that shutdown prevents new threads from starting.
     */
    @Test
    void testShutdownWithFork() throws Exception {
        ThreadFactory factory = task -> null;
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            scope.shutdown();
            // should not invoke the ThreadFactory to create thread
            TaskHandle<Void> handle = scope.fork(() -> null);
            assertEquals(TaskHandle.State.CANCELLED, handle.state());
        }
    }

    /**
     * Test that shutdown interrupts unfinished threads.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownInterruptsThreads1(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            var interrupted = new AtomicBoolean();
            var latch = new CountDownLatch(1);
            var handle = scope.fork(() -> {
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    interrupted.set(true);
                } finally {
                    latch.countDown();
                }
                return null;
            });

            scope.shutdown();

            // wait for task to complete
            latch.await();

            // task should be cancelled and thread interrupted
            assertEquals(TaskHandle.State.CANCELLED, handle.state());
            assertTrue(interrupted.get());

            scope.join();
        }
    }

    /**
     * Test that shutdown does not interrupt current thread.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownInterruptsThreads2(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            var interrupted = new AtomicBoolean();
            var latch = new CountDownLatch(1);
            var handle = scope.fork(() -> {
                try {
                    scope.shutdown();
                    interrupted.set(Thread.currentThread().isInterrupted());
                } finally {
                    latch.countDown();
                }
                return null;
            });

            // wait for task to complete
            latch.await();

            // task should be cancelled, the thread should not be interrupted
            assertEquals(TaskHandle.State.CANCELLED, handle.state());
            assertFalse(interrupted.get());

            scope.join();
        }
    }

    /**
     * Test shutdown wakes join.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownWakesJoin(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            var latch = new CountDownLatch(1);
            scope.fork(() -> {
                Thread.sleep(Duration.ofMillis(100));  // give time for join to block
                scope.shutdown();
                latch.await();
                return null;
            });

            scope.join();

            // join woke up, allow task to complete
            latch.countDown();
        }
    }

    /**
     * Test shutdown after scope is closed.
     */
    @Test
    void testShutdownAfterClose() throws Exception {
        try (var scope = new StructuredTaskScope<Object>()) {
            scope.join();
            scope.close();
            assertThrows(IllegalStateException.class, scope::shutdown);
        }
    }

    /**
     * Test shutdown is confined to threads in the scope "tree".
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownConfined(ThreadFactory factory) throws Exception {
        try (var scope1 = new StructuredTaskScope<Boolean>();
             var scope2 = new StructuredTaskScope<Boolean>()) {

            // thread in scope1 cannot shutdown scope2
            TaskHandle<Boolean> handle1 = scope1.fork(() -> {
                assertThrows(WrongThreadException.class, scope2::shutdown);
                return true;
            });

            // wait for task in scope1 to complete to avoid racing with task in scope2
            while (handle1.state() == TaskHandle.State.RUNNING) {
                Thread.sleep(10);
            }

            // thread in scope2 shutdown scope1
            TaskHandle<Boolean> handle2 = scope2.fork(() -> {
                scope1.shutdown();
                return true;
            });

            scope2.join();
            scope1.join();

            assertTrue(handle1.get());
            assertTrue(handle1.get());

            // random thread cannot shutdown
            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<Void> future = pool.submit(() -> {
                    assertThrows(WrongThreadException.class, scope1::shutdown);
                    assertThrows(WrongThreadException.class, scope2::shutdown);
                    return null;
                });
                future.get();
            }
        }
    }

    /**
     * Test close without join, no threads forked.
     */
    @Test
    void testCloseWithoutJoin1() {
        try (var scope = new StructuredTaskScope<Object>()) {
            // do nothing
        }
    }

    /**
     * Test close without join, unfinished threads.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCloseWithoutJoin2(ThreadFactory factory) {
        try (var scope = new StructuredTaskScope<String>(null, factory)) {
            TaskHandle<String> handle = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            });
            assertThrows(IllegalStateException.class, scope::close);
            assertEquals(TaskHandle.State.CANCELLED, handle.state());
        }
    }

    /**
     * Test close with threads forked after join.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCloseWithoutJoin3(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope(null, factory)) {
            scope.fork(() -> "foo");
            scope.join();

            TaskHandle<String> handle = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            });
            assertThrows(IllegalStateException.class, scope::close);
            assertEquals(TaskHandle.State.CANCELLED, handle.state());
        }
    }

    /**
     * Test close is owner confined.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCloseConfined(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Boolean>()) {

            // attempt to close from thread in scope
            TaskHandle<Boolean> handle = scope.fork(() -> {
                assertThrows(WrongThreadException.class, scope::close);
                return true;
            });

            scope.join();
            assertTrue(handle.get());

            // random thread cannot close scope
            try (var pool = Executors.newCachedThreadPool(factory)) {
                Future<Boolean> future = pool.submit(() -> {
                    assertThrows(WrongThreadException.class, scope::close);
                    return null;
                });
                future.get();
            }
        }
    }

    /**
     * Test close with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptClose1(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            var latch = new CountDownLatch(1);

            // start task that does not respond to interrupt
            scope.fork(() -> {
                boolean done = false;
                while (!done) {
                    try {
                        latch.await();
                        done = true;
                    } catch (InterruptedException e) { }
                }
                return null;
            });

            scope.shutdown();
            scope.join();

            // release task after a delay
            scheduler.schedule(latch::countDown, 100, TimeUnit.MILLISECONDS);

            // invoke close with interrupt status set
            Thread.currentThread().interrupt();
            try {
                scope.close();
            } finally {
                assertTrue(Thread.interrupted());   // clear interrupt status
            }
        }
    }

    /**
     * Test interrupting thread waiting in close.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptClose2(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            var latch = new CountDownLatch(1);

            // start task that does not respond to interrupt
            scope.fork(() -> {
                boolean done = false;
                while (!done) {
                    try {
                        latch.await();
                        done = true;
                    } catch (InterruptedException e) { }
                }
                return null;
            });

            scope.shutdown();
            scope.join();

            // release task after a delay
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            scheduler.schedule(latch::countDown, 3, TimeUnit.SECONDS);
            try {
                scope.close();
            } finally {
                assertTrue(Thread.interrupted());   // clear interrupt status
            }
        }
    }

    /**
     * Test that closing an enclosing scope closes the thread flock of a nested scope.
     */
    @Test
    void testCloseThrowsStructureViolation() throws Exception {
        try (var scope1 = new StructuredTaskScope<Object>()) {
            try (var scope2 = new StructuredTaskScope<Object>()) {

                // join + close enclosing scope
                scope1.join();
                try {
                    scope1.close();
                    fail("close did not throw");
                } catch (StructureViolationException expected) { }

                // underlying flock should be closed, fork should return a cancelled task
                var executed = new AtomicBoolean();
                TaskHandle<Void> handle = scope2.fork(() -> {
                    executed.set(true);
                    return null;
                });
                assertEquals(TaskHandle.State.CANCELLED, handle.state());
                scope2.join();
                assertFalse(executed.get());
            }
        }
    }

    /**
     * A StructuredTaskScope that collects the handles notified to the handleComplete method.
     */
    private static class CollectAll<T> extends StructuredTaskScope<T> {
        private final Set<TaskHandle<T>> handles = ConcurrentHashMap.newKeySet();

        CollectAll(ThreadFactory factory) {
            super(null, factory);
        }

        @Override
        protected void handleComplete(TaskHandle<T> handle) {
            handles.add(handle);
        }

        Set<TaskHandle<T>> handles() {
            return handles;
        }

        TaskHandle<T> find(Callable<T> task) {
            return handles.stream()
                    .filter(h -> task.equals(h.task()))
                    .findAny()
                    .orElseThrow();
        }
    }

    /**
     * Test that handleComplete method is invoked for tasks that complete before shutdown.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testHandleCompleteBeforeShutdown(ThreadFactory factory) throws Exception {
        try (var scope = new CollectAll<String>(factory)) {
            Callable<String> task1 = () -> "foo";
            Callable<String> task2 = () -> { throw new FooException(); };
            scope.fork(task1);
            scope.fork(task2);
            scope.join();

            var handle1  = scope.find(task1);
            assertEquals("foo", handle1.get());

            var handle2 = scope.find(task2);
            assertTrue(handle2.exception() instanceof FooException);
        }
    }

    /**
     * Test that handleComplete method is not invoked for tasks that complete after
     * shutdown.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testHandleCompleteAfterShutdown(ThreadFactory factory) throws Exception {
        try (var scope = new CollectAll<String>(factory)) {
            scope.fork(() -> {
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException ignore) { }
                return "foo";
            });
            scope.shutdown();
            scope.join();
            assertEquals(0, scope.handles().size());
        }
    }

    /**
     * Test that the default handleComplete throws IllegalArgumentException if called
     * with a running or cancelled task.
     */
    @Test
    void testHandleCompleteThrows() throws Exception {
        class TestScope<T> extends StructuredTaskScope<T> {
            protected void handleComplete(TaskHandle<T> handle) {
                super.handleComplete(handle);
            }
        }

        try (var scope = new TestScope<String>()) {
            var handle = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return "foo";
            });

            // running task
            assertEquals(TaskHandle.State.RUNNING, handle.state());
            assertThrows(IllegalArgumentException.class, () -> scope.handleComplete(handle));

            scope.shutdown();

            // cancelled task
            assertEquals(TaskHandle.State.CANCELLED, handle.state());
            assertThrows(IllegalArgumentException.class, () -> scope.handleComplete(handle));

            // null task
            assertThrows(NullPointerException.class, () -> scope.handleComplete(null));

            scope.join();
        }
    }

    /**
     * Test ensureOwnerAndJoined.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testEnsureOwnerAndJoined(ThreadFactory factory) throws Exception {
        class MyScope<T> extends StructuredTaskScope<T> {
            MyScope(ThreadFactory factory) {
                super(null, factory);
            }
            void invokeEnsureOwnerAndJoined() {
                super.ensureOwnerAndJoined();
            }
        }

        try (var scope = new MyScope<Boolean>(factory)) {
            // owner thread, before join
            scope.fork(() -> true);
            assertThrows(IllegalStateException.class, () -> {
                scope.invokeEnsureOwnerAndJoined();
            });

            // owner thread, after join
            scope.join();
            scope.invokeEnsureOwnerAndJoined();

            // thread in scope cannot invoke ensureOwnerAndJoined
            TaskHandle<Boolean> handle = scope.fork(() -> {
                assertThrows(WrongThreadException.class, () -> {
                    scope.invokeEnsureOwnerAndJoined();
                });
                return true;
            });
            scope.join();
            assertTrue(handle.get());

            // random thread cannot invoke ensureOwnerAndJoined
            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<Void> future = pool.submit(() -> {
                    assertThrows(WrongThreadException.class, () -> {
                        scope.invokeEnsureOwnerAndJoined();
                    });
                    return null;
                });
                future.get();
            }
        }
    }

    /**
     * Test toString.
     */
    @Test
    void testToString() throws Exception {
        ThreadFactory factory = Thread.ofVirtual().factory();
        try (var scope = new StructuredTaskScope<Object>("duke", factory)) {
            // open
            assertTrue(scope.toString().contains("duke"));

            // shutdown
            scope.shutdown();
            assertTrue(scope.toString().contains("duke"));

            // closed
            scope.join();
            scope.close();
            assertTrue(scope.toString().contains("duke"));
        }
    }

    /**
     * Test TaskHandle with task that completes successfully.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testTaskHandleWhenSuccess(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<String>(null, factory)) {
            Callable<String> task = () -> "foo";
            TaskHandle<String> handle = scope.fork(task);

            // before join, owner thread
            assertEquals(task, handle.task());
            assertThrows(IllegalStateException.class, handle::get);
            assertThrows(IllegalStateException.class, handle::exception);

            scope.join();

            // after join
            assertEquals(task, handle.task());
            assertEquals(TaskHandle.State.SUCCESS, handle.state());
            assertEquals("foo", handle.get());
            assertThrows(IllegalStateException.class, handle::exception);
        }
    }

    /**
     * Test TaskHandle with task that fails.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testTaskHandleWhenFailed(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<String>(null, factory)) {
            Callable<String> task = () -> { throw new FooException(); };
            TaskHandle<String> handle = scope.fork(task);

            // before join, owner thread
            assertEquals(task, handle.task());
            assertThrows(IllegalStateException.class, handle::get);
            assertThrows(IllegalStateException.class, handle::exception);

            scope.join();

            // after join
            assertEquals(task, handle.task());
            assertEquals(TaskHandle.State.FAILED, handle.state());
            assertThrows(IllegalStateException.class, handle::get);
            assertTrue(handle.exception() instanceof FooException);
        }
    }

    /**
     * Test TaskHandle after join with a task that has not completed.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testTaskHandleWhenNotCompleted(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            Callable<Void> task = () -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            };
            TaskHandle<Void> handle = scope.fork(task);

            // join without waiting
            assertThrows(TimeoutException.class, () -> scope.joinUntil(Instant.now()));

            // not completed
            assertEquals(task, handle.task());
            assertEquals(TaskHandle.State.RUNNING, handle.state());
            assertThrows(IllegalStateException.class, handle::get);
            assertThrows(IllegalStateException.class, handle::exception);
        }
    }

    /**
     * Test TaskHandle when scope shutdown before task completes.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testTaskHandleWhenShutdown(ThreadFactory factory) throws Exception {
        try (var scope = new StructuredTaskScope<Object>(null, factory)) {
            Callable<Void> task = () -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            };
            TaskHandle<Void> handle = scope.fork(task);

            scope.shutdown();
            scope.join();

            // not completed before shutdown
            assertEquals(task, handle.task());
            assertEquals(TaskHandle.State.CANCELLED, handle.state());
            assertThrows(IllegalStateException.class, handle::get);
            assertThrows(IllegalStateException.class, handle::exception);
        }
    }

    /**
     * Test TaskHandle::toString.
     */
    @Test
    void testTaskHandleToString() throws Exception {
        try (var scope = new StructuredTaskScope<Object>()) {
            // success
            var handle1 = scope.fork(() -> "foo");
            scope.join();
            assertTrue(handle1.toString().contains("Completed successfully"));

            // failed
            var handle2 = scope.fork(() -> { throw new FooException(); });
            scope.join();
            assertTrue(handle2.toString().contains("Failed"));

            // not completed
            Callable<Void> sleepForDay = () -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            };
            var handle3 = scope.fork(sleepForDay);
            assertTrue(handle3.toString().contains("Not completed"));

            // cancelled
            scope.shutdown();
            assertTrue(handle3.toString().contains("Cancelled"));
            var handle4 = scope.fork(sleepForDay);
            assertTrue(handle4.toString().contains("Cancelled"));

            scope.join();
        }
    }

    /**
     * Test ShutdownOnSuccess with no completed tasks.
     */
    @Test
    void testShutdownOnSuccess1() throws Exception {
        try (var scope = new ShutdownOnSuccess<Object>()) {
            assertThrows(IllegalStateException.class, () -> scope.result());
            assertThrows(IllegalStateException.class, () -> scope.result(e -> null));
        }
    }

    /**
     * Test ShutdownOnSuccess with tasks that complete successfully.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownOnSuccess2(ThreadFactory factory) throws Exception {
        try (var scope = new ShutdownOnSuccess<String>(null, factory)) {
            scope.fork(() -> "foo");
            scope.join();  // ensures foo completes first
            scope.fork(() -> "bar");
            scope.join();
            assertEquals("foo", scope.result());
            assertEquals("foo", scope.result(e -> null));
        }
    }

    /**
     * Test ShutdownOnSuccess with a task that completes successfully with a null result.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownOnSuccess3(ThreadFactory factory) throws Exception {
        try (var scope = new ShutdownOnSuccess<Object>(null, factory)) {
            scope.fork(() -> null);
            scope.join();
            assertNull(scope.result());
            assertNull(scope.result(e -> null));
        }
    }

    /**
     * Test ShutdownOnSuccess with tasks that complete succcessfully and tasks that fail.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownOnSuccess4(ThreadFactory factory) throws Exception {
        try (var scope = new ShutdownOnSuccess<String>(null, factory)) {
            scope.fork(() -> "foo");
            scope.fork(() -> { throw new ArithmeticException(); });
            scope.join();
            assertEquals("foo", scope.result());
            assertEquals("foo", scope.result(e -> null));
        }
    }

    /**
     * Test ShutdownOnSuccess with a task that fails.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownOnSuccess5(ThreadFactory factory) throws Exception {
        try (var scope = new ShutdownOnSuccess<Object>(null, factory)) {
            scope.fork(() -> { throw new ArithmeticException(); });
            scope.join();
            Throwable ex = assertThrows(ExecutionException.class, () -> scope.result());
            assertTrue(ex.getCause() instanceof  ArithmeticException);
            ex = assertThrows(FooException.class, () -> scope.result(e -> new FooException(e)));
            assertTrue(ex.getCause() instanceof  ArithmeticException);
        }
    }

    /**
     * Test ShutdownOnSuccess methods are confined to the owner.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownOnSuccessConfined(ThreadFactory factory) throws Exception {
        // owner before join
        try (var scope = new ShutdownOnSuccess<Boolean>(null, factory)) {
            scope.fork(() -> { throw new FooException(); });
            assertThrows(IllegalStateException.class, scope::result);
            assertThrows(IllegalStateException.class, () -> {
                scope.result(e -> new RuntimeException(e));
            });
            scope.join();
        }

        // non-owner
        try (var scope = new ShutdownOnSuccess<Boolean>(null, factory)) {
            TaskHandle<Boolean> handle = scope.fork(() -> {
                assertThrows(WrongThreadException.class, scope::result);
                assertThrows(WrongThreadException.class, () -> {
                    scope.result(e -> new RuntimeException(e));
                });
                return true;
            });
            scope.join();
            assertTrue(handle.get());
        }
    }

    /**
     * Test ShutdownOnFailure with no completed tasks.
     */
    @Test
    void testShutdownOnFailure1() throws Throwable {
        try (var scope = new ShutdownOnFailure()) {
            assertTrue(scope.exception().isEmpty());
            scope.throwIfFailed();
            scope.throwIfFailed(e -> new FooException(e));
        }
    }

    /**
     * Test ShutdownOnFailure with tasks that complete successfully.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownOnFailure2(ThreadFactory factory) throws Throwable {
        try (var scope = new ShutdownOnFailure(null, factory)) {
            scope.fork(() -> "foo");
            scope.fork(() -> "bar");
            scope.join();

            // no exception
            assertTrue(scope.exception().isEmpty());
            scope.throwIfFailed();
            scope.throwIfFailed(e -> new FooException(e));
        }
    }

    /**
     * Test ShutdownOnFailure with tasks that complete succcessfully and tasks that fail.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownOnFailure3(ThreadFactory factory) throws Throwable {
        try (var scope = new ShutdownOnFailure(null, factory)) {

            // one task completes successfully, the other fails
            scope.fork(() -> "foo");
            scope.fork(() -> { throw new ArithmeticException(); });
            scope.join();

            Throwable ex = scope.exception().orElse(null);
            assertTrue(ex instanceof ArithmeticException);

            ex = assertThrows(ExecutionException.class, () -> scope.throwIfFailed());
            assertTrue(ex.getCause() instanceof ArithmeticException);

            ex = assertThrows(FooException.class,
                              () -> scope.throwIfFailed(e -> new FooException(e)));
            assertTrue(ex.getCause() instanceof ArithmeticException);
        }
    }

    /**
     * Test ShutdownOnFailure methods are confined to the owner.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testShutdownOnFailureConfined(ThreadFactory factory) throws Exception {
        // owner before join
        try (var scope = new ShutdownOnFailure(null, factory)) {
            scope.fork(() -> "foo");
            assertThrows(IllegalStateException.class, scope::exception);
            assertThrows(IllegalStateException.class, scope::throwIfFailed);
            assertThrows(IllegalStateException.class, () -> {
                scope.throwIfFailed(e -> new RuntimeException(e));
            });
            scope.join();
        }

        // non-owner
        try (var scope = new ShutdownOnFailure(null, factory)) {
            TaskHandle<Boolean> handle = scope.fork(() -> {
                assertThrows(WrongThreadException.class, scope::exception);
                assertThrows(WrongThreadException.class, scope::throwIfFailed);
                assertThrows(WrongThreadException.class, () -> {
                    scope.throwIfFailed(e -> new RuntimeException(e));
                });
                return true;
            });
            scope.join();
            assertTrue(handle.get());
        }
    }

    /**
     * Test for NullPointerException.
     */
    @Test
    void testNulls() throws Exception {
        assertThrows(NullPointerException.class, () -> new StructuredTaskScope("", null));
        try (var scope = new StructuredTaskScope<Object>()) {
            assertThrows(NullPointerException.class, () -> scope.fork(null));
            assertThrows(NullPointerException.class, () -> scope.joinUntil(null));
        }

        assertThrows(NullPointerException.class, () -> new ShutdownOnSuccess<Object>("", null));
        try (var scope = new ShutdownOnSuccess<Object>()) {
            assertThrows(NullPointerException.class, () -> scope.fork(null));
            assertThrows(NullPointerException.class, () -> scope.joinUntil(null));
            assertThrows(NullPointerException.class, () -> scope.result(null));
        }

        assertThrows(NullPointerException.class, () -> new ShutdownOnFailure("", null));
        try (var scope = new ShutdownOnFailure()) {
            assertThrows(NullPointerException.class, () -> scope.fork(null));
            assertThrows(NullPointerException.class, () -> scope.joinUntil(null));
            assertThrows(NullPointerException.class, () -> scope.throwIfFailed(null));
        }
    }

    /**
     * A runtime exception for tests.
     */
    private static class FooException extends RuntimeException {
        FooException() { }
        FooException(Throwable cause) { super(cause); }
    }

    /**
     * Schedules a thread to be interrupted after the given delay.
     */
    private void scheduleInterrupt(Thread thread, Duration delay) {
        long millis = delay.toMillis();
        scheduler.schedule(thread::interrupt, millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the current time in milliseconds.
     */
    private static long millisTime() {
        long now = System.nanoTime();
        return TimeUnit.MILLISECONDS.convert(now, TimeUnit.NANOSECONDS);
    }

    /**
     * Check the duration of a task
     * @param start start time, in milliseconds
     * @param min minimum expected duration, in milliseconds
     * @param max maximum expected duration, in milliseconds
     * @return the duration (now - start), in milliseconds
     */
    private static long expectDuration(long start, long min, long max) {
        long duration = millisTime() - start;
        assertTrue(duration >= min,
                "Duration " + duration + "ms, expected >= " + min + "ms");
        assertTrue(duration <= max,
                "Duration " + duration + "ms, expected <= " + max + "ms");
        return duration;
    }
}
