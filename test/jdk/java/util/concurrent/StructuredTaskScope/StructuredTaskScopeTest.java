/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.TimeoutException;
import java.util.concurrent.StructuredTaskScope.Configuration;
import java.util.concurrent.StructuredTaskScope.FailedException;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.StructureViolationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import static java.lang.Thread.State.*;

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
     * Test that fork creates virtual threads when no ThreadFactory is configured.
     */
    @Test
    void testForkCreatesVirtualThread() throws Exception {
        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            for (int i = 0; i < 50; i++) {
                // runnable
                scope.fork(() -> {
                    threads.add(Thread.currentThread());
                });

                // callable
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
     * Test that fork create threads with the configured ThreadFactory.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkUsesThreadFactory(ThreadFactory factory) throws Exception {
        // TheadFactory that keeps reference to all threads it creates
        class RecordingThreadFactory implements ThreadFactory {
            final ThreadFactory delegate;
            final Set<Thread> threads = ConcurrentHashMap.newKeySet();
            RecordingThreadFactory(ThreadFactory delegate) {
                this.delegate = delegate;
            }
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = delegate.newThread(task);
                threads.add(thread);
                return thread;
            }
            Set<Thread> threads() {
                return threads;
            }
        }
        var recordingThreadFactory = new RecordingThreadFactory(factory);
        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(recordingThreadFactory))) {

            for (int i = 0; i < 50; i++) {
                // runnable
                scope.fork(() -> {
                    threads.add(Thread.currentThread());
                });

                // callable
                scope.fork(() -> {
                    threads.add(Thread.currentThread());
                    return null;
                });
            }
            scope.join();
        }
        assertEquals(100, threads.size());
        assertEquals(recordingThreadFactory.threads(), threads);
    }

    /**
     * Test fork method is owner confined.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkConfined(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<Boolean>awaitAll(),
                cf -> cf.withThreadFactory(factory))) {

            // random thread cannot fork
            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<Void> future = pool.submit(() -> {
                    assertThrows(WrongThreadException.class, () -> {
                        scope.fork(() -> null);
                    });
                    return null;
                });
                future.get();
            }

            // subtask cannot fork
            Subtask<Boolean> subtask = scope.fork(() -> {
                assertThrows(WrongThreadException.class, () -> {
                    scope.fork(() -> null);
                });
                return true;
            });
            scope.join();
            assertTrue(subtask.get());
        }
    }

    /**
     * Test fork after join, no subtasks forked before join.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkAfterJoin1(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            scope.join();
            assertThrows(IllegalStateException.class, () -> scope.fork(() -> "bar"));
        }
    }

    /**
     * Test fork after join, subtasks forked before join.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkAfterJoin2(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            scope.fork(() -> "foo");
            scope.join();
            assertThrows(IllegalStateException.class, () -> scope.fork(() -> "bar"));
        }
    }

    /**
     * Test fork after join throws.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkAfterJoinThrows(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            var latch = new CountDownLatch(1);
            var subtask1 = scope.fork(() -> {
                latch.await();
                return "foo";
            });

            // join throws
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, scope::join);

            // fork should throw
            assertThrows(IllegalStateException.class, () -> scope.fork(() -> "bar"));
        }
    }

    /**
     * Test fork after task scope is cancelled. This test uses a custom Joiner to
     * cancel execution.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkAfterCancel2(ThreadFactory factory) throws Exception {
        var countingThreadFactory = new CountingThreadFactory(factory);
        var testJoiner = new CancelAfterOneJoiner<String>();

        try (var scope = StructuredTaskScope.open(testJoiner,
                cf -> cf.withThreadFactory(countingThreadFactory))) {

            // fork subtask, the scope should be cancelled when the subtask completes
            var subtask1 = scope.fork(() -> "foo");
            awaitCancelled(scope);

            assertEquals(1, countingThreadFactory.threadCount());
            assertEquals(1, testJoiner.onForkCount());
            assertEquals(1, testJoiner.onCompleteCount());

            // fork second subtask, it should not run
            var subtask2 = scope.fork(() -> "bar");

            // onFork should be invoked, newThread and onComplete should not be invoked
            assertEquals(1, countingThreadFactory.threadCount());
            assertEquals(2, testJoiner.onForkCount());
            assertEquals(1, testJoiner.onCompleteCount());

            scope.join();

            assertEquals(1, countingThreadFactory.threadCount());
            assertEquals(2, testJoiner.onForkCount());
            assertEquals(1, testJoiner.onCompleteCount());
            assertEquals("foo", subtask1.get());
            assertEquals(Subtask.State.UNAVAILABLE, subtask2.state());
        }
    }

    /**
     * Test fork after task scope is closed.
     */
    @Test
    void testForkAfterClose() {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            scope.close();
            assertThrows(IllegalStateException.class, () -> scope.fork(() -> null));
        }
    }

    /**
     * Test fork with a ThreadFactory that rejects creating a thread.
     */
    @Test
    void testForkRejectedExecutionException() {
        ThreadFactory factory = task -> null;
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            assertThrows(RejectedExecutionException.class, () -> scope.fork(() -> null));
        }
    }

    /**
     * Test join with no subtasks.
     */
    @Test
    void testJoinWithNoSubtasks() throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            scope.join();
        }
    }

    /**
     * Test join with a remaining subtask.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinWithRemainingSubtasks(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            Subtask<String> subtask = scope.fork(() -> {
                Thread.sleep(Duration.ofMillis(100));
                return "foo";
            });
            scope.join();
            assertEquals("foo", subtask.get());
        }
    }

    /**
     * Test join after join completed with a result.
     */
    @Test
    void testJoinAfterJoin1() throws Exception {
        var results = new LinkedTransferQueue<>(List.of("foo", "bar", "baz"));
        Joiner<Object, String> joiner = results::take;
        try (var scope = StructuredTaskScope.open(joiner)) {
            scope.fork(() -> "foo");
            assertEquals("foo", scope.join());

            // join already called
            for (int i = 0 ; i < 3; i++) {
                assertThrows(IllegalStateException.class, scope::join);
            }
        }
    }

    /**
     * Test join after join completed with an exception.
     */
    @Test
    void testJoinAfterJoin2() throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.anySuccessfulResultOrThrow())) {
            scope.fork(() -> { throw new FooException(); });
            Throwable ex = assertThrows(FailedException.class, scope::join);
            assertTrue(ex.getCause() instanceof FooException);

            // join already called
            for (int i = 0 ; i < 3; i++) {
                assertThrows(IllegalStateException.class, scope::join);
            }
        }
    }

    /**
     * Test join after join completed with a timeout.
     */
    @Test
    void testJoinAfterJoin3() throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.anySuccessfulResultOrThrow(),
                cf -> cf.withTimeout(Duration.ofMillis(100)))) {
            // wait for scope to be cancelled by timeout
            awaitCancelled(scope);
            assertThrows(TimeoutException.class, scope::join);

            // join already called
            for (int i = 0 ; i < 3; i++) {
                assertThrows(IllegalStateException.class, scope::join);
            }
        }
    }

    /**
     * Test join method is owner confined.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinConfined(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<Boolean>awaitAll(),
                cf -> cf.withThreadFactory(factory))) {

            // random thread cannot join
            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<Void> future = pool.submit(() -> {
                    assertThrows(WrongThreadException.class, scope::join);
                    return null;
                });
                future.get();
            }

            // subtask cannot join
            Subtask<Boolean> subtask = scope.fork(() -> {
                assertThrows(WrongThreadException.class, () -> { scope.join(); });
                return true;
            });
            scope.join();
            assertTrue(subtask.get());
        }
    }

    /**
     * Test join with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptJoin1(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {

            Subtask<String> subtask = scope.fork(() -> {
                Thread.sleep(60_000);
                return "foo";
            });

            // join should throw
            Thread.currentThread().interrupt();
            try {
                scope.join();
                fail("join did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());   // interrupt status should be cleared
            }
        }
    }

    /**
     * Test interrupt of thread blocked in join.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptJoin2(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {

            var latch = new CountDownLatch(1);
            Subtask<String> subtask = scope.fork(() -> {
                Thread.sleep(60_000);
                return "foo";
            });

            // interrupt main thread when it blocks in join
            scheduleInterruptAt("java.util.concurrent.StructuredTaskScopeImpl.join");
            try {
                scope.join();
                fail("join did not throw");
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());   // interrupt status should be clear
            }
        }
    }

    /**
     * Test join when scope is cancelled.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinWhenCancelled(ThreadFactory factory) throws Exception {
        var countingThreadFactory = new CountingThreadFactory(factory);
        var testJoiner = new CancelAfterOneJoiner<String>();

        try (var scope = StructuredTaskScope.open(testJoiner,
                    cf -> cf.withThreadFactory(countingThreadFactory))) {

            // fork subtask, the scope should be cancelled when the subtask completes
            var subtask1 = scope.fork(() -> "foo");
            awaitCancelled(scope);

            // fork second subtask, it should not run
            var subtask2 = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return "bar";
            });

            scope.join();

            assertEquals("foo", subtask1.get());
            assertEquals(Subtask.State.UNAVAILABLE, subtask2.state());
        }
    }

    /**
     * Test join after scope is closed.
     */
    @Test
    void testJoinAfterClose() throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            scope.close();
            assertThrows(IllegalStateException.class, () -> scope.join());
        }
    }

    /**
     * Test join with timeout, subtasks finish before timeout expires.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinWithTimeout1(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory)
                        .withTimeout(Duration.ofDays(1)))) {

            Subtask<String> subtask = scope.fork(() -> {
                Thread.sleep(Duration.ofSeconds(1));
                return "foo";
            });

            scope.join();

            assertFalse(scope.isCancelled());
            assertEquals("foo", subtask.get());
        }
    }

    /**
     * Test join with timeout, timeout expires before subtasks finish.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinWithTimeout2(ThreadFactory factory) throws Exception {
        long startMillis = millisTime();
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory)
                        .withTimeout(Duration.ofSeconds(2)))) {

            Subtask<Void> subtask = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            });

            assertThrows(TimeoutException.class, scope::join);
            expectDuration(startMillis, /*min*/1900, /*max*/20_000);

            assertTrue(scope.isCancelled());
            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
        }
    }

    /**
     * Test join with timeout that has already expired.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testJoinWithTimeout3(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory)
                        .withTimeout(Duration.ofSeconds(-1)))) {

            Subtask<Void> subtask = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            });

            assertThrows(TimeoutException.class, scope::join);

            assertTrue(scope.isCancelled());
            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
        }
    }

    /**
     * Test that cancelling execution interrupts unfinished threads. This test uses
     * a custom Joiner to cancel execution.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCancelInterruptsThreads2(ThreadFactory factory) throws Exception {
        var testJoiner = new CancelAfterOneJoiner<String>();

        try (var scope = StructuredTaskScope.open(testJoiner,
                cf -> cf.withThreadFactory(factory))) {

            // fork subtask1 that runs for a long time
            var started = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            var subtask1 = scope.fork(() -> {
                started.countDown();
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            });
            started.await();

            // fork subtask2, the scope should be cancelled when the subtask completes
            var subtask2 = scope.fork(() -> "bar");
            awaitCancelled(scope);

            // subtask1 should be interrupted
            interrupted.await();

            scope.join();
            assertEquals(Subtask.State.UNAVAILABLE, subtask1.state());
            assertEquals("bar", subtask2.get());
        }
    }

    /**
     * Test that timeout interrupts unfinished threads.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testTimeoutInterruptsThreads(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory)
                        .withTimeout(Duration.ofSeconds(2)))) {

            var started = new AtomicBoolean();
            var interrupted = new CountDownLatch(1);
            Subtask<Void> subtask = scope.fork(() -> {
                started.set(true);
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
                return null;
            });

            // wait for scope to be cancelled by timeout
            awaitCancelled(scope);

            // if subtask started then it should be interrupted
            if (started.get()) {
                interrupted.await();
            }

            assertThrows(TimeoutException.class, scope::join);

            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
        }
    }

    /**
     * Test close without join, no subtasks forked.
     */
    @Test
    void testCloseWithoutJoin1() {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            // do nothing
        }
    }

    /**
     * Test close without join, subtasks forked.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCloseWithoutJoin2(ThreadFactory factory) {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            Subtask<String> subtask = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            });

            // first call to close should throw
            assertThrows(IllegalStateException.class, scope::close);

            // subsequent calls to close should not throw
            for (int i = 0; i < 3; i++) {
                scope.close();
            }

            // subtask result/exception not available
            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
            assertThrows(IllegalStateException.class, subtask::get);
            assertThrows(IllegalStateException.class, subtask::exception);
        }
    }

    /**
     * Test close after join throws. Close should not throw as join attempted.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCloseAfterJoinThrows(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            var subtask = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            });

            // join throws
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, scope::join);
            assertThrows(IllegalStateException.class, subtask::get);

        }  // close should not throw
    }

    /**
     * Test close method is owner confined.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCloseConfined(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<Boolean>awaitAll(),
                cf -> cf.withThreadFactory(factory))) {

            // random thread cannot close scope
            try (var pool = Executors.newCachedThreadPool(factory)) {
                Future<Boolean> future = pool.submit(() -> {
                    assertThrows(WrongThreadException.class, scope::close);
                    return null;
                });
                future.get();
            }

            // subtask cannot close
            Subtask<Boolean> subtask = scope.fork(() -> {
                assertThrows(WrongThreadException.class, scope::close);
                return true;
            });
            scope.join();
            assertTrue(subtask.get());
        }
    }

    /**
     * Test close with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptClose1(ThreadFactory factory) throws Exception {
        var testJoiner = new CancelAfterOneJoiner<String>();
        try (var scope = StructuredTaskScope.open(testJoiner,
                cf -> cf.withThreadFactory(factory))) {

            // fork first subtask, a straggler as it continues after being interrupted
            var started = new CountDownLatch(1);
            var done = new AtomicBoolean();
            scope.fork(() -> {
                started.countDown();
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    // interrupted by cancel, expected
                }
                Thread.sleep(Duration.ofMillis(100)); // force close to wait
                done.set(true);
                return null;
            });
            started.await();

            // fork second subtask, the scope should be cancelled when this subtask completes
            scope.fork(() -> "bar");
            awaitCancelled(scope);

            scope.join();

            // invoke close with interrupt status set
            Thread.currentThread().interrupt();
            try {
                scope.close();
            } finally {
                assertTrue(Thread.interrupted());   // clear interrupt status
                assertTrue(done.get());
            }
        }
    }

    /**
     * Test interrupting thread waiting in close.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInterruptClose2(ThreadFactory factory) throws Exception {
        var testJoiner = new CancelAfterOneJoiner<String>();
        try (var scope = StructuredTaskScope.open(testJoiner,
                cf -> cf.withThreadFactory(factory))) {

            Thread mainThread = Thread.currentThread();

            // fork first subtask, a straggler as it continues after being interrupted
            var started = new CountDownLatch(1);
            var done = new AtomicBoolean();
            scope.fork(() -> {
                started.countDown();
                try {
                    Thread.sleep(Duration.ofDays(1));
                } catch (InterruptedException e) {
                    // interrupted by cancel, expected
                }

                // interrupt main thread when it blocks in close
                interruptThreadAt(mainThread, "java.util.concurrent.StructuredTaskScopeImpl.close");

                Thread.sleep(Duration.ofMillis(100)); // force close to wait
                done.set(true);
                return null;
            });
            started.await();

            // fork second subtask, the scope should be cancelled when this subtask completes
            scope.fork(() -> "bar");
            awaitCancelled(scope);

            scope.join();

            // main thread will be interrupted while blocked in close
            try {
                scope.close();
            } finally {
                assertTrue(Thread.interrupted());   // clear interrupt status
                assertTrue(done.get());
            }
        }
    }

    /**
     * Test that closing an enclosing scope closes the thread flock of a nested scope.
     */
    @Test
    void testCloseThrowsStructureViolation() throws Exception {
        try (var scope1 = StructuredTaskScope.open(Joiner.awaitAll())) {
            try (var scope2 = StructuredTaskScope.open(Joiner.awaitAll())) {

                // close enclosing scope
                try {
                    scope1.close();
                    fail("close did not throw");
                } catch (StructureViolationException expected) { }

                // underlying flock should be closed
                var executed = new AtomicBoolean();
                Subtask<?> subtask = scope2.fork(() -> executed.set(true));
                assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
                scope2.join();
                assertFalse(executed.get());
                assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
            }
        }
    }

    /**
     * Test that isCancelled returns true after close.
     */
    @Test
    void testIsCancelledAfterClose() throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            assertFalse(scope.isCancelled());
            scope.close();
            assertTrue(scope.isCancelled());
        }
    }

    /**
     * Test Joiner.onFork throwing exception.
     */
    @Test
    void testOnForkThrows() throws Exception {
        var joiner = new Joiner<String, Void>() {
            @Override
            public boolean onFork(Subtask<? extends String> subtask) {
                throw new FooException();
            }
            @Override
            public Void result() {
                return null;
            }
        };
        try (var scope = StructuredTaskScope.open(joiner)) {
            assertThrows(FooException.class, () -> scope.fork(() -> "foo"));
        }
    }

    /**
     * Test Joiner.onFork returning true to cancel execution.
     */
    @Test
    void testOnForkCancelsExecution() throws Exception {
        var joiner = new Joiner<String, Void>() {
            @Override
            public boolean onFork(Subtask<? extends String> subtask) {
                return true;
            }
            @Override
            public Void result() {
                return null;
            }
        };
        try (var scope = StructuredTaskScope.open(joiner)) {
            assertFalse(scope.isCancelled());
            scope.fork(() -> "foo");
            assertTrue(scope.isCancelled());
            scope.join();
        }
    }

    /**
     * Test Joiner.onComplete throwing exception causes UHE to be invoked.
     */
    @Test
    void testOnCompleteThrows() throws Exception {
        var joiner = new Joiner<String, Void>() {
            @Override
            public boolean onComplete(Subtask<? extends String> subtask) {
                throw new FooException();
            }
            @Override
            public Void result() {
                return null;
            }
        };
        var excRef = new AtomicReference<Throwable>();
        Thread.UncaughtExceptionHandler uhe = (t, e) -> excRef.set(e);
        ThreadFactory factory = Thread.ofVirtual()
                .uncaughtExceptionHandler(uhe)
                .factory();
        try (var scope = StructuredTaskScope.open(joiner, cf -> cf.withThreadFactory(factory))) {
            scope.fork(() -> "foo");
            scope.join();
            assertInstanceOf(FooException.class, excRef.get());
        }
    }

    /**
     * Test Joiner.onComplete returning true to cancel execution.
     */
    @Test
    void testOnCompleteCancelsExecution() throws Exception {
        var joiner = new Joiner<String, Void>() {
            @Override
            public boolean onComplete(Subtask<? extends String> subtask) {
                return true;
            }
            @Override
            public Void result() {
                return null;
            }
        };
        try (var scope = StructuredTaskScope.open(joiner)) {
            assertFalse(scope.isCancelled());
            scope.fork(() -> "foo");
            awaitCancelled(scope);
            scope.join();
        }
    }

    /**
     * Test toString.
     */
    @Test
    void testToString() throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withName("duke"))) {

            // open
            assertTrue(scope.toString().contains("duke"));

            // closed
            scope.close();
            assertTrue(scope.toString().contains("duke"));
        }
    }

    /**
     * Test Subtask with task that completes successfully.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testSubtaskWhenSuccess(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<String>awaitAll(),
                cf -> cf.withThreadFactory(factory))) {

            Subtask<String> subtask = scope.fork(() -> "foo");

            // before join
            assertThrows(IllegalStateException.class, subtask::get);
            assertThrows(IllegalStateException.class, subtask::exception);

            scope.join();

            // after join
            assertEquals(Subtask.State.SUCCESS, subtask.state());
            assertEquals("foo", subtask.get());
            assertThrows(IllegalStateException.class, subtask::exception);
        }
    }

    /**
     * Test Subtask with task that fails.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testSubtaskWhenFailed(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<String>awaitAll(),
                cf -> cf.withThreadFactory(factory))) {

            Subtask<String> subtask = scope.fork(() -> { throw new FooException(); });

            // before join
            assertThrows(IllegalStateException.class, subtask::get);
            assertThrows(IllegalStateException.class, subtask::exception);

            scope.join();

            // after join
            assertEquals(Subtask.State.FAILED, subtask.state());
            assertThrows(IllegalStateException.class, subtask::get);
            assertTrue(subtask.exception() instanceof FooException);
        }
    }

    /**
     * Test Subtask with a task that has not completed.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testSubtaskWhenNotCompleted(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            Subtask<Void> subtask = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return null;
            });

            // before join
            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
            assertThrows(IllegalStateException.class, subtask::get);
            assertThrows(IllegalStateException.class, subtask::exception);

            // attempt join, join throws
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, scope::join);

            // after join
            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
            assertThrows(IllegalStateException.class, subtask::get);
            assertThrows(IllegalStateException.class, subtask::exception);
        }
    }

    /**
     * Test Subtask forked after execution cancelled.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testSubtaskWhenCancelled(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(new CancelAfterOneJoiner<String>())) {
            scope.fork(() -> "foo");
            awaitCancelled(scope);

            var subtask = scope.fork(() -> "foo");

            // before join
            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
            assertThrows(IllegalStateException.class, subtask::get);
            assertThrows(IllegalStateException.class, subtask::exception);

            scope.join();

            // after join
            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
            assertThrows(IllegalStateException.class, subtask::get);
            assertThrows(IllegalStateException.class, subtask::exception);
        }
    }

    /**
     * Test Subtask::toString.
     */
    @Test
    void testSubtaskToString() throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            var latch = new CountDownLatch(1);
            var subtask1 = scope.fork(() -> {
                latch.await();
                return "foo";
            });
            var subtask2 = scope.fork(() -> { throw new FooException(); });

            // subtask1 result is unavailable
            assertTrue(subtask1.toString().contains("Unavailable"));
            latch.countDown();

            scope.join();

            assertTrue(subtask1.toString().contains("Completed successfully"));
            assertTrue(subtask2.toString().contains("Failed"));
        }
    }

    /**
     * Test Joiner.allSuccessfulOrThrow() with no subtasks.
     */
    @Test
    void testAllSuccessfulOrThrow1() throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.allSuccessfulOrThrow())) {
            var subtasks = scope.join().toList();
            assertTrue(subtasks.isEmpty());
        }
    }

    /**
     * Test Joiner.allSuccessfulOrThrow() with subtasks that complete successfully.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAllSuccessfulOrThrow2(ThreadFactory factory) throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow(),
                cf -> cf.withThreadFactory(factory))) {
            var subtask1 = scope.fork(() -> "foo");
            var subtask2 = scope.fork(() -> "bar");
            var subtasks = scope.join().toList();
            assertEquals(List.of(subtask1, subtask2), subtasks);
            assertEquals("foo", subtask1.get());
            assertEquals("bar", subtask2.get());
        }
    }

    /**
     * Test Joiner.allSuccessfulOrThrow() with a subtask that complete successfully and
     * a subtask that fails.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAllSuccessfulOrThrow3(ThreadFactory factory) throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow(),
                cf -> cf.withThreadFactory(factory))) {
            scope.fork(() -> "foo");
            scope.fork(() -> { throw new FooException(); });
            try {
                scope.join();
            } catch (FailedException e) {
                assertTrue(e.getCause() instanceof FooException);
            }
        }
    }

    /**
     * Test Joiner.anySuccessfulResultOrThrow() with no subtasks.
     */
    @Test
    void testAnySuccessfulResultOrThrow1() throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.anySuccessfulResultOrThrow())) {
            try {
                scope.join();
            } catch (FailedException e) {
                assertTrue(e.getCause() instanceof NoSuchElementException);
            }
        }
    }

    /**
     * Test Joiner.anySuccessfulResultOrThrow() with a subtask that completes successfully.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAnySuccessfulResultOrThrow2(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<String>anySuccessfulResultOrThrow(),
                cf -> cf.withThreadFactory(factory))) {
            scope.fork(() -> "foo");
            String result = scope.join();
            assertEquals("foo", result);
        }
    }

    /**
     * Test Joiner.anySuccessfulResultOrThrow() with a subtask that completes successfully
     * with a null result.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAnySuccessfulResultOrThrow3(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<String>anySuccessfulResultOrThrow(),
                cf -> cf.withThreadFactory(factory))) {
            scope.fork(() -> null);
            String result = scope.join();
            assertNull(result);
        }
    }

    /**
     * Test Joiner.anySuccessfulResultOrThrow() with a subtask that complete succcessfully
     * and a subtask that fails.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAnySuccessfulResultOrThrow4(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<String>anySuccessfulResultOrThrow(),
                cf -> cf.withThreadFactory(factory))) {
            scope.fork(() -> "foo");
            scope.fork(() -> { throw new FooException(); });
            String first = scope.join();
            assertEquals("foo", first);
        }
    }

    /**
     * Test Joiner.anySuccessfulResultOrThrow() with a subtask that fails.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAnySuccessfulResultOrThrow5(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.anySuccessfulResultOrThrow(),
                cf -> cf.withThreadFactory(factory))) {
            scope.fork(() -> { throw new FooException(); });
            Throwable ex = assertThrows(FailedException.class, scope::join);
            assertTrue(ex.getCause() instanceof FooException);
        }
    }

    /**
     * Test Joiner.awaitAllSuccessfulOrThrow() with no subtasks.
     */
    @Test
    void testAwaitSuccessfulOrThrow1() throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
            var result = scope.join();
            assertNull(result);
        }
    }

    /**
     * Test Joiner.awaitAllSuccessfulOrThrow() with subtasks that complete successfully.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAwaitSuccessfulOrThrow2(ThreadFactory factory) throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.<String>awaitAllSuccessfulOrThrow(),
                cf -> cf.withThreadFactory(factory))) {
            var subtask1 = scope.fork(() -> "foo");
            var subtask2 = scope.fork(() -> "bar");
            var result = scope.join();
            assertNull(result);
            assertEquals("foo", subtask1.get());
            assertEquals("bar", subtask2.get());
        }
    }

    /**
     * Test Joiner.awaitAllSuccessfulOrThrow() with a subtask that complete successfully and
     * a subtask that fails.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAwaitSuccessfulOrThrow3(ThreadFactory factory) throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.<String>awaitAllSuccessfulOrThrow(),
                cf -> cf.withThreadFactory(factory))) {
            scope.fork(() -> "foo");
            scope.fork(() -> { throw new FooException(); });
            try {
                scope.join();
            } catch (FailedException e) {
                assertTrue(e.getCause() instanceof FooException);
            }
        }
    }

    /**
     * Test Joiner.awaitAll() with no subtasks.
     */
    @Test
    void testAwaitAll1() throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            var result = scope.join();
            assertNull(result);
        }
    }

    /**
     * Test Joiner.awaitAll() with subtasks that complete successfully.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAwaitAll2(ThreadFactory factory) throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.<String>awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            var subtask1 = scope.fork(() -> "foo");
            var subtask2 = scope.fork(() -> "bar");
            var result = scope.join();
            assertNull(result);
            assertEquals("foo", subtask1.get());
            assertEquals("bar", subtask2.get());
        }
    }

    /**
     * Test Joiner.awaitAll() with a subtask that complete successfully and a subtask
     * that fails.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAwaitAll3(ThreadFactory factory) throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.<String>awaitAll(),
                cf -> cf.withThreadFactory(factory))) {
            var subtask1 = scope.fork(() -> "foo");
            var subtask2 = scope.fork(() -> { throw new FooException(); });
            var result = scope.join();
            assertNull(result);
            assertEquals("foo", subtask1.get());
            assertTrue(subtask2.exception() instanceof FooException);
        }
    }

    /**
     * Test Joiner.allUntil(Predicate) with no subtasks.
     */
    @Test
    void testAllUntil1() throws Throwable {
        try (var scope = StructuredTaskScope.open(Joiner.allUntil(s -> false))) {
            var subtasks = scope.join();
            assertEquals(0, subtasks.count());
        }
    }

    /**
     * Test Joiner.allUntil(Predicate) with no cancellation.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAllUntil2(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<String>allUntil(s -> false),
                cf -> cf.withThreadFactory(factory))) {

            var subtask1 = scope.fork(() -> "foo");
            var subtask2 = scope.fork(() -> { throw new FooException(); });

            var subtasks = scope.join().toList();
            assertEquals(2, subtasks.size());

            assertSame(subtask1, subtasks.get(0));
            assertSame(subtask2, subtasks.get(1));
            assertEquals("foo", subtask1.get());
            assertTrue(subtask2.exception() instanceof FooException);
        }
    }

    /**
     * Test Joiner.allUntil(Predicate) with cancellation after one subtask completes.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAllUntil3(ThreadFactory factory) throws Exception {
        try (var scope = StructuredTaskScope.open(Joiner.<String>allUntil(s -> true),
                cf -> cf.withThreadFactory(factory))) {

            var subtask1 = scope.fork(() -> "foo");
            var subtask2 = scope.fork(() -> {
                Thread.sleep(Duration.ofDays(1));
                return "bar";
            });

            var subtasks = scope.join().toList();

            assertEquals(2, subtasks.size());
            assertSame(subtask1, subtasks.get(0));
            assertSame(subtask2, subtasks.get(1));
            assertEquals("foo", subtask1.get());
            assertEquals(Subtask.State.UNAVAILABLE, subtask2.state());
        }
    }

    /**
     * Test Joiner.allUntil(Predicate) with cancellation after serveral subtasks complete.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testAllUntil4(ThreadFactory factory) throws Exception {

        // cancel execution after two or more failures
        class CancelAfterTwoFailures<T> implements Predicate<Subtask<? extends T>> {
            final AtomicInteger failedCount = new AtomicInteger();
            @Override
            public boolean test(Subtask<? extends T> subtask) {
                return subtask.state() == Subtask.State.FAILED
                        && failedCount.incrementAndGet() >= 2;
            }
        }
        var joiner = Joiner.allUntil(new CancelAfterTwoFailures<String>());

        try (var scope = StructuredTaskScope.open(joiner)) {
            int forkCount = 0;

            // fork subtasks until execution cancelled
            while (!scope.isCancelled()) {
                scope.fork(() -> "foo");
                scope.fork(() -> { throw new FooException(); });
                forkCount += 2;
                Thread.sleep(Duration.ofMillis(20));
            }

            var subtasks = scope.join().toList();
            assertEquals(forkCount, subtasks.size());

            long failedCount = subtasks.stream()
                    .filter(s -> s.state() == Subtask.State.FAILED)
                    .count();
            assertTrue(failedCount >= 2);
        }
    }

    /**
     * Test Test Joiner.allUntil(Predicate) where the Predicate's test method throws.
     */
    @Test
    void testAllUntil5() throws Exception {
        var joiner = Joiner.allUntil(_ -> { throw new FooException(); });
        var excRef = new AtomicReference<Throwable>();
        Thread.UncaughtExceptionHandler uhe = (t, e) -> excRef.set(e);
        ThreadFactory factory = Thread.ofVirtual()
                .uncaughtExceptionHandler(uhe)
                .factory();
        try (var scope = StructuredTaskScope.open(joiner, cf -> cf.withThreadFactory(factory))) {
            scope.fork(() -> "foo");
            scope.join();
            assertInstanceOf(FooException.class, excRef.get());
        }
    }

    /**
     * Test Joiner default methods.
     */
    @Test
    void testJoinerDefaultMethods() throws Exception {
        try (var scope = StructuredTaskScope.open(new CancelAfterOneJoiner<String>())) {

            // need subtasks to test default methods
            var subtask1 = scope.fork(() -> "foo");
            awaitCancelled(scope);
            var subtask2 = scope.fork(() -> "bar");
            scope.join();

            assertEquals(Subtask.State.SUCCESS, subtask1.state());
            assertEquals(Subtask.State.UNAVAILABLE, subtask2.state());

            // Joiner that does not override default methods
            Joiner<Object, Void> joiner = () -> null;
            assertThrows(NullPointerException.class, () -> joiner.onFork(null));
            assertThrows(NullPointerException.class, () -> joiner.onComplete(null));
            assertThrows(IllegalArgumentException.class, () -> joiner.onFork(subtask1));
            assertFalse(joiner.onFork(subtask2));
            assertFalse(joiner.onComplete(subtask1));
            assertThrows(IllegalArgumentException.class, () -> joiner.onComplete(subtask2));
        }
    }

    /**
     * Test Joiners onFork/onComplete methods with a subtask in an unexpected state.
     */
    @Test
    void testJoinersWithUnavailableResult() throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            var done = new CountDownLatch(1);
            var subtask = scope.fork(() -> {
                done.await();
                return null;
            });

            // onComplete with uncompleted task should throw IAE
            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.allSuccessfulOrThrow().onComplete(subtask));
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.anySuccessfulResultOrThrow().onComplete(subtask));
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.awaitAllSuccessfulOrThrow().onComplete(subtask));
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.awaitAll().onComplete(subtask));
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.allUntil(_ -> false).onComplete(subtask));

            done.countDown();
            scope.join();

            // onFork with completed task should throw IAE
            assertEquals(Subtask.State.SUCCESS, subtask.state());
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.allSuccessfulOrThrow().onFork(subtask));
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.anySuccessfulResultOrThrow().onFork(subtask));
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.awaitAllSuccessfulOrThrow().onFork(subtask));
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.awaitAll().onFork(subtask));
            assertThrows(IllegalArgumentException.class,
                    () -> Joiner.allUntil(_ -> false).onFork(subtask));
        }

    }

    /**
     * Test the Configuration function apply method throwing an exception.
     */
    @Test
    void testConfigFunctionThrows() throws Exception {
        assertThrows(FooException.class,
                () -> StructuredTaskScope.open(Joiner.awaitAll(),
                                               cf -> { throw new FooException(); }));
    }

    /**
     * Test Configuration equals/hashCode/toString
     */
    @Test
    void testConfigMethods() throws Exception {
        Function<Configuration, Configuration> testConfig = cf -> {
            var name = "duke";
            var threadFactory = Thread.ofPlatform().factory();
            var timeout = Duration.ofSeconds(10);

            assertEquals(cf, cf);
            assertEquals(cf.withName(name), cf.withName(name));
            assertEquals(cf.withThreadFactory(threadFactory), cf.withThreadFactory(threadFactory));
            assertEquals(cf.withTimeout(timeout), cf.withTimeout(timeout));

            assertNotEquals(cf, cf.withName(name));
            assertNotEquals(cf, cf.withThreadFactory(threadFactory));
            assertNotEquals(cf, cf.withTimeout(timeout));

            assertEquals(cf.withName(name).hashCode(), cf.withName(name).hashCode());
            assertEquals(cf.withThreadFactory(threadFactory).hashCode(),
                    cf.withThreadFactory(threadFactory).hashCode());
            assertEquals(cf.withTimeout(timeout).hashCode(), cf.withTimeout(timeout).hashCode());

            assertTrue(cf.withName(name).toString().contains(name));
            assertTrue(cf.withThreadFactory(threadFactory).toString().contains(threadFactory.toString()));
            assertTrue(cf.withTimeout(timeout).toString().contains(timeout.toString()));

            return cf;
        };
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(), testConfig)) {
            // do nothing
        }
    }

    /**
     * Test for NullPointerException.
     */
    @Test
    void testNulls() throws Exception {
        assertThrows(NullPointerException.class,
                () -> StructuredTaskScope.open(null));
        assertThrows(NullPointerException.class,
                () -> StructuredTaskScope.open(null, cf -> cf));
        assertThrows(NullPointerException.class,
                () -> StructuredTaskScope.open(Joiner.awaitAll(), null));

        assertThrows(NullPointerException.class, () -> Joiner.allUntil(null));

        // fork
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            assertThrows(NullPointerException.class, () -> scope.fork((Callable<Object>) null));
            assertThrows(NullPointerException.class, () -> scope.fork((Runnable) null));
        }

        // Configuration and withXXX methods
        assertThrows(NullPointerException.class,
                () -> StructuredTaskScope.open(Joiner.awaitAll(), cf -> null));
        assertThrows(NullPointerException.class,
                () -> StructuredTaskScope.open(Joiner.awaitAll(), cf -> cf.withName(null)));
        assertThrows(NullPointerException.class,
                () -> StructuredTaskScope.open(Joiner.awaitAll(), cf -> cf.withThreadFactory(null)));
        assertThrows(NullPointerException.class,
                () -> StructuredTaskScope.open(Joiner.awaitAll(), cf -> cf.withTimeout(null)));

        // Joiner.onFork/onComplete
        assertThrows(NullPointerException.class,
                () -> Joiner.awaitAllSuccessfulOrThrow().onFork(null));
        assertThrows(NullPointerException.class,
                () -> Joiner.awaitAllSuccessfulOrThrow().onComplete(null));
        assertThrows(NullPointerException.class,
                () -> Joiner.awaitAll().onFork(null));
        assertThrows(NullPointerException.class,
                () -> Joiner.awaitAll().onComplete(null));
        assertThrows(NullPointerException.class,
                () -> Joiner.allSuccessfulOrThrow().onFork(null));
        assertThrows(NullPointerException.class,
                () -> Joiner.allSuccessfulOrThrow().onComplete(null));
        assertThrows(NullPointerException.class,
                () -> Joiner.anySuccessfulResultOrThrow().onFork(null));
        assertThrows(NullPointerException.class,
                () -> Joiner.anySuccessfulResultOrThrow().onComplete(null));
    }

    /**
     * ThreadFactory that counts usage.
     */
    private static class CountingThreadFactory implements ThreadFactory {
        final ThreadFactory delegate;
        final AtomicInteger threadCount = new AtomicInteger();
        CountingThreadFactory(ThreadFactory delegate) {
            this.delegate = delegate;
        }
        @Override
        public Thread newThread(Runnable task) {
            threadCount.incrementAndGet();
            return delegate.newThread(task);
        }
        int threadCount() {
            return threadCount.get();
        }
    }

    /**
     * A joiner that counts that counts the number of subtasks that are forked and the
     * number of subtasks that complete.
     */
    private static class CountingJoiner<T> implements Joiner<T, Void> {
        final AtomicInteger onForkCount = new AtomicInteger();
        final AtomicInteger onCompleteCount = new AtomicInteger();
        @Override
        public boolean onFork(Subtask<? extends T> subtask) {
            onForkCount.incrementAndGet();
            return false;
        }
        @Override
        public boolean onComplete(Subtask<? extends T> subtask) {
            onCompleteCount.incrementAndGet();
            return false;
        }
        @Override
        public Void result() {
            return null;
        }
        int onForkCount() {
            return onForkCount.get();
        }
        int onCompleteCount() {
            return onCompleteCount.get();
        }
    }

    /**
     * A joiner that cancels execution when a subtask completes. It also keeps a count
     * of the number of subtasks that are forked and the number of subtasks that complete.
     */
    private static class CancelAfterOneJoiner<T> implements Joiner<T, Void> {
        final AtomicInteger onForkCount = new AtomicInteger();
        final AtomicInteger onCompleteCount = new AtomicInteger();
        @Override
        public boolean onFork(Subtask<? extends T> subtask) {
            onForkCount.incrementAndGet();
            return false;
        }
        @Override
        public boolean onComplete(Subtask<? extends T> subtask) {
            onCompleteCount.incrementAndGet();
            return true;
        }
        @Override
        public Void result() {
            return null;
        }
        int onForkCount() {
            return onForkCount.get();
        }
        int onCompleteCount() {
            return onCompleteCount.get();
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
     * Returns the current time in milliseconds.
     */
    private long millisTime() {
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
    private long expectDuration(long start, long min, long max) {
        long duration = millisTime() - start;
        assertTrue(duration >= min,
                "Duration " + duration + "ms, expected >= " + min + "ms");
        assertTrue(duration <= max,
                "Duration " + duration + "ms, expected <= " + max + "ms");
        return duration;
    }

    /**
     * Wait for the given scope to be cancelled.
     */
    private static void awaitCancelled(StructuredTaskScope<?, ?> scope) throws InterruptedException {
        while (!scope.isCancelled()) {
            Thread.sleep(Duration.ofMillis(20));
        }
    }

    /**
     * Interrupts a thread when it waits (timed or untimed) at location "{@code c.m}".
     * {@code c} is the fully qualified class name and {@code m} is the method name.
     */
    private void interruptThreadAt(Thread target, String location) throws InterruptedException {
        int index = location.lastIndexOf('.');
        String className = location.substring(0, index);
        String methodName = location.substring(index + 1);

        boolean found = false;
        while (!found) {
            Thread.State state = target.getState();
            assertTrue(state != TERMINATED);
            if ((state == WAITING || state == TIMED_WAITING)
                    && contains(target.getStackTrace(), className, methodName)) {
                found = true;
            } else {
                Thread.sleep(20);
            }
        }
        target.interrupt();
    }

    /**
     * Schedules the current thread to be interrupted when it waits (timed or untimed)
     * at the given location.
     */
    private void scheduleInterruptAt(String location) {
        Thread target = Thread.currentThread();
        scheduler.submit(() -> {
            interruptThreadAt(target, location);
            return null;
        });
    }

    /**
     * Returns true if the given stack trace contains an element for the given class
     * and method name.
     */
    private boolean contains(StackTraceElement[] stack, String className, String methodName) {
        return Arrays.stream(stack)
                .anyMatch(e -> className.equals(e.getClassName())
                        && methodName.equals(e.getMethodName()));
    }
}
