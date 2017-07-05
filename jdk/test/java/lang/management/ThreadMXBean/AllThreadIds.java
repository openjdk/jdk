/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic unit test of ThreadMXBean.getAllThreadIds()
 * @author  Alexei Guibadoulline and Mandy Chung
 *
 * @run build Barrier
 * @run main/othervm AllThreadIds
 */

import java.lang.management.*;
import java.util.*;

public class AllThreadIds {
    final static int DAEMON_THREADS = 20;
    final static int USER_THREADS = 5;
    final static int ALL_THREADS = DAEMON_THREADS + USER_THREADS;
    private static volatile boolean live[] = new boolean[ALL_THREADS];
    private static Thread allThreads[] = new Thread[ALL_THREADS];
    private static ThreadMXBean mbean
        = ManagementFactory.getThreadMXBean();
    private static boolean testFailed = false;
    private static boolean trace = false;

    private static long prevTotalThreadCount = 0;
    private static int prevLiveThreadCount = 0;
    private static int prevPeakThreadCount = 0;
    private static long curTotalThreadCount = 0;
    private static int curLiveThreadCount = 0;
    private static int curPeakThreadCount = 0;

    // barrier for threads communication
    private static Barrier barrier = new Barrier(ALL_THREADS);

    private static void printThreadList() {
        if (!trace) return;

        long[] list = mbean.getAllThreadIds();
        for (int i = 1; i <= list.length; i++) {
            System.out.println(i + ": Thread id = " + list[i-1]);
        }
        for (int i = 0; i < ALL_THREADS; i++) {
            Thread t = allThreads[i];
            System.out.println(t.getName() + " Id = " + t.getId() +
                " die = " + live[i] +
                " alive = " + t.isAlive());
        }
    }

    private static void fail(String msg) {
        trace = true;
        printThreadList();
        throw new RuntimeException(msg);
    }

    private static void checkThreadCount(int numNewThreads,
                                         int numTerminatedThreads)
        throws Exception {
        prevTotalThreadCount = curTotalThreadCount;
        prevLiveThreadCount = curLiveThreadCount;
        prevPeakThreadCount = curPeakThreadCount;
        curTotalThreadCount = mbean.getTotalStartedThreadCount();
        curLiveThreadCount = mbean.getThreadCount();
        curPeakThreadCount = mbean.getPeakThreadCount();

        if ((curLiveThreadCount - prevLiveThreadCount) !=
            (numNewThreads - numTerminatedThreads)) {
            fail("Unexpected number of live threads: " +
                " Prev live = " + prevLiveThreadCount +
                " Current live = " + curLiveThreadCount +
                " Threads added = " + numNewThreads +
                " Threads terminated = " + numTerminatedThreads);
        }
        if (curPeakThreadCount - prevPeakThreadCount != numNewThreads) {
            fail("Unexpected number of peak threads: " +
                " Prev peak = " + prevPeakThreadCount +
                " Current peak = " + curPeakThreadCount +
                " Threads added = " + numNewThreads);
        }
        if (curTotalThreadCount - prevTotalThreadCount != numNewThreads) {
            fail("Unexpected number of total threads: " +
                " Prev Total = " + prevTotalThreadCount +
                " Current Total = " + curTotalThreadCount +
                " Threads added = " + numNewThreads);
        }
        long[] list = mbean.getAllThreadIds();
        if (list.length != curLiveThreadCount) {
            fail("Array length returned by " +
                "getAllThreadIds() = " + list.length +
                " not matched count = " + curLiveThreadCount);
        }
    }

    public static void main(String args[]) throws Exception {
        if (args.length > 0 && args[0].equals("trace")) {
            trace = true;
        }

        curTotalThreadCount = mbean.getTotalStartedThreadCount();
        curLiveThreadCount = mbean.getThreadCount();
        curPeakThreadCount = mbean.getPeakThreadCount();
        checkThreadCount(0, 0);


        // Start all threads and wait to be sure they all are alive
        barrier.set(ALL_THREADS);
        for (int i = 0; i < ALL_THREADS; i++) {
            live[i] = true;
            allThreads[i] = new MyThread(i);
            allThreads[i].setDaemon( (i < DAEMON_THREADS) ? true : false);
            allThreads[i].start();
        }
        // wait until all threads are started.
        barrier.await();


        checkThreadCount(ALL_THREADS, 0);
        printThreadList();

        // Check mbean now. All threads must appear in getAllThreadIds() list
        long[] list = mbean.getAllThreadIds();

        for (int i = 0; i < ALL_THREADS; i++) {
            long expectedId = allThreads[i].getId();
            boolean found = false;

            if (trace) {
                System.out.print("Looking for thread with id " + expectedId);
            }
            for (int j = 0; j < list.length; j++) {
                if (expectedId == list[j]) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                testFailed = true;
            }
            if (trace) {
                if (!found) {
                    System.out.print(". TEST FAILED.");
                }
                System.out.println();
            }
        }
        if (trace) {
            System.out.println();
        }

        // Stop daemon threads, wait to be sure they all are dead, and check
        // that they disappeared from getAllThreadIds() list
        barrier.set(DAEMON_THREADS);
        for (int i = 0; i < DAEMON_THREADS; i++) {
            live[i] = false;
        }
        // wait until daemon threads are terminated.
        barrier.await();

        // give chance to threads to terminate
        pause();
        checkThreadCount(0, DAEMON_THREADS);

        // Check mbean now
        list = mbean.getAllThreadIds();

        for (int i = 0; i < ALL_THREADS; i++) {
            long expectedId = allThreads[i].getId();
            boolean found = false;
            boolean live = (i >= DAEMON_THREADS);

            if (trace) {
                System.out.print("Looking for thread with id " + expectedId +
                    (live ? " expected alive." : " expected terminated."));
            }
            for (int j = 0; j < list.length; j++) {
                if (expectedId == list[j]) {
                    found = true;
                    break;
                }
            }

            if (live != found) {
                testFailed = true;
            }
            if (trace) {
                if (live != found) {
                    System.out.println(" TEST FAILED.");
                } else {
                    System.out.println();
                }
            }
        }

        // Stop all threads and wait to be sure they all are dead
        barrier.set(ALL_THREADS - DAEMON_THREADS);
        for (int i = DAEMON_THREADS; i < ALL_THREADS; i++) {
            live[i] = false;
        }
        // wait until daemon threads are terminated .
        barrier.await();

        // give chance to threads to terminate
        pause();
        checkThreadCount(0, ALL_THREADS - DAEMON_THREADS);

        if (testFailed)
            throw new RuntimeException("TEST FAILED.");

        System.out.println("Test passed.");
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
            while (live[id]) {
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    System.out.println("Unexpected exception is thrown.");
                    e.printStackTrace(System.out);
                    testFailed = true;
                }
            }
            // signal about to exit
            barrier.signal();
        }
    }

    private static Object pauseObj = new Object();
    private static void pause() {
        // Enter lock a without blocking
        synchronized (pauseObj) {
            try {
                // may need to tune this timeout for different platforms
                pauseObj.wait(50);
            } catch (Exception e) {
                System.err.println("Unexpected exception.");
                e.printStackTrace(System.err);
            }
        }
    }

}
