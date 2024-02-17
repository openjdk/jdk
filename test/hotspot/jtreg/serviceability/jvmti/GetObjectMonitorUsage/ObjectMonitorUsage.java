/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/GetObjectMonitorUsage/ObjectMonitorUsage.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test checks if JVMTI function GetObjectMonitorUsage returns
 *     the expected values for the owner, entry_count, water_count
 *     fields of JVMTI_monitor_info. The tescases are the following:
 *       - unowned object without any waitings
 *       - owned object without any waitings
 *       - unowned object with waitings through Object.wait()
 *       - unowned object has been waiting
 * COMMENTS
 *     Fixed according to 4669812 bug.
 *     Ported from JVMDI test nsk/jvmdi/GetMonitorInfo/getmoninfo003.
 * @requires vm.jvmti
 * @compile ObjectMonitorUsage.java
 * @run main/othervm/native -agentlib:ObjectMonitorUsage ObjectMonitorUsage
 */

public class ObjectMonitorUsage {

    final static int JCK_STATUS_BASE = 95;
    final static int NUMBER_OF_ENTERING_THREADS = 4;
    final static int NUMBER_OF_WAITING_THREADS  = 4;
    final static int NUMBER_OF_THREADS = NUMBER_OF_ENTERING_THREADS + NUMBER_OF_WAITING_THREADS;

    static Object lockCheck = new Object();
    static Thread thr[] = new Thread[NUMBER_OF_THREADS];

    native static int getRes();
    native static void check(Object obj, Thread owner,
                             int entryCount, int waiterCount, int notifyWaiterCount);

    static Thread startTask(TestTask task, boolean isVirtual) {
        Thread thread = isVirtual ? Thread.ofVirtual().start(task)
                                  : Thread.ofPlatform().start(task);
        task.waitReady();
        return thread;
    }

    /* Scenario #1:
     * - non-zero entering threads
     * - zero re-entering threads
     * - zero threads waiting to be notified
     */
    static void test1(boolean isVirtual) throws Error {
        synchronized (lockCheck) {
            // entry count: 1
            // count of threads waiting to enter: 0
            // count of threads waiting to re-enter: 0
            // count of threads waiting to be notified: 0
            check(lockCheck, Thread.currentThread(), 1, 0, 0);

            for (int i = 0; i < NUMBER_OF_ENTERING_THREADS; i++) {
                // this EnteringTask has to be blocked on the lockCheck enter
                thr[i] = startTask(new EnteringTask(), isVirtual);
            }
            // entry count: 1
            // count of threads waiting to enter:       NUMBER_OF_ENTERING_THREADS
            // count of threads waiting to re-enter:    0
            // count of threads waiting to be notified: 0
            check(lockCheck, Thread.currentThread(), 1,
                  NUMBER_OF_ENTERING_THREADS,
                  0 /* count of threads waiting to be notified: 0 */);
        }
        for (int i = 0; i < NUMBER_OF_ENTERING_THREADS; i++) {
            try {
                thr[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }
    }

    /* Scenario #2:
     * - non-zero entering threads
     * - zero re-entering threads
     * - non-zero waiting to be notified
     */
    static void test2(boolean isVirtual) throws Error {
        for (int i = NUMBER_OF_ENTERING_THREADS; i < NUMBER_OF_THREADS; i++) {
            // the WaitingTask has to wait to be notified in a lockCheck.wait()
            thr[i] = startTask(new WaitingTask(), isVirtual);
        }
        synchronized (lockCheck) {
            for (int i = 0; i < NUMBER_OF_ENTERING_THREADS; i++) {
                // this EnteringTask has to be blocked on the lockCheck enter
                thr[i] = startTask(new EnteringTask(), isVirtual);
            }
            // entry count: 1
            // count of threads waiting to enter:       NUMBER_OF_ENTERING_THREADS
            // count of threads waiting to re-enter:    0
            // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS
            check(lockCheck, Thread.currentThread(), 1,
                  NUMBER_OF_ENTERING_THREADS,
                  NUMBER_OF_WAITING_THREADS);

            lockCheck.notifyAll();
        }
        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            try {
                thr[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }
    }

    /* Scenario #3:
     * Initially we have:
     * - zero entering threads
     * - zero re-entering threads
     * - non-zero threads waiting to be notified
     *
     * The threads waiting to be notified are being notified one-by-one
     * until all threads are blocked on re-entering the monitor.
     * The numbers of entering/re-entering and waiting threads are checked
     * for correctness after each notification.
     */
    static void test3(boolean isVirtual) throws Error {
        for (int i = NUMBER_OF_ENTERING_THREADS; i < NUMBER_OF_THREADS; i++) {
            // the WaitingTask has to wait to be notified in a lockCheck.wait()
            thr[i] = startTask(new WaitingTask(), isVirtual);
        }
        synchronized (lockCheck) {
            // entry count: 1
            // count of threads waiting to enter:       0
            // count of threads waiting to re-enter:    0
            // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS
            check(lockCheck, Thread.currentThread(), 1,
                  0, // number of threads waiting to enter or re-enter
                  NUMBER_OF_WAITING_THREADS);

            for (int i = 0; i < NUMBER_OF_ENTERING_THREADS; i++) {
                // this EnteringTask has to be blocked on the lockCheck enter
                thr[i] = startTask(new EnteringTask(), isVirtual);
            }

            // entry count: 1
            // count of threads waiting to enter:       NUMBER_OF_ENTERING_THREADS
            // count of threads waiting to re-enter:    0
            // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS
            check(lockCheck, Thread.currentThread(), 1,
                  NUMBER_OF_ENTERING_THREADS,
                  NUMBER_OF_WAITING_THREADS);

            for (int i = 0; i < NUMBER_OF_WAITING_THREADS; i++) {
                lockCheck.notify();
                // now the notified WaitingTask has to be blocked on the lockCheck re-enter

                // entry count: 1
                // count of threads waiting to enter:       NUMBER_OF_ENTERING_THREADS
                // count of threads waiting to re-enter:    i + 1
                // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS - i - 1
                check(lockCheck, Thread.currentThread(), 1,
                      NUMBER_OF_ENTERING_THREADS + i + 1,
                      NUMBER_OF_WAITING_THREADS  - i - 1);
            }
        }
        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            try {
                thr[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }
    }

    public static void main(String args[]) {
        check(lockCheck, null, 0, 0, 0);

        // test platform threads
        test1(false);
        test2(false);
        test3(false);

        // test virtual threads
        test1(false);
        test2(false);
        test3(false);

        check(lockCheck, null, 0, 0, 0);
        if (getRes() > 0) {
            throw new RuntimeException("Failed status returned from the agent");
        }
    }

    static abstract class TestTask implements Runnable {
        public volatile boolean ready = false;
        public abstract void run();

        public void waitReady() {
            try {
                while (!ready) {
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }
    }

    static class EnteringTask extends TestTask {
        public void run() {
            ready = true;
            synchronized (lockCheck) {
            }
        }
    }

    static class WaitingTask extends TestTask {
         public void run() {
            synchronized (lockCheck) {
                try {
                    ready = true;
                    lockCheck.wait();
                } catch (InterruptedException e) {
                    throw new Error("Unexpected " + e);
                }
            }
        }
    }
}
