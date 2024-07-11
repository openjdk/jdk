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
 * @bug 8312498
 * @summary Basic test for JVMTI GetThreadState with virtual threads
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED GetThreadStateTest
 */

/*
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit/othervm/native -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations --enable-native-access=ALL-UNNAMED GetThreadStateTest
 */

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.thread.VThreadPinner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GetThreadStateTest {

    @BeforeAll
    static void setup() {
        System.loadLibrary("GetThreadStateTest");
        init();

        // need >=2 carriers for testing pinning when main thread is a virtual thread
        if (Thread.currentThread().isVirtual()) {
            VThreadRunner.ensureParallelism(2);
        }
    }

    /**
     * Test state of new/unstarted thread.
     */
    @Test
    void testUnstarted() {
        var thread = Thread.ofVirtual().unstarted(() -> { });
        check(thread, /*new*/ 0);
    }

    /**
     * Test state of terminated thread.
     */
    @Test
    void testTerminated() throws Exception {
        var thread = Thread.ofVirtual().start(() -> { });
        thread.join();
        check(thread, JVMTI_THREAD_STATE_TERMINATED);
    }

    /**
     * Test state of runnable thread.
     */
    @Test
    void testRunnable() throws Exception {
        var started = new AtomicBoolean();
        var done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            started.set(true);

            // spin until done
            while (!done.get()) {
                Thread.onSpinWait();
            }
        });
        try {
            // wait for thread to start execution
            awaitTrue(started);

            // thread should be runnable
            int expected = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_RUNNABLE;
            check(thread, expected);

            // re-test with interrupt status set
            thread.interrupt();
            check(thread, expected | JVMTI_THREAD_STATE_INTERRUPTED);
        } finally {
            done.set(true);
            thread.join();
        }
    }

    /**
     * Test state of thread waiting to enter a monitor when pinned and not pinned.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testMonitorEnter(boolean pinned) throws Exception {
        var ready = new AtomicBoolean();
        Object lock = new Object();
        var thread = Thread.ofVirtual().unstarted(() -> {
            if (pinned) {
                VThreadPinner.runPinned(() -> {
                    ready.set(true);
                    synchronized (lock) { }
                });
            } else {
                ready.set(true);
                synchronized (lock) { }
            }
        });
        try {
            synchronized (lock) {
                // start thread and wait for it to start execution
                thread.start();
                awaitTrue(ready);

                // thread should block on monitor enter
                int expected = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
                await(thread, expected);

                // re-test with interrupt status set
                thread.interrupt();
                check(thread, expected | JVMTI_THREAD_STATE_INTERRUPTED);
            }
        } finally {
            thread.join();
        }
    }

    /**
     * Test state of thread waiting in Object.wait() when pinned and not pinned.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testObjectWait(boolean pinned) throws Exception {
        var ready = new AtomicBoolean();
        Object lock = new Object();
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
        try {
            // wait for thread to start execution
            awaitTrue(ready);

            // thread should wait
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_INDEFINITELY |
                    JVMTI_THREAD_STATE_IN_OBJECT_WAIT;
            await(thread, expected);

            // notify so thread waits to re-enter monitor
            synchronized (lock) {
                lock.notifyAll();
                expected = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
                check(thread, expected);

                // re-test with interrupt status set
                thread.interrupt();
                check(thread, expected | JVMTI_THREAD_STATE_INTERRUPTED);
            }
        } finally {
            thread.interrupt();
            thread.join();
        }
    }

    /**
     * Test state of thread waiting in Object.wait(millis) when pinned and not pinned.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testObjectWaitMillis(boolean pinned) throws Exception {
        var ready = new AtomicBoolean();
        Object lock = new Object();
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                synchronized (lock) {
                    try {
                        if (pinned) {
                            VThreadPinner.runPinned(() -> {
                                ready.set(true);
                                lock.wait(Long.MAX_VALUE);
                            });
                        } else {
                            ready.set(true);
                            lock.wait(Long.MAX_VALUE);
                        }
                    } catch (InterruptedException e) { }
                }
            }
        });
        try {
            // wait for thread to start execution
            awaitTrue(ready);

            // thread should wait
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT |
                    JVMTI_THREAD_STATE_IN_OBJECT_WAIT;
            await(thread, expected);

            // notify so thread waits to re-enter monitor
            synchronized (lock) {
                lock.notifyAll();
                expected = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
                check(thread, expected);

                // re-test with interrupt status set
                thread.interrupt();
                check(thread, expected | JVMTI_THREAD_STATE_INTERRUPTED);
            }
        } finally {
            thread.interrupt();
            thread.join();
        }
    }

    /**
     * Test state of thread parked with LockSupport.park when pinned and not pinned.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testPark(boolean pinned) throws Exception {
        var ready = new AtomicBoolean();
        var done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            if (pinned) {
                VThreadPinner.runPinned(() -> {
                    ready.set(true);
                    while (!done.get()) {
                        LockSupport.park();
                    }
                });
            } else {
                ready.set(true);
                while (!done.get()) {
                    LockSupport.park();
                }
            }
        });
        try {
            // wait for thread to start execution
            awaitTrue(ready);

            // thread should park
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_INDEFINITELY |
                    JVMTI_THREAD_STATE_PARKED;
            await(thread, expected);
        } finally {
            done.set(true);
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Test state of thread parked with LockSupport.parkNanos when pinned and not pinned.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testParkNanos(boolean pinned) throws Exception {
        var ready = new AtomicBoolean();
        var done = new AtomicBoolean();
        var thread = Thread.ofVirtual().start(() -> {
            if (pinned) {
                VThreadPinner.runPinned(() -> {
                    ready.set(true);
                    while (!done.get()) {
                        LockSupport.parkNanos(Long.MAX_VALUE);
                    }
                });
            } else {
                ready.set(true);
                while (!done.get()) {
                    LockSupport.parkNanos(Long.MAX_VALUE);
                }
            }
        });
        try {
            // wait for thread to start execution
            awaitTrue(ready);

            // thread should park
            int expected = JVMTI_THREAD_STATE_ALIVE |
                    JVMTI_THREAD_STATE_WAITING |
                    JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT |
                    JVMTI_THREAD_STATE_PARKED;
            await(thread, expected);
        } finally {
            done.set(true);
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    /**
     * Waits for the boolean value to become true.
     */
    private static void awaitTrue(AtomicBoolean ref) throws Exception {
        while (!ref.get()) {
            Thread.sleep(20);
        }
    }

    /**
     * Asserts that the given thread has the expected JVMTI state.
     */
    private static void check(Thread thread, int expected) {
        System.err.format("  expect state=0x%x (%s) ...%n", expected, jvmtiStateToString(expected));
        int state = jvmtiState(thread);
        System.err.format("  thread state=0x%x (%s)%n", state, jvmtiStateToString(state));
        assertEquals(expected, state);
    }

    /**
     * Waits indefinitely for the given thread to get to the target JVMTI state.
     */
    private static void await(Thread thread, int targetState) throws Exception {
        System.err.format("  await state=0x%x (%s) ...%n", targetState, jvmtiStateToString(targetState));
        int state = jvmtiState(thread);
        System.err.format("  thread state=0x%x (%s)%n", state, jvmtiStateToString(state));
        while (state != targetState) {
            assertTrue(thread.isAlive(), "Thread has terminated");
            Thread.sleep(20);
            state = jvmtiState(thread);
            System.err.format("  thread state=0x%x (%s)%n", state, jvmtiStateToString(state));
        }
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
        String s = sj.toString();
        return s.isEmpty() ? "<empty>" : s;
    }
}
