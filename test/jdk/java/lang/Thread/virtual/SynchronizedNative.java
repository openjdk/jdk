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
 * @summary Test virtual threads with a synchronized native method and a native method
 *      that enter/exits a monitor with JNI MonitorEnter/MonitorExit
 * @requires vm.continuations
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm --enable-native-access=ALL-UNNAMED SynchronizedNative
 */

/*
 * @test id=Xint
 * @requires vm.continuations
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm -Xint --enable-native-access=ALL-UNNAMED SynchronizedNative
 */

/*
 * @test id=Xcomp-TieredStopAtLevel1
 * @requires vm.continuations
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm -Xcomp -XX:TieredStopAtLevel=1 --enable-native-access=ALL-UNNAMED SynchronizedNative
 */

/*
 * @test id=Xcomp-noTieredCompilation
 * @requires vm.continuations
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm -Xcomp -XX:-TieredCompilation --enable-native-access=ALL-UNNAMED SynchronizedNative
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jdk.test.lib.thread.VThreadPinner;
import jdk.test.lib.thread.VThreadRunner;   // ensureParallelism requires jdk.management
import jdk.test.lib.thread.VThreadScheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SynchronizedNative {

    @BeforeAll
    static void setup() throws Exception {
        // need at least two carriers to test pinning
        VThreadRunner.ensureParallelism(2);
        System.loadLibrary("SynchronizedNative");
    }

    /**
     * Test entering a monitor with a synchronized native method, no contention.
     */
    @Test
    void testEnter() throws Exception {
        Object lock = this;
        VThreadRunner.run(() -> {
            runWithSynchronizedNative(() -> {
                assertTrue(Thread.holdsLock(lock));
            });
            assertFalse(Thread.holdsLock(lock));
        });
    }

    /**
     * Test reentering a monitor with synchronized native method, no contention.
     */
    @Test
    void testReenter() throws Exception {
        Object lock = this;
        VThreadRunner.run(() -> {

            // enter, reenter with a synchronized native method
            synchronized (lock) {
                runWithSynchronizedNative(() -> {
                    assertTrue(Thread.holdsLock(lock));
                });
                assertTrue(Thread.holdsLock(lock));
            }
            assertFalse(Thread.holdsLock(lock));

            // enter with synchronized native method, renter with synchronized statement
            runWithSynchronizedNative(() -> {
                assertTrue(Thread.holdsLock(lock));
                synchronized (lock) {
                    assertTrue(Thread.holdsLock(lock));
                }
                assertTrue(Thread.holdsLock(lock));
            });
            assertFalse(Thread.holdsLock(lock));

            // enter with synchronized native method, reenter with synchronized native method
            runWithSynchronizedNative(() -> {
                assertTrue(Thread.holdsLock(lock));
                runWithSynchronizedNative(() -> {
                    assertTrue(Thread.holdsLock(lock));
                });
                assertTrue(Thread.holdsLock(lock));
            });
            assertFalse(Thread.holdsLock(lock));
        });
    }

    /**
     * Test entering a monitor with a synchronized native method and with contention.
     */
    @Test
    void testEnterWithContention() throws Exception {
        var lock = this;
        var started = new CountDownLatch(1);
        var entered = new AtomicBoolean();
        var vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            runWithSynchronizedNative(() -> {
                assertTrue(Thread.holdsLock(lock));
                entered.set(true);
            });
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
     * Returns a stream of elements that are ordered pairs of platform and virtual thread
     * counts. 0,2,4 platform threads. 2,4,6,8 virtual threads.
     */
    static Stream<Arguments> threadCounts() {
        return IntStream.range(0, 5)
                .filter(i -> i % 2 == 0)
                .mapToObj(i -> i)
                .flatMap(np -> IntStream.range(2, 9)
                        .filter(i -> i % 2 == 0)
                        .mapToObj(vp -> Arguments.of(np, vp)));
    }

    /**
     * Execute a task concurrently from both platform and virtual threads.
     */
    private void executeConcurrently(int nPlatformThreads,
                                     int nVirtualThreads,
                                     Runnable task) throws Exception {
        int parallism = nVirtualThreads;
        if (Thread.currentThread().isVirtual()) {
            parallism++;
        }
        int previousParallelism = VThreadRunner.ensureParallelism(parallism);
        try {
            int nthreads = nPlatformThreads + nVirtualThreads;
            var phaser = new Phaser(nthreads + 1);

            // start all threads
            var threads = new Thread[nthreads];
            int index = 0;
            for (int i = 0; i < nPlatformThreads; i++) {
                threads[index++] = Thread.ofPlatform().start(() -> {
                    phaser.arriveAndAwaitAdvance();
                    task.run();
                });
            }
            for (int i = 0; i < nVirtualThreads; i++) {
                threads[index++] = Thread.ofVirtual().start(() -> {
                    phaser.arriveAndAwaitAdvance();
                    task.run();
                });
            }

            // wait for all threads to start
            phaser.arriveAndAwaitAdvance();
            System.err.printf("  %d threads started%n", nthreads);

            // wait for all threads to terminate
            for (Thread thread : threads) {
                if (thread != null) {
                    System.err.printf("  join %s ...%n", thread);
                    thread.join();
                }
            }
        } finally {
            // reset parallelism
            VThreadRunner.setParallelism(previousParallelism);
        }
    }


    /**
     * Test entering a monitor with a synchronized native method from many threads
     * at the same time.
     */
    @ParameterizedTest
    @MethodSource("threadCounts")
    void testEnterConcurrently(int nPlatformThreads, int nVirtualThreads) throws Exception {
        var counter = new Object() {
            int value;
            int value() { return value; }
            void increment() { value++; }
        };
        var lock = this;
        executeConcurrently(nPlatformThreads, nVirtualThreads, () -> {
            runWithSynchronizedNative(() -> {
                assertTrue(Thread.holdsLock(lock));
                counter.increment();
                LockSupport.parkNanos(100_000_000);  // 100ms
            });
        });
        synchronized (lock) {
            assertEquals(nPlatformThreads + nVirtualThreads, counter.value());
        }
    }

    /**
     * Test entering a monitor with JNI MonitorEnter.
     */
    @Test
    void testEnterInNative() throws Exception {
        Object lock = new Object();
        VThreadRunner.run(() -> {
            runWithMonitorEnteredInNative(lock, () -> {
                assertTrue(Thread.holdsLock(lock));
            });
            assertFalse(Thread.holdsLock(lock));
        });
    }

    /**
     * Test reentering a monitor with JNI MonitorEnter.
     */
    @Test
    void testReenterInNative() throws Exception {
        Object lock = new Object();
        VThreadRunner.run(() -> {

            // enter, reenter with JNI MonitorEnter
            synchronized (lock) {
                runWithMonitorEnteredInNative(lock, () -> {
                    assertTrue(Thread.holdsLock(lock));
                });
                assertTrue(Thread.holdsLock(lock));
            }
            assertFalse(Thread.holdsLock(lock));

            // enter with JNI MonitorEnter, renter with synchronized statement
            runWithMonitorEnteredInNative(lock, () -> {
                assertTrue(Thread.holdsLock(lock));
                synchronized (lock) {
                    assertTrue(Thread.holdsLock(lock));
                }
                assertTrue(Thread.holdsLock(lock));
            });
            assertFalse(Thread.holdsLock(lock));

            // enter with JNI MonitorEnter, renter with JNI MonitorEnter
            runWithMonitorEnteredInNative(lock, () -> {
                assertTrue(Thread.holdsLock(lock));
                runWithMonitorEnteredInNative(lock, () -> {
                    assertTrue(Thread.holdsLock(lock));
                });
                assertTrue(Thread.holdsLock(lock));
            });
            assertFalse(Thread.holdsLock(lock));
        });
    }

    /**
     * Test entering a monitor with JNI MonitorEnter and with contention.
     */
    @Test
    void testEnterInNativeWithContention() throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);
        var entered = new AtomicBoolean();
        var vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            runWithMonitorEnteredInNative(lock, () -> {
                assertTrue(Thread.holdsLock(lock));
                entered.set(true);
            });
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
     * Test entering a monitor with JNI MonitorEnter from many threads at the same time.
     */
    @ParameterizedTest
    @MethodSource("threadCounts")
    void testEnterInNativeConcurrently(int nPlatformThreads, int nVirtualThreads) throws Exception {
        var counter = new Object() {
            int value;
            int value() { return value; }
            void increment() { value++; }
        };
        var lock = counter;
        executeConcurrently(nPlatformThreads, nVirtualThreads, () -> {
            runWithMonitorEnteredInNative(lock, () -> {
                assertTrue(Thread.holdsLock(lock));
                counter.increment();
                LockSupport.parkNanos(100_000_000);  // 100ms
            });
        });
        synchronized (lock) {
            assertEquals(nPlatformThreads + nVirtualThreads, counter.value());
        }
    }

    /**
     * Test parking with synchronized native method on stack.
     */
    @Test
    void testParkingWhenPinned() throws Exception {
        var lock = this;
        var started = new CountDownLatch(1);
        var entered = new AtomicBoolean();
        var done = new AtomicBoolean();
        var vthread = Thread.ofVirtual().start(() -> {
            started.countDown();
            runWithSynchronizedNative(() -> {
                assertTrue(Thread.holdsLock(lock));
                entered.set(true);
                while (!done.get()) {
                    LockSupport.park();
                }
            });
        });
        try {
            // wait for thread to start and block
            started.await();
            await(vthread, Thread.State.WAITING);
        } finally {
            done.set(true);
            LockSupport.unpark(vthread);
            vthread.join();
        }
        assertTrue(entered.get());
    }

    /**
     * Test blocking with synchronized native method on stack.
     */
    @Test
    void testBlockingWhenPinned() throws Exception {
        var lock1 = this;
        var lock2 = new Object();

        var started = new CountDownLatch(1);
        var entered1 = new AtomicBoolean();   // set to true when vthread enters lock1
        var entered2 = new AtomicBoolean();   // set to true when vthread enters lock2

        var vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            runWithSynchronizedNative(() -> {
                assertTrue(Thread.holdsLock(lock1));
                entered1.set(true);
                synchronized (lock2) {   // should block
                    assertTrue(Thread.holdsLock(lock2));
                    entered2.set(true);
                }
            });
        });
        try {
            synchronized (lock2) {
                // start thread and wait for it to block trying to enter lock2
                vthread.start();
                started.await();
                await(vthread, Thread.State.BLOCKED);

                assertTrue(entered1.get());
                assertFalse(entered2.get());
            }
        } finally {
            vthread.join();
        }
        assertTrue(entered2.get());
    }

    /**
     * Test that blocking on synchronized native method releases the carrier.
     */
    //@Test
    void testReleaseWhenBlocked() throws Exception {
        assertTrue(VThreadScheduler.supportsCustomScheduler(), "No support for custom schedulers");
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);

            var lock = this;
            var started = new CountDownLatch(1);
            var entered = new AtomicBoolean();   // set to true when vthread enters lock

            var vthread1 = factory.newThread(() -> {
                started.countDown();
                runWithSynchronizedNative(() -> {
                    assertTrue(Thread.holdsLock(lock));
                    entered.set(true);
                });
                assertFalse(Thread.holdsLock(lock));
            });

            vthread1.start();
            try {
                synchronized (this) {
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
     * Invokes the given task's run method while holding the monitor for "this".
     */
    private synchronized native void runWithSynchronizedNative(Runnable task);

    /**
     * Invokes the given task's run method while holding the monitor for the given
     * object. The monitor is entered with JNI MonitorEnter, and exited with JNI MonitorExit.
     */
    private native void runWithMonitorEnteredInNative(Object lock, Runnable task);

    /**
     * Called from native methods to run the given task.
     */
    private void run(Runnable task) {
        task.run();
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
