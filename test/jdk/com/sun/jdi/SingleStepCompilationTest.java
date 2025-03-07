/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

//  THIS TEST IS LINE NUMBER SENSITIVE

/**
 * @test
 * @bug 8229012
 * @summary Verify that during single stepping a method is not compiled and
 *          after single stepping it is compiled.
 * @requires vm.compMode == "Xmixed"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g SingleStepCompilationTest.java
 * @run driver SingleStepCompilationTest
 */

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;
import java.lang.reflect.Method;

import jdk.test.whitebox.WhiteBox;

class TestTarg {
    public final static int BKPT_LINE = 66;

    public static void main(String[] args) {
        // Call buswork() and verify that is get compiled.
        busyWork("Warmup");
        if (!isCompiled()) {
            throw new RuntimeException("busywork() did not get compiled after warmup");
        }

        // We need to force deopt the method now. Although we are about to force interp_only
        // mode by enabling single stepping, this does not result in the method being
        // deopt right away. It just causes the compiled method not to be used.
        deoptimizeMethod();

        // Call busywork() again. This time the debugger will have single stepping
        // enabled. After calling, verify that busywork() is not compiled.
        busyWork("StepOver"); // BKPT_LINE: We do a STEP_OVER here
        if (isCompiled()) {
            throw new RuntimeException("busywork() compiled during single stepping");
        }

        // Call busywork a 3rd time. This time the debugger will not have single stepping
        // enabled. After calling, verify that busywork is compiled.
        busyWork("AfterStep");
        if (!isCompiled()) {
            throw new RuntimeException("busywork() not compiled after single stepping completes");
        }
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static Method busyWorkMethod;
    static {
        try {
            busyWorkMethod = TestTarg.class.getDeclaredMethod("busyWork", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isCompiled() {
        return WB.isMethodCompiled(busyWorkMethod, true) || WB.isMethodCompiled(busyWorkMethod, false);
    }

    private static void deoptimizeMethod() {
        System.out.println("Decompile count before: " + WB.getMethodDecompileCount(busyWorkMethod));
        WB.deoptimizeMethod(busyWorkMethod, true);
        WB.deoptimizeMethod(busyWorkMethod, false);
        System.out.println("Decompile count after: " + WB.getMethodDecompileCount(busyWorkMethod));
    }

    // We put the array and the result variables in static volatiles just to make sure
    // the compiler doesn't optimize them away.
    public static final int ARRAY_SIZE = 1000*1024;
    public static volatile int[] a = new int[ARRAY_SIZE];
    public static volatile long result = 0;

    // Do something compute intensive to trigger compilation.
    public static void busyWork(String phase) {
        // Although timing is not necessary for this test, it is useful to have in the
        // log output for troubleshooting. The timing when step over is enabled should
        // be sigficantly slower than when not.
        long start = System.currentTimeMillis();

        // Burn some CPU time and trigger OSR compilation.
        for (int j = 0; j < 500; j++) {
            for (int i = 0; i < ARRAY_SIZE; i++) {
                a[i] = j*i + i << 5 + j;
            }
        }
        for (int i = 0; i < ARRAY_SIZE; i++) {
            result += a[i];
        }

        long end = System.currentTimeMillis();
        long totalTime = end - start;
        System.out.println(phase + " time: " + totalTime);
    }
}

/********** test program **********/

public class SingleStepCompilationTest extends TestScaffold {
    ClassType targetClass;
    ThreadReference mainThread;

    SingleStepCompilationTest (String args[]) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        /*
         * We need to pass some extra args to the debuggee for WhiteBox support and
         * for more consistent compilation behavior.
         */
        String[] newArgs = new String[5];
        if (args.length != 0) {
            throw new RuntimeException("Unexpected arguments passed to test");
        }

        // These are all needed for WhiteBoxAPI
        newArgs[0] = "-Xbootclasspath/a:.";
        newArgs[1] = "-XX:+UnlockDiagnosticVMOptions";
        newArgs[2] = "-XX:+WhiteBoxAPI";

        // In order to make sure the compilation is complete before we exit busyWork(),
        // we don't allow background compilations.
        newArgs[3] = "-XX:-BackgroundCompilation";

        // Disabled tiered compilations so a new compilation doesn't start in the middle
        // of executing busyWork(). (Might not really be needed)
        newArgs[4] = "-XX:-TieredCompilation";

        new SingleStepCompilationTest(newArgs).startTests();
    }

    /********** event handlers **********/

    EventRequestManager erm;
    BreakpointRequest bkptRequest;
    StepRequest stepRequest;

    // When we get a bkpt we want to disable the request and enable single stepping.
    public void breakpointReached(BreakpointEvent event) {
        System.out.println("Got BreakpointEvent: " + event);
        EventRequest req = event.request();
        req.disable();
        stepRequest = erm.createStepRequest(mainThread,
                                            StepRequest.STEP_LINE,
                                            StepRequest.STEP_OVER);
        stepRequest.enable();
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
        System.out.println("Got VMDisconnectEvent");
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        /*
         * Get to the top of main() to determine targetClass and mainThread.
         */
        BreakpointEvent bpe = startToMain("TestTarg");
        targetClass = (ClassType)bpe.location().declaringType();
        mainThread = bpe.thread();
        erm = vm().eventRequestManager();

        // The BKPT_LINE is the line we will STEP_OVER.
        Location loc1 = findLocation(targetClass, TestTarg.BKPT_LINE);
        bkptRequest = erm.createBreakpointRequest(loc1);
        bkptRequest.enable();

        try {
            addListener(this);
        } catch (Exception ex) {
            ex.printStackTrace();
            failure("failure: Could not add listener");
            throw new RuntimeException("SingleStepCompilationTest: failed", ex);
        }

        vm().resume();
        waitForVMDisconnect();

        System.out.println("done with loop");
        removeListener(this);

        /*
         * Deal with results of test. If anything has called failure("foo")
         * then testFailed will be true.
         */
        if (!testFailed) {
            System.out.println("SingleStepCompilationTest: passed");
        } else {
            throw new Exception("SingleStepCompilationTest: failed");
        }
    }
}
