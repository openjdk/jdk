/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug     4530538
 * @summary Basic unit test of ThreadInfo.getStackTrace() and
 *          ThreadInfo.getThreadState()
 * @author  Mandy Chung
 *
 * @run build Semaphore Utils
 * @run main ThreadStackTrace
 */

import java.lang.management.*;

public class ThreadStackTrace {
    private static ThreadMXBean mbean
        = ManagementFactory.getThreadMXBean();
    private static boolean notified = false;
    private static Object lockA = new Object();
    private static Object lockB = new Object();
    private static volatile boolean testFailed = false;
    private static String[] blockedStack = {"run", "test", "A", "B", "C", "D"};
    private static int bsDepth = 6;
    private static int methodB = 4;
    private static String[] examinerStack = {"run", "examine1", "examine2"};
    private static int esDepth = 3;
    private static int methodExamine1= 2;

    private static void checkNullThreadInfo(Thread t) throws Exception {
        ThreadInfo ti = mbean.getThreadInfo(t.getId());
        if (ti != null) {
            ThreadInfo info =
                mbean.getThreadInfo(t.getId(), Integer.MAX_VALUE);
            System.out.println(INDENT + "TEST FAILED:");
            if (info != null) {
                printStack(t, info.getStackTrace());
                System.out.println(INDENT + "Thread state: " + info.getThreadState());
            }
            throw new RuntimeException("TEST FAILED: " +
                "getThreadInfo() is expected to return null for " + t);
        }
    }

    private static boolean trace = false;
    public static void main(String args[]) throws Exception {
        if (args.length > 0 && args[0].equals("trace")) {
            trace = true;
        }

        Examiner examiner = new Examiner("Examiner");
        BlockedThread blocked = new BlockedThread("BlockedThread");
        examiner.setThread(blocked);

        checkNullThreadInfo(examiner);
        checkNullThreadInfo(blocked);

        // Start the threads and check them in  Blocked and Waiting states
        examiner.start();

        // block until examiner begins doing its real work
        examiner.waitForStarted();

        System.out.println("Checking stack trace for the examiner thread " +
                           "is waiting to begin.");

        // The Examiner should be waiting to be notified by the BlockedThread
        Utils.checkThreadState(examiner, Thread.State.WAITING);

        // Check that the stack is returned correctly for a new thread
        checkStack(examiner, examinerStack, esDepth);

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

        // Check that the stack is returned correctly for a terminated thread
        checkNullThreadInfo(examiner);
        checkNullThreadInfo(blocked);

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

    private static void checkStack(Thread t, String[] expectedStack,
                                   int depth) throws Exception {
        ThreadInfo ti = mbean.getThreadInfo(t.getId(), Integer.MAX_VALUE);
        StackTraceElement[] stack = ti.getStackTrace();

        if (trace) {
            printStack(t, stack);
        }
        int frame = stack.length - 1;
        for (int i = 0; i < depth; i++) {
            if (! stack[frame].getMethodName().equals(expectedStack[i])) {
                throw new RuntimeException("TEST FAILED: " +
                    "Expected " + expectedStack[i] + " in frame " + frame +
                    " but got " + stack[frame].getMethodName());
            }
            frame--;
        }
    }

    static class BlockedThread extends Thread {
        private Semaphore handshake = new Semaphore();

        BlockedThread(String name) {
            super(name);
        }
        boolean hasWaitersForBlocked() {
            return (handshake.getWaiterCount() > 0);
        }

        void waitUntilBlocked() {
            handshake.semaP();

            // give a chance for the examiner thread to really wait
            Utils.goSleep(20);
        }

        void waitUntilLockAReleased() {
            handshake.semaP();

            // give a chance for the examiner thread to really wait
            Utils.goSleep(50);
        }

        private void notifyWaiter() {
            // wait until the examiner waits on the semaphore
            while (handshake.getWaiterCount() == 0) {
                Utils.goSleep(20);
            }
            handshake.semaV();
        }

        private void test() {
            A();
        }
        private void A() {
            B();
        }
        private void B() {
            C();

            // notify the examiner about to block on lockB
            notifyWaiter();

            synchronized (lockB) {
            };
        }
        private void C() {
            D();
        }
        private void D() {
            // Notify that examiner about to enter lockA
            notifyWaiter();

            synchronized (lockA) {
                notified = false;
                while (!notified) {
                    try {
                        // notify the examiner about to release lockA
                        notifyWaiter();
                        // Wait and let examiner thread check the mbean
                        lockA.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        System.out.println("Unexpected exception.");
                        testFailed = true;
                    }
                }
                System.out.println("BlockedThread notified");
            }
        }

        public void run() {
            test();
        } // run()
    } // BlockedThread

    static class Examiner extends Thread {
        private static BlockedThread blockedThread;
        private Semaphore handshake = new Semaphore();

        Examiner(String name) {
            super(name);
        }

        public void setThread(BlockedThread thread) {
            blockedThread = thread;
        }

        public synchronized void waitForStarted() {
            // wait until the examiner is about to block
            handshake.semaP();

            // wait until the examiner is waiting for blockedThread's notification
            while (!blockedThread.hasWaitersForBlocked()) {
                Utils.goSleep(50);
            }
            // give a chance for the examiner thread to really wait
            Utils.goSleep(20);
        }

        private Thread itself;
        private void examine1() {
            synchronized (lockB) {
                examine2();
                try {
                    System.out.println("Checking examiner's its own stack trace");
                    Utils.checkThreadState(itself, Thread.State.RUNNABLE);
                    checkStack(itself, examinerStack, methodExamine1);

                    // wait until blockedThread is blocked on lockB
                    blockedThread.waitUntilBlocked();

                    System.out.println("Checking stack trace for " +
                        "BlockedThread - should be blocked on lockB.");
                    Utils.checkThreadState(blockedThread, Thread.State.BLOCKED);
                    checkStack(blockedThread, blockedStack, methodB);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Unexpected exception.");
                    testFailed = true;
                }
            }
        }

        private void examine2() {
            synchronized (lockA) {
                // wait until main thread gets signalled of the semaphore
                while (handshake.getWaiterCount() == 0) {
                    Utils.goSleep(20);
                }

                handshake.semaV();  // notify the main thread
                try {
                    // Wait until BlockedThread is about to block on lockA
                    blockedThread.waitUntilBlocked();

                    System.out.println("Checking examiner's its own stack trace");
                    Utils.checkThreadState(itself, Thread.State.RUNNABLE);
                    checkStack(itself, examinerStack, esDepth);

                    System.out.println("Checking stack trace for " +
                        "BlockedThread - should be blocked on lockA.");
                    Utils.checkThreadState(blockedThread, Thread.State.BLOCKED);
                    checkStack(blockedThread, blockedStack, bsDepth);

                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Unexpected exception.");
                    testFailed = true;
                }
            }

            // release lockA and let BlockedThread to get the lock
            // and wait on lockA
            blockedThread.waitUntilLockAReleased();

            synchronized (lockA) {
                try {
                    System.out.println("Checking stack trace for " +
                        "BlockedThread - should be waiting on lockA.");
                    Utils.checkThreadState(blockedThread, Thread.State.WAITING);
                    checkStack(blockedThread, blockedStack, bsDepth);

                    // Let the blocked thread go
                    notified = true;
                    lockA.notify();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Unexpected exception.");
                    testFailed = true;
                }
            }
            // give some time for BlockedThread to proceed
            Utils.goSleep(50);
        } // examine2()

        public void run() {
            itself = Thread.currentThread();
            examine1();
        } // run()
    } // Examiner
}
