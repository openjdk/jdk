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

import java.lang.reflect.Method;
import jdk.test.whitebox.WhiteBox;

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

    // line nunber for breakpoint
    public static final int BREAKPOINT_LINE_NUMBER = 168;

    // line number of the popframes003 breakpoint in stop()
    public static final int STOP_LINE_NUMBER = 182;

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

        if (TestedThreadClass.notCompiled) {
            log.display("Debugee FAILED");
            return popframes001.FAILED;
        }

        // popping only stop() would reach the breakpoint twice as well
        if (TestedThreadClass.COMPILED_CALLER && TestedThreadClass.invokations != 2) {
            log.complain("Tested method invoked " + TestedThreadClass.invokations + " times, expected 2");
            log.display("Debugee FAILED");
            return popframes001.FAILED;
        }

        log.display("Debugee PASSED");
        return popframes001.PASSED;
    }

    // tested thread class
    public static class TestedThreadClass extends ThreadWrapper {

        // number of invokations of tested method
        public static volatile int invokations = 0;

        // popframes002 and popframes003 run this debuggee with these set
        private static final boolean UNMOUNT = Boolean.getBoolean("popframes001.unmount");
        private static final boolean COMPILED_CALLER = Boolean.getBoolean("popframes001.compiledCaller");

        // set when run() or testedMethod() turns out not to be compiled
        public static volatile boolean notCompiled = false;

        public TestedThreadClass(String name) {
            super(name);
        }

        // invoke tested method
        public void run() {
            log.display("Tested thread: started");

            // invoke tested method
            int foo = 100;
            foo = testedMethod(foo);

            log.display("Tested thread: finished");
        }

        // tested method to pop frames
        public int testedMethod(int arg) {
            invokations++;
            log.display("Tested method invoked " + invokations + " time");
            int boo = 0;

            log.display("Breakpoint line reached");
            if (COMPILED_CALLER && invokations == 1) {
                // the frozen caller has to be a compiled frame in that test, checked once, the pop deoptimizes both
                try {
                    WhiteBox wb = WhiteBox.getWhiteBox();
                    Method run = TestedThreadClass.class.getDeclaredMethod("run");
                    Method tested = TestedThreadClass.class.getDeclaredMethod("testedMethod", int.class);
                    if (wb.getMethodCompilationLevel(run) == 0 || wb.getMethodCompilationLevel(tested) == 0) {
                        log.complain("run() or testedMethod() is not compiled");
                        notCompiled = true;
                    }
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
            if (UNMOUNT) {
                // joining a virtual thread unmounts a virtual tested thread, so the frame below is frozen when popped
                Thread tested = Thread.currentThread();
                Thread parker = Thread.ofVirtual().start(() -> {
                    // ends once the tested thread is parked in the join below
                    while (tested.getState() != Thread.State.WAITING) {
                        Thread.yield();
                    }
                });
                try {
                    parker.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            // next line is for breakpoint
            boo = arg * 2; // BREAKPOINT_LINE_NUMBER
            log.display("Breakpoint line passed");

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
