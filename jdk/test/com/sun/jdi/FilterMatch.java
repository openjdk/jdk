/*
 * Copyright (c) 2001, 2007, Oracle and/or its affiliates. All rights reserved.
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
 *  @run main/othervm FilterMatch
 */

/* Look at patternMatch in JDK file:
 *    .../src/share/back/eventHandler.c
 */

/*
 *  This test tests patterns passed to addClassFilter that do match
 *  the classname of the event.  See also testcase FilterNoMatch.java.
 */

import java.util.*;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

public class FilterMatch extends JDIScaffold {

    static boolean listenCalled;

    public static void main(String args[]) throws Exception {
        new FilterMatch().startTests();
    }

    public FilterMatch() {
        super();
    }

    private void listen() {
        TargetAdapter adapter = new TargetAdapter() {
            EventSet set = null;

            public boolean eventSetReceived(EventSet set) {
                this.set = set;
                return false;
            }

            // This gets called if all filters match.
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
        requestManager.deleteEventRequest(request);  // remove BP

        StepRequest request1 = requestManager.createStepRequest(event.thread(),
                                  StepRequest.STEP_LINE,StepRequest.STEP_OVER);

        // These patterns all match HelloWorld.  Since they all match, our
        // listener should get called and the test will pass.  If any of them
        // are erroneously determined to _not_ match, then our listener will
        // not get called and the test will fail.
        request1.addClassFilter("*");

        request1.addClassFilter("H*");
        request1.addClassFilter("He*");
        request1.addClassFilter("HelloWorld*");

        request1.addClassFilter("*d");
        request1.addClassFilter("*ld");
        request1.addClassFilter("*HelloWorld");

        request1.addClassFilter("HelloWorld");

        // As a test, uncomment this line and the test should fail.
        //request1.addClassFilter("x");

        request1.enable();
        listen();

        vm().resume();

        waitForVMDeath();

        if ( !listenCalled){
            throw new Exception( "Failed: Event filtered out.");
        }
        System.out.println( "Passed: Event not filtered out.");
    }
}
