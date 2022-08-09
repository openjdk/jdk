/*
 * Copyright (c) 2021 SAP SE. All rights reserved.
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
 * @bug 8274687
 * @summary Test the special handling in the JDWP agent of threads that call
 *          j.l.Thread.resume().
 *
 *          This is the sequence of actions by the debugger and the threads
 *          "main" and "resumee" in the target vm.
 *
 *          "resumee": Reaches breakpoint in methodWithBreakpoint() and is
 *                     suspended then.
 *
 *          "main": Calls j.l.Thread.resume() for "resumee". There is an internal
 *                  breakpoint in Thread.resume() so the JDWP agent receives a
 *                  breakpoint event. It finds that "resumee" is suspended because
 *                  of JDWP actions. The resume() call would interfere with the
 *                  debugger therefore "main" is blocked.
 *
 *          Debugger: Tests if the suspended "resumee" can be suspended a second
 *                    time and resumes it again.
 *
 *          Debugger: Resumes "resumee" by calling ThreadReference.resume().
 *                    The JDWP agent notifies "main" about it.
 *
 *          "resumee": Continues execution.
 *
 *          "main": Receives the notification, finds that "resumee" is not
 *                  suspended anymore and continues execution.
 *
 *          Debugger: Verifies that "main" is no longer blocked.
 *
 * @author Richard Reingruber richard DOT reingruber AT sap DOT com
 *
 * @library /test/lib
 *
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g ResumeAfterThreadResumeCallTest.java
 * @run driver ResumeAfterThreadResumeCallTest
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import jdk.test.lib.Asserts;

import java.util.*;

// Target program for the debugger
class ResumeAfterThreadResumeCallTarg extends Thread {

    public boolean reachedBreakpoint;
    public boolean mainThreadReturnedFromResumeCall;
    public boolean testFinished;

    public ResumeAfterThreadResumeCallTarg(String name) {
        super(name);
    }

    public static void log(String m) {
        String threadName = Thread.currentThread().getName();
        System.out.println("###(Target,"+ threadName +") " + m);
    }

    public static void main(String[] args) {
        log("Entered main()");

        // Start "resumee" thread.
        ResumeAfterThreadResumeCallTarg resumee = new ResumeAfterThreadResumeCallTarg("resumee");
        resumee.start();

        // Wait for "resumee" to reach the breakpoint in methodWithBreakpoint().
        while (!resumee.reachedBreakpoint) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) { /* ignored */ }
        }

        // "resumee" is suspended now because of the breakpoint
        // Calling Thread.resume() will block this thread.
        log("Calling Thread.resume()");
        resumee.resume();
        resumee.mainThreadReturnedFromResumeCall = true;
        log("Thread.resume() returned");

        // Wait for debugger
        while (!resumee.testFinished) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) { /* ignored */ }
        }
    }

    public void run() {
        log("up and running.");
        methodWithBreakpoint();
    }

    public void methodWithBreakpoint() {
        log("Entered methodWithBreakpoint()");
    }
}


// Debugger program

public class ResumeAfterThreadResumeCallTest extends TestScaffold {
    public static final String TARGET_CLS_NAME = ResumeAfterThreadResumeCallTarg.class.getName();
    public static final long UNBLOCK_TIMEOUT = 10000;

    ResumeAfterThreadResumeCallTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new ResumeAfterThreadResumeCallTest(args).startTests();
    }

    /**
     * Set a breakpoint in the given method and resume all threads. The
     * breakpoint is configured to suspend just the thread that reaches it
     * instead of all threads.
     */
    public BreakpointEvent resumeTo(String clsName, String methodName, String signature) {
        boolean suspendThreadOnly = true;
        return resumeTo(clsName, methodName, signature, suspendThreadOnly);
    }

    protected void runTests() throws Exception {
        BreakpointEvent bpe = startToMain(TARGET_CLS_NAME);
        mainThread = bpe.thread();

        log("Resuming to methodWithBreakpoint()");
        bpe = resumeTo(TARGET_CLS_NAME, "methodWithBreakpoint", "()V");

        log("Thread \"resumee\" has reached the breakpoint and is suspended now.");
        ThreadReference resumee = bpe.thread();
        ObjectReference resumeeThreadObj = resumee.frame(1).thisObject();
        printStack(resumee);
        mainThread.suspend();
        printStack(mainThread);
        mainThread.resume();
        log("resumee.isSuspended() -> " + resumee.isSuspended());
        log("mainThread.isSuspended() -> " + mainThread.isSuspended());
        log("Notify target main thread to continue by setting reachedBreakpoint = true.");
        setField(resumeeThreadObj, "reachedBreakpoint", vm().mirrorOf(true));

        log("Sleeping 500ms so that the main thread is blocked calling Thread.resume() on \"resumee\" Thread.");
        Thread.sleep(500);
        log("After sleep.");
        mainThread.suspend();
        printStack(mainThread);
        mainThread.resume();

        boolean mainThreadReturnedFromResumeCall = false;
        boolean resumedResumee = false;
        for (long sleepTime = 50; sleepTime < UNBLOCK_TIMEOUT && !mainThreadReturnedFromResumeCall; sleepTime <<= 1) {
            log("mainThread.isSuspended() -> " + mainThread.isSuspended());
            Value v = getField(resumeeThreadObj, "mainThreadReturnedFromResumeCall");
            mainThreadReturnedFromResumeCall = ((PrimitiveValue) v).booleanValue();
            if (!resumedResumee) {
                // main thread should still be blocked.
                Asserts.assertFalse(mainThreadReturnedFromResumeCall, "main Thread was not blocked");

                // Test suspending the already suspended resumee thread.
                Asserts.assertTrue(resumee.isSuspended(), "\"resumee\" is not suspended.");
                log("Check if suspended \"resumee\" can be suspended a 2nd time.");
                resumee.suspend();
                log("resumee.isSuspended() -> " + resumee.isSuspended());
                log("Resuming \"resumee\"");
                resumee.resume();
                Asserts.assertTrue(resumee.isSuspended(), "\"resumee\" is not suspended.");

                // Really resume the resumee thread.
                log("Resuming \"resumee\" a 2nd time will unblock the main thread.");
                resumee.resume();
                Asserts.assertFalse(resumee.isSuspended(), "\"resumee\" is still suspended.");
                resumedResumee = true;
            }
            log("Sleeping " + sleepTime + "ms");
            Thread.sleep(sleepTime);
        }
        Asserts.assertTrue(mainThreadReturnedFromResumeCall, "main Thread was not unblocked");

        setField(resumeeThreadObj, "testFinished", vm().mirrorOf(true));

        // Resume the target listening for events
        listenUntilVMDisconnect();
    }

    public void printStack(ThreadReference thread) throws Exception {
        log("Stack of thread '" + thread.name() + "':");
        List<StackFrame> stack_frames = thread.frames();
        int i = 0;
        for (StackFrame ff : stack_frames) {
            Location loc = ff.location();
            String locString = "bci:" + loc.codeIndex();
            try {
                locString = loc.sourceName() + ":" + loc.lineNumber() + "," + locString;
            } catch (AbsentInformationException e) {/* empty */};
            log("  frame[" + i++ +"]: " + ff.location().method() + " (" + locString + ")");
        }
    }

    public void setField(ObjectReference obj, String fName, Value val) throws Exception {
        log("set field " + fName + " = " + val);
        ReferenceType rt = obj.referenceType();
        Field fld = rt.fieldByName(fName);
        obj.setValue(fld, val);
        log("ok");
    }

    public Value getField(ObjectReference obj, String fName) throws Exception {
        log("get field " + fName);
        ReferenceType rt = obj.referenceType();
        Field fld = rt.fieldByName(fName);
        Value val = obj.getValue(fld);
        log("result : " + val);
        return val;
    }

    public void log(String m) {
        System.out.println("###(Debugger) " + m);
    }
}
