/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6960970
 * @summary Avoid interp-only mode during stepping over method calls.
 *
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g StepOverStressTest.java
 * @run driver/timeout=600 StepOverStressTest
 */

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

class TestTarg {
    public final static int BKPT_LINE1 = 54;
    public final static int BKPT_LINE2 = 56;

    static void log(String msg) { System.out.println(msg); }

    static int busyWork() {
        // When we enter this method we hit a breakpoint on the BKPT_LINE1 line below.
        // The debugger enables single step events and then resume. We time the compute
        // intensive task in busyWork1 method. Single step events are disabled when
        // second breakpoint on the BKPT_LINE2 line is hit. This will check if the
        // thread continues execution in interpOnly mode in the busyWork1 method.
        int x = 1; //  <-- BKPT_LINE1
        intensiveWork();
        return x;  //  <-- BKPT_LINE2
    }

    // Do something compute intensive and time it.
    static void intensiveWork() {
        long start = System.currentTimeMillis();
        for (int iter = 0; iter < 10; iter++) {
            LinkedList<Integer> list = new LinkedList<Integer>();
            for (int i = 0; i < 100 * 1024; i++) {
                list.addFirst(Integer.valueOf(i));
            }
            Collections.sort(list);
        }
        long end = System.currentTimeMillis();
        log("Total time #1: " + (end - start));
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        busyWork();
        long end = System.currentTimeMillis();
        log("Total time #2: " + (end - start));
    }
}

    /********** test program **********/

public class StepOverStressTest extends TestScaffold {
    ClassType targetClass;
    ThreadReference mainThread;

    static void log(String msg) { System.out.println(msg); }

    StepOverStressTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new StepOverStressTest(args).startTests();
    }

    /********** event handlers **********/

    EventRequestManager erm;
    BreakpointRequest bkptReq1;
    BreakpointRequest bkptReq2;
    StepRequest stepRequest;
    static boolean wasBreakpointHit = false;

    public void breakpointReached(BreakpointEvent event) {
        if (!wasBreakpointHit) {
            log("Got BreakpointEvent #1: " + event);
            stepRequest = erm.createStepRequest(mainThread,
                                                StepRequest.STEP_LINE,
                                                StepRequest.STEP_OVER);
            wasBreakpointHit = true;
            stepRequest.enable();
            bkptReq1.disable();
        } else {
            log("Got BreakpointEvent #2: " + event);
            stepRequest.disable();
            bkptReq2.disable();
        }
    }

    public void stepCompleted(StepEvent event) {
        log("Got StepEvent: " + event);
    }

    public void eventSetComplete(EventSet set) {
        set.resume();
    }

    public void vmDisconnected(VMDisconnectEvent event) {
        log("Got VMDisconnectEvent");
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        // Get to the top of main() to determine targetClass and mainThread.
        BreakpointEvent bpe = startToMain("TestTarg");
        targetClass = (ClassType)bpe.location().declaringType();
        mainThread = bpe.thread();
        erm = vm().eventRequestManager();

        Location loc1 = findLocation(
                            targetClass,
                            TestTarg.BKPT_LINE1);

        Location loc2 = findLocation(
                            targetClass,
                            TestTarg.BKPT_LINE2);

        bkptReq1 = erm.createBreakpointRequest(loc1);
        bkptReq2 = erm.createBreakpointRequest(loc2);
        bkptReq1.enable();
        bkptReq2.enable();

        try {
            addListener(this);
        } catch (Exception ex){
            ex.printStackTrace();
            failure("failure: Could not add listener");
            throw new Exception("StepOverStressTest: failed");
        }

        vm().resume();
        while (!vmDisconnected) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ee) {
            }
        }

        println("done with loop");
        removeListener(this);

         // Deal with results of test if anything has called failure("foo")
         // testFailed will be true.
        if (!testFailed) {
            println("StepOverStressTest: passed");
        } else {
            throw new Exception("StepOverStressTest: failed");
        }
    }
}

