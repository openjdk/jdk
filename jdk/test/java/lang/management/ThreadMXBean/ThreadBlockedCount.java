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
 * @bug     4530538
 * @summary Basic unit test of ThreadInfo.getBlockedCount()
 * @author  Alexei Guibadoulline and Mandy Chung
 *
 * @build ThreadExecutionSynchronizer
 * @run main ThreadBlockedCount
 */

import java.lang.management.*;

public class ThreadBlockedCount {
    final static long EXPECTED_BLOCKED_COUNT = 3;
    final static int  DEPTH = 10;
    private static ThreadMXBean mbean
        = ManagementFactory.getThreadMXBean();

    private static Object a = new Object();
    private static Object b = new Object();
    private static Object c = new Object();
    private static boolean aNotified = false;
    private static boolean bNotified = false;
    private static boolean cNotified = false;
    private static Object blockedObj1 = new Object();
    private static Object blockedObj2 = new Object();
    private static Object blockedObj3 = new Object();
    private static volatile boolean testFailed = false;
    private static BlockingThread blocking;
    private static BlockedThread blocked;
    private static ThreadExecutionSynchronizer thrsync;



    public static void main(String args[]) throws Exception {
        // Create the BlockingThread before BlockedThread
        // to make sure BlockingThread enter the lock before BlockedThread
        thrsync = new ThreadExecutionSynchronizer();

        blocking = new BlockingThread();
        blocking.start();

        blocked = new BlockedThread();
        blocked.start();

        try {
            blocking.join();
            blocked.join();
        } catch (InterruptedException e) {
            System.err.println("Unexpected exception.");
            e.printStackTrace(System.err);
            throw e;
        }

        if (testFailed) {
            throw new RuntimeException("TEST FAILED.");
        }
        System.out.println("Test passed.");
    }


    static class BlockedThread extends Thread {
        // NOTE: We can't use a.wait() here because wait() call is counted
        // as blockedCount.  Instead, we use a boolean flag and sleep.
        //
        public void run() {
            // wait Blocking thread
            thrsync.signal();

            // Enter lock a without blocking
            synchronized (a) {
                // wait until BlockingThread holds blockedObj1
                while (!aNotified) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        System.err.println("Unexpected exception.");
                        e.printStackTrace(System.err);
                        testFailed = true;
                        break;
                    }
                }

                // signal BlockingThread.
                thrsync.signal();

                // Block to enter blockedObj1
                // blockedObj1 should be owned by BlockingThread
                synchronized (blockedObj1) {
                    System.out.println("BlockedThread entered lock blockedObj1.");
                }
            }

            // signal BlockingThread.
            thrsync.signal();

            // Enter lock a without blocking
            synchronized (b) {
                // wait until BlockingThread holds blockedObj2
                while (!bNotified) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        System.err.println("Unexpected exception.");
                        e.printStackTrace(System.err);
                        testFailed = true;
                        break;
                    }
                }

                // signal BlockingThread.
                thrsync.signal();

                // Block to enter blockedObj2
                // blockedObj2 should be owned by BlockingThread
                synchronized (blockedObj2) {
                    System.out.println("BlockedThread entered lock blockedObj2.");
                }
            }

            // signal BlockingThread.
            thrsync.signal();

            // Enter lock a without blocking
            synchronized (c) {
                // wait until BlockingThread holds blockedObj3
                while (!cNotified) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        System.err.println("Unexpected exception.");
                        e.printStackTrace(System.err);
                        testFailed = true;
                        break;
                    }
                }

                // signal BlockingThread.
                thrsync.signal();

                // Block to enter blockedObj3
                // blockedObj3 should be owned by BlockingThread
                synchronized (blockedObj3) {
                    System.out.println("BlockedThread entered lock blockedObj3.");
                }
            }

            // wait for the thread stats to be updated for 10 seconds
            for (int i = 0; i < 100; i++) {
                if (getBlockedCount() == EXPECTED_BLOCKED_COUNT) {
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.err.println("Unexpected exception.");
                    e.printStackTrace(System.err);
                    testFailed = true;
                    return;
                }
            }
            long count = getBlockedCount();
            if (count != EXPECTED_BLOCKED_COUNT) {
                System.err.println("TEST FAILED: Blocked thread has " + count +
                                    " blocked counts. Expected " +
                                    EXPECTED_BLOCKED_COUNT);
                testFailed = true;
            }
        } // run()
    } // BlockingThread

    static class BlockingThread extends Thread {
        private void waitForSignalToRelease() {

            // wait for BlockedThread.
            thrsync.waitForSignal();

            boolean threadBlocked = false;
            while (!threadBlocked) {
                // give a chance for BlockedThread to really block
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    System.err.println("Unexpected exception.");
                    e.printStackTrace(System.err);
                    testFailed = true;
                    break;
                }
                ThreadInfo info = mbean.getThreadInfo(blocked.getId());
                threadBlocked = (info.getThreadState() == Thread.State.BLOCKED);
            }
        }

        public void run() {
            // wait for BlockedThread.
            thrsync.waitForSignal();

            synchronized (blockedObj1) {
                System.out.println("BlockingThread attempts to notify a");
                aNotified = true;
                waitForSignalToRelease();
            }

            // wait for BlockedThread.
            thrsync.waitForSignal();

            // block until BlockedThread is ready
            synchronized (blockedObj2) {
                System.out.println("BlockingThread attempts to notify b");
                bNotified = true;
                waitForSignalToRelease();
            }

            // wait for BlockedThread.
            thrsync.waitForSignal();

            // block until BlockedThread is ready
            synchronized (blockedObj3) {
                System.out.println("BlockingThread attempts to notify c");
                cNotified = true;
                waitForSignalToRelease();
            }

        } // run()
    } // BlockedThread

    private static long getBlockedCount() {
        long count;
        // Check the mbean now
        ThreadInfo ti = mbean.getThreadInfo(Thread.currentThread().
                getId());
        count = ti.getBlockedCount();
        return count;
    }
}
