/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM Testbase nsk/jvmti/GetCurrentContendedMonitor/contmon002.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test checks if JVMTI function GetCurrentContendedMonitor returns
 *     NULL when there is no current contended monitor.
 *     Failing criteria for the test are:
 *       - value returned by GetCurrentContendedMonitor is not NULL;
 *       - failures of used JVMTI functions.
 * COMMENTS
 *     Fixed according to the bug 4509016.
 *     Ported from JVMDI.
 *     Fixed according to 4925857 bug:
 *       - rearranged synchronization of tested thread
 *       - enhanced descripton
 *
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} contmon02.java
 * @run main/othervm/native --enable-preview -agentlib:contmon02 contmon02
 */

public class contmon02 {

    native static void checkMonitor(int point, Thread thr);

    static {
        System.loadLibrary("contmon02");
    }

    public static boolean startingBarrier = true;

    public static void doSleep() {
        try {
            Thread.sleep(10);
        } catch (Exception e) {
            throw new Error("Unexpected " + e);
        }
    }

    public static void main(String argv[]) {
        test(true);
        test(false);
    }

    public static void test(boolean isVirtual) {
        checkMonitor(1, Thread.currentThread());

        contmon02Task task = new contmon02Task();
        Thread thread = isVirtual ? Thread.ofVirtual().start(task) : Thread.ofPlatform().start(task);

        while (startingBarrier) {
            doSleep();
        }
        checkMonitor(2, thread);
        task.letItGo();
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected " + e);
        }
    }
}

class contmon02Task implements Runnable {
    private volatile boolean flag = true;

    private synchronized void meth() {
        contmon02.startingBarrier = false;
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
    }

    public void run() {
        meth();
    }

    public void letItGo() {
        flag = false;
    }
}
