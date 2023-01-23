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
 * @summary converted from VM Testbase nsk/jvmti/SuspendThread/suspendthrd002.
 * VM Testbase keywords: [jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     This JVMTI test exercises JVMTI thread function SuspendThread().
 *     This tests checks that thread suspended by SuspendThread()
 *     will not run until resumed by ResumeThread().
 * COMMENTS
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:suspendthrd02=-waittime=5 suspendthrd02
 */

import jdk.test.lib.jvmti.DebugeeClass;

public class suspendthrd02 extends DebugeeClass {

    // load native library if required
    static {
        System.loadLibrary("suspendthrd02");
    }

    public static void main(String argv[]) {
        int result = new suspendthrd02().runIt();
        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }

    /* =================================================================== */

    long timeout = 0;
    int status = DebugeeClass.TEST_PASSED;

    // tested thread
    suspendthrd02Thread thread = null;

    // run debuggee
    public int runIt() {
        timeout = 60 * 1000; // milliseconds

        // create tested thread
        thread = new suspendthrd02Thread("TestedThread");

        // run tested thread
        System.out.println("Staring tested thread");
        try {
            thread.start();
            if (!thread.checkReady()) {
                throw new RuntimeException("Unable to prepare tested thread: " + thread);
            }

            // testing sync
            System.out.println("Sync: thread started");
            status = checkStatus(status);
        } finally {
            // let thread to finish
            thread.letFinish();
        }

        // wait for thread to finish
        System.out.println("Finishing tested thread");
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // testing sync
        System.out.println("Sync: thread finished");
        status = checkStatus(status);

        return status;
    }
}

/* =================================================================== */

// basic class for tested threads
class suspendthrd02Thread extends Thread {
    private volatile boolean threadReady = false;
    private volatile boolean shouldFinish = false;

    // make thread with specific name
    public suspendthrd02Thread(String name) {
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
            Thread.yield();
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
