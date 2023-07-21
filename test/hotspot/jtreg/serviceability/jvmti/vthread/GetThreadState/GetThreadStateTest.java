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

/*
 * @test id=default
 * @bug 8312498
 * @summary Basic test for JVMTI GetThreadState with virtual threads
 * @run junit/othervm/native GetThreadStateTest
 */

/*
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @run junit/othervm/native -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations GetThreadStateTest
 */

import java.lang.ref.Reference;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GetThreadStateTest {

    @BeforeAll
    static void setup() {
        System.loadLibrary("GetThreadStateTest");
        init();
    }

    /**
     * Test unstarted thread.
     */
    @Test
    void testUnstarted() {
        var thread = Thread.ofVirtual().unstarted(() -> { });
        check(thread, 0);
    }

    /**
     * Test runnable (mounted).
     */
    @Test
    void testRunnable() throws Exception {
        var started = new AtomicBoolean();
        var done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            started.set(true);
            while (!done.get()) {
                Thread.onSpinWait();
            }
        });
        try {
            // wait for thread to start execution
            while (!started.get()) {
                Thread.sleep(10);
            }
            // thread should be runnable
            int expected = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_RUNNABLE;
            check(thread, expected);

            // runnable + interrupted
            thread.interrupt();
            check(thread, expected | JVMTI_THREAD_STATE_INTERRUPTED);
        } finally {
            done.set(true);
            thread.join();
        }
    }

    /**
     * Test terminated thread.
     */
    @Test
    void testTerminated() throws Exception {
        var thread = Thread.ofVirtual().start(() -> { });
        thread.join();
        check(thread, JVMTI_THREAD_STATE_TERMINATED);
    }

    /**
     * Test waiting to enter a monitor.
     */
    @Test
    void testMonitorEnter() throws Exception {
        Object lock = new Object();
        var thread = Thread.ofVirtual().unstarted(() -> {
            synchronized (lock) { }
        });
        try {
            synchronized (lock) {
                thread.start();
                awaitBlocked(thread);
                int expected = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
                check(thread, expected);

                // waiting to enter + interrupted
                thread.interrupt();
                check(thread, expected | JVMTI_THREAD_STATE_INTERRUPTED);
            }
            thread.join();
        } finally {
            Reference.reachabilityFence(lock);
        }
    }

    /**
     * Test waiting in untimed-Object.wait.
     */
    @Test
    void testUntimedWait() throws Exception {
        Object lock = new Object();
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) { }
            }
        });
        try {
            awaitParked(thread);
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_INDEFINITELY |
                    JVMTI_THREAD_STATE_IN_OBJECT_WAIT;
            check(thread, expected);

            // notify so that virtual thread is waiting to re-enter monitor
            synchronized (lock) {
                lock.notifyAll();
                expected = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
                check(thread, expected);

                // waiting to re-enter + interrupted
                thread.interrupt();
                check(thread, expected | JVMTI_THREAD_STATE_INTERRUPTED);
            }
        } finally {
            thread.interrupt();
            thread.join();
            Reference.reachabilityFence(lock);
        }
    }

    /**
     * Test waiting in timed-Object.wait.
     */
    @Test
    void testTimedWait() throws Exception {
        Object lock = new Object();
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                try {
                    lock.wait(Long.MAX_VALUE);
                } catch (InterruptedException e) { }
            }
        });
        try {
            awaitParked(thread);
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT |
                    JVMTI_THREAD_STATE_IN_OBJECT_WAIT;
            check(thread, expected);

            // notify so that virtual thread is waiting to re-enter monitor
            synchronized (lock) {
                lock.notifyAll();
                expected = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
                check(thread, expected);

                // waiting to re-enter + interrupted
                thread.interrupt();
                check(thread, expected | JVMTI_THREAD_STATE_INTERRUPTED);
            }
        } finally {
            thread.interrupt();
            thread.join();
            Reference.reachabilityFence(lock);
        }
    }

    /**
     * Test untimed-park.
     */
    @Test
    void testUntimedPark() throws Exception {
        var thread = Thread.ofVirtual().start(LockSupport::park);
        try {
            awaitParked(thread);
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_INDEFINITELY |
                    JVMTI_THREAD_STATE_PARKED;
            check(thread, expected);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test timed parked.
     */
    @Test
    void testTimedPark() throws Exception {
        var thread = Thread.ofVirtual().start(() -> LockSupport.parkNanos(Long.MAX_VALUE));
        try {
            awaitParked(thread);
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT |
                    JVMTI_THREAD_STATE_PARKED;
            check(thread, expected);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test untimed-park while holding a monitor.
     */
    @Test
    void testUntimedParkWhenPinned() throws Exception {
        Object lock = new Object();
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                LockSupport.park();
            }
        });
        try {
            awaitParked(thread);
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_INDEFINITELY |
                    JVMTI_THREAD_STATE_PARKED;
            check(thread, expected);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
            Reference.reachabilityFence(lock);
        }
    }

    /**
     * Test timed-park while holding a monitor.
     */
    @Test
    void testTimedParkWhenPinned() throws Exception {
        Object lock = new Object();
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                LockSupport.parkNanos(Long.MAX_VALUE);
            }
        });
        try {
            awaitParked(thread);
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT |
                    JVMTI_THREAD_STATE_PARKED;
            check(thread, expected);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
            Reference.reachabilityFence(lock);
        }
    }
    
    private static void check(Thread thread, int expected) {
        int state = jvmtiState(thread);
        System.err.format("  state=0x%x (%s)%n", state, jvmtiStateToString(state));
        assertEquals(expected, state);
    }

    private static final int JVMTI_THREAD_STATE_ALIVE = 0x0001;
    private static final int JVMTI_THREAD_STATE_TERMINATED = 0x0002;
    private static final int JVMTI_THREAD_STATE_RUNNABLE = 0x0004;
    private static final int JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400;
    private static final int JVMTI_THREAD_STATE_WAITING = 0x0080;
    private static final int JVMTI_THREAD_STATE_WAITING_INDEFINITELY = 0x0010;
    private static final int JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT = 0x0020;
    private static final int JVMTI_THREAD_STATE_SLEEPING = 0x0040;
    private static final int JVMTI_THREAD_STATE_IN_OBJECT_WAIT = 0x0100;
    private static final int JVMTI_THREAD_STATE_PARKED = 0x0200;
    private static final int JVMTI_THREAD_STATE_SUSPENDED = 0x100000;
    private static final int JVMTI_THREAD_STATE_INTERRUPTED = 0x200000;
    private static final int JVMTI_THREAD_STATE_IN_NATIVE = 0x400000;

    private static native void init();
    private static native int jvmtiState(Thread thread);
    
    private static String jvmtiStateToString(int state) {
        StringJoiner sj = new StringJoiner(" | ");
        if ((state & JVMTI_THREAD_STATE_ALIVE) != 0)
            sj.add("JVMTI_THREAD_STATE_ALIVE");
        if ((state & JVMTI_THREAD_STATE_TERMINATED) != 0)
            sj.add("JVMTI_THREAD_STATE_TERMINATED");
        if ((state & JVMTI_THREAD_STATE_RUNNABLE) != 0)
            sj.add("JVMTI_THREAD_STATE_RUNNABLE");
        if ((state & JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER) != 0)
            sj.add("JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER");
        if ((state & JVMTI_THREAD_STATE_WAITING) != 0)
            sj.add("JVMTI_THREAD_STATE_WAITING");
        if ((state & JVMTI_THREAD_STATE_WAITING_INDEFINITELY) != 0)
            sj.add("JVMTI_THREAD_STATE_WAITING_INDEFINITELY");
        if ((state & JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT) != 0)
            sj.add("JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT");
        if ((state & JVMTI_THREAD_STATE_IN_OBJECT_WAIT) != 0)
            sj.add("JVMTI_THREAD_STATE_IN_OBJECT_WAIT");
        if ((state & JVMTI_THREAD_STATE_PARKED) != 0)
            sj.add("JVMTI_THREAD_STATE_PARKED");
        if ((state & JVMTI_THREAD_STATE_SUSPENDED) != 0)
            sj.add("JVMTI_THREAD_STATE_SUSPENDED");
        if ((state & JVMTI_THREAD_STATE_INTERRUPTED) != 0)
            sj.add("JVMTI_THREAD_STATE_INTERRUPTED");
        if ((state & JVMTI_THREAD_STATE_IN_NATIVE) != 0)
            sj.add("JVMTI_THREAD_STATE_IN_NATIVE");
        return sj.toString();
    }

    /**
     * Waits for the given thread to park.
     */
    private static void awaitParked(Thread thread) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
            assertFalse(state == Thread.State.TERMINATED, "Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }

    /**
     * Waits for the given thread to block waiting on a monitor.
     */
    private static void awaitBlocked(Thread thread) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != Thread.State.BLOCKED) {
            assertFalse(state == Thread.State.TERMINATED, "Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }
}
