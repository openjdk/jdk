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
 *     the expected values for the owner, entry_count, waiter_count
 *     fields of JVMTI_monitor_info.
 *     The testcases are the following:
 *       - unowned object without any threads waiting
 *       - unowned object with threads waiting to be notified
 *       - owned object without any threads waiting
 *       - owned object with N threads waiting to enter the monitor
 *       - owned object with N threads waiting to be notified
 *       - owned object with N threads waiting to enter, from 0 to N threads
 *         waiting to re-enter, from N to 0 threads waiting to be notified
 *       - all the above scenarios are executed with platform and virtual threads
 * @requires vm.jvmti
 * @run main/othervm/native
 *     -Djdk.virtualThreadScheduler.parallelism=10
 *     -agentlib:ObjectMonitorUsage ObjectMonitorUsage
 */

public class ObjectMonitorUsage {
    final static int NUMBER_OF_ENTERING_THREADS = 4;
    final static int NUMBER_OF_WAITING_THREADS  = 4;
    final static int NUMBER_OF_THREADS = NUMBER_OF_ENTERING_THREADS + NUMBER_OF_WAITING_THREADS;

    static Object lockCheck = new Object();

    native static int getRes();
    native static int setTestedMonitor(Object monitor);
    native static void ensureBlockedOnEnter(Thread thread);
    native static void ensureWaitingToBeNotified(Thread thread);
    native static void check(Object obj, Thread owner,
                             int entryCount, int waiterCount, int notifyWaiterCount);

    static void log(String msg) {
        System.out.println(msg);
    }

    static String vtag(boolean isVirtual) {
        return isVirtual ? "virtual" : "platform";
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    static Thread startTask(int idx, TestTask task, boolean isVirtual, String kind) {
        Thread thread = isVirtual ? Thread.ofVirtual().name(kind + "VT" + idx).start(task)
                                  : Thread.ofPlatform().name(kind + "PT" + idx).start(task);
        task.setName(thread.getName());
        task.waitReady();
        return thread;
    }

    static Thread[] startWaitingThreads(boolean isVirtual) {
        Thread[] threads = new Thread[NUMBER_OF_WAITING_THREADS];
        for (int i = 0; i < NUMBER_OF_WAITING_THREADS; i++) {
            // the WaitingTask has to wait to be notified in lockCheck.wait()
            Thread thread = startTask(i, new WaitingTask(), isVirtual, "Waiting");
            ensureWaitingToBeNotified(thread);
            threads[i] = thread;
        }
        return threads;
    }

    static Thread[] startEnteringThreads(boolean isVirtual) {
        Thread[] threads = new Thread[NUMBER_OF_ENTERING_THREADS];
        for (int i = 0; i < NUMBER_OF_ENTERING_THREADS; i++) {
            // the EnteringTask has to be blocked at the lockCheck enter
            Thread thread = startTask(i, new EnteringTask(), isVirtual, "Entering");
            ensureBlockedOnEnter(thread);
            threads[i] = thread;
        }
        return threads;
    }

    static void joinThreads(Thread[] threads) {
        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            throw new Error("Unexpected " + e);
        }
    }
    static Thread expOwnerThread() {
        return Thread.currentThread().isVirtual() ? null : Thread.currentThread();
    }

    static int expEntryCount() {
        return Thread.currentThread().isVirtual() ? 0 : 1;
    }

    /* Scenario #0:
     * - owning:         0
     * - entering:       0
     * - re-entering:    0
     * - to be notified: N
     */
    static void test0(boolean isVirtual) {
        String vtag = vtag(isVirtual);
        log("\n### test0: started " + vtag);

        setTestedMonitor(lockCheck);
        Thread[] wThreads = startWaitingThreads(isVirtual);
        final int expWaitingCount = isVirtual ? 0 : NUMBER_OF_WAITING_THREADS;

        // The numbers below describe the testing scenario, not the expected results.
        // The expected numbers are different for virtual threads because
        // they are not supported by JVMTI GetObjectMonitorUsage.
        // entry count: 0
        // count of threads waiting to enter:       0
        // count of threads waiting to re-enter:    0
        // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS
        check(lockCheck, null, 0, // no owner thread
              0, // count of threads waiting to enter: 0
              expWaitingCount);

        synchronized (lockCheck) {
            lockCheck.notifyAll();
        }
        joinThreads(wThreads);
        setTestedMonitor(null);
        log("### test0: finished " + vtag);
    }

    /* Scenario #1:
     * - owning:         1
     * - entering:       N
     * - re-entering:    0
     * - to be notified: 0
     */
    static void test1(boolean isVirtual) {
        String vtag = vtag(isVirtual);
        log("\n### test1: started " + vtag);

        setTestedMonitor(lockCheck);
        Thread[] eThreads = null;

        synchronized (lockCheck) {
            // Virtual threads are not supported by GetObjectMonitorUsage.
            // Correct the expected values for the virtual thread case.
            int expEnteringCount = isVirtual ? 0 : NUMBER_OF_ENTERING_THREADS;

            // The numbers below describe the testing scenario, not the expected results.
            // The expected numbers are different for virtual threads because
            // they are not supported by JVMTI GetObjectMonitorUsage.
            // entry count: 1
            // count of threads waiting to enter: 0
            // count of threads waiting to re-enter: 0
            // count of threads waiting to be notified: 0
            check(lockCheck, expOwnerThread(), expEntryCount(), 0, 0);

            eThreads = startEnteringThreads(isVirtual);

            // The numbers below describe the testing scenario, not the expected results.
            // The expected numbers are different for virtual threads because
            // they are not supported by JVMTI GetObjectMonitorUsage.
            // entry count: 1
            // count of threads waiting to enter:       NUMBER_OF_ENTERING_THREADS
            // count of threads waiting to re-enter:    0
            // count of threads waiting to be notified: 0
            check(lockCheck, expOwnerThread(), expEntryCount(),
                  expEnteringCount,
                  0 /* count of threads waiting to be notified: 0 */);

        }
        joinThreads(eThreads);
        setTestedMonitor(null);
        log("### test1: finished " + vtag);
    }

