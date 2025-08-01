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
 * @summary Test virtual threads using park/unpark
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit Parking
 */

/*
 * @test id=Xint
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm -Xint Parking
 */

/*
 * @test id=Xcomp
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm -Xcomp Parking
 */

/*
 * @test id=Xcomp-noTieredCompilation
 * @modules java.base/java.lang:+open jdk.management
 * @library /test/lib
 * @run junit/othervm -Xcomp -XX:-TieredCompilation Parking
 */

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class Parking {
    static final int MAX_VTHREAD_COUNT = 4 * Runtime.getRuntime().availableProcessors();
    static final Object lock = new Object();

    /**
     * Park, unparked by platform thread.
     */
    @Test
    void testPark1() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        Thread.sleep(1000); // give time for virtual thread to park
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park, unparked by virtual thread.
     */
    @Test
    void testPark2() throws Exception {
        var thread1 = Thread.ofVirtual().start(LockSupport::park);
        Thread.sleep(1000); // give time for virtual thread to park
        var thread2 = Thread.ofVirtual().start(() -> LockSupport.unpark(thread1));
        thread1.join();
        thread2.join();
    }

    /**
     * Park while holding monitor, unparked by platform thread.
     */
    @Test
    void testPark3() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                LockSupport.park();
            }
        });
        Thread.sleep(1000); // give time for virtual thread to park
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park with native frame on stack.
     */
    @Test
    void testPark4() throws Exception {
        // not implemented
    }

    /**
     * Unpark before park.
     */
    @Test
    void testPark5() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            LockSupport.unpark(Thread.currentThread());
            LockSupport.park();
        });
        thread.join();
    }

    /**
     * 2 x unpark before park.
     */
    @Test
    void testPark6() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            Thread me = Thread.currentThread();
            LockSupport.unpark(me);
            LockSupport.unpark(me);
            LockSupport.park();
            LockSupport.park();  // should park
        });
        Thread.sleep(1000); // give time for thread to park
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * 2 x park and unpark by platform thread.
     */
    @Test
    void testPark7() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            LockSupport.park();
            LockSupport.park();
        });

        Thread.sleep(1000); // give time for thread to park

        // unpark, virtual thread should park again
        LockSupport.unpark(thread);
        Thread.sleep(1000);
        assertTrue(thread.isAlive());

        // let it terminate
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park with interrupt status set.
     */
    @Test
    void testPark8() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.interrupt();
            LockSupport.park();
            assertTrue(t.isInterrupted());
        });
    }

    /**
     * Thread interrupt when parked.
     */
    @Test
    void testPark9() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 1000);
            while (!Thread.currentThread().isInterrupted()) {
                LockSupport.park();
            }
        });
    }

    /**
     * Park while holding monitor and with interrupt status set.
     */
    @Test
    void testPark10() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.interrupt();
            synchronized (lock) {
                LockSupport.park();
            }
            assertTrue(t.isInterrupted());
        });
    }

    /**
     * Thread interrupt when parked while holding monitor
     */
    @Test
    void testPark11() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 1000);
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (lock) {
                    LockSupport.park();
                }
            }
        });
    }

    /**
     * parkNanos(-1) completes immediately
     */
    @Test
    void testParkNanos1() throws Exception {
        VThreadRunner.run(() -> LockSupport.parkNanos(-1));
    }

    /**
     * parkNanos(0) completes immediately
     */
    @Test
    void testParkNanos2() throws Exception {
        VThreadRunner.run(() -> LockSupport.parkNanos(0));
    }

    /**
     * parkNanos(1000ms) parks thread.
     */
    @Test
    void testParkNanos3() throws Exception {
        VThreadRunner.run(() -> {
            // park for 1000ms
            long nanos = TimeUnit.NANOSECONDS.convert(1000, TimeUnit.MILLISECONDS);
            long start = System.nanoTime();
            LockSupport.parkNanos(nanos);

            // check that virtual thread parked for >= 900ms
            long elapsed = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start,
                    TimeUnit.NANOSECONDS);
            assertTrue(elapsed >= 900);
        });
    }

    /**
     * Park with parkNanos, unparked by platform thread.
     */
    @Test
    void testParkNanos4() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            long nanos = TimeUnit.NANOSECONDS.convert(1, TimeUnit.DAYS);
            LockSupport.parkNanos(nanos);
        });
        Thread.sleep(100); // give time for virtual thread to park
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park with parkNanos, unparked by virtual thread.
     */
    @Test
    void testParkNanos5() throws Exception {
        var thread1 = Thread.ofVirtual().start(() -> {
            long nanos = TimeUnit.NANOSECONDS.convert(1, TimeUnit.DAYS);
            LockSupport.parkNanos(nanos);
        });
        Thread.sleep(100);  // give time for virtual thread to park
        var thread2 = Thread.ofVirtual().start(() -> LockSupport.unpark(thread1));
        thread1.join();
        thread2.join();
    }

    /**
     * Unpark before parkNanos.
     */
    @Test
    void testParkNanos6() throws Exception {
        VThreadRunner.run(() -> {
            LockSupport.unpark(Thread.currentThread());
            long nanos = TimeUnit.NANOSECONDS.convert(1, TimeUnit.DAYS);
            LockSupport.parkNanos(nanos);
        });
    }

    /**
     * Unpark before parkNanos(0), should consume parking permit.
     */
    @Test
    void testParkNanos7() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            LockSupport.unpark(Thread.currentThread());
            LockSupport.parkNanos(0);  // should consume parking permit
            LockSupport.park();  // should block
        });
        boolean isAlive = thread.join(Duration.ofSeconds(2));
        assertTrue(isAlive);
        LockSupport.unpark(thread);
        thread.join();
    }

    /**
     * Park with parkNanos and interrupt status set.
     */
    @Test
    void testParkNanos8() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.interrupt();
            LockSupport.parkNanos(Duration.ofDays(1).toNanos());
            assertTrue(t.isInterrupted());
        });
    }

    /**
     * Thread interrupt when parked in parkNanos.
     */
    @Test
    void testParkNanos9() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 1000);
            while (!Thread.currentThread().isInterrupted()) {
                LockSupport.parkNanos(Duration.ofDays(1).toNanos());
            }
        });
    }

    /**
     * Park with parkNanos while holding monitor and with interrupt status set.
     */
    @Test
    void testParkNanos10() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.interrupt();
            synchronized (lock) {
                LockSupport.parkNanos(Duration.ofDays(1).toNanos());
            }
            assertTrue(t.isInterrupted());
        });
    }

    /**
     * Thread interrupt when parked in parkNanos and while holding monitor.
     */
    @Test
    void testParkNanos11() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 1000);
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (lock) {
                    LockSupport.parkNanos(Duration.ofDays(1).toNanos());
                }
            }
        });
    }

    /**
     * Test that parking while holding a monitor releases the carrier.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testParkWhenHoldingMonitor(boolean reenter) throws Exception {
        assumeTrue(VThreadScheduler.supportsCustomScheduler(), "No support for custom schedulers");
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);

            var lock = new Object();

            // thread enters (and maybe reenters) a monitor and parks
            var started = new CountDownLatch(1);
            var vthread1 = factory.newThread(() -> {
                started.countDown();
                synchronized (lock) {
                    if (reenter) {
                        synchronized (lock) {
                            LockSupport.park();
                        }
                    } else {
                        LockSupport.park();
                    }
                }
            });

            vthread1.start();
            try {
                // wait for thread to start and park
                started.await();
                await(vthread1, Thread.State.WAITING);

                // carrier should be released, use it for another thread
                var executed = new AtomicBoolean();
                var vthread2 = factory.newThread(() -> {
                    executed.set(true);
                });
                vthread2.start();
                vthread2.join();
                assertTrue(executed.get());
            } finally {
                LockSupport.unpark(vthread1);
                vthread1.join();
            }
        }
    }

    /**
     * Test lots of virtual threads parked while holding a monitor. If the number of
     * virtual threads exceeds the number of carrier threads then this test will hang if
     * parking doesn't release the carrier.
     */
    @Test
    void testManyParkedWhenHoldingMonitor() throws Exception {
        Thread[] vthreads = new Thread[MAX_VTHREAD_COUNT];
        var done = new AtomicBoolean();
        for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
            var lock = new Object();
            var started = new CountDownLatch(1);
            var vthread = Thread.ofVirtual().start(() -> {
                started.countDown();
                synchronized (lock) {
                    while (!done.get()) {
                        LockSupport.park();
                    }
                }
            });
            // wait for thread to start and park
            started.await();
            await(vthread, Thread.State.WAITING);
            vthreads[i] = vthread;
        }

        // cleanup
        done.set(true);
        for (int i = 0; i < MAX_VTHREAD_COUNT; i++) {
            var vthread = vthreads[i];
            LockSupport.unpark(vthread);
            vthread.join();
        }
    }

    /**
     * Schedule a thread to be interrupted after a delay.
     */
    private static void scheduleInterrupt(Thread thread, long delay) {
        Runnable interruptTask = () -> {
            try {
                Thread.sleep(delay);
                thread.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        new Thread(interruptTask).start();
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
