/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jvmti.GetObjectMonitorUsage;

import java.io.PrintStream;

public class objmonusage003 {

    final static int JCK_STATUS_BASE = 95;
    final static int NUMBER_OF_ENTERING_THREADS = 4;
    final static int NUMBER_OF_WAITING_THREADS  = 4;
    final static int NUMBER_OF_THREADS = NUMBER_OF_ENTERING_THREADS + NUMBER_OF_WAITING_THREADS;

    static {
        try {
            System.loadLibrary("objmonusage003");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load objmonusage003 library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    static Object lockCheck = new Object();
    static TestThread thr[] = new TestThread[NUMBER_OF_THREADS];

    native static int getRes();
    native static void check(Object obj, Thread owner,
                             int entryCount, int waiterCount, int notifyWaiterCount);

    /* Scenario #1:
     * - non-zero entering threads
     * - zero re-entering threads
     * - zero threads waiting to be notified
     */
    static void test1() throws Error {
        synchronized (lockCheck) {
            // entry count: 1
            // count of threads waiting to enter: 0
            // count of threads waiting to re-enter: 0
            // count of threads waiting to be notified: 0
            check(lockCheck, Thread.currentThread(), 1, 0, 0);

            for (int i = 0; i < NUMBER_OF_ENTERING_THREADS; i++) {
                thr[i] = new EnteringThread();
                thr[i].start();
                // this EnteringThread has to be blocked on the lockCheck enter
                thr[i].waitReady();
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
    static void test2() throws Error {
        for (int i = NUMBER_OF_ENTERING_THREADS; i < NUMBER_OF_THREADS; i++) {
            thr[i] = new WaitingThread();
            thr[i].start();
            thr[i].waitReady(); // the WaitingThread has to wait to be notified in a lockCheck.wait()
        }
        synchronized (lockCheck) {
            for (int i = 0; i < NUMBER_OF_ENTERING_THREADS; i++) {
                thr[i] = new EnteringThread();
                thr[i].start();
                thr[i].waitReady(); // the EnteringThread has to block on monitor enter
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
    static void test3() throws Error {
        for (int i = NUMBER_OF_ENTERING_THREADS; i < NUMBER_OF_THREADS; i++) {
            thr[i] = new WaitingThread();
            thr[i].start();
            // the WaitingThread has to wait to be notified in a lockCheck.wait()
            thr[i].waitReady();
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
                thr[i] = new EnteringThread();
                thr[i].start();
                // this EnteringThread has to be blocked on the lockCheck enter
                thr[i].waitReady();
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
                // now the notified WaitingThread has to be blocked on the lockCheck re-enter

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
        args = nsk.share.jvmti.JVMTITest.commonInit(args);

        // produce JCK-like exit status.
        System.exit(run(args, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String args[], PrintStream out) {
        check(lockCheck, null, 0, 0, 0);

        test1();
        test2();
        test3();

        check(lockCheck, null, 0, 0, 0);
        return getRes();
    }

    static class TestThread extends Thread {
        public volatile boolean ready = false;
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

    static class EnteringThread extends TestThread {
        public void run() {
            ready = true;
            synchronized (lockCheck) {
            }
        }
    }

    static class WaitingThread extends TestThread {
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
