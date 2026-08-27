/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM Testbase nsk/jvmti/SuspendThread/suspendthrd001.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     This JVMTI test exercises JVMTI thread function SuspendThread().
 *     This tests checks that for thread suspended by SuspendThread()
 *     function GetThreadState() returns JVMTI_THREAD_STATE_SUSPENDED.
 * COMMENTS
 *     Modified due to fix of the RFE
 *     5001769 TEST_RFE: remove usage of deprecated GetThreadStatus function
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:suspendthrd01 suspendthrd01
 */

public class suspendthrd01 {

    // load native library if required
    static {
        System.loadLibrary("suspendthrd01");
    }

    native static boolean suspendTestedThread(Thread thread);
    native static boolean checkSuspendedState(Thread thread);
    native static boolean resumeTestedThread(Thread thread);

    // run test from command line
    public static void main(String argv[]) {
        suspendthrd01Thread thread = new suspendthrd01Thread("TestedThread");
        System.out.println("Starting tested thread");
        thread.start();
        if (!thread.checkReady()) {
            throw new RuntimeException("Unable to prepare tested thread: " + thread);
        }
        if (!suspendTestedThread(thread)) {
            throw new RuntimeException("SuspendThread failed");
        }
        try {
            // the suspended thread cannot see the flag and must not finish
            thread.letFinish();
            if (!checkSuspendedState(thread)) {
                throw new RuntimeException("Thread is not in the suspended state");
            }
        } finally {
            if (!resumeTestedThread(thread)) {
                throw new RuntimeException("ResumeThread failed");
            }
        }
        System.out.println("Finishing tested thread");
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

/* =================================================================== */

// basic class for tested threads
class suspendthrd01Thread extends Thread {
    private volatile boolean threadReady = false;
    private volatile boolean shouldFinish = false;

    // make thread with specific name
    public suspendthrd01Thread(String name) {
        super(name);
    }

    // run thread continuously
    public void run() {
        // run in a loop
        threadReady = true;
        int i = 0;
        int n = 1000;
        while (!shouldFinish) {
            if (n <= 0) {
                n = 1000;
            }
            if (i > n) {
                i = 0;
                n = n - 1;
            }
            i = i + 1;
        }
    }

    // check if thread is ready
    public boolean checkReady() {
        try {
            while (!threadReady) {
                sleep(1000);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interruption while preparing tested thread: \n\t" + e);
        }
        return threadReady;
    }

    // let thread to finish
    public void letFinish() {
        shouldFinish = true;
    }
}