    /* Scenario #2:
     * - owning:         1
     * - entering:       N
     * - re-entering:    0
     * - to be notified: N
     */
    static void test2(boolean isVirtual) throws Error {
        String vtag = vtag(isVirtual);
        log("\n### test2: started " + vtag);

        setTestedMonitor(lockCheck);
        Thread[] wThreads = startWaitingThreads(isVirtual);
        Thread[] eThreads = null;

        synchronized (lockCheck) {
            // Virtual threads are not supported by the GetObjectMonitorUsage.
            // Correct the expected values for the virtual thread case.
            int expEnteringCount = isVirtual ? 0 : NUMBER_OF_ENTERING_THREADS;
            int expWaitingCount  = isVirtual ? 0 : NUMBER_OF_WAITING_THREADS;

            eThreads = startEnteringThreads(isVirtual);

            // The numbers below describe the testing scenario, not the expected results.
            // The expected numbers are different for virtual threads because
            // they are not supported by JVMTI GetObjectMonitorUsage.
            // entry count: 1
            // count of threads waiting to enter:       NUMBER_OF_ENTERING_THREADS
            // count of threads waiting to re-enter:    0
            // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS
            check(lockCheck, expOwnerThread(), expEntryCount(),
                  expEnteringCount,
                  expWaitingCount);

            lockCheck.notifyAll();
        }
        joinThreads(wThreads);
        joinThreads(eThreads);
        setTestedMonitor(null);
        log("### test2: finished " + vtag);
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
        log("\n### test3: started " + vtag);

        setTestedMonitor(lockCheck);
        Thread[] wThreads = startWaitingThreads(isVirtual);
        Thread[] eThreads = null;

        synchronized (lockCheck) {
            // Virtual threads are not supported by GetObjectMonitorUsage.
            // Correct the expected values for the virtual thread case.
            int expEnteringCount = isVirtual ? 0 : NUMBER_OF_ENTERING_THREADS;
            int expWaitingCount  = isVirtual ? 0 : NUMBER_OF_WAITING_THREADS;

            // The numbers below describe the testing scenario, not the expected results.
            // The expected numbers are different for virtual threads because
            // they are not supported by JVMTI GetObjectMonitorUsage.
            // entry count: 1
            // count of threads waiting to enter:       0
            // count of threads waiting to re-enter:    0
            // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS
            check(lockCheck, expOwnerThread(), expEntryCount(),
                  0, // number of threads waiting to enter or re-enter
                  expWaitingCount);

            eThreads = startEnteringThreads(isVirtual);

            // The numbers below describe the testing scenario, not the expected results.
            // The expected numbers are different for virtual threads because
            // they are not supported by JVMTI GetObjectMonitorUsage.
            // entry count: 1
            // count of threads waiting to enter:       NUMBER_OF_ENTERING_THREADS
            // count of threads waiting to re-enter:    0
            // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS
            check(lockCheck, expOwnerThread(), expEntryCount(),
                  expEnteringCount,
                  expWaitingCount);

            for (int i = 0; i < NUMBER_OF_WAITING_THREADS; i++) {
                expEnteringCount = isVirtual ? 0 : NUMBER_OF_ENTERING_THREADS + i + 1;
                expWaitingCount  = isVirtual ? 0 : NUMBER_OF_WAITING_THREADS - i - 1;
                lockCheck.notify(); // notify waiting threads one by one
                // now the notified WaitingTask has to be blocked on the lockCheck re-enter

                // The numbers below describe the testing scenario, not the expected results.
                // The expected numbers are different for virtual threads because
                // they are not supported by JVMTI GetObjectMonitorUsage.
                // entry count: 1
                // count of threads waiting to enter:       NUMBER_OF_ENTERING_THREADS
                // count of threads waiting to re-enter:    i + 1
                // count of threads waiting to be notified: NUMBER_OF_WAITING_THREADS - i - 1
                check(lockCheck, expOwnerThread(), expEntryCount(),
                      expEnteringCount,
                      expWaitingCount);
            }
        }
        joinThreads(wThreads);
        joinThreads(eThreads);
        setTestedMonitor(null);
        log("### test3: finished " + vtag);
    }

    static void test(boolean isVirtual) {
        test0(isVirtual);
        test1(isVirtual);
        test2(isVirtual);
        test3(isVirtual);
    }

    public static void main(String args[]) {
        log("\n### main: started\n");
        check(lockCheck, null, 0, 0, 0);

        test(false); // test platform threads
        test(true);  // test virtual threads

        check(lockCheck, null, 0, 0, 0);
        if (getRes() > 0) {
            throw new RuntimeException("Failed status returned from the agent");
        }
        log("\n### main: finished\n");
    }

    static abstract class TestTask implements Runnable {
        volatile boolean ready = false;
        String name;

        public abstract void run();

        String getName() { return name; }
        void setName(String name) { this.name = name; }

        void waitReady() {
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
                    // no protection against spurious wakeups here
                    lockCheck.wait();
                } catch (InterruptedException e) {
                    throw new Error("Unexpected " + e);
                }
            }
        }
    }
}
