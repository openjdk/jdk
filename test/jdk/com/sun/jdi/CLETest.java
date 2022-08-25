/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8292217
 * @summary Test co-located events (CLE) for MethodEntry, SingleStep, and Breakpoint events.
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g CLETest.java
 * @run driver CLETest
 */

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.*;

class t1 {
    public static void foo() {
    }
}
class t2 {
    public static void foo() {
    }
}

/*
 * The debuggee has a large number of breakpoints pre-setup to help control the test.
 * They are each hit just once, and in the order of their number. No instructions in the
 * debuggee are ever executed more than once.
 *
 * NOTE: the breakpoints are sensitive to the their line number within the method.
 * If that changes, then the "breakpoints" table needs to be updated.
 */
class CLEDebugee {
    public static void main(String[] args) {
        runTests();
    }

    public static void runTests() {
        test1();
        test2();
        test3(); // BREAKPOINT_3
        test4(); // BREAKPOINT_5
        test5(); // BREAKPOINT_7
        test6(); // BREAKPOINT_9
    }

    // test1 and test2 are testing for the bug described in 8292217. For this test MethodEntry
    // events are enabled when we hit the breakpoint, and we single step OVER (test1) or
    // INTO (test2) an instruction with an unresolved contant pool entry. The Debugger will
    // verify that the generated MethodEntry events during class loading are not improperly
    // co-located as described the the CR.
    public static void test1() {
        t1.foo();  // BREAKPOINT_1
    }
    public static void test2() {
        t2.foo();  // BREAKPOINT_2
    }

    // Tests that MethodEntry, Step, and Breakpoint events that occur at the same
    // location are properly co-located in the same EventSet. MethodEntry and Step
    // are enabled when we hit BREAKPOINT_3 above. When the BreakpointEvent for
    // BREAKPOINT_4 is generated, the EventSet should also include a StepEvent
    // and a MethodEntryEvent.
    public static void test3() {
        int x = 1;   // BREAKPOINT_4
    }

    // Same as test3 but only check for co-located MethodEntry and Breakpoint events.
    // MethodEntry is enabled when we hit BREAKPOINT_5 above. StepEvent is not enabled.
    // When the BreakpointEvent for BREAKPOINT_6 is generated, the EventSet should also
    // include a MethodEntryEvent.
    public static void test4() {
        int x = 1;   // BREAKPOINT_6
    }

    // Same as test3 but only check for co-located Step and Breakpoint events.
    // StepEvents are enabled when we hit BREAKPOINT_7 above. When the BreakpointEvent
    // for BREAKPOINT_8 is generated, the EventSet should also include a StepEvent,
    public static void test5() {
        int x = 1;    // BREAKPOINT_8
    }

    // Same as test3 but only check for co-located MethodEntry and Step events.
    // MethodEntry and Step events are enabled when we hit BREAKPOINT_9 above. When
    // the StepEvent is received, the EventSet should also include the MethodEntryEvent.
    public static void test6() {
        int x = 1;
    }
}

public class CLETest extends TestScaffold {
    ClassType targetClass;
    EventRequestManager erm;
    StepRequest stepRequest;
    MethodEntryRequest entryRequest;
    MethodExitRequest exitRequest;
    int methodEntryCount = 0;
    int breakpointCount = 0;
    boolean testcaseFailed = false;
    int testcase = 0;

