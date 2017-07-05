/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
 *  @test
 *  @bug 4331522
 *  @summary addClassFilter("Foo") acts like "Foo*"
 *
 *  @author Robert Field/Jim Holmlund
 *
 *  @library scaffold
 *  @run build JDIScaffold VMConnection
 *  @run compile -g HelloWorld.java
 *  @run driver FilterNoMatch
 */

/* This tests the patternMatch function in JDK file:
 *    .../src/share/back/eventHandler.c
 *
 * This test verifies that patterns that do not match really don't.
 * See also testcase FilterMatch.java.
 */

import java.util.*;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

public class FilterNoMatch extends JDIScaffold {

    static boolean listenCalled;

    public static void main(String args[]) throws Exception {
        new FilterNoMatch().startTests();
    }

    public FilterNoMatch() {
        super();
    }

    private void listen() {
        TargetAdapter adapter = new TargetAdapter() {
            EventSet set = null;

            public boolean eventSetReceived(EventSet set) {
                this.set = set;
                return false;
            }

            // This gets called if no patterns match.  If any
            // pattern is erroneously matched, then this method
            // will not get called.
            public boolean stepCompleted(StepEvent event) {
                listenCalled = true;
                System.out.println("listen: line#=" + event.location().lineNumber()
                                   + " event=" + event);
                // disable the step and then run to completion
                StepRequest str= (StepRequest)event.request();
                str.disable();
                set.resume();
                return false;
            }
        };
        listenCalled = false;
        addListener(adapter);
    }

    protected void runTests() throws Exception {
        String[] args = new String[2];
        args[0] = "-connect";
        args[1] = "com.sun.jdi.CommandLineLaunch:main=HelloWorld";

        connect(args);
        waitForVMStart();

        // VM has started, but hasn't started running the test program yet.
        EventRequestManager requestManager = vm().eventRequestManager();
        ReferenceType referenceType = resumeToPrepareOf("HelloWorld").referenceType();

        // The debuggee is stopped
        // I don't think we really have to set a bkpt and then do a step,
        // we should just be able to do a step.  Problem is the
        // createStepRequest call needs a thread and I don't know
        // yet where to get one other than from the bkpt handling :-(
        Location location = findLocation(referenceType, 3);
        BreakpointRequest request
            = requestManager.createBreakpointRequest(location);

        request.enable();

        // This does a resume, so we shouldn't come back to it until
        // the debuggee has run and hit the bkpt.
        BreakpointEvent event = (BreakpointEvent)waitForRequestedEvent(request);

        // The bkpt was hit; remove it.
        requestManager.deleteEventRequest(request);

        StepRequest request1 = requestManager.createStepRequest(event.thread(),
                                  StepRequest.STEP_LINE,StepRequest.STEP_OVER);


        // We have to filter out all these so that they don't cause the
        // listener to be called.
        request1.addClassExclusionFilter("java.*");
        request1.addClassExclusionFilter("javax.*");
        request1.addClassExclusionFilter("sun.*");
        request1.addClassExclusionFilter("com.sun.*");
        request1.addClassExclusionFilter("com.oracle.*");
        request1.addClassExclusionFilter("oracle.*");
        request1.addClassExclusionFilter("jdk.internal.*");

        // We want our listener to be called if a pattern does not match.
        // So, here we want patterns that do not match HelloWorld.
        // If any pattern here erroneously matches, then our listener
        // will not get called and the test will fail.
        request1.addClassExclusionFilter("H");
        request1.addClassExclusionFilter("HelloWorl");
        request1.addClassExclusionFilter("HelloWorldx");
        request1.addClassExclusionFilter("xHelloWorld");

        request1.addClassExclusionFilter("*elloWorldx");
        request1.addClassExclusionFilter("*elloWorl");
        request1.addClassExclusionFilter("*xHelloWorld");
        request1.addClassExclusionFilter("elloWorld*");
        request1.addClassExclusionFilter("HelloWorldx*");
        request1.addClassExclusionFilter("xHelloWorld*");

        // As a test, uncomment this line and this test should fail.
        //request1.addClassExclusionFilter("*elloWorld");


        request1.enable();
        listen();

        vm().resume();

        waitForVMDeath();

        if ( !listenCalled){
            throw new Exception( "Failed: .");
        }
        System.out.println( "Passed: ");
    }
}
