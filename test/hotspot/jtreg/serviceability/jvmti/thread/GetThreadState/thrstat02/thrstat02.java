/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM Testbase nsk/jvmti/GetThreadState/thrstat002.
 * VM Testbase keywords: [quick, jpda, jvmti, onload_only_logic, noras, quarantine]
 * VM Testbase comments: 6260469
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMDI function GetThreadState.  Java program launches
 *     a thread and for various thread states calls Thread.suspend()/resume()
 *     methods or JVMDI functions SuspendThread/ResumeThread. Then native method
 *     checkStatus is invoked. This method calls GetThreadState and checks if
 *     the returned values are correct and JVMTI_THREAD_STATE_SUSPENDED bit
 *     is set (or clear after resume).
 *     The thread statuses are:
 *       - JVMTI_THREAD_STATE_RUNNABLE
 *               if thread is runnable
 *       - JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER
 *               if thread is waiting to enter sync. block
 *       - JVMTI_THREAD_STATE_IN_OBJECT_WAIT
 *               if thread is waiting by object.wait()
 *     Failing criteria for the test are:
 *       - values returned by GetThreadState are not the same as expected;
 *       - failure of used JVMTI functions.
 * COMMENTS
 *     Converted the test to use GetThreadState instead of GetThreadStatus.
 *     Fixed according to 4387521 and 4427103 bugs.
 *     Fixed according to 4463667 bug.
 *     Fixed according to 4669812 bug.
 *     Ported from JVMDI.
 *     Fixed according to 4925857 bug:
 *       - rearranged synchronization of tested thread
 *       - enhanced descripton
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:thrstat02 thrstat02 5
 */

import java.util.concurrent.CountDownLatch;

public class thrstat02 {

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_MONITOR = 1;
    public static final int STATUS_WAIT    = 2;

    native static void init(int waitTime);
    native static boolean checkStatus0(int statInd, boolean suspended);
    static void checkStatus(int statInd, boolean suspended) {
        if (!checkStatus0(statInd, suspended)) {
            throw new RuntimeException("checkStatus failed");
        }
    }

    public static void main(String[] args) {
        int waitTime = 2;
        if (args.length > 0) {
            try {
                int i  = Integer.parseInt(args[0]);
                waitTime = i;
            } catch (NumberFormatException ex) {
                System.out.println("# Wrong argument \"" + args[0]
                    + "\", the default value is used");
            }
        }
        System.out.println("# Waiting time = " + waitTime + " mins");
        init(waitTime);
        try {
            new thrstat02().meth();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static CountDownLatch startingBarrier;
    public static CountDownLatch runningBarrier;
    public static Object blockingMonitor = new Object();
    public static Lock endingMonitor = new Lock();

    static volatile boolean targetAboutToLock = false;

    void meth() throws InterruptedException {
        thrstat02a thr = new thrstat02a("tested_thread_thr1");
        startingBarrier = new CountDownLatch(1);
        runningBarrier = new CountDownLatch(1);

        synchronized (blockingMonitor) {
            thr.start();
            System.out.println("thrstat02.meth after thr.start()");

            startingBarrier.await();
            System.out.println("thrstat02.meth after thr.startingBarrier.waitFor()");

            waitForThreadBlocked(thr);

            checkStatus(STATUS_MONITOR, false);
            System.out.println("thrstat02.meth after checkStatus(STATUS_MONITOR,false)");

            thr.suspend();
            System.out.println("thrstat02.meth after thr.suspend()");
            checkStatus(STATUS_MONITOR, true);
            System.out.println("thrstat02.meth after checkStatus(STATUS_MONITOR,true)");

            thr.resume();
            System.out.println("thrstat02.meth after thr.resume()");
            checkStatus(STATUS_MONITOR, false);
            System.out.println("thrstat02.meth after checkStatus(STATUS_MONITOR,false)");
        }

        runningBarrier.await();
        checkStatus(STATUS_RUNNING, false);
        thr.suspend();
        checkStatus(STATUS_RUNNING, true);
        thr.resume();
        checkStatus(STATUS_RUNNING, false);
        thr.letItGo();

        synchronized (endingMonitor) {
            checkStatus(STATUS_WAIT, false);
            thr.suspend();
            checkStatus(STATUS_WAIT, true);
            thr.resume();
            checkStatus(STATUS_WAIT, false);
            endingMonitor.val++;
            endingMonitor.notifyAll();
        }

        try {
            thr.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected: " + e);
        }
    }

    private static void waitForThreadBlocked(Thread t) {
        // Ensure that the thread is blocked on the right monitor
        while (!targetAboutToLock || t.getState() != Thread.State.BLOCKED) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                System.out.println("thrstat02.waitForThreadBlocked was interrupted: " + ex.getMessage());
            }
        }
    }

    static class Lock {
        public int val = 0;
    }
}

class thrstat02a extends Thread {
    private volatile boolean flag = true;

    public thrstat02a(String name) {
        super(name);
    }

    public void run() {
        synchronized (thrstat02.endingMonitor) {
            System.out.println("thrstat02a.run before startingBarrier.unlock");
            thrstat02.startingBarrier.countDown();

            System.out.println("thrstat02a.run after  startingBarrier.unlock");

            System.out.println("thrstat02a.run before blockingMonitor lock");

            thrstat02.targetAboutToLock = true;

            synchronized (thrstat02.blockingMonitor) {
               System.out.println("thrstat02a.run blockingMonitor locked");
            }
            System.out.println("thrstat02a.run after blockingMonitor lock");

            thrstat02.runningBarrier.countDown();
            System.out.println("thrstat02a.run after runningBarrier unlock");
            int i = 0;
            int n = 1000;
            while (flag) {
                if (n <= 0) {
                    n = 1000;
                }
                if (i > n) {
                    i = 0;
                    n--;
                }
                i++;
            }

            thrstat02.endingMonitor.val = 0;
            while (thrstat02.endingMonitor.val == 0) {
                try {
                    thrstat02.endingMonitor.wait();
                } catch (InterruptedException e) {
                    throw new Error("Unexpected: " + e);
                }
            }
            System.out.println("thrstat02a.run before endingMonitor unlock");
       }
    }

    public void letItGo() {
        flag = false;
    }
}
