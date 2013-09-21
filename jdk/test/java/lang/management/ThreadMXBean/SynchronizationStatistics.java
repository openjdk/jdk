/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4530538
 * @summary Basic unit test of the synchronization statistics support:
 *
 * @author  Mandy Chung
 *
 * @ignore  6309226
 * @build Semaphore
 * @run main/othervm SynchronizationStatistics
 */

import java.lang.management.*;

public class SynchronizationStatistics {
    private static ThreadMXBean mbean = ManagementFactory.getThreadMXBean();

    private static boolean blockedTimeCheck =
        mbean.isThreadContentionMonitoringSupported();
    private static boolean trace = false;

    private static Object lockA = new Object();
    private static Object lockB = new Object();
    private static Object lockC = new Object();
    private static Object lockD = new Object();
    private static Object waiter = new Object();
    private static volatile boolean testFailed = false;

    private static Object go = new Object();

    private static void goSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Unexpected exception.");
            testFailed = true;
        }
    }

    public static void main(String args[]) throws Exception {
        if (args.length > 0 && args[0].equals("trace")) {
            trace = true;
        }

        if (blockedTimeCheck) {
            mbean.setThreadContentionMonitoringEnabled(true);
        }

        if (!mbean.isThreadContentionMonitoringEnabled()) {
            throw new RuntimeException("TEST FAILED: " +
                "Thread Contention Monitoring is not enabled");
        }

        Examiner examiner = new Examiner("Examiner");
        BlockedThread blocked = new BlockedThread("BlockedThread");
        examiner.setThread(blocked);

        // Start the threads and check them in  Blocked and Waiting states
        examiner.start();

        // wait until the examiner acquires all the locks and waiting
        // for the BlockedThread to start
        examiner.waitUntilWaiting();

        System.out.println("Checking the thread state for the examiner thread " +
                           "is waiting to begin.");

        // The Examiner should be waiting to be notified by the BlockedThread
        checkThreadState(examiner, Thread.State.WAITING);

        System.out.println("Now starting the blocked thread");
        blocked.start();

        try {
            examiner.join();
            blocked.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Unexpected exception.");
            testFailed = true;
        }

        if (testFailed)
            throw new RuntimeException("TEST FAILED.");

        System.out.println("Test passed.");
    }

    private static String INDENT = "    ";
    private static void printStack(Thread t, StackTraceElement[] stack) {
        System.out.println(INDENT +  t +
                           " stack: (length = " + stack.length + ")");
        if (t != null) {
            for (int j = 0; j < stack.length; j++) {
                System.out.println(INDENT + stack[j]);
            }
            System.out.println();
        }
    }

    private static void checkThreadState(Thread thread, Thread.State s)
        throws Exception {

        ThreadInfo ti = mbean.getThreadInfo(thread.getId());
        if (ti.getThreadState() != s) {
            ThreadInfo info = mbean.getThreadInfo(thread.getId(),
                                                  Integer.MAX_VALUE);
            System.out.println(INDENT + "TEST FAILED:");
            printStack(thread, info.getStackTrace());
            System.out.println(INDENT + "Thread state: " + info.getThreadState());

            throw new RuntimeException("TEST FAILED: " +
                "Thread state for " + thread + " returns " + ti.getThreadState() +
                ".  Expected to be " + s);
        }
    }

    private static void checkThreadState(Thread thread,
                                         Thread.State s1, Thread.State s2)
        throws Exception {

        ThreadInfo ti = mbean.getThreadInfo(thread.getId());
        if (ti.getThreadState() != s1 && ti.getThreadState() != s2) {
            throw new RuntimeException("TEST FAILED: " +
                "Thread state for " + thread + " returns " + ti.getThreadState() +
                ".  Expected to be " + s1 + " or " + s2);
        }
    }

    static class StatThread extends Thread {
        private long blockingBaseTime = 0;
        private long totalWaitTime = 0;
        private long totalBlockedEnterTime = 0;

        StatThread(String name) {
            super(name);
        }

        void addWaitTime(long ns) {
            totalWaitTime = totalWaitTime + ns;
        }
        void addBlockedEnterTime(long ns) {
            totalBlockedEnterTime = totalBlockedEnterTime + ns;
        }
        void setBlockingBaseTime(long time) {
            blockingBaseTime = time;
        }

        long totalBlockedTimeMs() {
            return totalBlockedEnterTime / 1000000;
        }

        long totalBlockedTimeMs(long now) {
            long t = totalBlockedEnterTime + (now - blockingBaseTime);
            return t / 1000000;
        }

        long totalWaitTimeMs() {
            return totalWaitTime / 1000000;
        }

        long totalWaitTimeMs(long now) {
            long t = totalWaitTime + (now - blockingBaseTime);
            return t / 1000000;
        }
    }

    static class BlockedThread extends StatThread {
        private Semaphore handshake = new Semaphore();
        BlockedThread(String name) {
            super(name);
        }
        void waitUntilBlocked() {
            handshake.semaP();

            // give a chance for the examiner thread to really wait
            goSleep(20);
        }

        void waitUntilWaiting() {
            waitUntilBlocked();
        }

        boolean hasWaitersForBlocked() {
            return (handshake.getWaiterCount() > 0);
        }

        private void notifyWaiter() {
            // wait until the examiner waits on the semaphore
            while (handshake.getWaiterCount() == 0) {
                goSleep(20);
            }
            handshake.semaV();
        }

        private void waitObj(long ms) {
            synchronized (waiter) {
                try {
                    // notify examinerabout to wait on a monitor
                    notifyWaiter();

                    long base = System.nanoTime();
                    setBlockingBaseTime(base);
                    waiter.wait(ms);
                    long now = System.nanoTime();
                    addWaitTime(now - base);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Unexpected exception.");
                    testFailed = true;
                }
            }
        }

        private void test() {
            // notify examiner about to block on lockA
            notifyWaiter();

            long base = System.nanoTime();
            setBlockingBaseTime(base);
            synchronized (lockA) {
                long now = System.nanoTime();
                addBlockedEnterTime(now - base);

                A(); // Expected blocked count = 1
            }
            E();
        }
        private void A() {
            // notify examiner about to block on lockB
            notifyWaiter();

            long base = System.nanoTime();
            setBlockingBaseTime(base);
            synchronized (lockB) {
                long now = System.nanoTime();
                addBlockedEnterTime(now - base);

                B(); // Expected blocked count = 2
            }
        }
        private void B() {
            // notify examiner about to block on lockC
            notifyWaiter();

            long base = System.nanoTime();
            setBlockingBaseTime(base);
            synchronized (lockC) {
                long now = System.nanoTime();
                addBlockedEnterTime(now - base);

                C();  // Expected blocked count = 3
            }
        }
        private void C() {
            // notify examiner about to block on lockD
            notifyWaiter();

            long base = System.nanoTime();
            setBlockingBaseTime(base);
            synchronized (lockD) {
                long now = System.nanoTime();
                addBlockedEnterTime(now - base);

                D();  // Expected blocked count = 4
            }
        }
        private void D() {
            goSleep(50);
        }
        private void E() {
            final int WAIT = 1000;
            waitObj(WAIT);
            waitObj(WAIT);
            waitObj(WAIT);
        }

        public void run() {
            test();
        } // run()
    } // BlockedThread

    static int blockedCount = 0;
    static int waitedCount = 0;
    static class Examiner extends StatThread {
        private BlockedThread blockedThread;
        private Semaphore semaphore = new Semaphore();

        Examiner(String name) {
            super(name);
        }

        public void setThread(BlockedThread thread) {
            blockedThread = thread;
        }

        private void blockedTimeRangeCheck(StatThread t,
                                           long blockedTime,
                                           long nowNano)
            throws Exception {
            long expected = t.totalBlockedTimeMs(nowNano);

            // accept 5% range
            timeRangeCheck(blockedTime, expected, 5);
        }
        private void waitedTimeRangeCheck(StatThread t,
                                          long waitedTime,
                                          long nowNano)
            throws Exception {
            long expected = t.totalWaitTimeMs(nowNano);

            // accept 5% range
            timeRangeCheck(waitedTime, expected, 5);
        }

        private void timeRangeCheck(long time, long expected, int percent)
            throws Exception {

            double diff = expected - time;

            if (trace) {
                 System.out.println("  Time = " + time +
                    " expected = " + expected +
                    ".  Diff = " + diff);

            }
            // throw an exception if blockedTime and expectedTime
            // differs > percent%
            if (diff < 0) {
                diff = diff * -1;
            }

            long range = (expected * percent) / 100;
            // minimum range = 2 ms
            if (range < 2) {
                range = 2;
            }
            if (diff > range) {
                throw new RuntimeException("TEST FAILED: " +
                    "Time returned = " + time +
                    " expected = " + expected + ".  Diff = " + diff);
            }
        }
        private void checkInfo(StatThread t, Thread.State s, Object lock,
                               String lockName, int bcount, int wcount)
            throws Exception {

            String action = "ERROR";
            if (s == Thread.State.WAITING || s == Thread.State.TIMED_WAITING) {
                action = "wait on ";
            } else if (s == Thread.State.BLOCKED) {
                action = "block on ";
            }
            System.out.println(t + " expected to " + action + lockName +
                " with blocked count = " + bcount +
                " and waited count = " + wcount);

            long now = System.nanoTime();
            ThreadInfo info = mbean.getThreadInfo(t.getId());
            if (info.getThreadState() != s) {
                printStack(t, info.getStackTrace());
                throw new RuntimeException("TEST FAILED: " +
                    "Thread state returned is " + info.getThreadState() +
                    ". Expected to be " + s);
            }

            if (info.getLockName() == null ||
                !info.getLockName().equals(lock.toString())) {
                throw new RuntimeException("TEST FAILED: " +
                    "getLockName() returned " + info.getLockName() +
                    ". Expected to be " + lockName + " - "  + lock.toString());
            }

            if (info.getBlockedCount() != bcount) {
                throw new RuntimeException("TEST FAILED: " +
                    "Blocked Count returned is " + info.getBlockedCount() +
                    ". Expected to be " + bcount);
            }
            if (info.getWaitedCount() != wcount) {
                throw new RuntimeException("TEST FAILED: " +
                    "Waited Count returned is " + info.getWaitedCount() +
                    ". Expected to be " + wcount);
            }

            String lockObj = info.getLockName();
            if (lockObj == null || !lockObj.equals(lock.toString())) {
                throw new RuntimeException("TEST FAILED: " +
                    "Object blocked on is " + lockObj  +
                    ". Expected to be " + lock.toString());
            }

            if (!blockedTimeCheck) {
                return;
            }
            long blockedTime = info.getBlockedTime();
            if (blockedTime < 0) {
                throw new RuntimeException("TEST FAILED: " +
                    "Blocked time returned is negative = " + blockedTime);
            }

            if (s == Thread.State.BLOCKED) {
                blockedTimeRangeCheck(t, blockedTime, now);
            } else {
                timeRangeCheck(blockedTime, t.totalBlockedTimeMs(), 5);
            }

            long waitedTime = info.getWaitedTime();
            if (waitedTime < 0) {
                throw new RuntimeException("TEST FAILED: " +
                    "Waited time returned is negative = " + waitedTime);
            }
            if (s == Thread.State.WAITING || s == Thread.State.TIMED_WAITING) {
                waitedTimeRangeCheck(t, waitedTime, now);
            } else {
                timeRangeCheck(waitedTime, t.totalWaitTimeMs(), 5);
            }

        }

        private void examine() {
            try {
                synchronized (lockD) {
                    synchronized (lockC) {
                        synchronized (lockB) {
                            synchronized (lockA) {
                                // notify main thread to continue
                                semaphore.semaV();

                                // wait until BlockedThread has started
                                blockedThread.waitUntilBlocked();

                                blockedCount++;
                                checkInfo(blockedThread, Thread.State.BLOCKED,
                                          lockA, "lockA",
                                          blockedCount, waitedCount);
                            }

                           // wait until BlockedThread to block on lockB
                            blockedThread.waitUntilBlocked();

                            blockedCount++;
                            checkInfo(blockedThread, Thread.State.BLOCKED,
                                      lockB, "lockB",
                                      blockedCount, waitedCount);
                        }

                        // wait until BlockedThread to block on lockC
                        blockedThread.waitUntilBlocked();

                        blockedCount++;
                        checkInfo(blockedThread, Thread.State.BLOCKED,
                                  lockC, "lockC",
                                  blockedCount, waitedCount);
                    }
                    // wait until BlockedThread to block on lockD
                    blockedThread.waitUntilBlocked();
                    blockedCount++;

                    checkInfo(blockedThread, Thread.State.BLOCKED,
                              lockD, "lockD",
                              blockedCount, waitedCount);
                }

                // wait until BlockedThread about to call E()
                // BlockedThread will wait on waiter for 3 times
                blockedThread.waitUntilWaiting();

                waitedCount++;
                checkInfo(blockedThread, Thread.State.TIMED_WAITING,
                          waiter, "waiter", blockedCount, waitedCount);

                blockedThread.waitUntilWaiting();

                waitedCount++;
                checkInfo(blockedThread, Thread.State.TIMED_WAITING,
                          waiter, "waiter", blockedCount, waitedCount);

                blockedThread.waitUntilWaiting();

                waitedCount++;
                checkInfo(blockedThread, Thread.State.TIMED_WAITING,
                          waiter, "waiter", blockedCount, waitedCount);

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Unexpected exception.");
                testFailed = true;
            }
        }

        public void run() {
            examine();
        } // run()

        public void waitUntilWaiting() {
            semaphore.semaP();

            // wait until the examiner is waiting for
            while (!blockedThread.hasWaitersForBlocked()) {
                goSleep(50);
            }
            // give a chance for the examiner thread to really wait
            goSleep(20);

        }
    } // Examiner
}
