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
 * @bug 8333542
 * @summary Missed breakpoint due to JVM not blocking other threads while
 *          delivering a ClassPrepareEvent.
 *
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g BreakpointOnClassPrepare.java
 * @run driver BreakpointOnClassPrepare SUSPEND_NONE
 * @run driver BreakpointOnClassPrepare SUSPEND_EVENT_THREAD
 * @run driver BreakpointOnClassPrepare SUSPEND_ALL
 */

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

// The debuggee spawns 50 threads that call LoadedClass.foo(). The debugger enables
// ClassPrepareEvent for LoadedClass, and sets a breakpoint on LoadedClass.foo() when
// the ClassPrepareEvent arrives. The debugger expects 50 breakpoints to be hit.
// This verifies that the thread that causes the generation of the ClassPrepareEvent
// has properly blocked all other threads from executing LoadedClass.foo() until the
// ClassPrepareEvent has been delivered.

class LoadedClass {
    static void foo(int k) {
        System.out.println("HIT = " + k); // set breakpoint here
    }
}

class BreakpointOnClassPrepareTarg {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Start");
        Thread threads[] = new Thread[BreakpointOnClassPrepare.NUM_BREAKPOINTS];
        for (int i = 0; i < BreakpointOnClassPrepare.NUM_BREAKPOINTS; i++) {
            int k = i;
            Thread t = DebuggeeWrapper.newThread(() -> {
                System.out.println("k = " + k);
                LoadedClass.foo(k);
            });
            threads[i] = t;
            t.setDaemon(true);
            t.setName("MyThread-" + k);
            t.start();
        }

        for (int i = 0; i < BreakpointOnClassPrepare.NUM_BREAKPOINTS; i++) {
            try {
                Thread t = threads[i];
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("Finish");
    }
}

    /********** test program **********/

public class BreakpointOnClassPrepare extends TestScaffold {
    ClassType targetClass;
    ThreadReference mainThread;

    BreakpointOnClassPrepare(String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new BreakpointOnClassPrepare(args).startTests();
    }

    /********** event handlers **********/

    static final int NUM_BREAKPOINTS = 50;
    int bkptCount;
    BreakpointRequest bkptRequest;

    public void breakpointReached(BreakpointEvent event) {
        bkptCount++;
        String threadInfo;
        try {
            threadInfo = event.thread().toString();
        } catch (ObjectCollectedException e) {
            // It's possible the Thread already terminated and was collected
            // if the SUSPEND_NONE policy was used.
            threadInfo = "(thread collected)";
        }
        System.out.println("Got BreakpointEvent: " + bkptCount + " for thread " + threadInfo);
    }

    public void vmDisconnected(VMDisconnectEvent event) {
        println("Got VMDisconnectEvent");
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        /* Determine which suspend policy to use. */
        int policy;
        if (args.length != 1) {
            throw new RuntimeException("Invalid number of args: " + args.length);
        }
        String policyString = args[0];
        if (policyString.equals("SUSPEND_NONE")) {
            policy = EventRequest.SUSPEND_NONE;
        } else if (policyString.equals("SUSPEND_ALL")) {
            policy = EventRequest.SUSPEND_ALL;
        } else if (policyString.equals("SUSPEND_EVENT_THREAD")) {
            policy = EventRequest.SUSPEND_EVENT_THREAD;
        } else {
            throw new RuntimeException("Invalid suspend policy: " + policyString);
        }

        /* Stop when the target is loaded. */
        BreakpointEvent bpe = startToMain("BreakpointOnClassPrepareTarg");

        /* Stop when "LoadedClass" is loaded. */
        EventRequestManager erm = vm().eventRequestManager();
        ClassPrepareEvent cpe = resumeToPrepareOf("LoadedClass");
        println("Got ClassPrepareEvent: " + cpe);

        /* Set a breakpoint for each time LoadedClass.foo() is called. */
        ClassType loadedClass = (ClassType)cpe.referenceType() ;
        Location loc1 = findMethodLocation(loadedClass,  "foo", "(I)V", 1);
        bkptRequest = erm.createBreakpointRequest(loc1);
        bkptRequest.setSuspendPolicy(policy);
        bkptRequest.enable();

        listenUntilVMDisconnect();

        if (!testFailed && bkptCount == NUM_BREAKPOINTS) {
            println("BreakpointOnClassPrepare: passed");
        } else {
            throw new Exception("BreakpointOnClassPrepare: failed. bkptCount == " + bkptCount);
        }
    }
}
