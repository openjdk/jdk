/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4413752 8262881
 * @summary Test SuspendThread with ObjectMonitor wait.
 * @requires vm.jvmti
 * @library /test/lib
 * @compile SuspendWithObjectMonitorWaitReentryPartSecond.java
 * @run main/othervm/native -agentlib:SuspendWithObjectMonitorWait SuspendWithObjectMonitorWaitReentryPartSecond 3
 */

import java.io.PrintStream;

//
// SuspendWithObjectMonitorWaitReentryPartSecond algorithm:
//
// main                 waiter                  resumer
// ===================  ======================  ===================
// launch waiter
// <launch returns>     waiter running
// launch resumer       enter threadLock
// <launch returns>     while !READY_TO_NOTIFY  resumer running
// :                      threadLock.wait(1)    wait for notify
// enter threadLock     :                       :
// set READY_TO_NOTIFY  :
// threadLock.notify    wait finishes           :
// : delay 200ms        reenter blocks          :
// suspend waiter       <suspended>             :
// <ready to test>      :                       :
// :                    :                       :
// notify resumer       :                       wait finishes
// delay 1-second       :                       :
// exit threadLock      :                       :
// join resumer         :                       enter threadLock
// :                    <resumed>               resume waiter
// :                    :                       exit threadLock
// :                    reenter threadLock      :
// <join returns>       :                       resumer exits
// join waiter          :
// <join returns>       waiter exits
//
// Note: The sleep(1-second) in main along with the delayed exit
//       of threadLock in main forces the resumer thread to reach
//       "enter threadLock" and block. This difference from the default scenario
//       forces the resumer thread to be contending for threadLock
//       while the waiter thread is in the threadLock.wait(1) tight
//       loop increasing stress on the monitor sub-system.
//
// Note: sleep(200ms) here while holding the threadLock to allow the
//       waiter thread's timed wait to finish before we attempt to
//       suspend the waiter thread.
//

public class SuspendWithObjectMonitorWaitReentryPartSecond extends SuspendWithObjectMonitorWaitBase {

    @Override
    public int run(int timeMax, PrintStream out) {
        return doWork3(timeMax, out);
    }

    // Suspend on the re-entry path of wait.
    public int doWork3(int timeMax, PrintStream out) {
        SuspendWithObjectMonitorWaitWorker waiter;    // waiter thread
        SuspendWithObjectMonitorWaitWorker resumer;   // resumer thread

        System.out.println("Test 3: About to execute for " + timeMax + " seconds.");

        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (timeMax * 1000)) {
            count++;
            testState = TS_INIT;  // starting the test loop

            // launch the waiter thread
            waiter = launchWaiter(100);

            // launch the resumer thread
            resumer = launchResumer(waiter);

            checkTestState(TS_RESUMER_RUNNING);

            // The waiter thread was synchronized on threadLock before it
            // set TS_WAITER_RUNNING and notified barrierLaunch above so
            // we cannot enter threadLock until the waiter thread calls
            // threadLock.wait().
            synchronized (threadLock) {
                // notify waiter thread so it can try to reenter threadLock
                testState = TS_READY_TO_NOTIFY;
                threadLock.notify();

                try {
                    Thread.sleep(200);
                } catch (Exception e) {}

                // wait for the waiter thread to block
                logDebug("before contended enter wait");
                int retCode = wait4ContendedEnter(waiter);
                if (retCode != 0) {
                    throw new RuntimeException("error in JVMTI GetThreadState: "
                            + "retCode=" + retCode);
                }
                logDebug("done contended enter wait");

                checkTestState(TS_READY_TO_NOTIFY);
                testState = TS_CALL_SUSPEND;
                logDebug("before suspend thread");
                retCode = suspendThread(waiter);
                if (retCode != 0) {
                    throw new RuntimeException("error in JVMTI SuspendThread: "
                            + "retCode=" + retCode);
                }
                logDebug("suspended thread");

                //
                // At this point, all of the child threads are running
                // and we can get to meat of the test:
                //
                // - suspended threadLock waiter (trying to reenter)
                // - a blocked threadLock enter in the resumer thread while the
                //   threadLock is held by the main thread.
                // - resumption of the waiter thread
                // - a threadLock enter in the freshly resumed waiter thread
                //
                barrierResumerNotify();
                try {
                    // Delay for 1-second while holding the threadLock to force the
                    // resumer thread to block on entering the threadLock.
                    Thread.sleep(1000);
                } catch (Exception e) {}
            }

            shutDown(waiter ,resumer);
            checkTestState(TS_WAITER_DONE);
        }

        System.out.println("Executed " + count + " loops in " + timeMax +
                " seconds.");

        return 0;
    }
}
