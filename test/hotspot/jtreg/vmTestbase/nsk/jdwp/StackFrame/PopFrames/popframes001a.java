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

//    THIS TEST IS LINE NUMBER SENSITIVE

package nsk.jdwp.StackFrame.PopFrames;

import jdk.test.lib.thread.ThreadWrapper;
import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdwp.*;

import java.io.*;

/**
 * This class represents debuggee part in the test.
 */
public class popframes001a {

    // name of the tested thread
    public static final String THREAD_NAME = "testedThread";

    // line number for breakpoint
    public static final int BREAKPOINT_LINE_NUMBER = 160;

    // line number of the popframes003 breakpoint in stop()
    public static final int STOP_LINE_NUMBER = 177;

    // scaffold objects
    private static volatile ArgumentHandler argumentHandler = null;
    private static volatile Log log = null;

    public static void main(String args[]) {
        popframes001a _popframes001a = new popframes001a();
        System.exit(popframes001.JCK_STATUS_BASE + _popframes001a.runIt(args, System.err));
    }

    public int runIt(String args[], PrintStream out) {
        //make log for debugee messages
        argumentHandler = new ArgumentHandler(args);
        log = new Log(out, argumentHandler);

        // create tested thread
        log.display("Creating testing thread");
        TestedThreadClass thread = new TestedThreadClass(THREAD_NAME);
        log.display("  ... thread created");

        if (thread.isVirtual()) {
            // The thread joined by the virtual thread in testedMethod. It exits once the
            // virtual thread has unmounted. Created here to avoid setup code in testedMethod
            // that could trigger deoptimization before join().
            Thread tested = thread.getThread();
            TestedThreadClass.parker = Thread.ofVirtual().start(() -> {
                while (tested.getState() != Thread.State.WAITING) {
                    Thread.yield();
                }
            });
        }

        // start tested thread
        log.display("Starting tested thread");
        thread.start();
        log.display("  ... thread started");

        // wait for tested thread finished
        try {
            log.display("Waiting for tested thread finished");
            thread.join();
            log.display("  ... thread finished");
        } catch(InterruptedException e) {
            log.complain("Interruption while waiting for tested thread finished:\n\t" + e);
            log.display("Debugee FAILED");
            return popframes001.FAILED;
        }

        // the tested method is expected to be invoked twice
        if (TestedThreadClass.invocations != 2) {
            log.complain("Tested method invoked " + TestedThreadClass.invocations + " times, expected 2");
            log.display("Debugee FAILED");
            return popframes001.FAILED;
        }

        if (TestedThreadClass.argsMismatch != null) {
            log.complain("Tested method got wrong arguments: " + TestedThreadClass.argsMismatch);
            log.display("Debugee FAILED");
            return popframes001.FAILED;
        }

        log.display("Debugee PASSED");
        return popframes001.PASSED;
    }

    // tested thread class
    public static class TestedThreadClass extends ThreadWrapper {

        // number of invocations of tested method
        public static volatile int invocations = 0;

        // arguments run() passes to testedMethod, checked on every invocation
        static final long LONG_ARG = 0x1234567890ABL;
        public static volatile String argsMismatch = null;

        // popframes003 runs this debuggee with this set
        private static final boolean COMPILED_CALLER = Boolean.getBoolean("popframes001.compiledCaller");

        // joined by the tested method when the tested thread is virtual, started in runIt
        public static Thread parker = null;

        public TestedThreadClass(String name) {
            super(name);
        }

        // invoke tested method, nothing before the call that could deoptimize this frame
        public void run() {
            int foo = 100;
            foo = testedMethod(foo, LONG_ARG, this);

            log.display("Tested thread: finished");
        }

        // tested method to pop frames
        public int testedMethod(int arg, long arg2, Object arg3) {
            invocations++;
            int boo = 0;

            if (isVirtual()) {
                // PopFrame pops this frame, testedMethod() runs twice. The first join unmounts the
                // thread, leaving the caller in the heap when PopFrame runs. Code before this join
                // must not deoptimize this frame, an interpreted frame thaws its compiled caller.
                try {
                    parker.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            log.display("Tested method invoked " + invocations + " time");

            log.display("Breakpoint line reached");
            // next line is for breakpoint
            boo = arg * 2; // BREAKPOINT_LINE_NUMBER
            log.display("Breakpoint line passed");
            if (arg != 100 || arg2 != LONG_ARG || arg3 != this) {
                argsMismatch = "arg " + arg + " arg2 " + arg2 + " arg3 " + arg3;
            }

            if (COMPILED_CALLER) {
                // popframes003 breaks in stop() and pops this frame together with it
                stop();
            }

            return boo;
        }

        // breakpoint method for popframes003
        public void stop() {
            // next line is for breakpoint
            log.display("Stop line reached"); // STOP_LINE_NUMBER
        }
    }
}
