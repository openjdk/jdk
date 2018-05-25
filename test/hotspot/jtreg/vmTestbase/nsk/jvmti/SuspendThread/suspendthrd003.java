/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jvmti.SuspendThread;

import java.io.PrintStream;

import nsk.share.*;
import nsk.share.jvmti.*;

public class suspendthrd003 extends DebugeeClass {

    final static int N_THREADS = 10;

    // load native library if required
    static {
        System.loadLibrary("suspendthrd003");
    }

    // run test from command line
    public static void main(String argv[]) {
        argv = nsk.share.jvmti.JVMTITest.commonInit(argv);

        // JCK-compatible exit
        System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
    }

    // run test from JCK-compatible environment
    public static int run(String argv[], PrintStream out) {
        return new suspendthrd003().runIt(argv, out);
    }

    /* =================================================================== */

    // scaffold objects
    ArgumentHandler argHandler = null;
    Log log = null;
    long timeout = 0;
    int status = Consts.TEST_PASSED;

    // tested thread
    suspendthrd003Thread thread = null;

    // run debuggee
    public int runIt(String argv[], PrintStream out) {
        argHandler = new ArgumentHandler(argv);
        log = new Log(out, argHandler);
        timeout = argHandler.getWaitTime() * 60 * 1000; // milliseconds

        for (int i = 0; i < N_THREADS; i++) {
            System.out.println("Starting TestedThread #" + i + ".");

            // Original suspendthrd001 test block starts here:
            //
            // create tested thread
            // Note: Cannot use TestedThread-N for thread name since
            // the agent has to know the thread's name.
            thread = new suspendthrd003Thread("TestedThread");

            // run tested thread
            log.display("Starting tested thread");
            try {
                thread.start();
                // SP1-w - wait for TestedThread-N to be ready
                if (!thread.checkReady()) {
                    throw new Failure("Unable to prepare tested thread: " + thread);
                }

                // testing sync
                log.display("Sync: thread started");
                // SP2.1-w - wait for agent thread
                // SP3.1-n - notify to start test
                // SP5.1-w - wait while testing
                status = checkStatus(status);
            } finally {
                // let thread to finish
                thread.letFinish();
            }

            // wait for thread to finish
            log.display("Finishing tested thread");
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new Failure(e);
            }

            // testing sync
            log.display("Sync: thread finished");
            // SP4.1-w - second wait for agent thread
            // SP6.1-n - notify to end test
            // SP7.1 - wait for agent end
            status = checkStatus(status);

            //
            // Original suspendthrd001 test block ends here.

            if (status != Consts.TEST_PASSED) {
                break;
            }

            resetAgentData();  // reset for another iteration
        }

        return status;
    }
}

/* =================================================================== */

// basic class for tested threads
class suspendthrd003Thread extends Thread {
    private volatile boolean threadReady = false;
    private volatile boolean shouldFinish = false;

    // make thread with specific name
    public suspendthrd003Thread(String name) {
        super(name);
    }

    // run thread continuously
    public void run() {
        // run in a loop
        // SP1-n - tell main we are ready
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
            throw new Failure("Interruption while preparing tested thread: \n\t" + e);
        }
        return threadReady;
    }

    // let thread to finish
    public void letFinish() {
        shouldFinish = true;
    }
}
