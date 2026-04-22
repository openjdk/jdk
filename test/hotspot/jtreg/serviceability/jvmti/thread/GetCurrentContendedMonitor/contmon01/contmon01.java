/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM Testbase nsk/jvmti/GetCurrentContendedMonitor/contmon01.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI function GetCurrentContendedMonitor.
 *     The test cases include:
 *       - current contended monitor: present or not;
 *       - thread: current, non-current;
 *       - thread waiting to enter a monitor or after wait();
 *     Failing criteria for the test are:
 *       - object returned by GetCurrentContendedMonitor is not the same
 *         as expected;
 *       - failures of used JVMTI functions.
 * COMMENTS
 *     By today, the test is referred from two bugs, 4327280 and 4463667
 *     To fix bug 4463667, one code fragment with "Thread.sleep(500);"
 *     is replaced with following one:
 *         Object obj = new Object();
 *             *
 *             *
 *         synchronized (obj) {
 *             obj.wait(500);
 *         }
 *     Note. Until 4327280 gets fixing, the correction cannot be tested.
 *     Fixed according to 4509016 bug.
 *     Fixed according to 4669812 bug.
 *     The test was fixed due to the following bug:
 *         4762695 nsk/jvmti/GetCurrentContendedMonitor/contmon01 has an
 *                 incorrect test
 *     Ported from JVMDI.
 *     Fixed according to 4925857 bug:
 *       - rearranged synchronization of tested thread
 *       - enhanced descripton
 *
 * @library /test/lib
 * @compile contmon01.java
 * @run main/othervm/native -agentlib:contmon01 contmon01
 */

public class contmon01 {

    native static void checkMonitor(int point, Thread thread, Object monitor);

    static {
        System.loadLibrary("contmon01");
    }

    public static volatile boolean startingBarrier = true;
    public static volatile boolean waitingBarrier = true;
    static Object lockFld = new Object();

    public static void doSleep() {
        try {
            Thread.sleep(10);
        } catch (Exception e) {
            throw new Error("Unexpected " + e);
        }
    }

    public static void main(String argv[]) {
        test(false);
        test(true);
    }

    public static void test(boolean isVirtual) {
        startingBarrier = true;
        waitingBarrier = true;
        Object lock = new Object();
        Thread currThread = Thread.currentThread();

        System.out.println("\nCheck #1: verifying a contended monitor of current thread \""
                + currThread.getName() + "\" ...");
        synchronized (lock) {
            checkMonitor(1, currThread, null);
        }
        System.out.println("Check #1 done");

        contmon01Task task = new contmon01Task();

        Thread thread = isVirtual ? Thread.ofVirtual().start(task) : Thread.ofPlatform().start(task);

        System.out.println("\nWaiting for auxiliary thread ...");
        while (startingBarrier) {
            doSleep();
        }
        System.out.println("Auxiliary thread is ready");

        System.out.println("\nCheck #3: verifying a contended monitor of auxiliary thread ...");
        checkMonitor(3, thread, null);
        System.out.println("Check #3 done");

        task.letItGo();

        while (waitingBarrier) {
            doSleep();
        }
        synchronized (lockFld) {
            System.out.println("\nMain thread entered lockFld's monitor"
                    + "\n\tand calling lockFld.notifyAll() to awake auxiliary thread");
            lockFld.notifyAll();
            System.out.println("\nCheck #4: verifying a contended monitor of auxiliary thread ...");
            checkMonitor(4, thread, lockFld);
            System.out.println("Check #4 done");
        }

        System.out.println("\nMain thread released lockFld's monitor"
                + "\n\tand waiting for auxiliary thread death ...");

        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected " + e);
        }
        System.out.println("\nCheck #5: verifying a contended monitor of dead auxiliary thread ...");
        checkMonitor(5, thread, null);
        System.out.println("Check #5 done");
    }
}


class contmon01Task implements Runnable {
    private volatile boolean flag = true;

    public void run() {
        System.out.println("check #2: verifying a contended monitor of current auxiliary thread ...");
        contmon01.checkMonitor(2, Thread.currentThread(), null);
        System.out.println("check #2 done");

        System.out.println("notifying main thread");
        System.out.println("thread is going to loop while <flag> is true ...");
        contmon01.startingBarrier = false;

        int i = 0;
        int n = 1000;
        while (flag) {
            if (n <= 0) {
                n = 1000;
                // no contmon01.doSleep() is allowed here as it can grab a lock
            }
            if (i > n) {
                i = 0;
                n--;
            }
            i++;
        }
        System.out.println("looping is done: <flag> is false");

        synchronized (contmon01.lockFld) {
            contmon01.waitingBarrier = false;
            System.out.println("\nthread entered lockFld's monitor"
                    + "\n\tand releasing it through the lockFld.wait() call");
            try {
                contmon01.lockFld.wait();
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }

        System.out.println("thread exiting");
    }

    public void letItGo() {
        flag = false;
    }
}
