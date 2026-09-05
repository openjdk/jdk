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

import java.io.PrintStream;

public class SuspendWithObjectMonitorWaitBase {
    protected static final String AGENT_LIB = "SuspendWithObjectMonitorWait";
    protected static final int exit_delta   = 95;

    protected static final int DEF_TIME_MAX = 60;    // default max # secs to test
    protected static final int JOIN_MAX     = 30;    // max # secs to wait for join

    public static final int TS_INIT            = 1;  // initial testState
    public static final int TS_WAITER_RUNNING  = 2;  // waiter is running
    public static final int TS_RESUMER_RUNNING = 3;  // resumer is running
    public static final int TS_READY_TO_NOTIFY = 4;  // ready to notify threadLock
    public static final int TS_CALL_SUSPEND    = 5;  // call suspend on contender
    public static final int TS_READY_TO_RESUME = 6;  // ready to resume waiter
    public static final int TS_CALL_RESUME     = 7;  // call resume on waiter
    public static final int TS_WAITER_DONE     = 8;  // waiter has run; done

    public static Object barrierLaunch = new Object();   // controls thread launch
    public static Object barrierResumer = new Object();  // controls resumer
    public static Object threadLock = new Object();      // testing object

    public static long count = 0;
    public static boolean printDebug = false;
    public volatile static int testState;

    protected static void log(String msg) { System.out.println(msg); }

    native static int suspendThread(SuspendWithObjectMonitorWaitWorker thr);
    native static int wait4ContendedEnter(SuspendWithObjectMonitorWaitWorker thr);

    public static void logDebug(String mesg) {
        if (printDebug) {
            System.err.println(Thread.currentThread().getName() + ": " + mesg);
        }
    }

    public static void usage() {
        System.err.println("Usage: " + AGENT_LIB + " test_case [-p] [time_max]");
        System.err.println("where:");
        System.err.println("    test_case ::= 1 | 2 | 3");
        System.err.println("    -p        ::= print debug info");
        System.err.println("    time_max  ::= max looping time in seconds");
        System.err.println("                  (default is " + DEF_TIME_MAX +
                " seconds)");
        System.exit(1);
    }

    public static void checkTestState(int exp) {
        if (testState != exp) {
            System.err.println("Failure at " + count + " loops.");
            throw new InternalError("Unexpected test state value: "
                    + "expected=" + exp + " actual=" + testState);
        }
    }

    public SuspendWithObjectMonitorWaitWorker launchWaiter(long waitTimeout) {
        SuspendWithObjectMonitorWaitWorker waiter;
        // launch the waiter thread
        synchronized (barrierLaunch) {
            waiter = new SuspendWithObjectMonitorWaitWorker("waiter", waitTimeout);
            waiter.start();

            while (testState != TS_WAITER_RUNNING) {
                try {
                    barrierLaunch.wait(0);  // wait until it is running
                } catch (InterruptedException ex) {
                }
            }
        }
        return waiter;
    }

    public SuspendWithObjectMonitorWaitWorker launchResumer(SuspendWithObjectMonitorWaitWorker waiter) {
        SuspendWithObjectMonitorWaitWorker resumer;
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
        return resumer;
    }

    public void barrierResumerNotify() {
        synchronized (barrierResumer) {
            checkTestState(TS_CALL_SUSPEND);

            // tell resumer thread to resume waiter thread
            testState = TS_READY_TO_RESUME;
            barrierResumer.notify();

            // Can't call checkTestState() here because the
            // resumer thread may have already resumed the
            // waiter thread.
        }
    }

    public void shutDown(SuspendWithObjectMonitorWaitWorker resumer, SuspendWithObjectMonitorWaitWorker waiter) {
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
    }

    public int run(int timeMax, PrintStream out) {
        return 0;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Invalid number of arguments, there should be at least a test_case given.");
            usage();
        }

        if (args.length > 3) {
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

        int testCase = 0;
        int timeMax = 0;
        for (int argIndex = 0; argIndex < args.length; argIndex++) {
            if (args[argIndex].equals("-p")) {
                // Handle optional -p arg regardless of position.
                printDebug = true;
                continue;
            }

            if (testCase == 0) {
                try {
                    // testCase must be the first non-optional arg.
                    testCase = Integer.parseUnsignedInt(args[argIndex]);
                    log("testCase = " + testCase);
                } catch (NumberFormatException nfe) {
                    System.err.println("'" + args[argIndex] +
                            "': invalid test_case value.");
                    usage();
                }
                if (testCase < 1 || testCase > 3) {
                    System.err.println("Invalid test_case value: '" + testCase + "'");
                    usage();
                }
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

        if (timeMax == 0) {
            timeMax = DEF_TIME_MAX;
        }
        log("timeMax = " + timeMax);

        if (testCase == 0) {
            // Just -p was given.
            System.err.println("Invalid number of arguments, no test_case given.");
            usage();
        }

        SuspendWithObjectMonitorWaitBase test = null;
        switch (testCase) {
            case 1:
                test = new SuspendWithObjectMonitorWaitDefault();
                break;
            case 2:
                test = new SuspendWithObjectMonitorWaitReentryPartFirst();
                break;
            case 3:
                test = new SuspendWithObjectMonitorWaitReentryPartSecond();
                break;
            default:
                // Impossible
                break;
        }
        int result = test.run(timeMax, System.out);
        System.exit(result + exit_delta);
    }
}
