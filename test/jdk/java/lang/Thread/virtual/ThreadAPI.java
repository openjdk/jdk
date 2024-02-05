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
 * @test id=default
 * @bug 8284161 8286788 8321270
 * @summary Test Thread API with virtual threads
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit/othervm --enable-native-access=ALL-UNNAMED ThreadAPI
 */

/*
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations
 *     --enable-native-access=ALL-UNNAMED ThreadAPI
 */

import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.nio.channels.Selector;

import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.thread.VThreadPinner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class ThreadAPI {
    private static final Object lock = new Object();

    // used for scheduling thread interrupt
    private static ScheduledExecutorService scheduler;

    @BeforeAll
    static void setup() throws Exception {
        ThreadFactory factory = Executors.defaultThreadFactory();
        scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    @AfterAll
    static void finish() {
        scheduler.shutdown();
    }

    /**
     * An operation that does not return a result but may throw an exception.
     */
    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Test Thread.currentThread before/after park.
     */
    @Test
    void testCurrentThread1() throws Exception {
        var before = new AtomicReference<Thread>();
        var after = new AtomicReference<Thread>();
        var thread = Thread.ofVirtual().start(() -> {
            before.set(Thread.currentThread());
            LockSupport.park();
            after.set(Thread.currentThread());
        });
        await(thread, Thread.State.WAITING);
        LockSupport.unpark(thread);
        thread.join();
        assertTrue(before.get() == thread);
        assertTrue(after.get() == thread);
    }

    /**
     * Test Thread.currentThread before/after entering synchronized block.
     */
    @Test
    void testCurrentThread2() throws Exception {
        var ref1 = new AtomicReference<Thread>();
        var ref2 = new AtomicReference<Thread>();
        var ref3 = new AtomicReference<Thread>();
        var thread = Thread.ofVirtual().unstarted(() -> {
            ref1.set(Thread.currentThread());
            synchronized (lock) {
                ref2.set(Thread.currentThread());
            }
            ref3.set(Thread.currentThread());
        });
        synchronized (lock) {
            thread.start();
            await(thread, Thread.State.BLOCKED);
        }
        thread.join();
        assertTrue(ref1.get() == thread);
        assertTrue(ref2.get() == thread);
        assertTrue(ref3.get() == thread);
    }

    /**
     * Test Thread.currentThread before/after acquiring lock.
     */
    @Test
    void testCurrentThread3() throws Exception {
        var ref1 = new AtomicReference<Thread>();
        var ref2 = new AtomicReference<Thread>();
        var ref3 = new AtomicReference<Thread>();
        var lock = new ReentrantLock();
        var thread = Thread.ofVirtual().unstarted(() -> {
            ref1.set(Thread.currentThread());
            lock.lock();
            try {
                ref2.set(Thread.currentThread());
            } finally {
                lock.unlock();
            }
            ref3.set(Thread.currentThread());
        });
        lock.lock();
        try {
            thread.start();
            await(thread, Thread.State.WAITING);
        } finally {
            lock.unlock();
        }
        thread.join();
        assertTrue(ref1.get() == thread);
        assertTrue(ref2.get() == thread);
        assertTrue(ref3.get() == thread);
    }

    /**
     * Test Thread::run.
     */
    @Test
    void testRun1() throws Exception {
        var ref = new AtomicBoolean();
        var thread = Thread.ofVirtual().unstarted(() -> ref.set(true));
        thread.run();
        assertFalse(ref.get());
    }

    /**
     * Test Thread::start.
     */
    @Test
    void testStart1() throws Exception {
        var ref = new AtomicBoolean();
        var thread = Thread.ofVirtual().unstarted(() -> ref.set(true));
        assertFalse(ref.get());
        thread.start();
        thread.join();
        assertTrue(ref.get());
    }

    /**
     * Test Thread::start, thread already started.
     */
    @Test
    void testStart2() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        try {
            assertThrows(IllegalThreadStateException.class, thread::start);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread::start, thread already terminated.
     */
    @Test
    void testStart3() throws Exception {
        var thread = Thread.ofVirtual().start(() -> { });
        thread.join();
        assertThrows(IllegalThreadStateException.class, thread::start);
    }

    /**
     * Test Thread.startVirtualThread.
     */
    @Test
    void testStartVirtualThread() throws Exception {
        var ref = new AtomicReference<Thread>();
        Thread vthread = Thread.startVirtualThread(() -> {
            ref.set(Thread.currentThread());
            LockSupport.park();
        });
        try {
            assertTrue(vthread.isVirtual());

            // Thread.currentThread() returned by the virtual thread
            Thread current;
            while ((current = ref.get()) == null) {
                Thread.sleep(10);
            }
            assertTrue(current == vthread);
        } finally {
            LockSupport.unpark(vthread);
        }

        assertThrows(NullPointerException.class, () -> Thread.startVirtualThread(null));
    }

    /**
     * Test Thread::stop from current thread.
     */
    @Test
    void testStop1() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            assertThrows(UnsupportedOperationException.class, t::stop);
        });
    }

    /**
     * Test Thread::stop from another thread.
     */
    @Test
    void testStop2() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(20*1000);
            } catch (InterruptedException e) { }
        });
        try {
            assertThrows(UnsupportedOperationException.class, thread::stop);
        } finally {
            thread.interrupt();
            thread.join();
        }
    }

    /**
     * Test Thread.join before thread starts, platform thread invokes join.
     */
    @Test
    void testJoin1() throws Exception {
        var thread = Thread.ofVirtual().unstarted(() -> { });

        thread.join();
        thread.join(0);
        thread.join(0, 0);
        thread.join(100);
        thread.join(100, 0);
        assertThrows(IllegalThreadStateException.class,
                () -> thread.join(Duration.ofMillis(-100)));
        assertThrows(IllegalThreadStateException.class,
                () -> thread.join(Duration.ofMillis(0)));
        assertThrows(IllegalThreadStateException.class,
                () -> thread.join(Duration.ofMillis(100)));
    }

    /**
     * Test Thread.join before thread starts, virtual thread invokes join.
     */
    @Test
    void testJoin2() throws Exception {
        VThreadRunner.run(this::testJoin1);
    }

    /**
     * Test Thread.join where thread does not terminate, platform thread invokes join.
     */
    @Test
    void testJoin3() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        try {
            thread.join(20);
            thread.join(20, 0);
            thread.join(20, 20);
            thread.join(0, 20);
            assertFalse(thread.join(Duration.ofMillis(-20)));
            assertFalse(thread.join(Duration.ofMillis(0)));
            assertFalse(thread.join(Duration.ofMillis(20)));
            assertTrue(thread.isAlive());
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread.join where thread does not terminate, virtual thread invokes join.
     */
    @Test
    void testJoin4() throws Exception {
        VThreadRunner.run(this::testJoin3);
    }

    /**
     * Test Thread.join where thread terminates, platform thread invokes join.
     */
    @Test
    void testJoin5() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) { }
        });
        thread.join();
        assertFalse(thread.isAlive());
    }

    /**
     * Test Thread.join where thread terminates, virtual thread invokes join.
     */
    @Test
    void testJoin6() throws Exception {
        VThreadRunner.run(this::testJoin5);
    }

    /**
     * Test Thread.join where thread terminates, platform thread invokes timed-join.
     */
    @Test
    void testJoin7() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) { }
        });
        thread.join(10*1000);
        assertFalse(thread.isAlive());
    }

    /**
     * Test Thread.join where thread terminates, virtual thread invokes timed-join.
     */
    @Test
    void testJoin8() throws Exception {
        VThreadRunner.run(this::testJoin7);
    }

    /**
     * Test Thread.join where thread terminates, platform thread invokes timed-join.
     */
    @Test
    void testJoin11() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) { }
        });
        assertTrue(thread.join(Duration.ofSeconds(10)));
        assertFalse(thread.isAlive());
    }

    /**
     * Test Thread.join where thread terminates, virtual thread invokes timed-join.
     */
    @Test
    void testJoin12() throws Exception {
        VThreadRunner.run(this::testJoin11);
    }

    /**
     * Test Thread.join where thread already terminated, platform thread invokes join.
     */
    @Test
    void testJoin13() throws Exception {
        var thread = Thread.ofVirtual().start(() -> { });
        while (thread.isAlive()) {
            Thread.sleep(10);
        }
        thread.join();
        thread.join(0);
        thread.join(0, 0);
        thread.join(100);
        thread.join(100, 0);
        assertTrue(thread.join(Duration.ofMillis(-100)));
        assertTrue(thread.join(Duration.ofMillis(0)));
        assertTrue(thread.join(Duration.ofMillis(100)));
    }

    /**
     * Test Thread.join where thread already terminated, virtual thread invokes join.
     */
    @Test
    void testJoin14() throws Exception {
        VThreadRunner.run(this::testJoin13);
    }

    /**
     * Test platform thread invoking Thread.join with interrupt status set.
     */
    @Test
    void testJoin15() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, thread::join);
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test virtual thread invoking Thread.join with interrupt status set.
     */
    @Test
    void testJoin16() throws Exception {
        VThreadRunner.run(this::testJoin15);
    }

    /**
     * Test platform thread invoking timed-Thread.join with interrupt status set.
     */
    @Test
    void testJoin17() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> thread.join(100));
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test virtual thread invoking timed-Thread.join with interrupt status set.
     */
    @Test
    void testJoin18() throws Exception {
        VThreadRunner.run(this::testJoin17);
    }

    /**
     * Test platform thread invoking timed-Thread.join with interrupt status set.
     */
    @Test
    void testJoin19() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                         () -> thread.join(Duration.ofMillis(100)));
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test virtual thread invoking timed-Thread.join with interrupt status set.
     */
    @Test
    void testJoin20() throws Exception {
        VThreadRunner.run(this::testJoin19);
    }

    /**
     * Test interrupt of platform thread blocked in Thread.join.
     */
    @Test
    void testJoin21() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        scheduleInterrupt(Thread.currentThread(), 100);
        try {
            assertThrows(InterruptedException.class, thread::join);
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test interrupt of virtual thread blocked in Thread.join.
     */
    @Test
    void testJoin22() throws Exception {
        VThreadRunner.run(this::testJoin17);
    }

    /**
     * Test interrupt of platform thread blocked in timed-Thread.join.
     */
    @Test
    void testJoin23() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        scheduleInterrupt(Thread.currentThread(), 100);
        try {
            assertThrows(InterruptedException.class, () -> thread.join(10*1000));
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test interrupt of virtual thread blocked in Thread.join.
     */
    @Test
    void testJoin24() throws Exception {
        VThreadRunner.run(this::testJoin23);
    }

    /**
     * Test interrupt of platform thread blocked in Thread.join.
     */
    @Test
    void testJoin25() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        scheduleInterrupt(Thread.currentThread(), 100);
        try {
            assertThrows(InterruptedException.class,
                         () -> thread.join(Duration.ofSeconds(10)));
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test interrupt of virtual thread blocked in Thread.join.
     */
    @Test
    void testJoin26() throws Exception {
        VThreadRunner.run(this::testJoin25);
    }

    /**
     * Test virtual thread calling Thread.join to wait for platform thread to terminate.
     */
    @Test
    void testJoin27() throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        VThreadRunner.run(() -> {
            var thread = new Thread(() -> {
                while (!done.get()) {
                    LockSupport.park();
                }
            });
            thread.start();
            try {
                assertFalse(thread.join(Duration.ofMillis(-100)));
                assertFalse(thread.join(Duration.ofMillis(0)));
                assertFalse(thread.join(Duration.ofMillis(100)));
            } finally {
                done.set(true);
                LockSupport.unpark(thread);
                thread.join();
            }
        });
    }

    /**
     * Test virtual thread calling Thread.join to wait for platform thread to terminate.
     */
    @Test
    void testJoin28() throws Exception {
        long nanos = TimeUnit.NANOSECONDS.convert(100, TimeUnit.MILLISECONDS);
        VThreadRunner.run(() -> {
            var thread = new Thread(() -> LockSupport.parkNanos(nanos));
            thread.start();
            try {
                assertTrue(thread.join(Duration.ofSeconds(Integer.MAX_VALUE)));
                assertFalse(thread.isAlive());
            } finally {
                LockSupport.unpark(thread);
                thread.join();
            }
        });
    }

    /**
     * Test virtual thread with interrupt status set calling Thread.join to wait
     * for platform thread to terminate.
     */
    @Test
    void testJoin29() throws Exception {
        VThreadRunner.run(() -> {
            var thread = new Thread(LockSupport::park);
            thread.start();
            Thread.currentThread().interrupt();
            try {
                thread.join(Duration.ofSeconds(Integer.MAX_VALUE));
                fail("join not interrupted");
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());
            } finally {
                LockSupport.unpark(thread);
                thread.join();
            }
        });
    }

    /**
     * Test interrupting virtual thread that is waiting in Thread.join for
     * platform thread to terminate.
     */
    @Test
    void testJoin30() throws Exception {
        VThreadRunner.run(() -> {
            AtomicBoolean done = new AtomicBoolean();
            var thread = new Thread(() -> {
                while (!done.get()) {
                    LockSupport.park();
                }
            });
            thread.start();
            scheduleInterrupt(Thread.currentThread(), 100);
            try {
                thread.join(Duration.ofSeconds(Integer.MAX_VALUE));
                fail("join not interrupted");
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());
            } finally {
                done.set(true);
                LockSupport.unpark(thread);
                thread.join();
            }
        });
    }

    /**
     * Test platform thread invoking Thread.join on a thread that is parking
     * and unparking.
     */
    @Test
    void testJoin31() throws Exception {
        Thread thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                for (int i = 0; i < 10; i++) {
                    LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
                }
            }
        });
        thread.join();
        assertFalse(thread.isAlive());
    }

    /**
     * Test virtual thread invoking Thread.join on a thread that is parking
     * and unparking.
     */
    @Test
    void testJoin32() throws Exception {
        VThreadRunner.run(this::testJoin31);
    }

    /**
     * Test platform thread invoking timed-Thread.join on a thread that is parking
     * and unparking while pinned.
     */
    @Test
    void testJoin33() throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        Thread thread = Thread.ofVirtual().start(() -> {
            VThreadPinner.runPinned(() -> {
                while (!done.get()) {
                    LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
                }
            });
        });
        try {
            assertFalse(thread.join(Duration.ofMillis(100)));
        } finally {
            done.set(true);
            thread.join();
        }
    }

    /**
     * Test virtual thread invoking timed-Thread.join on a thread that is parking
     * and unparking while pinned.
     */
    @Test
    void testJoin34() throws Exception {
        // need at least two carrier threads due to pinning
        int previousParallelism = VThreadRunner.ensureParallelism(2);
        try {
            VThreadRunner.run(this::testJoin33);
        } finally {
            // restore
            VThreadRunner.setParallelism(previousParallelism);
        }
    }

    /**
     * Test Thread.join(null).
     */
    @Test
    void testJoin35() throws Exception {
        var thread = Thread.ofVirtual().unstarted(LockSupport::park);

        // unstarted
        assertThrows(NullPointerException.class, () -> thread.join(null));

        // started
        thread.start();
        try {
            assertThrows(NullPointerException.class, () -> thread.join(null));
        } finally {
            LockSupport.unpark(thread);
        }
        thread.join();

        // terminated
        assertThrows(NullPointerException.class, () -> thread.join(null));
    }

    /**
     * Test Thread.interrupt on current thread.
     */
    @Test
    void testInterrupt1() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            assertFalse(me.isInterrupted());
            me.interrupt();
            assertTrue(me.isInterrupted());
            Thread.interrupted();  // clear interrupt status
            assertFalse(me.isInterrupted());
            me.interrupt();
        });
    }

    /**
     * Test Thread.interrupt before thread started.
     */
    @Test
    void testInterrupt2() throws Exception {
        var thread = Thread.ofVirtual().unstarted(() -> { });
        thread.interrupt();
        assertTrue(thread.isInterrupted());
    }

    /**
     * Test Thread.interrupt after thread started.
     */
    @Test
    void testInterrupt3() throws Exception {
        var thread = Thread.ofVirtual().start(() -> { });
        thread.join();
        thread.interrupt();
        assertTrue(thread.isInterrupted());
    }

    /**
     * Test termination with interrupt status set.
     */
    @Test
    void testInterrupt4() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            Thread.currentThread().interrupt();
        });
        thread.join();
        assertTrue(thread.isInterrupted());
    }

    /**
     * Test Thread.interrupt of thread blocked in Selector.select.
     */
    @Test
    void testInterrupt5() throws Exception {
        var exception = new AtomicReference<Exception>();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                try (var sel = Selector.open()) {
                    sel.select();
                    assertTrue(Thread.currentThread().isInterrupted());
                }
            } catch (Exception e) {
                exception.set(e);
            }
        });
        Thread.sleep(100);  // give time for thread to block
        thread.interrupt();
        thread.join();
        assertNull(exception.get());
    }

    /**
     * Test Thread.interrupt of thread parked in sleep.
     */
    @Test
    void testInterrupt6() throws Exception {
        var exception = new AtomicReference<Exception>();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                try {
                    Thread.sleep(60*1000);
                    fail("sleep not interrupted");
                } catch (InterruptedException e) {
                    // interrupt status should be reset
                    assertFalse(Thread.interrupted());
                }
            } catch (Exception e) {
                exception.set(e);
            }
        });
        await(thread, Thread.State.TIMED_WAITING);
        thread.interrupt();
        thread.join();
        assertNull(exception.get());
    }

    /**
     * Test Thread.interrupt of parked thread.
     */
    @Test
    void testInterrupt7() throws Exception {
        var exception = new AtomicReference<Exception>();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                LockSupport.park();
                assertTrue(Thread.currentThread().isInterrupted());
            } catch (Exception e) {
                exception.set(e);
            }
        });
        await(thread, Thread.State.WAITING);
        thread.interrupt();
        thread.join();
        assertNull(exception.get());
    }

    /**
     * Test trying to park with interrupt status set.
     */
    @Test
    void testInterrupt8() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            LockSupport.park();
            assertTrue(Thread.interrupted());
        });
    }

    /**
     * Test trying to wait with interrupt status set.
     */
    @Test
    void testInterrupt9() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            synchronized (lock) {
                try {
                    lock.wait();
                    fail("wait not interrupted");
                } catch (InterruptedException expected) {
                    assertFalse(Thread.interrupted());
                }
            }
        });
    }

    /**
     * Test trying to block with interrupt status set.
     */
    @Test
    void testInterrupt10() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            try (Selector sel = Selector.open()) {
                sel.select();
                assertTrue(Thread.interrupted());
            }
        });
    }

    /**
     * Test Thread.getName and setName from current thread, started without name.
     */
    @Test
    void testSetName1() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            assertTrue(me.getName().isEmpty());
            me.setName("fred");
            assertEquals("fred", me.getName());
        });
    }

    /**
     * Test Thread.getName and setName from current thread, started with name.
     */
    @Test
    void testSetName2() throws Exception {
        VThreadRunner.run("fred", () -> {
            Thread me = Thread.currentThread();
            assertEquals("fred", me.getName());
            me.setName("joe");
            assertEquals("joe", me.getName());
        });
    }

    /**
     * Test Thread.getName and setName from another thread.
     */
    @Test
    void testSetName3() throws Exception {
        var thread = Thread.ofVirtual().unstarted(LockSupport::park);
        assertTrue(thread.getName().isEmpty());

        // not started
        thread.setName("fred1");
        assertEquals("fred1", thread.getName());

        // started
        thread.start();
        try {
            assertEquals("fred1", thread.getName());
            thread.setName("fred2");
            assertEquals("fred2", thread.getName());
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }

        // terminated
        assertEquals("fred2", thread.getName());
        thread.setName("fred3");
        assertEquals("fred3", thread.getName());
    }

    /**
     * Test Thread.getPriority and setPriority from current thread.
     */
    @Test
    void testSetPriority1() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            assertEquals(Thread.NORM_PRIORITY, me.getPriority());

            me.setPriority(Thread.MAX_PRIORITY);
            assertEquals(Thread.NORM_PRIORITY, me.getPriority());

            me.setPriority(Thread.NORM_PRIORITY);
            assertEquals(Thread.NORM_PRIORITY, me.getPriority());

            me.setPriority(Thread.MIN_PRIORITY);
            assertEquals(Thread.NORM_PRIORITY, me.getPriority());

            assertThrows(IllegalArgumentException.class, () -> me.setPriority(-1));
        });
    }

    /**
     * Test Thread.getPriority and setPriority from another thread.
     */
    @Test
    void testSetPriority2() throws Exception {
        var thread = Thread.ofVirtual().unstarted(LockSupport::park);

        // not started
        assertEquals(Thread.NORM_PRIORITY, thread.getPriority());

        thread.setPriority(Thread.MAX_PRIORITY);
        assertEquals(Thread.NORM_PRIORITY, thread.getPriority());

        thread.setPriority(Thread.NORM_PRIORITY);
        assertEquals(Thread.NORM_PRIORITY, thread.getPriority());

        thread.setPriority(Thread.MIN_PRIORITY);
        assertEquals(Thread.NORM_PRIORITY, thread.getPriority());

        assertThrows(IllegalArgumentException.class, () -> thread.setPriority(-1));

        // running
        thread.start();
        try {
            assertEquals(Thread.NORM_PRIORITY, thread.getPriority());
            thread.setPriority(Thread.NORM_PRIORITY);

            thread.setPriority(Thread.MAX_PRIORITY);
            assertEquals(Thread.NORM_PRIORITY, thread.getPriority());

            thread.setPriority(Thread.NORM_PRIORITY);
            assertEquals(Thread.NORM_PRIORITY, thread.getPriority());

            thread.setPriority(Thread.MIN_PRIORITY);
            assertEquals(Thread.NORM_PRIORITY, thread.getPriority());

            assertThrows(IllegalArgumentException.class, () -> thread.setPriority(-1));

        } finally {
            LockSupport.unpark(thread);
        }
        thread.join();

        // terminated
        assertEquals(Thread.NORM_PRIORITY, thread.getPriority());
    }

    /**
     * Test Thread.isDaemon and setDaemon from current thread.
     */
    @Test
    void testSetDaemon1() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            assertTrue(me.isDaemon());
            assertThrows(IllegalThreadStateException.class, () -> me.setDaemon(true));
            assertThrows(IllegalArgumentException.class, () -> me.setDaemon(false));
        });
    }

    /**
     * Test Thread.isDaemon and setDaemon from another thread.
     */
    @Test
    void testSetDaemon2() throws Exception {
        var thread = Thread.ofVirtual().unstarted(LockSupport::park);

        // not started
        assertTrue(thread.isDaemon());
        thread.setDaemon(true);
        assertThrows(IllegalArgumentException.class, () -> thread.setDaemon(false));

        // running
        thread.start();
        try {
            assertTrue(thread.isDaemon());
            assertThrows(IllegalThreadStateException.class, () -> thread.setDaemon(true));
            assertThrows(IllegalArgumentException.class, () -> thread.setDaemon(false));
        } finally {
            LockSupport.unpark(thread);
        }
        thread.join();

        // terminated
        assertTrue(thread.isDaemon());
    }

    /**
     * Test Thread.yield releases carrier thread.
     */
    @Test
    void testYield1() throws Exception {
        assumeTrue(ThreadBuilders.supportsCustomScheduler(), "No support for custom schedulers");
        var list = new CopyOnWriteArrayList<String>();
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            Thread.Builder builder = ThreadBuilders.virtualThreadBuilder(scheduler);
            ThreadFactory factory = builder.factory();
            var thread = factory.newThread(() -> {
                list.add("A");
                var child = factory.newThread(() -> {
                    list.add("B");
                    Thread.yield();
                    list.add("B");
                });
                child.start();
                Thread.yield();
                list.add("A");
                try { child.join(); } catch (InterruptedException e) { }
            });
            thread.start();
            thread.join();
        }
        assertEquals(List.of("A", "B", "A", "B"), list);
    }

    /**
     * Test Thread.yield when thread is pinned by native frame.
     */
    @Test
    void testYield2() throws Exception {
        assumeTrue(ThreadBuilders.supportsCustomScheduler(), "No support for custom schedulers");
        var list = new CopyOnWriteArrayList<String>();
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            Thread.Builder builder = ThreadBuilders.virtualThreadBuilder(scheduler);
            ThreadFactory factory = builder.factory();
            var thread = factory.newThread(() -> {
                list.add("A");
                var child = factory.newThread(() -> {
                    list.add("B");
                });
                child.start();
                VThreadPinner.runPinned(() -> {
                    Thread.yield();   // pinned so will be a no-op
                    list.add("A");
                });
                try { child.join(); } catch (InterruptedException e) { }
            });
            thread.start();
            thread.join();
        }
        assertEquals(List.of("A", "A", "B"), list);
    }

    /**
     * Test Thread.yield does not consume the thread's parking permit.
     */
    @Test
    void testYield3() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            LockSupport.unpark(Thread.currentThread());
            Thread.yield();
            LockSupport.park();  // should not park
        });
        thread.join();
    }

    /**
     * Test Thread.yield does not make available the thread's parking permit.
     */
    @Test
    void testYield4() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            Thread.yield();
            LockSupport.park();  // should park
        });
        try {
            await(thread, Thread.State.WAITING);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread.onSpinWait.
     */
    @Test
    void testOnSpinWait() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            Thread.onSpinWait();
            assertTrue(Thread.currentThread() == me);
        });
    }

    /**
     * Test Thread.sleep(-1).
     */
    @Test
    void testSleep1() throws Exception {
        VThreadRunner.run(() -> {
            assertThrows(IllegalArgumentException.class, () -> Thread.sleep(-1));
            assertThrows(IllegalArgumentException.class, () -> Thread.sleep(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> Thread.sleep(0, -1));
            assertThrows(IllegalArgumentException.class, () -> Thread.sleep(0, 1_000_000));
        });
        VThreadRunner.run(() -> Thread.sleep(Duration.ofMillis(-1)));
    }

    /**
     * Test Thread.sleep(0).
     */
    @Test
    void testSleep2() throws Exception {
        VThreadRunner.run(() -> Thread.sleep(0));
        VThreadRunner.run(() -> Thread.sleep(0, 0));
        VThreadRunner.run(() -> Thread.sleep(Duration.ofMillis(0)));
    }

    /**
     * Tasks that sleep for 1 second.
     */
    static Stream<ThrowingRunnable> oneSecondSleepers() {
        return Stream.of(
                () -> Thread.sleep(1000),
                () -> Thread.sleep(Duration.ofSeconds(1))
        );
    }

    /**
     * Test Thread.sleep duration.
     */
    @ParameterizedTest
    @MethodSource("oneSecondSleepers")
    void testSleep3(ThrowingRunnable sleeper) throws Exception {
        VThreadRunner.run(() -> {
            long start = millisTime();
            sleeper.run();
            expectDuration(start, /*min*/900, /*max*/20_000);
        });
    }

    /**
     * Tasks that sleep for zero or longer duration.
     */
    static Stream<ThrowingRunnable> sleepers() {
        return Stream.of(
                () -> Thread.sleep(0),
                () -> Thread.sleep(0, 0),
                () -> Thread.sleep(1000),
                () -> Thread.sleep(1000, 0),
                () -> Thread.sleep(Duration.ofMillis(0)),
                () -> Thread.sleep(Duration.ofMillis(1000))
        );
    }

    /**
     * Test Thread.sleep with interrupt status set.
     */
    @ParameterizedTest
    @MethodSource("sleepers")
    void testSleep4(ThrowingRunnable sleeper) throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            try {
                sleeper.run();
                fail("sleep was not interrupted");
            } catch (InterruptedException e) {
                // expected
                assertFalse(me.isInterrupted());
            }
        });
    }

    /**
     * Test Thread.sleep with interrupt status set and a negative duration.
     */
    @Test
    void testSleep4() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            Thread.sleep(Duration.ofMillis(-1000));  // does nothing
            assertTrue(me.isInterrupted());
        });
    }

    /**
     * Tasks that sleep for a long time.
     */
    static Stream<ThrowingRunnable> longSleepers() {
        return Stream.of(
                () -> Thread.sleep(20_000),
                () -> Thread.sleep(20_000, 0),
                () -> Thread.sleep(Duration.ofSeconds(20))
        );
    }

    /**
     * Test interrupting Thread.sleep.
     */
    @ParameterizedTest
    @MethodSource("longSleepers")
    void testSleep5(ThrowingRunnable sleeper) throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 100);
            try {
                sleeper.run();
                fail("sleep was not interrupted");
            } catch (InterruptedException e) {
                // interrupt status should be cleared
                assertFalse(t.isInterrupted());
            }
        });
    }

    /**
     * Test that Thread.sleep does not disrupt parking permit.
     */
    @Test
    void testSleep6() throws Exception {
        VThreadRunner.run(() -> {
            LockSupport.unpark(Thread.currentThread());

            long start = millisTime();
            Thread.sleep(1000);
            expectDuration(start, /*min*/900, /*max*/20_000);

            // check that parking permit was not consumed
            LockSupport.park();
        });
    }

    /**
     * Test that Thread.sleep is not disrupted by unparking thread.
     */
    @Test
    void testSleep7() throws Exception {
        AtomicReference<Exception> exc = new AtomicReference<>();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                long start = millisTime();
                Thread.sleep(1000);
                expectDuration(start, /*min*/900, /*max*/20_000);
            } catch (Exception e) {
                exc.set(e);
            }

        });
        // attempt to disrupt sleep
        for (int i = 0; i < 5; i++) {
            Thread.sleep(20);
            LockSupport.unpark(thread);
        }
        thread.join();
        Exception e = exc.get();
        if (e != null) {
            throw e;
        }
    }

    /**
     * Test Thread.sleep when pinned.
     */
    @Test
    void testSleep8() throws Exception {
        VThreadPinner.runPinned(() -> {
            long start = millisTime();
            Thread.sleep(1000);
            expectDuration(start, /*min*/900, /*max*/20_000);
        });
    }

    /**
     * Test Thread.sleep when pinned and with interrupt status set.
     */
    @Test
    void testSleep9() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            try {
                VThreadPinner.runPinned(() -> {
                    Thread.sleep(2000);
                });
                fail("sleep not interrupted");
            } catch (InterruptedException e) {
                // expected
                assertFalse(me.isInterrupted());
            }
        });
    }

    /**
     * Test interrupting Thread.sleep when pinned.
     */
    @Test
    void testSleep10() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 100);
            try {
                VThreadPinner.runPinned(() -> {
                    Thread.sleep(20 * 1000);
                });
                fail("sleep not interrupted");
            } catch (InterruptedException e) {
                // interrupt status should be cleared
                assertFalse(t.isInterrupted());
            }
        });
    }

    /**
     * Test Thread.sleep(null).
     */
    @Test
    void testSleep11() throws Exception {
        assertThrows(NullPointerException.class, () -> Thread.sleep(null));
        VThreadRunner.run(() -> {
            assertThrows(NullPointerException.class, () -> Thread.sleep(null));
        });
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
    private static void expectDuration(long start, long min, long max) {
        long duration = millisTime() - start;
        assertTrue(duration >= min,
                "Duration " + duration + "ms, expected >= " + min + "ms");
        assertTrue(duration <= max,
                "Duration " + duration + "ms, expected <= " + max + "ms");
    }

    /**
     * Test Thread.xxxContextClassLoader from the current thread.
     */
    @Test
    void testContextClassLoader1() throws Exception {
        ClassLoader loader = new ClassLoader() { };
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.setContextClassLoader(loader);
            assertTrue(t.getContextClassLoader() == loader);
        });
    }

    /**
     * Test inheriting initial value of TCCL from platform thread.
     */
    @Test
    void testContextClassLoader2() throws Exception {
        ClassLoader loader = new ClassLoader() { };
        Thread t = Thread.currentThread();
        ClassLoader savedLoader = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            VThreadRunner.run(() -> {
                assertTrue(Thread.currentThread().getContextClassLoader() == loader);
            });
        } finally {
            t.setContextClassLoader(savedLoader);
        }
    }

    /**
     * Test inheriting initial value of TCCL from virtual thread.
     */
    @Test
    void testContextClassLoader3() throws Exception {
        VThreadRunner.run(() -> {
            ClassLoader loader = new ClassLoader() { };
            Thread.currentThread().setContextClassLoader(loader);
            VThreadRunner.run(() -> {
                assertTrue(Thread.currentThread().getContextClassLoader() == loader);
            });
        });
    }

    /**
     * Test inheriting initial value of TCCL through an intermediate virtual thread.
     */
    @Test
    void testContextClassLoader4() throws Exception {
        ClassLoader loader = new ClassLoader() { };
        Thread t = Thread.currentThread();
        ClassLoader savedLoader = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            VThreadRunner.run(() -> {
                VThreadRunner.run(() -> {
                    assertTrue(Thread.currentThread().getContextClassLoader() == loader);
                });
            });
        } finally {
            t.setContextClassLoader(savedLoader);
        }
    }

    /**
     * Test Thread.xxxContextClassLoader when thread does not inherit the
     * initial value of inheritable thread locals.
     */
    @Test
    void testContextClassLoader5() throws Exception {
        VThreadRunner.run(() -> {
            ClassLoader loader = new ClassLoader() { };
            Thread.currentThread().setContextClassLoader(loader);
            int characteristics = VThreadRunner.NO_INHERIT_THREAD_LOCALS;
            VThreadRunner.run(characteristics, () -> {
                Thread t = Thread.currentThread();
                assertTrue(t.getContextClassLoader() == ClassLoader.getSystemClassLoader());
                t.setContextClassLoader(loader);
                assertTrue(t.getContextClassLoader() == loader);
            });
        });
    }

    /**
     * Test Thread.setUncaughtExceptionHandler.
     */
    @Test
    void testUncaughtExceptionHandler1() throws Exception {
        class FooException extends RuntimeException { }
        var handler = new CapturingUHE();
        Thread thread = Thread.ofVirtual().start(() -> {
            Thread me = Thread.currentThread();
            assertTrue(me.getUncaughtExceptionHandler() == me.getThreadGroup());
            me.setUncaughtExceptionHandler(handler);
            assertTrue(me.getUncaughtExceptionHandler() == handler);
            throw new FooException();
        });
        thread.join();
        assertInstanceOf(FooException.class, handler.exception());
        assertEquals(thread, handler.thread());
        assertNull(thread.getUncaughtExceptionHandler());
    }

    /**
     * Test default UncaughtExceptionHandler.
     */
    @Test
    void testUncaughtExceptionHandler2() throws Exception {
        class FooException extends RuntimeException { }
        var handler = new CapturingUHE();
        Thread.UncaughtExceptionHandler savedHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread thread;
        try {
            thread = Thread.ofVirtual().start(() -> {
                Thread me = Thread.currentThread();
                throw new FooException();
            });
            thread.join();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(savedHandler);  // restore
        }
        assertInstanceOf(FooException.class, handler.exception());
        assertEquals(thread, handler.thread());
        assertNull(thread.getUncaughtExceptionHandler());
    }

    /**
     * Test Thread and default UncaughtExceptionHandler set.
     */
    @Test
    void testUncaughtExceptionHandler3() throws Exception {
        class FooException extends RuntimeException { }
        var defaultHandler = new CapturingUHE();
        var threadHandler = new CapturingUHE();
        Thread.UncaughtExceptionHandler savedHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(defaultHandler);
        Thread thread;
        try {
            thread = Thread.ofVirtual().start(() -> {
                Thread me = Thread.currentThread();
                assertTrue(me.getUncaughtExceptionHandler() == me.getThreadGroup());
                me.setUncaughtExceptionHandler(threadHandler);
                assertTrue(me.getUncaughtExceptionHandler() == threadHandler);
                throw new FooException();
            });
            thread.join();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(savedHandler);  // restore
        }
        assertInstanceOf(FooException.class, threadHandler.exception());
        assertNull(defaultHandler.exception());
        assertEquals(thread, threadHandler.thread());
        assertNull(thread.getUncaughtExceptionHandler());
    }

    /**
     * Test no Thread or default UncaughtExceptionHandler set.
     */
    @Test
    void testUncaughtExceptionHandler4() throws Exception {
        Thread.UncaughtExceptionHandler savedHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(null);
        try {
            class FooException extends RuntimeException { }
            Thread thread = Thread.ofVirtual().start(() -> {
                throw new FooException();
            });
            thread.join();
            assertNull(thread.getUncaughtExceptionHandler());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(savedHandler);
        }
    }

    /**
     * Test Thread::threadId and getId.
     */
    @Test
    void testThreadId1() throws Exception {
        record ThreadIds(long threadId, long id) { }
        var ref = new AtomicReference<ThreadIds>();

        Thread vthread = Thread.ofVirtual().unstarted(() -> {
            Thread thread = Thread.currentThread();
            ref.set(new ThreadIds(thread.threadId(), thread.getId()));
            LockSupport.park();
        });

        // unstarted
        long tid = vthread.threadId();

        // running
        ThreadIds tids;
        vthread.start();
        try {
            while ((tids = ref.get()) == null) {
                Thread.sleep(10);
            }
            assertTrue(tids.threadId() == tid);
            assertTrue(tids.id() == tid);
        } finally {
            LockSupport.unpark(vthread);
            vthread.join();
        }

        // terminated
        assertTrue(vthread.threadId() == tid);
        assertTrue(vthread.getId() == tid);
    }

    /**
     * Test that each Thread has a unique ID
     */
    @Test
    void testThreadId2() throws Exception {
        // thread ID should be unique
        long tid1 = Thread.ofVirtual().unstarted(() -> { }).threadId();
        long tid2 = Thread.ofVirtual().unstarted(() -> { }).threadId();
        long tid3 = Thread.currentThread().threadId();
        assertFalse(tid1 == tid2);
        assertFalse(tid1 == tid3);
        assertFalse(tid2 == tid3);
    }

    /**
     * Test Thread::getState when thread is new/unstarted.
     */
    @Test
    void testGetState1() {
        var thread = Thread.ofVirtual().unstarted(() -> { });
        assertEquals(Thread.State.NEW, thread.getState());
    }

    /**
     * Test Thread::getState when thread is terminated.
     */
    @Test
    void testGetState2() throws Exception {
        var thread = Thread.ofVirtual().start(() -> { });
        thread.join();
        assertEquals(Thread.State.TERMINATED, thread.getState());
    }

    /**
     * Test Thread::getState when thread is runnable (mounted).
     */
    @Test
    void testGetState3() throws Exception {
        var started = new CountDownLatch(1);
        var done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            started.countDown();

            // spin until done
            while (!done.get()) {
                Thread.onSpinWait();
            }
        });
        try {
            // wait for thread to start
            started.await();

            // thread should be runnable
            assertEquals(Thread.State.RUNNABLE, thread.getState());
        } finally {
            done.set(true);
            thread.join();
        }
    }

    /**
     * Test Thread::getState when thread is runnable (not mounted).
     */
    @Test
    void testGetState4() throws Exception {
        assumeTrue(ThreadBuilders.supportsCustomScheduler(), "No support for custom schedulers");
        AtomicBoolean completed = new AtomicBoolean();
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            Thread.Builder builder = ThreadBuilders.virtualThreadBuilder(scheduler);
            Thread t1 = builder.start(() -> {
                Thread t2 = builder.unstarted(LockSupport::park);
                assertEquals(Thread.State.NEW, t2.getState());

                // start t2 to make it runnable
                t2.start();
                try {
                    assertEquals(Thread.State.RUNNABLE, t2.getState());

                    // yield to allow t2 to run and park
                    Thread.yield();
                    assertEquals(Thread.State.WAITING, t2.getState());
                } finally {
                    // unpark t2 to make it runnable again
                    LockSupport.unpark(t2);
                }

                // t2 should be runnable (not mounted)
                assertEquals(Thread.State.RUNNABLE, t2.getState());

                completed.set(true);
            });
            t1.join();
        }
        assertTrue(completed.get() == true);
    }

    /**
     * Test Thread::getState when thread is waiting to enter a monitor.
     */
    @Test
    void testGetState5() throws Exception {
        var started = new CountDownLatch(1);
        var thread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            synchronized (lock) { }
        });
        synchronized (lock) {
            thread.start();
            started.await();

            // wait for thread to block
            await(thread, Thread.State.BLOCKED);
        }
        thread.join();
    }

    /**
     * Test Thread::getState when thread is waiting in Object.wait.
     */
    @Test
    void testGetState6() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                try { lock.wait(); } catch (InterruptedException e) { }
            }
        });
        try {
            // wait for thread to wait
            await(thread, Thread.State.WAITING);
        } finally {
            thread.interrupt();
            thread.join();
        }
    }

    /**
     * Test Thread::getState when thread is waiting in Object.wait(millis).
     */
    @Test
    void testGetState7() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                try {
                    lock.wait(Long.MAX_VALUE);
                } catch (InterruptedException e) { }
            }
        });
        try {
            // wait for thread to wait
            await(thread, Thread.State.TIMED_WAITING);
        } finally {
            thread.interrupt();
            thread.join();
        }
    }

    /**
     * Test Thread::getState when thread is parked.
     */
    @Test
    void testGetState8() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        try {
            await(thread, Thread.State.WAITING);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread::getState when thread is timed parked.
     */
    @Test
    void testGetState9() throws Exception {
        var thread = Thread.ofVirtual().start(() -> LockSupport.parkNanos(Long.MAX_VALUE));
        try {
            await(thread, Thread.State.TIMED_WAITING);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread::getState when thread is parked while holding a monitor.
     */
    @Test
    void testGetState10() throws Exception {
        var started = new CountDownLatch(1);
        var done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            started.countDown();
            synchronized (lock) {
                while (!done.get()) {
                    LockSupport.park();
                }
            }
        });
        try {
            // wait for thread to start
            started.await();

            // wait for thread to park
            await(thread, Thread.State.WAITING);
        } finally {
            done.set(true);
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread::getState when thread is timed parked while holding a monitor.
     */
    @Test
    void testGetState11() throws Exception {
        var started = new CountDownLatch(1);
        var done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            started.countDown();
            synchronized (lock) {
                while (!done.get()) {
                    LockSupport.parkNanos(Long.MAX_VALUE);
                }
            }
        });
        try {
            // wait for thread to start
            started.await();

            // wait for thread to park
            await(thread, Thread.State.TIMED_WAITING);
        } finally {
            done.set(true);
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread::isAlive.
     */
    @Test
    void testIsAlive1() throws Exception {
        // unstarted
        var thread = Thread.ofVirtual().unstarted(LockSupport::park);
        assertFalse(thread.isAlive());

        // started
        thread.start();
        try {
            assertTrue(thread.isAlive());
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }

        // terminated
        assertFalse(thread.isAlive());
    }

    /**
     * Test Thread.holdsLock when lock not held.
     */
    @Test
    void testHoldsLock1() throws Exception {
        VThreadRunner.run(() -> {
            var lock = new Object();
            assertFalse(Thread.holdsLock(lock));
        });
    }

    /**
     * Test Thread.holdsLock when lock held.
     */
    @Test
    void testHoldsLock2() throws Exception {
        VThreadRunner.run(() -> {
            var lock = new Object();
            synchronized (lock) {
                assertTrue(Thread.holdsLock(lock));
            }
        });
    }

    /**
     * Test Thread::getStackTrace on unstarted thread.
     */
    @Test
    void testGetStackTrace1() {
        var thread = Thread.ofVirtual().unstarted(() -> { });
        StackTraceElement[] stack = thread.getStackTrace();
        assertTrue(stack.length == 0);
    }

    /**
     * Test Thread::getStackTrace on thread that has been started but has not run.
     */
    @Test
    void testGetStackTrace2() throws Exception {
        assumeTrue(ThreadBuilders.supportsCustomScheduler(), "No support for custom schedulers");
        Executor scheduler = task -> { };
        Thread.Builder builder = ThreadBuilders.virtualThreadBuilder(scheduler);
        Thread thread = builder.start(() -> { });
        StackTraceElement[] stack = thread.getStackTrace();
        assertTrue(stack.length == 0);
    }

    /**
     * Test Thread::getStackTrace on running thread.
     */
    @Test
    void testGetStackTrace3() throws Exception {
        var sel = Selector.open();
        var thread = Thread.ofVirtual().start(() -> {
            try { sel.select(); } catch (Exception e) { }
        });
        try {
            while (!contains(thread.getStackTrace(), "select")) {
                assertTrue(thread.isAlive());
                Thread.sleep(20);
            }
        } finally {
            sel.close();
            thread.join();
        }
    }

    /**
     * Test Thread::getStackTrace on thread waiting in Object.wait.
     */
    @Test
    void testGetStackTrace4() throws Exception {
        assumeTrue(ThreadBuilders.supportsCustomScheduler(), "No support for custom schedulers");
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            AtomicReference<Thread> ref = new AtomicReference<>();
            Executor scheduler = task -> {
                pool.submit(() -> {
                    ref.set(Thread.currentThread());
                    task.run();
                });
            };

            Thread.Builder builder = ThreadBuilders.virtualThreadBuilder(scheduler);
            Thread vthread = builder.start(() -> {
                synchronized (lock) {
                    try {
                        lock.wait();
                    } catch (Exception e) { }
                }
            });

            // get carrier Thread
            Thread carrier;
            while ((carrier = ref.get()) == null) {
                Thread.sleep(20);
            }

            // wait for virtual thread to block in wait
            await(vthread, Thread.State.WAITING);

            // get stack trace of both carrier and virtual thread
            StackTraceElement[] carrierStackTrace = carrier.getStackTrace();
            StackTraceElement[] vthreadStackTrace = vthread.getStackTrace();

            // allow virtual thread to terminate
            synchronized (lock) {
                lock.notifyAll();
            }

            // check carrier thread's stack trace
            assertTrue(contains(carrierStackTrace, "java.util.concurrent.ForkJoinPool.runWorker"));
            assertFalse(contains(carrierStackTrace, "java.lang.Object.wait"));

            // check virtual thread's stack trace
            assertFalse(contains(vthreadStackTrace, "java.util.concurrent.ForkJoinPool.runWorker"));
            assertTrue(contains(vthreadStackTrace, "java.lang.Object.wait"));
        }
    }

    /**
     * Test Thread::getStackTrace on parked thread.
     */
    @Test
    void testGetStackTrace5() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        await(thread, Thread.State.WAITING);
        try {
            StackTraceElement[] stack = thread.getStackTrace();
            assertTrue(contains(stack, "LockSupport.park"));
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread::getStackTrace on timed-parked thread.
     */
    @Test
    void testGetStackTrace6() throws Exception {
        var thread = Thread.ofVirtual().start(() -> {
            LockSupport.parkNanos(Long.MAX_VALUE);
        });
        await(thread, Thread.State.TIMED_WAITING);
        try {
            StackTraceElement[] stack = thread.getStackTrace();
            assertTrue(contains(stack, "LockSupport.parkNanos"));
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread::getStackTrace on parked thread that is pinned.
     */
    @Test
    void testGetStackTrace7() throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            VThreadPinner.runPinned(() -> {
                while (!done.get()) {
                    LockSupport.park();
                }
            });
        });
        await(thread, Thread.State.WAITING);
        try {
            StackTraceElement[] stack = thread.getStackTrace();
            assertTrue(contains(stack, "LockSupport.park"));
        } finally {
            done.set(true);
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread::getStackTrace on timed-parked thread that is pinned.
     */
    @Test
    void testGetStackTrace8() throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            VThreadPinner.runPinned(() -> {
                while (!done.get()) {
                    LockSupport.parkNanos(Long.MAX_VALUE);
                }
            });
        });
        await(thread, Thread.State.TIMED_WAITING);
        try {
            StackTraceElement[] stack = thread.getStackTrace();
            assertTrue(contains(stack, "LockSupport.parkNanos"));
        } finally {
            done.set(true);
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test Thread::getStackTrace on terminated thread.
     */
    @Test
    void testGetStackTrace9() throws Exception {
        var thread = Thread.ofVirtual().start(() -> { });
        thread.join();
        StackTraceElement[] stack = thread.getStackTrace();
        assertTrue(stack.length == 0);
    }

    /**
     * Test that Thread.getAllStackTraces does not include virtual threads.
     */
    @Test
    void testGetAllStackTraces1() throws Exception {
        VThreadRunner.run(() -> {
            Set<Thread> threads = Thread.getAllStackTraces().keySet();
            assertFalse(threads.stream().anyMatch(Thread::isVirtual));
        });
    }

    /**
     * Test that Thread.getAllStackTraces includes carrier threads.
     */
    @Test
    void testGetAllStackTraces2() throws Exception {
        assumeTrue(ThreadBuilders.supportsCustomScheduler(), "No support for custom schedulers");
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            AtomicReference<Thread> ref = new AtomicReference<>();
            Executor scheduler = task -> {
                pool.submit(() -> {
                    ref.set(Thread.currentThread());
                    task.run();
                });
            };

            Thread.Builder builder = ThreadBuilders.virtualThreadBuilder(scheduler);
            Thread vthread = builder.start(() -> {
                synchronized (lock) {
                    try {
                        lock.wait();
                    } catch (Exception e) { }
                }
            });

            // get carrier Thread
            Thread carrier;
            while ((carrier = ref.get()) == null) {
                Thread.sleep(20);
            }

            // wait for virtual thread to block in wait
            await(vthread, Thread.State.WAITING);

            // get all stack traces
            Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();

            // allow virtual thread to terminate
            synchronized (lock) {
                lock.notifyAll();
            }

            // get stack trace for the carrier thread
            StackTraceElement[] stackTrace = map.get(carrier);
            assertNotNull(stackTrace);
            assertTrue(contains(stackTrace, "java.util.concurrent.ForkJoinPool"));
            assertFalse(contains(stackTrace, "java.lang.Object.wait"));

            // there should be no stack trace for the virtual thread
            assertNull(map.get(vthread));
        }
    }

    private boolean contains(StackTraceElement[] stack, String expected) {
        return Stream.of(stack)
                .map(Object::toString)
                .anyMatch(s -> s.contains(expected));
    }

    /**
     * Test Thread::getThreadGroup on virtual thread created by platform thread.
     */
    @Test
    void testThreadGroup1() throws Exception {
        var thread = Thread.ofVirtual().unstarted(LockSupport::park);
        var vgroup = thread.getThreadGroup();
        thread.start();
        try {
            assertEquals(vgroup, thread.getThreadGroup());
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
        assertNull(thread.getThreadGroup());
    }

    /**
     * Test Thread::getThreadGroup on platform thread created by virtual thread.
     */
    @Test
    void testThreadGroup2() throws Exception {
        VThreadRunner.run(() -> {
            ThreadGroup vgroup = Thread.currentThread().getThreadGroup();
            Thread child = new Thread(() -> { });
            ThreadGroup group = child.getThreadGroup();
            assertEquals(vgroup, group);
        });
    }

    /**
     * Test ThreadGroup returned by Thread::getThreadGroup and subgroup
     * created with 2-arg ThreadGroup constructor.
     */
    @Test
    void testThreadGroup3() throws Exception {
        var ref = new AtomicReference<ThreadGroup>();
        var thread = Thread.startVirtualThread(() -> {
            ref.set(Thread.currentThread().getThreadGroup());
        });
        thread.join();

        ThreadGroup vgroup = ref.get();
        assertEquals(Thread.MAX_PRIORITY, vgroup.getMaxPriority());

        ThreadGroup group = new ThreadGroup(vgroup, "group");
        assertTrue(group.getParent() == vgroup);
        assertEquals(Thread.MAX_PRIORITY, group.getMaxPriority());

        vgroup.setMaxPriority(Thread.MAX_PRIORITY - 1);
        assertEquals(Thread.MAX_PRIORITY, vgroup.getMaxPriority());
        assertEquals(Thread.MAX_PRIORITY - 1, group.getMaxPriority());

        vgroup.setMaxPriority(Thread.MIN_PRIORITY);
        assertEquals(Thread.MAX_PRIORITY, vgroup.getMaxPriority());
        assertEquals(Thread.MIN_PRIORITY, group.getMaxPriority());
    }

    /**
     * Test ThreadGroup returned by Thread::getThreadGroup and subgroup
     * created with 1-arg ThreadGroup constructor.
     */
    @Test
    void testThreadGroup4() throws Exception {
        VThreadRunner.run(() -> {
            ThreadGroup vgroup = Thread.currentThread().getThreadGroup();
            assertEquals(Thread.MAX_PRIORITY, vgroup.getMaxPriority());

            ThreadGroup group = new ThreadGroup("group");
            assertEquals(vgroup, group.getParent());
            assertEquals(Thread.MAX_PRIORITY, group.getMaxPriority());

            vgroup.setMaxPriority(Thread.MAX_PRIORITY - 1);
            assertEquals(Thread.MAX_PRIORITY, vgroup.getMaxPriority());
            assertEquals(Thread.MAX_PRIORITY - 1, group.getMaxPriority());

            vgroup.setMaxPriority(Thread.MIN_PRIORITY);
            assertEquals(Thread.MAX_PRIORITY, vgroup.getMaxPriority());
            assertEquals(Thread.MIN_PRIORITY, group.getMaxPriority());
        });
    }

    /**
     * Test Thread.enumerate(false).
     */
    @Test
    void testEnumerate1() throws Exception {
        VThreadRunner.run(() -> {
            ThreadGroup vgroup = Thread.currentThread().getThreadGroup();
            Thread[] threads = new Thread[100];
            int n = vgroup.enumerate(threads, /*recurse*/false);
            assertFalse(Arrays.stream(threads, 0, n).anyMatch(Thread::isVirtual));
        });
    }

    /**
     * Test Thread.enumerate(true).
     */
    @Test
    void testEnumerate2() throws Exception {
        VThreadRunner.run(() -> {
            ThreadGroup vgroup = Thread.currentThread().getThreadGroup();
            Thread[] threads = new Thread[100];
            int n = vgroup.enumerate(threads, /*recurse*/true);
            assertFalse(Arrays.stream(threads, 0, n).anyMatch(Thread::isVirtual));
        });
    }

    /**
     * Test equals and hashCode.
     */
    @Test
    void testEqualsAndHashCode() throws Exception {
        Thread vthread1 = Thread.ofVirtual().unstarted(LockSupport::park);
        Thread vthread2 = Thread.ofVirtual().unstarted(LockSupport::park);

        // unstarted
        assertTrue(vthread1.equals(vthread1));
        assertTrue(vthread2.equals(vthread2));
        assertFalse(vthread1.equals(vthread2));
        assertFalse(vthread2.equals(vthread1));
        int hc1 = vthread1.hashCode();
        int hc2 = vthread2.hashCode();

        vthread1.start();
        vthread2.start();
        try {
            // started, maybe running or parked
            assertTrue(vthread1.equals(vthread1));
            assertTrue(vthread2.equals(vthread2));
            assertFalse(vthread1.equals(vthread2));
            assertFalse(vthread2.equals(vthread1));
            assertTrue(vthread1.hashCode() == hc1);
            assertTrue(vthread2.hashCode() == hc2);
        } finally {
            LockSupport.unpark(vthread1);
            LockSupport.unpark(vthread2);
        }
        vthread1.join();
        vthread2.join();

        // terminated
        assertTrue(vthread1.equals(vthread1));
        assertTrue(vthread2.equals(vthread2));
        assertFalse(vthread1.equals(vthread2));
        assertFalse(vthread2.equals(vthread1));
        assertTrue(vthread1.hashCode() == hc1);
        assertTrue(vthread2.hashCode() == hc2);
    }

    /**
     * Test toString on unstarted thread.
     */
    @Test
    void testToString1() {
        Thread thread = Thread.ofVirtual().unstarted(() -> { });
        thread.setName("fred");
        assertTrue(thread.toString().contains("fred"));
    }

    /**
     * Test toString on running thread.
     */
    @Test
    void testToString2() throws Exception {
        VThreadRunner.run(() -> {
            Thread me = Thread.currentThread();
            me.setName("fred");
            assertTrue(me.toString().contains("fred"));
        });
    }

    /**
     * Test toString on parked thread.
     */
    @Test
    void testToString3() throws Exception {
        Thread thread = Thread.ofVirtual().start(() -> {
            Thread me = Thread.currentThread();
            me.setName("fred");
            LockSupport.park();
        });
        await(thread, Thread.State.WAITING);
        try {
            assertTrue(thread.toString().contains("fred"));
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test toString on terminated thread.
     */
    @Test
    void testToString4() throws Exception {
        Thread thread = Thread.ofVirtual().start(() -> {
            Thread me = Thread.currentThread();
            me.setName("fred");
        });
        thread.join();
        assertTrue(thread.toString().contains("fred"));
    }

    /**
     * Thread.UncaughtExceptionHandler that captures the first exception thrown.
     */
    private static class CapturingUHE implements Thread.UncaughtExceptionHandler {
        Thread thread;
        Throwable exception;
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            synchronized (this) {
                if (thread == null) {
                    this.thread = t;
                    this.exception = e;
                }
            }
        }
        Thread thread() {
            synchronized (this) {
                return thread;
            }
        }
        Throwable exception() {
            synchronized (this) {
                return exception;
            }
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
     * Schedule a thread to be interrupted after a delay.
     */
    private void scheduleInterrupt(Thread thread, long delayInMillis) {
        scheduler.schedule(thread::interrupt, delayInMillis, TimeUnit.MILLISECONDS);
    }
}
