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
 * @run compile -g SingleStepPerformanceTest1.java
 * @run driver/timeout=6000 SingleStepPerformanceTest1
 */

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

class TestTarg {
    public final static int BKPT_LINE = 57;

    public static void main(String[] args) {
        // We call busyWork() 3 times:
        //   1. The first call is to get a baseline time with none of the overhead of
        //      single stepping.
        //   2. Before the 2nd call we will hit a breakpoint and then single step over
        //      the call to busyWork(). This is to get the expected much slower time
        //       since single stepping will be enabled.
        //   3. On the 3rd time there is no single stepping, and the time should be
        //      close to the baseline time. This is testing to make sure that the
        //      single step that was done in the method is not keeping the thread in
        //      interp_only mode until this method returns..
        long baselineTime = busyWork();
        long stepOverTime = busyWork(); // BKPT_LINE: We do a STEP_OVER here
        long afterStepTime = busyWork();
    }

    // Do something compute intensive and time it.
    public static long busyWork() {
        long start = System.currentTimeMillis();
        LinkedList<Integer> list = new LinkedList<Integer>();
        for (int i = 0; i < 100*1024; i++) {
            list.addFirst(Integer.valueOf(i));
        }
        Collections.sort(list);
        long end = System.currentTimeMillis();
        long totalTime = end - start;
        System.out.println("Total time: " + totalTime);
        return totalTime;
    }
}

/********** test program **********/

public class SingleStepPerformanceTest1 extends TestScaffold {
    ClassType targetClass;
    ThreadReference mainThread;

    SingleStepPerformanceTest1 (String args[]) {
        super(args);
    }

    public static void main(String[] args)   throws Exception {
        new SingleStepPerformanceTest1(args).startTests();
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
        EventRequest req = event.request();
        req.disable();
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
            throw new Exception("SingleStepPerformanceTest1: failed");
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
            println("SingleStepPerformanceTest1: passed");
        } else {
            throw new Exception("SingleStepPerformanceTest1: failed");
        }
    }
}
