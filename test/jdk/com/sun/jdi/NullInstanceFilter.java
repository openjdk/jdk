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

/**
 * @test
 * @bug 8395702
 * @summary Verify that a null instance filter on a MethodEntryRequest only
 *  matches static methods.
 * @author Robert Field/Jim Holmlund
 *
 * @run build TestScaffold VMConnection TargetAdapter TargetListener
 * @run compile -g NullInstanceFilter.java
 * @run driver NullInstanceFilter
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

class NullInstanceFilterTarg {
    static NullInstanceFilterTarg instance = new NullInstanceFilterTarg();

    public static void main(String args[]) {
        instance.start();
        staticDone();
    }

    void start() {
        instanceCall1();
        staticCall();
        instanceCall2();
    }

    void instanceCall1() {
    }

    void instanceCall2() {
    }

    static void staticCall() {
    }

    static void staticDone() {
    }
}

public class NullInstanceFilter extends TestScaffold {
    ReferenceType targetClass;

    ObjectReference theInstance;
    MethodEntryRequest methodEntryRequest;
    ThreadReference mainThread;
    int methodCount = 0;

    public static void main(String args[]) throws Exception {
        new NullInstanceFilter(args).startTests();
    }

    NullInstanceFilter(String args[]) throws Exception {
        super(args);
    }

    /**
     * Override TestScaffold.methodEntered.  This should get called
     * once for each method named in 'expectedMethods', and for
     * the instance that we select to filter on.
     */
    public void methodEntered(MethodEntryEvent event) {
        if (testFailed) {
            return;
        }
        // Find the instance and verify that it is
        // the one we want.
        ObjectReference theThis;
        StackFrame frame;
        String methodName;
        try {
            frame = event.thread().frame(0);
            theThis = frame.thisObject();
            println(frame.toString());
            methodName = event.location().method().name();
        } catch (IncompatibleThreadStateException ee) {
            methodEntryRequest.disable();
            failure("FAILED: Exception occured in methodEntered: " + ee);
            return;
        }

        println("Method: " + methodName);

        if (theThis != null) {
            methodEntryRequest.disable();
            failure("FAILED: somehow got event on a non-static method");
            return;
        }

        if (event.thread() != mainThread) {
            methodEntryRequest.disable();
            failure("FAILED: somehow got event not on main thread");
            return;
        }
        
        if ("staticDone".equals(methodName)) {
            methodEntryRequest.disable();
            return;
        }

        if ("staticCall".equals(methodName)) {
            methodCount++;
            return;
        }

        // fail if we don't get called for each of the expected methods
        methodEntryRequest.disable();
        failure("FAILED: Unexpected event on static method: " + methodName);
    }

    protected void runTests() throws Exception {

        BreakpointEvent bpe = startTo("NullInstanceFilterTarg", "start", "()V");
        targetClass = bpe.location().declaringType();
        mainThread = bpe.thread();

        EventRequestManager mgr = vm().eventRequestManager();
        methodEntryRequest = mgr.createMethodEntryRequest();
        methodEntryRequest.addInstanceFilter(null);
        methodEntryRequest.addThreadFilter(mainThread);
        methodEntryRequest.enable();

        listenUntilVMDisconnect();

        if (!testFailed && methodCount != 1) {
            failure("FAILED: Expected 1 event, got " + methodCount);
        }
        if (!testFailed) {
            println("NullInstanceFilter: passed");
        } else {
            throw new Exception("NullInstanceFilter: failed");
        }
    }
}
