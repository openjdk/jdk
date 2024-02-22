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
 * @bug 8247972
 *
 * @summary converted from VM Testbase nsk/jvmti/GetObjectMonitorUsage/objmonusage003
 * DESCRIPTION
 *     The test checks if the JVMTI function GetObjectMonitorUsage returns
 *     the expected values for the owner, entry_count, water_count
 *     fields of JVMTI_monitor_info.
 *     The testcases are the following:
 *       - unowned object without any waitings
 *       - unowned object with waitings to be notified
 *       - owned object without any waitings
 *       - owned object with N waitings to enter the monitor
 *       - owned object with N waitings to be notified
 *       - owned object with N waitings to enter, from 0 to N waitings to re-enter,
 *         from N to 0 waitings to be notified
 *       - all the above scenarios are executed with platform and virtual threads
 * @requires vm.jvmti
 * @run main/othervm/native -agentlib:ObjectMonitorUsage ObjectMonitorUsage
 */

public class ObjectMonitorUsage {

    final static int NUMBER_OF_ENTERING_THREADS = 4;
    final static int NUMBER_OF_WAITING_THREADS  = 4;
    final static int NUMBER_OF_THREADS = NUMBER_OF_ENTERING_THREADS + NUMBER_OF_WAITING_THREADS;

    static Object lockCheck = new Object();
    static Thread[] thr = new Thread[NUMBER_OF_THREADS];

    native static int getRes();
    native static void check(Object obj, Thread owner,
                             int entryCount, int waiterCount, int notifyWaiterCount);

    static void log(String msg) {
        System.out.println(msg);
    }

    static String vtag(boolean isVirtual) {
        return isVirtual ? "virtual" : "platform";
    }

    static Thread startTask(int idx, TestTask task, boolean isVirtual, String kind) {
        Thread thread = isVirtual ? Thread.ofVirtual().name(kind + "VT" + idx).start(task)
                                  : Thread.ofPlatform().name(kind + "PT" + idx).start(task);
        task.waitReady();
        return thread;
    }

    /* Scenario #0:
     * - owning:         0
     * - entering:       0
     * - re-entering:    0
     * - to be notified: N
     */
    static void test0(boolean isVirtual) {
        String vtag = vtag(isVirtual);
        log("\n###test0: started " + vtag);

        for (int i = 0; i < NUMBER_OF_WAITING_THREADS; i++) {
            // the WaitingTask has to wait to be notified in a lockCheck.wait()
            thr[i] = startTask(i, new WaitingTask(), isVirtual, "Waiting");
        }
        // entry count: 0
        // count of threads waiting to enter:       0
        // count of threads waiting to re-enter:    0
        // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS
        check(lockCheck, null, 0, // no owner thread
              0, // count of threads waiting to enter: 0
              NUMBER_OF_ENTERING_THREADS);

        synchronized (lockCheck) {
            lockCheck.notifyAll();
        }
        for (int i = 0; i < NUMBER_OF_WAITING_THREADS; i++) {
            try {
                thr[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }
        log("###test0: finished " + vtag);
    }

    /* Scenario #1:
     * - owning:         1
     * - entering:       N
     * - re-entering:    0
     * - to be notified: 0
     */
    static void test1(boolean isVirtual) {
        String vtag = vtag(isVirtual);
        log("\n###test1: started " + vtag);

        synchronized (lockCheck) {
            // entry count: 1
            // count of threads waiting to enter: 0
            // count of threads waiting to re-enter: 0
            // count of threads waiting to be notified: 0
            check(lockCheck, Thread.currentThread(), 1, 0, 0);

            for (int i = 0; i < NUMBER_OF_ENTERING_THREADS; i++) {
                // this EnteringTask has to be blocked on the lockCheck enter
                thr[i] = startTask(i, new EnteringTask(), isVirtual, "Entering");
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
        log("###test1: finished " + vtag);
    }

    /* Scenario #2:
     * - owning:         1
     * - entering:       N
     * - re-entering:    0
     * - to be notified: N
     */
    static void test2(boolean isVirtual) throws Error {
        String vtag = vtag(isVirtual);
        log("\n###test2: started " + vtag);

        for (int i = 0; i < NUMBER_OF_WAITING_THREADS; i++) {
            // the WaitingTask has to wait to be notified in a lockCheck.wait()
            thr[i] = startTask(i, new WaitingTask(), isVirtual, "Waiting");
        }
        synchronized (lockCheck) {
            for (int i = 0; i < NUMBER_OF_ENTERING_THREADS; i++) {
                // this EnteringTask has to be blocked on the lockCheck enter
                thr[NUMBER_OF_WAITING_THREADS + i] = startTask(i, new EnteringTask(), isVirtual, "Entering");
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
        log("###test2: finished " + vtag);
    }

    /* Scenario #3:
     * Initially we have:
     * - owning:         1
     * - entering:       0
     * - re-entering:    0
     * - to be notified: N
     *
     * The threads waiting to be notified are being notified one-by-one
     * until all threads are blocked on re-entering the monitor.
     * The numbers of entering/re-entering and waiting threads are checked
     * for correctness after each notification.
     */
    static void test3(boolean isVirtual) throws Error {
        String vtag = vtag(isVirtual);
        log("\n###test3: started " + vtag);

        for (int i = 0; i < NUMBER_OF_WAITING_THREADS; i++) {
            // the WaitingTask has to wait to be notified in a lockCheck.wait()
            thr[i] = startTask(i, new WaitingTask(), isVirtual, "Waiting");
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
                thr[NUMBER_OF_WAITING_THREADS + i] = startTask(i, new EnteringTask(), isVirtual, "Entering");
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
        log("###test3: finished " + vtag);
    }

    public static void main(String args[]) {
        log("\n###main: started\n");
        check(lockCheck, null, 0, 0, 0);

        // test platform threads
        test0(false);
        test1(false);
        test2(false);
        test3(false);

        // test virtual threads
        test0(true);
        test1(true);
        test2(true);
        test3(true);

        check(lockCheck, null, 0, 0, 0);
        if (getRes() > 0) {
            throw new RuntimeException("Failed status returned from the agent");
        }
        log("\n###main: finished\n");
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
