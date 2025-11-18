/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @compile SuspendWithObjectMonitorWait2.java
 * @run main/othervm/native -agentlib:SuspendWithObjectMonitorWait SuspendWithObjectMonitorWait2
 */

import java.io.PrintStream;

//
// doWork2 algorithm:
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
// <ready to test>    :                   :
// :                  :                   :
// notify resumer     :                   wait finishes
// delay 1-second     :                   :
// exit threadLock    :                   :
// join resumer       :                   enter threadLock
// :                  <resumed>           resume waiter
// :                  :                   exit threadLock
// :                  reenter threadLock  :
// <join returns>     :                   resumer exits
// join waiter        :
// <join returns>     waiter exits
//
// Note: The sleep(1-second) in main along with the delayed exit
//       of threadLock in main forces the resumer thread to reach
//       "enter threadLock" and block. This difference from doWork1
//       forces the resumer thread to be contending for threadLock
//       while the waiter thread is in threadLock.wait() increasing
//       stress on the monitor sub-system.
//

public class SuspendWithObjectMonitorWait2 extends SuspendWithObjectMonitorWaitBase {

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            System.err.println("Invalid number of arguments, there are too many arguments.");
            usage();
        }

        try {
            System.loadLibrary(AGENT_LIB);
            log("Loaded library: " + AGENT_LIB);
        } catch (UnsatisfiedLinkError ule) {
            log("Failed to load library: " + AGENT_LIB);
            log("java.library.path: " + System.getProperty("java.library.path"));
            throw ule;
        }

        int timeMax = 0;
        for (int argIndex = 0; argIndex < args.length; argIndex++) {
            if ("-p".equals(args[argIndex])) {
                // Handle optional -p arg regardless of position.
                printDebug = true;
                continue;
            }

            if (argIndex < args.length) {
                // timeMax is an optional arg.
                try {
                    timeMax = Integer.parseUnsignedInt(args[argIndex]);
                } catch (NumberFormatException nfe) {
                    System.err.println("'" + args[argIndex] +
                                       "': invalid time_max value.");
                    usage();
                }
            } else {
                timeMax = DEF_TIME_MAX;
            }
        }

        System.exit(run(timeMax, System.out) + exit_delta);
    }

    public static int run(int timeMax, PrintStream out) {
        return (new SuspendWithObjectMonitorWait2()).doWork2(timeMax, out);
    }

    // Notify the resumer while holding the threadLock.
    public int doWork2(int timeMax, PrintStream out) {
        SuspendWithObjectMonitorWaitWorker waiter;    // waiter thread
        SuspendWithObjectMonitorWaitWorker resumer;    // resumer thread

        System.out.println("About to execute for " + timeMax + " seconds.");

        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (timeMax * 1000)) {
            count++;
            testState = TS_INIT;  // starting the test loop

            // launch the waiter thread
            synchronized (barrierLaunch) {
                waiter = new SuspendWithObjectMonitorWaitWorker("waiter");
                waiter.start();

                while (testState != TS_WAITER_RUNNING) {
                    try {
                        barrierLaunch.wait(0);  // wait until it is running
                    } catch (InterruptedException ex) {
                    }
                }
            }

            // launch the resumer thread
            synchronized (barrierLaunch) {
                resumer = new SuspendWithObjectMonitorWaitWorker("resumer", waiter);
                resumer.start();

                while (testState != TS_RESUMER_RUNNING) {
                    try {
                        barrierLaunch.wait(0);  // wait until it is running
                    } catch (InterruptedException ex) {
                    }
                }
            }

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

                synchronized (barrierResumer) {
                    checkTestState(TS_CALL_SUSPEND);

                    // tell resumer thread to resume waiter thread
                    testState = TS_READY_TO_RESUME;
                    barrierResumer.notify();

                    // Can't call checkTestState() here because the
                    // resumer thread may have already resumed the
                    // waiter thread.
                }
                try {
                    // Delay for 1-second while holding the threadLock to force the
                    // resumer thread to block on entering the threadLock.
                    Thread.sleep(1000);
                } catch(Exception e) {}
            }

            try {
                resumer.join(JOIN_MAX * 1000);
                if (resumer.isAlive()) {
                    System.err.println("Failure at " + count + " loops.");
                    throw new InternalError("resumer thread is stuck");
                }
                waiter.join(JOIN_MAX * 1000);
                if (waiter.isAlive()) {
                    System.err.println("Failure at " + count + " loops.");
                    throw new InternalError("waiter thread is stuck");
                }
            } catch (InterruptedException ex) {
            }

            checkTestState(TS_WAITER_DONE);
        }

        System.out.println("Executed " + count + " loops in " + timeMax +
                " seconds.");

        return 0;
    }
}




