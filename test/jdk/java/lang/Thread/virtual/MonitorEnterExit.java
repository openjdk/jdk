/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED MonitorEnterExit
 */

/*
 * @test id=Xint
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native -Xint --enable-native-access=ALL-UNNAMED MonitorEnterExit
 */

/*
 * @test id=Xcomp
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native -Xcomp --enable-native-access=ALL-UNNAMED MonitorEnterExit
 */

/*
 * @test id=Xcomp-TieredStopAtLevel1
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native -Xcomp -XX:TieredStopAtLevel=1 --enable-native-access=ALL-UNNAMED MonitorEnterExit
 */

/*
 * @test id=Xcomp-noTieredCompilation
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm/native -Xcomp -XX:-TieredCompilation --enable-native-access=ALL-UNNAMED MonitorEnterExit
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
import jdk.test.lib.thread.VThreadRunner;   // ensureParallelism requires jdk.management
import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class MonitorEnterExit {
    static final int MAX_VTHREAD_COUNT = 4 * Runtime.getRuntime().availableProcessors();
    static final int MAX_ENTER_DEPTH = 256;

    @BeforeAll
    static void setup() {
        // need >=2 carriers for tests that pin
        VThreadRunner.ensureParallelism(2);
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
     * Test monitor reenter when there are other threads blocked trying to enter.
     */
    @Test
    void testReenterWithContention() throws Exception {
        var lock = new Object();
        VThreadRunner.run(() -> {
            List<Thread> threads = new ArrayList<>();
            testReenter(lock, 0, threads);

            // wait for threads to terminate
            for (Thread vthread : threads) {
                vthread.join();
            }
        });
    }

    private void testReenter(Object lock, int depth, List<Thread> threads) throws Exception {
        if (depth < MAX_ENTER_DEPTH) {
            synchronized (lock) {
                assertTrue(Thread.holdsLock(lock));

                // start platform or virtual thread that blocks waiting to enter
                var started = new CountDownLatch(1);
                ThreadFactory factory = ThreadLocalRandom.current().nextBoolean()
                        ? Thread.ofPlatform().factory()
                        : Thread.ofVirtual().factory();
                var thread = factory.newThread(() -> {
                    started.countDown();
                    synchronized (lock) {
                        /* do nothing */
                    }
                });
                thread.start();

                // wait for thread to start and block
                started.await();
                await(thread, Thread.State.BLOCKED);
                threads.add(thread);

                // test reenter
                testReenter(lock, depth + 1, threads);
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
        VThreadRunner.run(this::testEnterWithContentionWhenPinned);
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
     * Test that blocking waiting to enter a monitor releases the carrier.
     */
    @Test
    void testReleaseWhenBlocked() throws Exception {
        assumeTrue(VThreadScheduler.supportsCustomScheduler(), "No support for custom schedulers");
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);

            var lock = new Object();

            // thread enters monitor
            var started = new CountDownLatch(1);
            var vthread1 = factory.newThread(() -> {
                started.countDown();
                synchronized (lock) {
                }
            });

            try {
                synchronized (lock) {
                    // start thread and wait for it to block
                    vthread1.start();
                    started.await();
                    await(vthread1, Thread.State.BLOCKED);

                    // carrier should be released, use it for another thread
                    var executed = new AtomicBoolean();
                    var vthread2 = factory.newThread(() -> {
                        executed.set(true);
                    });
                    vthread2.start();
                    vthread2.join();
                    assertTrue(executed.get());
                }
            } finally {
                vthread1.join();
            }
        }
    }

    /**
     * Test lots of virtual threads blocked waiting to enter a monitor. If the number
     * of virtual threads exceeds the number of carrier threads this test will hang if
     * carriers aren't released.
     */
    @Test
    void testManyBlockedThreads() throws Exception {
        Thread[] vthreads = new Thread[MAX_VTHREAD_COUNT];
        var lock = new Object();
        synchronized (lock) {
            for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
                var started = new CountDownLatch(1);
                var vthread = Thread.ofVirtual().start(() -> {
                    started.countDown();
                    synchronized (lock) {
                    }
                });
                // wait for thread to start and block
                started.await();
                await(vthread, Thread.State.BLOCKED);
                vthreads[i] = vthread;
            }
        }

        // cleanup
        for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
            vthreads[i].join();
        }
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
