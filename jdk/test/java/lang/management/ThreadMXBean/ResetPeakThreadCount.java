/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4892507 8020875 8021335
 * @summary Basic Test for the following reset methods:
 *          - ThreadMXBean.resetPeakThreadCount()
 * @author  Mandy Chung
 * @author  Jaroslav Bachorik
 *
 * @build ResetPeakThreadCount
 * @build ThreadDump
 * @run main/othervm ResetPeakThreadCount
 */

import java.lang.management.*;

public class ResetPeakThreadCount {
    // initial number of new threads started
    private static final int DAEMON_THREADS_1 = 8;
    private static final int EXPECTED_PEAK_DELTA_1 = 8;

    // Terminate half of the threads started
    private static final int TERMINATE_1 = 4;

    // start new threads but expected the peak unchanged
    private static final int DAEMON_THREADS_2 = 2;
    private static final int EXPECTED_PEAK_DELTA_2 = 0;

    // peak thread count reset before starting new threads
    private static final int DAEMON_THREADS_3 = 4;
    private static final int EXPECTED_PEAK_DELTA_3 = 4;

    private static final int TERMINATE_2 = 8;

    private static final int TERMINATE_3 = 2;

    private static final int ALL_THREADS = DAEMON_THREADS_1 +
        DAEMON_THREADS_2 + DAEMON_THREADS_3;
    // barrier for threads communication
    private static final Barrier barrier = new Barrier(DAEMON_THREADS_1);

    private static final Thread allThreads[] = new Thread[ALL_THREADS];
    private static final boolean live[] = new boolean[ALL_THREADS];
    private static final ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
    private static volatile boolean testFailed = false;

    public static void main(String[] argv) throws Exception {
        // This test does not expect any threads to be created
        // by the test harness after main() is invoked.
        // The checkThreadCount() method is to produce more
        // diagnostic information in case any unexpected test failure occur.
        long previous = mbean.getThreadCount();
        long current = previous;

        // reset the peak to start from a scratch
        resetPeak(current);

        // start DAEMON_THREADS_1 number of threads
        current = startThreads(0, DAEMON_THREADS_1, EXPECTED_PEAK_DELTA_1);

        checkThreadCount(previous, current, DAEMON_THREADS_1);
        previous = current;

        // terminate TERMINATE_1 number of threads and reset peak
        current = terminateThreads(0, TERMINATE_1);

        checkThreadCount(previous, current, TERMINATE_1 * -1);

        previous = current;

        // start DAEMON_THREADS_2 number of threads
        // expected peak is unchanged
        current = startThreads(DAEMON_THREADS_1, DAEMON_THREADS_2,
                               EXPECTED_PEAK_DELTA_2);

        checkThreadCount(previous, current, DAEMON_THREADS_2);
        previous = current;

        // Reset the peak
        resetPeak(current);

        // start DAEMON_THREADS_3 number of threads
        current = startThreads(DAEMON_THREADS_1 + DAEMON_THREADS_2,
                               DAEMON_THREADS_3, EXPECTED_PEAK_DELTA_3);

        checkThreadCount(previous, current, DAEMON_THREADS_3);
        previous = current;

        // terminate TERMINATE_2 number of threads and reset peak
        current = terminateThreads(TERMINATE_1, TERMINATE_2);

        checkThreadCount(previous, current, TERMINATE_2 * -1);
        previous = current;

        resetPeak(current);

        // terminate TERMINATE_3 number of threads and reset peak
        current = terminateThreads(TERMINATE_1 + TERMINATE_2, TERMINATE_3);

        checkThreadCount(previous, current, TERMINATE_3 * -1);
        resetPeak(current);

        if (testFailed)
            throw new RuntimeException("TEST FAILED.");

        System.out.println("Test passed");
    }

