/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4413752
 * @summary Test SuspendThread with ObjectMonitor wait.
 * @requires vm.jvmti
 * @library /test/lib
 * @compile SuspendWithObjectMonitorWait.java
 * @run main/othervm/native -agentlib:SuspendWithObjectMonitorWait SuspendWithObjectMonitorWait
 */

import java.io.PrintStream;

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

public class SuspendWithObjectMonitorWait {
    private static final String AGENT_LIB = "SuspendWithObjectMonitorWait";
    private static final int exit_delta   = 95;

    private static final int DEF_TIME_MAX = 60;    // default max # secs to test
    private static final int JOIN_MAX     = 30;    // max # secs to wait for join

    public static final int THR_MAIN      = 0;     // ID for main thread
    public static final int THR_RESUMER   = 1;     // ID for resumer thread
    public static final int THR_WAITER    = 2;     // ID for waiter thread

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

    public volatile static int testState;
    public static long count = 0;

    private static void log(String msg) { System.out.println(msg); }

    native static int GetResult();
    native static void SetPrintDebug();
    native static void SuspendThread(int id, SuspendWithObjectMonitorWaitWorker thr);
    native static void Wait4ContendedEnter(int id, SuspendWithObjectMonitorWaitWorker thr);

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(AGENT_LIB);
            log("Loaded library: " + AGENT_LIB);
        } catch (UnsatisfiedLinkError ule) {
            log("Failed to load library: " + AGENT_LIB);
            log("java.library.path: " + System.getProperty("java.library.path"));
            throw ule;
        }

        System.exit(run(args, System.out) + exit_delta);
    }

    public static void usage() {
        System.err.println("Usage: " + AGENT_LIB + " [-p][time_max]");
        System.err.println("where:");
        System.err.println("    -p       ::= print debug info");
        System.err.println("    time_max ::= max looping time in seconds");
        System.err.println("                 (default is " + DEF_TIME_MAX +
                           " seconds)");
        System.exit(1);
    }

    public static int run(String[] args, PrintStream out) {
        return (new SuspendWithObjectMonitorWait()).doWork(args, out);
    }

    public static void checkTestState(int exp) {
        if (testState != exp) {
            System.err.println("Failure at " + count + " loops.");
            throw new InternalError("Unexpected test state value: "
                + "expected=" + exp + " actual=" + testState);
        }
    }

    public int doWork(String[] args, PrintStream out) {
        int time_max = 0;
        if (args.length == 0) {
            time_max = DEF_TIME_MAX;
        } else {
            int arg_index = 0;
            int args_left = args.length;
            if (args[0].equals("-p")) {
                SetPrintDebug();
                arg_index = 1;
                args_left--;
            }
            if (args_left == 0) {
                time_max = DEF_TIME_MAX;
            } else if (args_left == 1) {
                try {
                    time_max = Integer.parseUnsignedInt(args[arg_index]);
                } catch (NumberFormatException nfe) {
                    System.err.println("'" + args[arg_index] +
                                       "': invalid time_max value.");
                    usage();
                }
            } else {
                usage();
            }
        }

        SuspendWithObjectMonitorWaitWorker waiter;    // waiter thread
        SuspendWithObjectMonitorWaitWorker resumer;    // resumer thread

        System.out.println("About to execute for " + time_max + " seconds.");

        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (time_max * 1000)) {
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
                Wait4ContendedEnter(THR_MAIN, waiter);

                checkTestState(TS_READY_TO_NOTIFY);
                testState = TS_CALL_SUSPEND;
                SuspendThread(THR_MAIN, waiter);
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

        System.out.println("Executed " + count + " loops in " + time_max +
                           " seconds.");

        return GetResult();
    }
}

class SuspendWithObjectMonitorWaitWorker extends Thread {
    private SuspendWithObjectMonitorWaitWorker target;  // target for resume operation

    public SuspendWithObjectMonitorWaitWorker(String name) {
        super(name);
    }

    public SuspendWithObjectMonitorWaitWorker(String name, SuspendWithObjectMonitorWaitWorker target) {
        super(name);
        this.target = target;
    }

