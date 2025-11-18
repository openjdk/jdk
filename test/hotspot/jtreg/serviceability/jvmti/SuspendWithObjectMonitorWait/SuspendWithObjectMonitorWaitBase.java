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
}
