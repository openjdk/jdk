/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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

//  THIS TEST IS LINE NUMBER SENSITIVE

/**
 * @test
 * @bug 1234567
 * @summary XXX
 *
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g SingleStepPerformanceTest.java
 * @run driver/timeout=10 SingleStepPerformanceTest
 */

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

class TestTarg {
    public final static int BKPT_LINE = 55;

    // Do something compute intensive and time it.
    public static void busyWork() {
        // First time we enter this method we'll hit a breakpoint on the first line below.
        // The debugger will do a single step and then resume. We time the compute
        // intensive task. Eventually this method is called a 2nd time and we hit
        // the breakpoint again. This time we do not do a single step and do
        // the compute intensive task again. This will check if single stepping
        // once keeps the thread in interpOnly mode until this method exits.
        int x = 1; //  <-- BKPT_LINE
        long start = System.currentTimeMillis();
        LinkedList<Integer> list = new LinkedList<Integer>();
        for (int i = 0; i < 10*1024; i++) {
            list.addFirst(Integer.valueOf(i));
        }
        Collections.sort(list);
        long end = System.currentTimeMillis();
        System.out.println("Total time: " + (end - start));        
    }

    public static void main(String[] args) {
        busyWork(); 
        busyWork();
    }
}

/********** test program **********/

public class SingleStepPerformanceTest extends TestScaffold {
    ClassType targetClass;
    ThreadReference mainThread;

    SingleStepPerformanceTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new SingleStepPerformanceTest(args).startTests();
    }

    /********** event handlers **********/

    EventRequestManager erm;
    BreakpointRequest bkptRequest;
    StepRequest stepRequest;
    static boolean firstBreakpointHit = false;

    // When we get a bkpt we want to disable the request,
    // resume the debuggee, and then re-enable the request
    public void breakpointReached(BreakpointEvent event) {
        System.out.println("Got BreakpointEvent: " + event);
        if (!firstBreakpointHit) {
            stepRequest = erm.createStepRequest(mainThread,
                                                StepRequest.STEP_LINE,
                                                StepRequest.STEP_OVER);
            firstBreakpointHit = true;
            stepRequest.enable();
        }
    }

    public void stepCompleted(StepEvent event) {
        System.out.println("Got StepEvent: " + event);
        EventRequest req = event.request();
        req.disable();
    }

    public void eventSetComplete(EventSet set) {
        set.resume();
    }

    public void vmDisconnected(VMDisconnectEvent event) {
        println("Got VMDisconnectEvent");
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        /*
         * Get to the top of main()
         * to determine targetClass and mainThread
         */
        BreakpointEvent bpe = startToMain("TestTarg");
        targetClass = (ClassType)bpe.location().declaringType();
        mainThread = bpe.thread();
        erm = vm().eventRequestManager();
        
        Location loc1 = findLocation(targetClass, TestTarg.BKPT_LINE);

        bkptRequest = erm.createBreakpointRequest(loc1);
        bkptRequest.enable();

        try {
            addListener(this);
        } catch (Exception ex){
            ex.printStackTrace();
            failure("failure: Could not add listener");
            throw new Exception("SingleStepPerformanceTest: failed");
        }

        vm().resume();
        while (!vmDisconnected) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ee) {
            }
        }

        println("done with loop");
        removeListener(this);

        /*
         * deal with results of test
         * if anything has called failure("foo") testFailed will be true
         */
        if (!testFailed) {
            println("SingleStepPerformanceTest: passed");
        } else {
            throw new Exception("SingleStepPerformanceTest: failed");
        }
    }
}