    native static int GetPrintDebug();
    native static void ResumeThread(int id, SuspendWithObjectMonitorWaitWorker thr);

    public void run() {
        if (GetPrintDebug() != 0) {
            System.err.println(getName() + " thread running");
        }

        //
        // Launch the waiter thread:
        // - grab the threadLock
        // - threadLock.wait()
        // - releases threadLock
        //
        if (getName().equals("waiter")) {
            // grab threadLock before we tell main we are running
            if (GetPrintDebug() != 0) {
                System.err.println(getName() + ": before enter threadLock");
            }
            synchronized(SuspendWithObjectMonitorWait.threadLock) {
                if (GetPrintDebug() != 0) {
                    System.err.println(getName() + ": enter threadLock");
                }

                SuspendWithObjectMonitorWait.checkTestState(SuspendWithObjectMonitorWait.TS_INIT);

                synchronized(SuspendWithObjectMonitorWait.barrierLaunch) {
                    // tell main we are running
                    SuspendWithObjectMonitorWait.testState = SuspendWithObjectMonitorWait.TS_WAITER_RUNNING;
                    SuspendWithObjectMonitorWait.barrierLaunch.notify();
                }

                if (GetPrintDebug() != 0) {
                    System.err.println(getName() + " before wait");
                }

                // TS_READY_TO_NOTIFY is set after the main thread has
                // entered threadLock so a spurious wakeup can't get the
                // waiter thread out of this threadLock.wait(0) call:
                while (SuspendWithObjectMonitorWait.testState <= SuspendWithObjectMonitorWait.TS_READY_TO_NOTIFY) {
                    try {
                        SuspendWithObjectMonitorWait.threadLock.wait(0);
                    } catch (InterruptedException ex) {
                    }
                }

                if (GetPrintDebug() != 0) {
                    System.err.println(getName() + ": after wait");
                }

                SuspendWithObjectMonitorWait.checkTestState(SuspendWithObjectMonitorWait.TS_CALL_RESUME);
                SuspendWithObjectMonitorWait.testState = SuspendWithObjectMonitorWait.TS_WAITER_DONE;

                if (GetPrintDebug() != 0) {
                    System.err.println(getName() + ": exit threadLock");
                }
            }
        }
        //
        // Launch the resumer thread:
        // - tries to grab the threadLock (should not block!)
        // - grabs threadLock
        // - resumes the waiter thread
        // - releases threadLock
        //
        else if (getName().equals("resumer")) {
            synchronized(SuspendWithObjectMonitorWait.barrierResumer) {
                synchronized(SuspendWithObjectMonitorWait.barrierLaunch) {
                    // tell main we are running
                    SuspendWithObjectMonitorWait.testState = SuspendWithObjectMonitorWait.TS_RESUMER_RUNNING;
                    SuspendWithObjectMonitorWait.barrierLaunch.notify();
                }
                if (GetPrintDebug() != 0) {
                    System.err.println(getName() + " thread waiting");
                }
                while (SuspendWithObjectMonitorWait.testState != SuspendWithObjectMonitorWait.TS_READY_TO_RESUME) {
                    try {
                        // wait for main to tell us when to continue
                        SuspendWithObjectMonitorWait.barrierResumer.wait(0);
                    } catch (InterruptedException ex) {
                    }
                }
            }

            if (GetPrintDebug() != 0) {
                System.err.println(getName() + ": before enter threadLock");
            }
            synchronized(SuspendWithObjectMonitorWait.threadLock) {
                if (GetPrintDebug() != 0) {
                    System.err.println(getName() + ": enter threadLock");
                }

                SuspendWithObjectMonitorWait.checkTestState(SuspendWithObjectMonitorWait.TS_READY_TO_RESUME);
                SuspendWithObjectMonitorWait.testState = SuspendWithObjectMonitorWait.TS_CALL_RESUME;

                // resume the waiter thread so waiter.join() can work
                ResumeThread(SuspendWithObjectMonitorWait.THR_RESUMER, target);

                if (GetPrintDebug() != 0) {
                    System.err.println(getName() + ": exit threadLock");
                }
            }
        }
    }
}
