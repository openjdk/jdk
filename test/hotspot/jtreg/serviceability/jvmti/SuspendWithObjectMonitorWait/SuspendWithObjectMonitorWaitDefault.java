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
 * @compile SuspendWithObjectMonitorWaitDefault.java
 * @run main/othervm/native -agentlib:SuspendWithObjectMonitorWait SuspendWithObjectMonitorWaitDefault 1
 */

import java.io.PrintStream;

//
// SuspendWithObjectMonitorWaitDefault algorithm:
//
// main               waiter              resumer
// =================  ==================  ===================
// launch waiter
// <launch returns>   waiter running
// launch resumer     enter threadLock
// <launch returns>   threadLock.wait()   resumer running
// enter threadLock   :                   wait for notify
// threadLock.notify  wait finishes       :
// :                  reenter blocks      :
// suspend waiter     <suspended>         :
// exit threadLock    :                   :
// <ready to test>    :                   :
// :                  :                   :
// notify resumer     :                   wait finishes
// join resumer       :                   enter threadLock
// :                  <resumed>           resume waiter
// :                  :                   exit threadLock
// :                  reenter threadLock  :
// <join returns>     :                   resumer exits
// join waiter        :
// <join returns>     waiter exits
//

public class SuspendWithObjectMonitorWaitDefault extends SuspendWithObjectMonitorWaitBase {

    @Override
    public int run(int timeMax, PrintStream out) {
        return doWork1(timeMax, out);
    }

    // Default scenario, the resumer thread is always able to grab the threadLock once notified by the main thread.
    public int doWork1(int timeMax, PrintStream out) {
        SuspendWithObjectMonitorWaitWorker waiter;    // waiter thread
        SuspendWithObjectMonitorWaitWorker resumer;   // resumer thread

        System.out.println("Test 1: About to execute for " + timeMax + " seconds.");

        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (timeMax * 1000)) {
            count++;
            testState = TS_INIT;  // starting the test loop

            // launch the waiter thread
            waiter = launchWaiter(0);

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
            }

            //
            // At this point, all of the child threads are running
            // and we can get to meat of the test:
            //
            // - suspended threadLock waiter (trying to reenter)
            // - a threadLock enter in the resumer thread
            // - resumption of the waiter thread
            // - a threadLock enter in the freshly resumed waiter thread
            //
            barrierResumerNotify();

            shutDown(waiter ,resumer);
            checkTestState(TS_WAITER_DONE);
        }

        System.out.println("Executed " + count + " loops in " + timeMax +
                           " seconds.");

        return 0;
    }
}
