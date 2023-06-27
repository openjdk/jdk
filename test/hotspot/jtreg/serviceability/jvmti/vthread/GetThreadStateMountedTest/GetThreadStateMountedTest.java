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

/**
 * @test
 * @bug 8309612 8310584
 * @summary The test verifies that JVMTI GetThreadState function reports expected state
 *          for mounted (pinned) virtual thread and its carrier thread
 * @requires vm.jvmti
 * @requires vm.continuations
 * @run main/othervm/native
 *      -agentlib:GetThreadStateMountedTest
 *      GetThreadStateMountedTest
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

public class GetThreadStateMountedTest {

    static final int JVMTI_THREAD_STATE_RUNNABLE                 = 0x0004;
    static final int JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400;
    static final int JVMTI_THREAD_STATE_WAITING                  = 0x0080;
    static final int JVMTI_THREAD_STATE_WAITING_INDEFINITELY     = 0x0010;
    static final int JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT     = 0x0020;
    static final int JVMTI_THREAD_STATE_IN_OBJECT_WAIT           = 0x0100;
    static final int JVMTI_THREAD_STATE_SLEEPING                 = 0x0040;
    static final int JVMTI_THREAD_STATE_PARKED                   = 0x0200;
    static final int JVMTI_THREAD_STATE_IN_NATIVE                = 0x400000;

    static void runnable() throws Exception {
        TestStatus status = new TestStatus("JVMTI_THREAD_STATE_RUNNABLE");
        CountDownLatch ready = new CountDownLatch(1);
        final boolean[] stopFlag = new boolean[1];
        Thread vthread = createPinnedVThread(() -> {
            ready.countDown();
            int i = 0;
            while (!stopFlag[0]) {
                if (i < 200) {
                    i++;
                } else {
                    i = 0;
                }
            }
        });
        vthread.start();
        ready.await();
        testThreadStates(vthread, true, JVMTI_THREAD_STATE_RUNNABLE);
        stopFlag[0] = true;
        status.print();
    }

    static void blockedOnMonitorEnter() throws Exception {
        // JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER
        // Thread is waiting to enter a synchronized block/method or,
        // after an Object.wait(), waiting to re-enter a synchronized block/method.
        TestStatus status = new TestStatus("JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER");
        CountDownLatch ready = new CountDownLatch(1);
        final Object syncObj = new Object();
        Thread vthread = createPinnedVThread(() -> {
            ready.countDown();
            synchronized (syncObj) {
            }
        });
        synchronized (syncObj) {
            vthread.start();
            ready.await();
            Thread.sleep(500); // wait some time to ensure the thread is blocked on monitor
            testThreadStates(vthread, true, JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER);
        }
        status.print();
    }

    static void waiting(boolean withTimeout) throws Exception {
        // JVMTI_THREAD_STATE_WAITING
        // Thread is waiting.
        // JVMTI_THREAD_STATE_WAITING_INDEFINITELY
        // Thread is waiting without a timeout. For example, Object.wait().
        // JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT
        // Thread is waiting with a maximum time to wait specified. For example, Object.wait(long).
        TestStatus status = new TestStatus(withTimeout
                                           ? ">>JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT"
                                           : ">>JVMTI_THREAD_STATE_WAITING_INDEFINITELY");
        CountDownLatch ready = new CountDownLatch(1);
        final Object syncObj = new Object();
        Thread vthread = createPinnedVThread(() -> {
            synchronized (syncObj) {
                try {
                    ready.countDown();
                    if (withTimeout) {
                        syncObj.wait(60000);
                    } else {
                        syncObj.wait();
                    }
                } catch (InterruptedException ex) {
                    // expected, ignore
                }
            }
        });
        vthread.start();
        ready.await();
        Thread.sleep(500); // wait some time to ensure the thread is blocked on Object.wait
        int expectedState = JVMTI_THREAD_STATE_WAITING
                | JVMTI_THREAD_STATE_IN_OBJECT_WAIT
                | (withTimeout
                    ? JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT
                    : JVMTI_THREAD_STATE_WAITING_INDEFINITELY);
        testThreadStates(vthread, true, expectedState);
        // signal test thread to finish (for safety, Object.wait should throw InterruptedException)
        synchronized (syncObj) {
            syncObj.notifyAll();
        }
        status.print();
    }

    static void sleeping() throws Exception {
        // JVMTI_THREAD_STATE_SLEEPING
        // Thread is sleeping -- Thread.sleep.
        // JVMTI_THREAD_STATE_PARKED
        // A virtual thread that is sleeping, in Thread.sleep,
        // may have this state flag set instead of JVMTI_THREAD_STATE_SLEEPING.
        TestStatus status = new TestStatus("JVMTI_THREAD_STATE_SLEEPING");
        CountDownLatch ready = new CountDownLatch(1);
        Thread vthread = createPinnedVThread(() -> {
            ready.countDown();
            try {
                Thread.sleep(60000);
            } catch (InterruptedException ex) {
                // expected, ignore
            }
        });
        vthread.start();
        ready.await();
        Thread.sleep(500); // wait some time to ensure the thread has reached waiting state
        // don't test interrupt() - it causes thread state change for parked thread
        // even if it's suspended
        testThreadStates(vthread, false,
                JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT,
                JVMTI_THREAD_STATE_SLEEPING | JVMTI_THREAD_STATE_PARKED);
        status.print();
    }

    static void parked() throws Exception {
        // JVMTI_THREAD_STATE_PARKED
        // Thread is parked, for example: LockSupport.park, LockSupport.parkUtil and LockSupport.parkNanos.
        TestStatus status = new TestStatus("JVMTI_THREAD_STATE_PARKED");
        CountDownLatch ready = new CountDownLatch(1);
        Thread vthread = createPinnedVThread(() -> {
            ready.countDown();
            LockSupport.park(Thread.currentThread());
        });
        vthread.start();
        ready.await();
        Thread.sleep(500); // wait some time to ensure the thread has reached waiting state
        // don't test interrupt() - it causes thread state change for parked thread
        // even if it's suspended
        testThreadStates(vthread, false,
                JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_INDEFINITELY | JVMTI_THREAD_STATE_PARKED);
        // allow test thread to finish
        LockSupport.unpark(vthread);
        status.print();
    }

    static void inNative() throws Exception {
        TestStatus status = new TestStatus("JVMTI_THREAD_STATE_IN_NATIVE");
        Thread vthread = createPinnedVThread(() -> {
            waitInNative();
        });
        vthread.start();
        while (!waitInNativeReady) {
            Thread.sleep(50);
        }
        testThreadStates(vthread, true,
                JVMTI_THREAD_STATE_RUNNABLE | JVMTI_THREAD_STATE_IN_NATIVE,
                0);
        status.print();
    }


    public static void main(String[] args) throws Exception {
        runnable();
        /* "waiting" test cases fail due JDK-8310584
        blockedOnMonitorEnter();
        waiting(false);
        waiting(true);
        sleeping();
        parked();
        */
        inNative();

        int errCount = getErrorCount();
        if (errCount > 0) {
            throw new RuntimeException("Test failed, " + errCount + " errors");
        }
    }

    private static Thread createPinnedVThread(Runnable runnable) {
        final Object syncObj = new Object();
        return Thread.ofVirtual().unstarted(() -> {
            synchronized (syncObj) {
                runnable.run();
            }
        });
    }

    // Tests thread states (vthread and the carrier thread).
    // expectedStrong specifies value which must be present in vthreat state;
    // expectedWeak is a combination of bits which may be set in vthreat state
    // (at least one of the bit must set, but not all).
    private static native void testThread(Thread vthread,
                                          boolean testInterrupt,
                                          int expectedStrong, int expectedWeak);
    private static native int getErrorCount();

    private static boolean waitInNativeReady = false;

    // Sets waitInNativeReady static field to true
    // and then waits until endWait() method is called.
    private static native void waitInNative();
    // Signals waitInNative() to exit.
    private static native void endWait();

    private static void testThreadStates(Thread vthread,
                                         boolean testInterrupt,
                                         int expectedStrong, int expectedWeak) {
        String name = vthread.toString();
        log("Thread " + name);
        testThread(vthread, testInterrupt, expectedStrong, expectedWeak);
    }

    private static void testThreadStates(Thread vthread, boolean testInterrupt, int expectedState) {
        testThreadStates(vthread, testInterrupt, expectedState, 0);
    }

    // helper class to print status of each test
    private static class TestStatus {
        private final String name;
        private final int startErrorCount;
        TestStatus(String name) {
            this.name = name;
            startErrorCount = getErrorCount();
            log(">>" + name);
        }
        void print() {
            log("<<" + name + (startErrorCount == getErrorCount() ? " - OK" : " - FAILED"));
            log("");
        }
    }

    private static void log(Object s) {
        System.out.println(s);
    }
}
