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
 * @summary converted from VM Testbase nsk/jvmti/GetThreadState/thrstat001.
 * VM Testbase keywords: [quick, jpda, jvmti, onload_only_logic, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI function GetThreadState.  Java program
 *     launchs thread and 3 times calls a native method checkStatus. This
 *     method calls GetThreadState and checks if the returned value is
 *     correct.
 *     The test exercises JVMTI function GetThreadState.  Java program
 *     launches a thread and 3 times calls a native method checkStatus.
 *     This method calls GetThreadState and checks if the returned value is:
 *       - JVMTI_THREAD_STATE_RUNNABLE
 *               if thread is runnable
 *       - JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER
 *               if thread is waiting to enter sync. block
 *       - JVMTI_THREAD_STATE_IN_OBJECT_WAIT
 *               if thread is waiting by object.wait()
 *     The test also enables JVMPI_EVENT_METHOD_ENTRY and JVMPI_EVENT_METHOD_EXIT
 *     events and checks if GetThreadState returns JVMPI_THREAD_RUNNABLE for
 *     their threads.
 *     Failing criteria for the test are:
 *       - value returned by GetThreadState does not match expected value;
 *       - failures of used JVMTI functions.
 * COMMENTS
 *     Converted the test to use GetThreadState instead of GetThreadStatus.
 *     The test was updated to be more precise in its thread state transitions.
 *     Fixed according to 4387521 bug.
 *     To fix bug 4463667,
 *         1) two code fragments with "Thread.sleep(500);" are replaced
 *            with following ones:
 *                 Object obj = new Object();
 *                 *
 *                 *
 *                 synchronized (obj) {
 *                     obj.wait(500);
 *                 }
 *         2) extra waiting time
 *                 synchronized (obj) {
 *                     obj.wait(500);
 *                 }
 *            is added to get waiting time certainly after "contendCount"
 *            is set to 1.
 *     Fixed according to 4669812 bug.
 *     Ported from JVMDI.
 *     Fixed according to 4925857 bug:
 *       - rearranged synchronization of tested thread
 *       - enhanced descripton
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} thrstat01.java
 * @run main/othervm/native --enable-preview -agentlib:thrstat01 thrstat01
 */

public class thrstat01 {

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_MONITOR = 1;
    public static final int STATUS_WAIT    = 2;

    native static boolean checkStatus0(int statInd);

    static void checkStatus(int statInd) {
        if (!checkStatus0(statInd)) {
            throw new RuntimeException("Failed");
        }
    }

    public static void main(String[] args) {
        thrstat01 t = new thrstat01();
        t.meth(Thread.ofVirtual().name("tested_thread_thr1").unstarted(new TestedThreadThr1()));
        t.meth(Thread.ofPlatform().name("tested_thread_thr1").unstarted(new TestedThreadThr1()));
    }

    // barriers for testing thread status values
    public static Lock startingMonitor = new Lock();
    public static Object blockingMonitor = new Object();
    public static Lock endingMonitor = new Lock();

    void meth(Thread thr) {
        synchronized (blockingMonitor) {
            synchronized (startingMonitor) {
                startingMonitor.val = 0;
                thr.start();
                while (startingMonitor.val == 0) {
                    try {
                        startingMonitor.wait();
                    } catch (InterruptedException e) {
                        throw new Error("Unexpected: " + e);
                    }
                }
            }
            Thread.yield();
            checkStatus(STATUS_MONITOR);
        }

        synchronized (endingMonitor) {
            checkStatus(STATUS_WAIT);
            endingMonitor.val++;
            endingMonitor.notify();
        }

        try {
            thr.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected: " + e);
        }
   }

    static class Lock {
        public int val = 0;
    }
}

class TestedThreadThr1 implements Runnable {

    public TestedThreadThr1() {
    }

    public void run() {
        synchronized (thrstat01.endingMonitor) {
            thrstat01.checkStatus(thrstat01.STATUS_RUNNING);
            synchronized (thrstat01.startingMonitor) {
                thrstat01.startingMonitor.val++;
                thrstat01.startingMonitor.notifyAll();
            }

            synchronized (thrstat01.blockingMonitor) {
            }

            thrstat01.endingMonitor.val = 0;
            while (thrstat01.endingMonitor.val == 0) {
                try {
                    thrstat01.endingMonitor.wait();
                } catch (InterruptedException e) {
                    throw new Error("Unexpected: " + e);
                }
            }
        }
    }
}
