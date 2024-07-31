/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test virtual thread with monitor enter/exit
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit/othervm --enable-native-access=ALL-UNNAMED MonitorEnterExit
 */

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jdk.test.lib.thread.VThreadPinner;
import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.condition.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class MonitorEnterExit {
    static final int MAX_ENTER_DEPTH = 256;

    @BeforeAll
    static void setup() {
        // need >=2 carriers for testing pinning when main thread is a virtual thread
        if (Thread.currentThread().isVirtual()) {
            VThreadRunner.ensureParallelism(2);
        }
    }

    /**
     * Test monitor enter with no contention.
     */
    @Test
    void testEnterNoContention() throws Exception {
        var lock = new Object();
        VThreadRunner.run(() -> {
            synchronized (lock) {
                assertTrue(Thread.holdsLock(lock));
            }
            assertFalse(Thread.holdsLock(lock));
        });
    }

    /**
     * Test monitor enter with contention, monitor is held by platform thread.
     */
    @Test
    void testEnterWhenHeldByPlatformThread() throws Exception {
        testEnterWithContention();
    }

    /**
     * Test monitor enter with contention, monitor is held by virtual thread.
     */
    @Test
    void testEnterWhenHeldByVirtualThread() throws Exception {
        VThreadRunner.run(this::testEnterWithContention);
    }

    /**
     * Test monitor enter with contention, monitor will be held by caller thread.
     */
    private void testEnterWithContention() throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);
        var entered = new AtomicBoolean();
        var vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            synchronized (lock) {
                assertTrue(Thread.holdsLock(lock));
                entered.set(true);
            }
            assertFalse(Thread.holdsLock(lock));
        });
        try {
            synchronized (lock) {
                vthread.start();

                // wait for thread to start and block
                started.await();
                await(vthread, Thread.State.BLOCKED);

                assertFalse(entered.get());
            }
        } finally {
            vthread.join();
        }
        assertTrue(entered.get());
    }

    /**
     * Test monitor reenter.
     */
    @Test
    void testReenter() throws Exception {
        var lock = new Object();
        VThreadRunner.run(() -> {
            testReenter(lock, 0);
            assertFalse(Thread.holdsLock(lock));
        });
    }

    private void testReenter(Object lock, int depth) {
        if (depth < MAX_ENTER_DEPTH) {
            synchronized (lock) {
                assertTrue(Thread.holdsLock(lock));
                testReenter(lock, depth + 1);
                assertTrue(Thread.holdsLock(lock));
            }
        }
    }

    /**
     * Test monitor enter when pinned.
     */
    @Test
    void testEnterWhenPinned() throws Exception {
        var lock = new Object();
        VThreadPinner.runPinned(() -> {
            synchronized (lock) {
                assertTrue(Thread.holdsLock(lock));
            }
            assertFalse(Thread.holdsLock(lock));
        });
    }

    /**
     * Test monitor reenter when pinned.
     */
    @Test
    void testReenterWhenPinned() throws Exception {
        VThreadRunner.run(() -> {
            var lock = new Object();
            synchronized (lock) {
                VThreadPinner.runPinned(() -> {
                    assertTrue(Thread.holdsLock(lock));
                    synchronized (lock) {
                        assertTrue(Thread.holdsLock(lock));
                    }
                    assertTrue(Thread.holdsLock(lock));
                });
            }
            assertFalse(Thread.holdsLock(lock));
        });
    }

    /**
     * Test contended monitor enter when pinned. Monitor is held by platform thread.
     */
    @Test
    void testContendedEnterWhenPinnedHeldByPlatformThread() throws Exception {
        testEnterWithContentionWhenPinned();
    }

    /**
     * Test contended monitor enter when pinned. Monitor is held by virtual thread.
     */
    @Test
    void testContendedEnterWhenPinnedHeldByVirtualThread() throws Exception {
        // need at least two carrier threads
        int previousParallelism = VThreadRunner.ensureParallelism(2);
        try {
            VThreadRunner.run(this::testEnterWithContentionWhenPinned);
        } finally {
            VThreadRunner.setParallelism(previousParallelism);
        }
    }

    /**
     * Test contended monitor enter when pinned, monitor will be held by caller thread.
     */
    private void testEnterWithContentionWhenPinned() throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);
        var entered = new AtomicBoolean();
        Thread vthread = Thread.ofVirtual().unstarted(() -> {
            VThreadPinner.runPinned(() -> {
                started.countDown();
                synchronized (lock) {
                    entered.set(true);
                }
            });
        });
        synchronized (lock) {
            // start thread and wait for it to block
            vthread.start();
            started.await();
            await(vthread, Thread.State.BLOCKED);
            assertFalse(entered.get());
        }
        vthread.join();

        // check thread entered monitor
        assertTrue(entered.get());
    }

    /**
     * Returns a stream of elements that are ordered pairs of platform and virtual thread
     * counts. 0,2,4,..16 platform threads. 2,4,6,..32 virtual threads.
     */
    static Stream<Arguments> threadCounts() {
        return IntStream.range(0, 17)
                .filter(i -> i % 2 == 0)
                .mapToObj(i -> i)
                .flatMap(np -> IntStream.range(2, 33)
                        .filter(i -> i % 2 == 0)
                        .mapToObj(vp -> Arguments.of(np, vp)));
    }

    /**
     * Test mutual exclusion of monitors with platform and virtual threads.
     */
    @ParameterizedTest
    @MethodSource("threadCounts")
    void testMutualExclusion(int nPlatformThreads, int nVirtualThreads) throws Exception {
        class Counter {
            int count;
            synchronized void increment() {
                count++;
                Thread.yield();
            }
        }
        var counter = new Counter();
        int nThreads = nPlatformThreads + nVirtualThreads;
        var threads = new Thread[nThreads];
        int index = 0;
        for (int i = 0; i < nPlatformThreads; i++) {
            threads[index] = Thread.ofPlatform()
                    .name("platform-" + index)
                    .unstarted(counter::increment);
            index++;
        }
        for (int i = 0; i < nVirtualThreads; i++) {
            threads[index] = Thread.ofVirtual()
                    .name("virtual-" + index)
                    .unstarted(counter::increment);
            index++;
        }
        // start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        // wait for all threads to terminate
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(nThreads, counter.count);
    }

    /**
     * Test unblocking a virtual thread waiting to enter a monitor held by a platform thread.
     */
    @RepeatedTest(20)
    void testUnblockingByPlatformThread() throws Exception {
        testUnblocking();
    }

    /**
     * Test unblocking a virtual thread waiting to enter a monitor held by another
     * virtual thread.
     */
    @RepeatedTest(20)
    void testUnblockingByVirtualThread() throws Exception {
        VThreadRunner.run(this::testUnblocking);
    }

    /**
     * Test unblocking a virtual thread waiting to enter a monitor, monitor will be
     * initially be held by caller thread.
     */
    private void testUnblocking() throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);
        var entered = new AtomicBoolean();
        var vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            synchronized (lock) {
                entered.set(true);
            }
        });
        try {
            synchronized (lock) {
                vthread.start();
                started.await();

                // random delay before exiting monitor
                switch (ThreadLocalRandom.current().nextInt(4)) {
                    case 0 -> { /* no delay */}
                    case 1 -> Thread.onSpinWait();
                    case 2 -> Thread.yield();
                    case 3 -> await(vthread, Thread.State.BLOCKED);
                    default -> fail();
                }

                assertFalse(entered.get());
            }
        } finally {
            vthread.join();
        }
        assertTrue(entered.get());
    }

    /**
     * Test that unblocking a virtual thread waiting to enter a monitor does not consume
     * the thread's parking permit.
     */
    @Test
    void testParkingPermitNotConsumed() throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);
        var vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            LockSupport.unpark(Thread.currentThread());
            synchronized (lock) { }  // should block
            LockSupport.park();      // should not park
        });

        synchronized (lock) {
            vthread.start();
            // wait for thread to start and block
            started.await();
            await(vthread, Thread.State.BLOCKED);
        }
        vthread.join();
    }

    /**
     * Test that unblocking a virtual thread waiting to enter a monitor does not make
     * available the thread's parking permit.
     */
    @Test
    void testParkingPermitNotOffered() throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);
        var vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            synchronized (lock) { }  // should block
            LockSupport.park();      // should park
        });

        synchronized (lock) {
            vthread.start();
            // wait for thread to start and block
            started.await();
            await(vthread, Thread.State.BLOCKED);
        }

        try {
            // wait for thread to park, it should not terminate
            await(vthread, Thread.State.WAITING);
            vthread.join(Duration.ofMillis(100));
            assertEquals(Thread.State.WAITING, vthread.getState());
        } finally {
            LockSupport.unpark(vthread);
            vthread.join();
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
}