    private static long startThreads(int from, int count, int delta) throws InterruptedException {
        // get current peak thread count
        long peak1 = mbean.getPeakThreadCount();
        long current = mbean.getThreadCount();

        // Start threads and wait to be sure they all are alive
        System.out.println("Starting " + count + " threads....");
        barrier.set(count);
        synchronized(live) {
            for (int i = from; i < (from + count); i++) {
                live[i] = true;
                allThreads[i] = new MyThread(i);
                allThreads[i].setDaemon(true);
                allThreads[i].start();
            }
        }
        // wait until all threads have started.
        barrier.await();

        // get peak thread count after daemon threads have started
        long peak2 = mbean.getPeakThreadCount();

        System.out.println("   Current = " + mbean.getThreadCount() +
            " Peak before = " + peak1 + " after: " + peak2);

        if (peak2 != (peak1 + delta)) {
            throw new RuntimeException("Current Peak = " + peak2 +
                " Expected to be == previous peak = " + peak1 + " + " +
                delta);
        }
        // wait until the current thread count gets incremented
        while (mbean.getThreadCount() < (current + count)) {
            Thread.sleep(100);
        }
        current = mbean.getThreadCount();
        System.out.println("   Live thread count before returns " + current);
        return current;
    }

    private static long terminateThreads(int from, int count) throws InterruptedException {
        // get current peak thread count
        long peak1 = mbean.getPeakThreadCount();

        // Stop daemon threads and wait to be sure they all are dead
        System.out.println("Terminating " + count + " threads....");
        barrier.set(count);
        synchronized(live) {
            for (int i = from; i < (from+count); i++) {
                live[i] = false;
            }
            live.notifyAll();
        }
        // wait until daemon threads terminated.
        barrier.await();

        // get peak thread count after daemon threads have terminated
        long peak2 = mbean.getPeakThreadCount();
        // assuming no system thread is added
        if (peak2 != peak1) {
            throw new RuntimeException("Current Peak = " + peak2 +
                " Expected to be = previous peak = " + peak1);
        }

        for (int i = from; i < (from+count); i++) {
            allThreads[i].join();
        }

        // there is a race in the counter update logic somewhere causing
        // the thread counters go ff
        // we need to give the terminated threads some extra time to really die
        // JDK-8021335
        Thread.sleep(500);

        long current = mbean.getThreadCount();
        System.out.println("   Live thread count before returns " + current);
        return current;
    }

    private static void resetPeak(long expectedCount) {
        long peak3 = mbean.getPeakThreadCount();
        long current = mbean.getThreadCount();

        // Nightly testing showed some intermittent failure.
        // Check here to get diagnostic information if some strange
        // behavior occurs.
        checkThreadCount(expectedCount, current, 0);

        // Reset peak thread count
        mbean.resetPeakThreadCount();

        long afterResetPeak = mbean.getPeakThreadCount();
        long afterResetCurrent = mbean.getThreadCount();
        System.out.println("Reset peak before = " + peak3 +
            " current = " + current +
            " after reset peak = " + afterResetPeak +
            " current = " + afterResetCurrent);

        if (afterResetPeak != current) {
            throw new RuntimeException("Current Peak after reset = " +
                afterResetPeak +
                " Expected to be = current count = " + current);
        }
    }

    private static void checkThreadCount(long previous, long current, int expectedDelta) {
        if (current != previous + expectedDelta) {
            ThreadDump.threadDump();
            throw new RuntimeException("***** Unexpected thread count:" +
                               " previous = " + previous +
                               " current = " + current +
                               " delta = " + expectedDelta + "*****");
        }
    }

    // The MyThread thread lives as long as correspondent live[i] value is true
    private static class MyThread extends Thread {
        int id;

        MyThread(int id) {
            this.id = id;
        }

        public void run() {
            // signal started
            barrier.signal();
            synchronized(live) {
                while (live[id]) {
                    try {
                        live.wait(100);
                    } catch (InterruptedException e) {
                        System.out.println("Unexpected exception is thrown.");
                        e.printStackTrace(System.out);
                        testFailed = true;
                    }
                }
            }
            // signal about to exit
            barrier.signal();
        }
    }

}