    CLETest(String args[]) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        CLETest cle = new CLETest(args);
        cle.startTests();
    }

    static class MethodBreakpointData {
        final String method;
        final String signature;
        final int lineNumber;
        public MethodBreakpointData(String method, String signature, int lineNumber) {
            this.method     = method;
            this.signature  = signature;
            this.lineNumber = lineNumber;
        }
    }

    // Table of all breakpoints based on method name and sig, plus the line number within the method.
    static MethodBreakpointData[] breakpoints = new MethodBreakpointData[] {
        new MethodBreakpointData("runTests", "()V", 3), // BREAKPOINT_3
        new MethodBreakpointData("runTests", "()V", 4), // BREAKPOINT_5
        new MethodBreakpointData("runTests", "()V", 5), // BREAKPOINT_7
        new MethodBreakpointData("runTests", "()V", 6), // BREAKPOINT_9
        new MethodBreakpointData("test1", "()V", 1), // BREAKPOINT_1
        new MethodBreakpointData("test2", "()V", 1), // BREAKPOINT_2
        new MethodBreakpointData("test3", "()V", 1), // BREAKPOINT_4
        new MethodBreakpointData("test4", "()V", 1), // BREAKPOINT_6
        new MethodBreakpointData("test5", "()V", 1)  // BREAKPOINT_8
    };

    public static void printStack(ThreadReference thread) {
        try {
            List<StackFrame> frames = thread.frames();
            Iterator<StackFrame> iter = frames.iterator();
            while (iter.hasNext()) {
                StackFrame frame = iter.next();
                System.out.println(getLocationString(frame.location()));
            }
        } catch (Exception e) {
            System.out.println("printStack: exception " + e);
        }
    }

    public static String getLocationString(Location loc) {
        return
            loc.declaringType().name() + "." +
            loc.method().name() + ":" +
            loc.lineNumber();
    }

    /*
     * Returns true if the specified event types are all co-located in this EventSet,
     * and no other events are included. Note that the order of the events (when present)
     * is required to be: MethodEntryEvent, StepEvent, BreakpointEvent.
     */
    public boolean isColocated(EventSet set, boolean needEntry, boolean needStep, boolean needBreakpoint) {
        int expectedSize = (needEntry ? 1 : 0) + (needStep ? 1 : 0) + (needBreakpoint ? 1 : 0);
        if (set.size() != expectedSize) {
            return false;
        }
        EventIterator iter = set.eventIterator();
        if (needEntry) {
            Event meEvent = iter.next();
            if (!(meEvent instanceof MethodEntryEvent)) {
                return false;
            }
        }
        if (needStep) {
            Event ssEvent = iter.next();
            if (!(ssEvent instanceof StepEvent)) {
                return false;
            }
        }
        if (needBreakpoint) {
            Event bpEvent = iter.next();
            if (!(bpEvent instanceof BreakpointEvent)) {
                return false;
            }
        }
        return true;
    }

    public void eventSetReceived(EventSet set) {
        System.out.println("\nEventSet for test case #" + testcase + ": " + set);
        switch (testcase) {
        case 1:
        case 2: {
            // During the first two test cases we should never receive an EventSet with
            // more than one Event in it.
            if (set.size() != 1) {
                testcaseFailed = true;
                // For now, we expect these two test cases to fail due to 8292217,
                // so don't fail the overall test run as a result of these failures.
                // testFailed = true;
                System.out.println("TESTCASE #" + testcase + " FAILED (ignoring): too many events in EventSet: " + set.size());
            }
            break;
        }
        case 3: {
            // At some point during test3 we should receive co-located MethodEntry, Step, and Breakpoint events.
            if (isColocated(set, true, true, true)) {
                testcaseFailed = false;
            }
            break;
        }
        case 4: {
            // At some point during test4 we should receive co-located MethodEntry and Breakpoint events.
            if (isColocated(set, true, false, true)) {
                testcaseFailed = false;
            }
            break;
        }
        case 5: {
            // At some point during test5 we should receive co-located Step and Breakpoint events.
            if (isColocated(set, false, true, true)) {
                testcaseFailed = false;
            }
            break;
        }
        case 6: {
            // At some point during test6 we should receive co-located MethodEntry and Step events.
            if (isColocated(set, true, true, false)) {
                testcaseFailed = false;
            }
            break;
        }
        }
    }

    /*
     * Most of the control flow of the test is handled via breakpoints. There is one at the start
     * of each test case that is used to enable other events that we check for during the test case.
     * In some cases there is an additional Breakpoint enabled for the test cases that is
     * also used to determine when the test case is complete. Other test cases are completed
     * when a Step or MethodEntry event arrives.
     */
    public void breakpointReached(BreakpointEvent event) {
        breakpointCount++;
        if (breakpointCount != 4 && breakpointCount != 6 && breakpointCount != 8) {
            testcase++;
        }
        System.out.println("Got BreakpointEvent(" + breakpointCount + "): " + getLocationString(event.location()));
        event.request().disable();

        // Setup test1. Completion is checked for in stepCompleted().
        if (breakpointCount == 1) {
            testcaseFailed = false; // assume passing unless error detected
            entryRequest.enable();
            exitRequest.enable();
            stepRequest = erm.createStepRequest(mainThread,
                                                StepRequest.STEP_LINE,
                                                StepRequest.STEP_OVER);
            stepRequest.addCountFilter(1);
            stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            stepRequest.enable();
        }

        // Setup test2. Completion is checked for in stepCompleted().
        if (breakpointCount == 2) {
            testcaseFailed = false; // assume passing unless error detected
            entryRequest.enable();
            exitRequest.enable();
            stepRequest = erm.createStepRequest(mainThread,
                                                StepRequest.STEP_LINE,
                                                StepRequest.STEP_INTO);
            stepRequest.addCountFilter(1);
            stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            stepRequest.enable();
        }

        // Setup test3: MethodEntry, Step, and Breakpoint co-located events.
        // Completion is handled by the next breakpoint being hit.
        if (breakpointCount == 3) {
            testcaseFailed = true; // assume failing unless pass detected
            entryRequest.enable();
            stepRequest = erm.createStepRequest(mainThread,
                                                StepRequest.STEP_LINE,
                                                StepRequest.STEP_INTO);
            stepRequest.addCountFilter(1);
            stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            stepRequest.enable();
        }
        // Complete test3. We fail if we never saw the expected co-located events.
        if (breakpointCount == 4) {
            if (testcaseFailed) {
                testFailed = true;
                System.out.println("TESTCASE #3 FAILED: did not get MethodEntry, Step, and Breakpoint co-located events");
            } else {
                System.out.println("TESTCASE #3 PASSED");
            }
        }

        // Setup test4: MethodEntry and Breakpoint co-located events.
        // Completion is handled by the next breakpoint being hit.
        if (breakpointCount == 5) {
            testcaseFailed = true; // assume failing unless pass detected
            entryRequest.enable();
        }
        // Complete test4. We fail if we never saw the expected co-located events.
        if (breakpointCount == 6) {
            entryRequest.disable();
            if (testcaseFailed) {
                testFailed = true;
                System.out.println("TESTCASE #4 FAILED: did not get MethodEntry and Breakpoint co-located events");
            } else {
                System.out.println("TESTCASE #4 PASSED");
            }
        }

        // Setup test5: Step and Breakpoint co-located events.
        // Completion is handled by the next breakpoint being hit.
        if (breakpointCount == 7) {
            testcaseFailed = true; // assume failing unless pass detected
            stepRequest = erm.createStepRequest(mainThread,
                                                StepRequest.STEP_LINE,
                                                StepRequest.STEP_INTO);
            stepRequest.addCountFilter(1);
            stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            stepRequest.enable();
        }
        // Complete test5. We fail if we never saw the expected co-located events.
        if (breakpointCount == 8) {
            if (testcaseFailed) {
                testFailed = true;
                System.out.println("TESTCASE #5 FAILED: did not get Step and Breakpoint co-located events");
            } else {
                System.out.println("TESTCASE #5 PASSED");
            }
        }

        // Setup test: MethodEntry and Step co-located events
        // Completion is handled by the stepCompleted() since there is no additional breakpoint.
        if (breakpointCount == 9) {
            testcaseFailed = true; // assume failing unless pass detected
            entryRequest.enable();
            stepRequest = erm.createStepRequest(mainThread,
                                                StepRequest.STEP_LINE,
                                                StepRequest.STEP_INTO);
            stepRequest.addCountFilter(1);
            stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            stepRequest.enable();
        }
    }

    public void stepCompleted(StepEvent event) {
        System.out.println("Got StepEvent: " + getLocationString(event.location()));
        event.request().disable();
        entryRequest.disable();
        if (testcase == 6 && testcaseFailed) {
            testFailed = true;
            System.out.println("TESTCASE #6 FAILED: did not get MethodEntry and Step co-located events");
        }
        if (testcase == 1 || testcase == 2 || testcase == 6) {
            exitRequest.disable();
            if (!testcaseFailed) {  // We already did a println if the test failed.
                System.out.println("TESTCASE #" + testcase + " PASSED");
            }
        }
    }

    public void methodEntered(MethodEntryEvent event) {
        System.out.println("Got MethodEntryEvent: " + getLocationString(event.location()));
        if (methodEntryCount++ == 25) {
            entryRequest.disable(); // Just in case the test loses control.
        }
    }

    public void methodExited(MethodExitEvent event) {
        System.out.println("Got MethodExitEvent: " + getLocationString(event.location()));
        //printStack(event.thread());
        exitRequest.disable();
        entryRequest.disable();
    }

    protected void runTests() throws Exception {
        System.out.println("Starting CLETest");
        BreakpointEvent bpe = startToMain("CLEDebugee");
        targetClass = (ClassType)bpe.location().declaringType();
        mainThread = bpe.thread();
        System.out.println("Got main thread: " + mainThread);
        erm = eventRequestManager();

        try {
            // Setup all breakpoints
            for (MethodBreakpointData bpData : breakpoints) {
                Location loc = findMethodLocation(targetClass, bpData.method,
                                                  bpData.signature, bpData.lineNumber);
                BreakpointRequest req = erm.createBreakpointRequest(loc);
                req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                req.enable();
            }

            // Ask for method entry events
            entryRequest = erm.createMethodEntryRequest();
            entryRequest.addThreadFilter(mainThread);
            entryRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);

            // Ask for method exit events
            exitRequest = erm.createMethodExitRequest();
            exitRequest.addThreadFilter(mainThread);
            exitRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);

            System.out.println("Waiting for events: ");

            listenUntilVMDisconnect();
            System.out.println("All done...");
        } catch (Exception ex){
            ex.printStackTrace();
            testFailed = true;
        }

        if (!testFailed) {
            println("CLETest: passed");
        } else {
            throw new Exception("CLETest: failed");
        }
    }
}
