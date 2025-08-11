/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @summary Test virtual threads using Object.wait/notifyAll
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED MonitorWaitNotify
 */

/*
 * @test id=Xint
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native -Xint --enable-native-access=ALL-UNNAMED MonitorWaitNotify
 */

/*
 * @test id=Xcomp
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native -Xcomp --enable-native-access=ALL-UNNAMED MonitorWaitNotify
 */

/*
 * @test id=Xcomp-TieredStopAtLevel1
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native -Xcomp -XX:TieredStopAtLevel=1 --enable-native-access=ALL-UNNAMED MonitorWaitNotify
 */

/*
 * @test id=Xcomp-noTieredCompilation
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native -Xcomp -XX:-TieredCompilation --enable-native-access=ALL-UNNAMED MonitorWaitNotify
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import jdk.test.lib.thread.VThreadScheduler;
import jdk.test.lib.thread.VThreadRunner;   // ensureParallelism requires jdk.management
import jdk.test.lib.thread.VThreadPinner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class MonitorWaitNotify {

    @BeforeAll
    static void setup() {
        // need >=2 carriers for testing pinning
        VThreadRunner.ensureParallelism(2);
    }

    /**
     * Test virtual thread waits, notified by platform thread.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testWaitNotify1(boolean pinned) throws Exception {
        var lock = new Object();
        var ready = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                try {
                    if (pinned) {
                        VThreadPinner.runPinned(() -> {
                            ready.set(true);
                            lock.wait();
                        });
                    } else {
                        ready.set(true);
                        lock.wait();
                    }
                } catch (InterruptedException e) { }
            }
        });
        awaitTrue(ready);

        // notify, thread should block waiting to reenter
        synchronized (lock) {
            lock.notifyAll();
            await(thread, Thread.State.BLOCKED);
        }
        thread.join();
    }

    /**
     * Test platform thread waits, notified by virtual thread.
     */
    @Test
    void testWaitNotify2() throws Exception {
        var lock = new Object();
        var thread = Thread.ofVirtual().unstarted(() -> {
            synchronized (lock) {
                lock.notifyAll();
            }
        });
        synchronized (lock) {
            thread.start();
            lock.wait();
        }
        thread.join();
    }

    /**
     * Test virtual thread waits, notified by another virtual thread.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testWaitNotify3(boolean pinned) throws Exception {
        var lock = new Object();
        var ready = new AtomicBoolean();
        var thread1 = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                try {
                    if (pinned) {
                        VThreadPinner.runPinned(() -> {
                            ready.set(true);
                            lock.wait();
                        });
                    } else {
                        ready.set(true);
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        var thread2 = Thread.ofVirtual().start(() -> {
            try {
                awaitTrue(ready);

                // notify, thread should block waiting to reenter
                synchronized (lock) {
                    lock.notifyAll();
                    await(thread1, Thread.State.BLOCKED);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread1.join();
        thread2.join();
    }

    /**
     * Test notifyAll when there are no threads waiting.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 30000, Integer.MAX_VALUE })
    void testNotifyBeforeWait(int timeout) throws Exception {
        var lock = new Object();

        // no threads waiting
        synchronized (lock) {
            lock.notifyAll();
        }

        var ready = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                synchronized (lock) {
                    ready.set(true);

                    // thread should wait
                    if (timeout > 0) {
                        lock.wait(timeout);
                    } else {
                        lock.wait();
                    }
                }
            } catch (InterruptedException e) { }
        });

        try {
            // wait for thread to start and wait
            awaitTrue(ready);
            Thread.State expectedState = timeout > 0
                    ? Thread.State.TIMED_WAITING
                    : Thread.State.WAITING;
            await(thread, expectedState);

            // poll thread state again, it should still be waiting
            Thread.sleep(10);
            assertEquals(thread.getState(), expectedState);
        } finally {
            synchronized (lock) {
                lock.notifyAll();
            }
            thread.join();
        }
    }

    /**
     * Returns a stream of elements that are ordered pairs of platform and virtual thread
     * counts. 0,2,4,..8 platform threads. 2,4,6,..16 virtual threads.
     */
    static Stream<Arguments> threadCounts() {
        return IntStream.range(0, 9)
                .filter(i -> i % 2 == 0)
                .mapToObj(i -> i)
                .flatMap(np -> IntStream.range(2, 17)
                        .filter(i -> i % 2 == 0)
                        .mapToObj(vp -> Arguments.of(np, vp)));
    }

    /**
     * Test notify wakes only one thread when platform and virtual threads are waiting.
     */
    @ParameterizedTest
    @MethodSource("threadCounts")
    void testNotifyOneThread(int nPlatformThreads, int nVirtualThreads) throws Exception {
        int nThreads = nPlatformThreads + nVirtualThreads;

        var lock = new Object();
        var ready = new CountDownLatch(nThreads);
        var notified = new AtomicInteger();

        Runnable waitTask = () -> {
            synchronized (lock) {
                try {
                    ready.countDown();
                    lock.wait();
                    notified.incrementAndGet();
                } catch (InterruptedException e) { }
            }
        };

        var threads = new ArrayList<Thread>();
        try {
            for (int i = 0; i < nPlatformThreads; i++) {
                threads.add(Thread.ofPlatform().start(waitTask));
            }
            for (int i = 0; i < nVirtualThreads; i++) {
                threads.add(Thread.ofVirtual().start(waitTask));
            }

            // wait for all threads to wait
            ready.await();

            // wake threads, one by one
            for (int i = 0; i < threads.size(); i++) {

                // wake one thread
                synchronized (lock) {
                    lock.notify();
                }

                // one thread should have awoken
                int expectedWakeups = i + 1;
                while (notified.get() < expectedWakeups) {
                    Thread.sleep(10);
                }
                assertEquals(expectedWakeups, notified.get());
            }
        } finally {
            for (Thread t : threads) {
                t.interrupt();
                t.join();
            }
        }
    }

    /**
     * Test notifyAll wakes all threads.
     */
    @ParameterizedTest
    @MethodSource("threadCounts")
    void testNotifyAllThreads(int nPlatformThreads, int nVirtualThreads) throws Exception {
        int nThreads = nPlatformThreads + nVirtualThreads;

        var lock = new Object();
        var ready = new CountDownLatch(nThreads);
        var notified = new CountDownLatch(nThreads);

        Runnable waitTask = () -> {
            synchronized (lock) {
                try {
                    ready.countDown();
                    lock.wait();
                    notified.countDown();
                } catch (InterruptedException e) { }
            }
        };

        var threads = new ArrayList<Thread>();
        try {
            for (int i = 0; i < nPlatformThreads; i++) {
                threads.add(Thread.ofPlatform().start(waitTask));
            }
            for (int i = 0; i < nVirtualThreads; i++) {
                threads.add(Thread.ofVirtual().start(waitTask));
            }

            // wait for all threads to wait
            ready.await();

            // wakeup all threads
            synchronized (lock) {
                lock.notifyAll();
            }

            // wait for all threads to have awoken
            notified.await();

        } finally {
            for (Thread t : threads) {
                t.interrupt();
                t.join();
            }
        }
    }

    /**
     * Test duration of timed Object.wait.
     */
    @Test
    void testTimedWaitDuration1() throws Exception {
        var lock = new Object();

        var durationRef = new AtomicReference<Long>();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                synchronized (lock) {
                    long start = millisTime();
                    lock.wait(2000);
                    durationRef.set(millisTime() - start);
                }
            } catch (InterruptedException e) { }
        });

        thread.join();

        long duration = durationRef.get();
        checkDuration(duration, 1900, 20_000);
    }

    /**
     * Test duration of timed Object.wait. This test invokes wait twice, first with a short
     * timeout, the second with a longer timeout. The test scenario ensures that the
     * timeout from the first wait doesn't interfere with the second wait.
     */
    @Test
    void testTimedWaitDuration2() throws Exception {
        var lock = new Object();

        var ready = new AtomicBoolean();
        var waited = new AtomicBoolean();
        var durationRef = new AtomicReference<Long>();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                synchronized (lock) {
                    ready.set(true);
                    lock.wait(200);
                    waited.set(true);

                    long start = millisTime();
                    lock.wait(2000);
                    durationRef.set(millisTime() - start);
                }
            } catch (InterruptedException e) { }
        });

        awaitTrue(ready);
        synchronized (lock) {
            // wake thread if waiting in first wait
            if (!waited.get()) {
                lock.notifyAll();
            }
        }

        thread.join();

        long duration = durationRef.get();
        checkDuration(duration, 1900, 20_000);
    }

    /**
     * Testing invoking Object.wait with interrupt status set.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 30000, Integer.MAX_VALUE })
    void testWaitWithInterruptSet(int timeout) throws Exception {
        VThreadRunner.run(() -> {
            Object lock = new Object();
            synchronized (lock) {
                Thread.currentThread().interrupt();
                if (timeout > 0) {
                    assertThrows(InterruptedException.class, () -> lock.wait(timeout));
                } else {
                    assertThrows(InterruptedException.class, lock::wait);
                }
                assertFalse(Thread.currentThread().isInterrupted());
            }
        });
    }

    /**
     * Test interrupting a virtual thread waiting in Object.wait.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 30000, Integer.MAX_VALUE })
    void testInterruptWait(int timeout) throws Exception {
        var lock = new Object();
        var ready = new AtomicBoolean();
        var interruptedException = new AtomicBoolean();
        var vthread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                try {
                    ready.set(true);
                    if (timeout > 0) {
                        lock.wait(timeout);
                    } else {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    // check stack trace has the expected frames
                    Set<String> expected = Set.of("wait0", "wait", "run");
                    Set<String> methods = Stream.of(e.getStackTrace())
                            .map(StackTraceElement::getMethodName)
                            .collect(Collectors.toSet());
                    assertTrue(methods.containsAll(expected));

                    interruptedException.set(true);
                }
            }
        });

        // wait for thread to start and wait
        awaitTrue(ready);
        await(vthread, timeout > 0 ? Thread.State.TIMED_WAITING : Thread.State.WAITING);

        // interrupt thread, should block, then throw InterruptedException
        synchronized (lock) {
            vthread.interrupt();
            await(vthread, Thread.State.BLOCKED);
        }
        vthread.join();
        assertTrue(interruptedException.get());
    }

    /**
     * Test interrupting a virtual thread blocked waiting to reenter after waiting.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 30000, Integer.MAX_VALUE })
    void testInterruptReenterAfterWait(int timeout) throws Exception {
        var lock = new Object();
        var ready = new AtomicBoolean();
        var interruptedException = new AtomicBoolean();
        var vthread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                try {
                    ready.set(true);
                    if (timeout > 0) {
                        lock.wait(timeout);
                    } else {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    interruptedException.set(true);
                }
            }
        });

        // wait for thread to start and wait
        awaitTrue(ready);
        await(vthread, timeout > 0 ? Thread.State.TIMED_WAITING : Thread.State.WAITING);

        // notify, thread should block waiting to reenter
        synchronized (lock) {
            lock.notifyAll();
            await(vthread, Thread.State.BLOCKED);

            // interrupt when blocked
            vthread.interrupt();
        }

        vthread.join();
        assertFalse(interruptedException.get());
        assertTrue(vthread.isInterrupted());
    }

    /**
     * Test Object.wait when the monitor entry count > 1.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 30000, Integer.MAX_VALUE })
    void testWaitWhenEnteredManyTimes(int timeout) throws Exception {
        var lock = new Object();
        var ready = new AtomicBoolean();
        var vthread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                synchronized (lock) {
                    synchronized (lock) {
                        try {
                            ready.set(true);
                            if (timeout > 0) {
                                lock.wait(timeout);
                            } else {
                                lock.wait();
                            }
                        } catch (InterruptedException e) { }
                    }
                }
            }
        });

        // wait for thread to start and wait
        awaitTrue(ready);
        await(vthread, timeout > 0 ? Thread.State.TIMED_WAITING : Thread.State.WAITING);

        // notify, thread should block waiting to reenter
        synchronized (lock) {
            lock.notifyAll();
            await(vthread, Thread.State.BLOCKED);
        }
        vthread.join();
    }

    /**
     * Test that Object.wait does not consume the thread's parking permit.
     */
    @Test
    void testParkingPermitNotConsumed() throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);
        var completed = new AtomicBoolean();
        var vthread = Thread.ofVirtual().start(() -> {
            started.countDown();
            LockSupport.unpark(Thread.currentThread());
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    fail("wait interrupted");
                }
            }
            LockSupport.park();      // should not park
            completed.set(true);
        });

        // wait for thread to start and wait
        started.await();
        await(vthread, Thread.State.WAITING);

        // wakeup thread
        synchronized (lock) {
            lock.notifyAll();
        }

        // thread should terminate
        vthread.join();
        assertTrue(completed.get());
    }

    /**
     * Test that Object.wait does not make available the thread's parking permit.
     */
    @Test
    void testParkingPermitNotOffered() throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);
        var readyToPark = new CountDownLatch(1);
        var completed = new AtomicBoolean();
        var vthread = Thread.ofVirtual().start(() -> {
            started.countDown();
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    fail("wait interrupted");
                }
            }
            readyToPark.countDown();
            LockSupport.park();      // should park
            completed.set(true);
        });

        // wait for thread to start and wait
        started.await();
        await(vthread, Thread.State.WAITING);

        // wakeup thread
        synchronized (lock) {
            lock.notifyAll();
        }

        // thread should park
        readyToPark.await();
        await(vthread, Thread.State.WAITING);

        LockSupport.unpark(vthread);

        // thread should terminate
        vthread.join();
        assertTrue(completed.get());
    }

    /**
     * Test that Object.wait releases the carrier. This test uses a custom scheduler
     * with one carrier thread.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 30000, Integer.MAX_VALUE })
    void testReleaseWhenWaiting1(int timeout) throws Exception {
        assumeTrue(VThreadScheduler.supportsCustomScheduler(), "No support for custom schedulers");
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);

            var lock = new Object();
            var ready = new AtomicBoolean();
            var completed = new AtomicBoolean();

            var vthread1 = factory.newThread(() -> {
                synchronized (lock) {
                    try {
                        ready.set(true);
                        if (timeout > 0) {
                            lock.wait(timeout);
                        } else {
                            lock.wait();
                        }
                    } catch (InterruptedException e) {
                        fail("wait interrupted");
                    }
                }
                completed.set(true);
            });
            vthread1.start();

            // wait for vthread1 to start and wait
            awaitTrue(ready);
            await(vthread1, timeout > 0 ? Thread.State.TIMED_WAITING : Thread.State.WAITING);

            // carrier should be released, use it for another thread
            var executed = new AtomicBoolean();
            var vthread2 = factory.newThread(() -> {
                executed.set(true);
            });
            vthread2.start();
            vthread2.join();
            assertTrue(executed.get());

            // wakeup vthread1
            synchronized (lock) {
                lock.notifyAll();
            }

            vthread1.join();
            assertTrue(completed.get());
        }
    }

    /**
     * Test that Object.wait releases the carrier. This test arranges for 4*ncores - 1
     * virtual threads to wait. For long timeout and no timeout cases, all virtual threads
     * will wait until they are notified.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 10, 20, 100, 500, 30000, Integer.MAX_VALUE })
    void testReleaseWhenWaiting2(int timeout) throws Exception {
        int VTHREAD_COUNT = 4 * Runtime.getRuntime().availableProcessors();
        CountDownLatch latch = new CountDownLatch(VTHREAD_COUNT);
        Object lock = new Object();
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < VTHREAD_COUNT; i++) {
            Thread.ofVirtual().name("vthread-" + i).start(() -> {
                synchronized (lock) {
                    if (counter.incrementAndGet() == VTHREAD_COUNT) {
                        lock.notifyAll();
                    } else {
                        try {
                            if (timeout > 0) {
                                lock.wait(timeout);
                            } else {
                                lock.wait();
                            }
                        } catch (InterruptedException e) {}
                    }
                }
                latch.countDown();
            });
        }
        latch.await();
    }

    /**
     * Test that wait(long) throws IAE when timeout is negative.
     */
    @Test
    void testIllegalArgumentException() throws Exception {
        VThreadRunner.run(() -> {
            Object obj = new Object();
            synchronized (obj) {
                assertThrows(IllegalArgumentException.class, () -> obj.wait(-1L));
                assertThrows(IllegalArgumentException.class, () -> obj.wait(-1000L));
                assertThrows(IllegalArgumentException.class, () -> obj.wait(Long.MIN_VALUE));
            }
        });
    }

    /**
     * Test that wait throws IMSE when not owner.
     */
    @Test
    void testIllegalMonitorStateException() throws Exception {
        VThreadRunner.run(() -> {
            Object obj = new Object();
            assertThrows(IllegalMonitorStateException.class, () -> obj.wait());
            assertThrows(IllegalMonitorStateException.class, () -> obj.wait(0));
            assertThrows(IllegalMonitorStateException.class, () -> obj.wait(1000));
            assertThrows(IllegalMonitorStateException.class, () -> obj.wait(Long.MAX_VALUE));
        });
    }

    /**
     * Waits for the boolean value to become true.
     */
    private static void awaitTrue(AtomicBoolean ref) throws InterruptedException {
        while (!ref.get()) {
            Thread.sleep(20);
        }
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    private void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            assertTrue(state != Thread.State.TERMINATED, "Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }

    /**
     * Returns the current time in milliseconds.
     */
    private static long millisTime() {
        long now = System.nanoTime();
        return TimeUnit.MILLISECONDS.convert(now, TimeUnit.NANOSECONDS);
    }

    /**
     * Check a duration is within expected bounds.
     * @param duration, in milliseconds
     * @param min minimum expected duration, in milliseconds
     * @param max maximum expected duration, in milliseconds
     * @return the duration (now - start), in milliseconds
     */
    private static void checkDuration(long duration, long min, long max) {
        assertTrue(duration >= min,
                "Duration " + duration + "ms, expected >= " + min + "ms");
        assertTrue(duration <= max,
                "Duration " + duration + "ms, expected <= " + max + "ms");
    }
}
