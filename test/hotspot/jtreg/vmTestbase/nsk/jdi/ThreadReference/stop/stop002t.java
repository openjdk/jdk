/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.ThreadReference.stop;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

//    THIS TEST IS LINE NUMBER SENSITIVE

/**
 * This is a debuggee class.
 */
public class stop002t {
    private Log log;
    private IOPipe pipe;
    volatile boolean stopLooping1 = false;
    volatile boolean stopLooping2 = false;
    volatile static int testNumReady = 0;
    static final boolean vthreadMode = "Virtual".equals(System.getProperty("test.thread.factory"));

    public static void main(String args[]) {
        System.exit(run(args) + Consts.JCK_STATUS_BASE);
    }

    public static int run(String args[]) {
        return new stop002t().runIt(args);
    }

    private int runIt(String args[]) {
        ArgumentHandler argHandler = new ArgumentHandler(args);

        log = argHandler.createDebugeeLog();
        pipe = argHandler.createDebugeeIOPipe();

        Thread.currentThread().setName(stop002.DEBUGGEE_THRNAME);

        // non-throwable object which will be used by debugger
        // as wrong parameter of JDI method ThreadReference.stop()
        stop002t stop002tNonThrowable = this;

        // throwable object which will be used by debugger
        // as valid parameter of JDI method ThreadReference.stop()
        Throwable stop002tThrowable = new MyThrowable("Async exception");

        // Now the debuggee is ready for testing
        pipe.println(stop002.COMMAND_READY);
        String cmd = pipe.readln();
        if (cmd.equals(stop002.COMMAND_QUIT)) {
            log.complain("Debuggee: premature debuggee exit due to the command "
                    + cmd);
            return Consts.TEST_FAILED;
        }

        /*
         * TEST #1: Tests that stop() properly throws InvalidTypeException if
         * the specified throwable is not an instance of java.lang.Throwable
         * in the debuggee. It does not involve the debuggee at all, so there
         * is no code here for it.
         */

        /*
         * TEST #2: async exception while suspended at a breakpoint.
         */
        int stopMeHere = 0;
        try {
            stopMeHere = 1; // stop002.DEBUGGEE_STOPATLINE
            log.complain("TEST #2: Failed to throw expected exception");
            return Consts.TEST_FAILED;
        } catch (Throwable t) {
            // Call Thread.interrupted(). Workaround for JDK-8306324
            log.display("TEST #2: interrupted = " + Thread.interrupted());
            if (t instanceof MyThrowable) {
                log.display("TEST #2: Caught expected exception while at breakpoint: " + t);
            } else {
                log.complain("TEST #2: Unexpected exception caught: " + t);
                t.printStackTrace();
                return Consts.TEST_FAILED;
            }
        }
        log.display("TEST #2: all done");

        /*
         * TEST #3: async exception while not suspended in a loop.
         */
        log.display("TEST #3: going to loop ...");
        try {
            while (!stopLooping1) { // looping
                testNumReady = 3; // signal debugger side of test that we are ready
                stopMeHere++; stopMeHere--;
            }
            if (vthreadMode) {
                log.display("TEST #3: Correctly did not throw async exception for virtual thread");
            } else {
                log.complain("TEST #3: Failed to throw expected exception");
                return Consts.TEST_FAILED;
            }
        } catch (Throwable t) {
            // Call Thread.interrupted(). Workaround for JDK-8306324
            log.display("TEST #3: interrupted = " + Thread.interrupted());
            // We don't expect the exception to be thrown when in vthread mode.
            if (!vthreadMode && t instanceof MyThrowable) {
                log.display("TEST #3: Caught expected exception while in loop: " + t);
            } else {
                log.complain("TEST #3: Unexpected exception caught: " + t);
                t.printStackTrace();
                return Consts.TEST_FAILED;
            }
        }
        log.display("TEST #3: all done");

        /*
         * TEST #4: async exception while suspended in a loop.
         */
        log.display("TEST #4: going to loop ...");
        try {
            while (!stopLooping2) { // looping
                testNumReady = 4; // signal debugger side of test that we are ready
                stopMeHere++; stopMeHere--;
            }
            log.complain("TEST #4: Failed to throw expected exception");
            return Consts.TEST_FAILED;
        } catch (Throwable t) {
            // Call Thread.interrupted(). Workaround for JDK-8306324
            log.display("TEST #4: interrupted = " + Thread.interrupted());
            if (t instanceof MyThrowable) {
                log.display("TEST #4: Caught expected exception while in loop: " + t);
            } else {
                log.complain("TEST #4: Unexpected exception caught: " + t);
                t.printStackTrace();
                return Consts.TEST_FAILED;
            }
        }
        log.display("TEST #4: all done");

        /*
         * TEST #5: async exception while suspended doing Thread.sleep().
         */
        try {
            try {
                // Signal debugger side of test that we are "almost" ready. The
                // debugger will still need to check that we are in the sleep state.
                testNumReady = 5;
                log.display("TEST #5: going to sleep ...");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                log.complain("TEST #5: Unexpected InterruptedException");
                e.printStackTrace();
                return Consts.TEST_FAILED;
            }
            if (vthreadMode) {
                log.display("TEST #5: Correctly did not throw exception while in sleep");
            } else {
                log.complain("TEST #5: Failed to throw expected exception");
                return Consts.TEST_FAILED;
            }
        } catch (Throwable t) {
            // Call Thread.interrupted(). Workaround for JDK-8306324
            log.display("TEST #5: interrupted = " + Thread.interrupted());
            // We don't expect the exception to be thrown when in vthread mode.
            if (!vthreadMode && t instanceof MyThrowable) {
                log.display("TEST #5: Caught expected exception while in loop: " + t);
            } else {
                log.complain("TEST #5: Unexpected exception caught: " + t);
                t.printStackTrace();
                return Consts.TEST_FAILED;
            }
        }
        log.display("TEST #5: all done");

        /*
         * Test shutdown.
         */
        cmd = pipe.readln();
        if (!cmd.equals(stop002.COMMAND_QUIT)) {
            log.complain("TEST BUG: unknown debugger command: "
                + cmd);
            return Consts.TEST_FAILED;
        }
        return Consts.TEST_PASSED;
    }
}

class MyThrowable extends Throwable
{
    MyThrowable(String message) {
        super(message);
    }
}
