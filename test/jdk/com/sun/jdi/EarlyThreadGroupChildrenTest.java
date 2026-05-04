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

/**
 * @test
 * @bug 8352088
 * @summary If ThreadGroupReference.groups() is called very early on from an
 *          event handler, it can cause a deadlock because the call can result
 *          in ClassPrepareEvents, which the debug agent will block on until
 *          the debugger handles them, which it won't because the event handler
 *          thread is waiting for a reply to ThreadGroupReference.groups().
 *
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g EarlyThreadGroupChildrenTest.java
 * @run main/othervm/timeout=20 EarlyThreadGroupChildrenTest
 */

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

class EarlyThreadGroupChildrenTestTarg {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Start");
        System.out.println("Finish");
    }
}

    /********** test program **********/

public class EarlyThreadGroupChildrenTest extends TestScaffold {
    EarlyThreadGroupChildrenTest(String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new EarlyThreadGroupChildrenTest(args).startTests();
    }

    /********** event handlers **********/

    ClassPrepareRequest cpRequest;
    ThreadStartRequest tsRequest;

    @Override
    public void threadStarted(ThreadStartEvent event) {
        System.out.println("Got ThreadStartEvent: " + event);
        cpRequest = eventRequestManager().createClassPrepareRequest();
        cpRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        cpRequest.enable();
    }

    static volatile int classPreparedCount = 0;

    @Override
    public void classPrepared(ClassPrepareEvent event) {
        try {
            ++classPreparedCount;
            System.out.println("ClassPreparedEvent " + classPreparedCount +
                               " on thread " + event.thread() +
                               ": " + event.referenceType());
            List<ThreadGroupReference> groups = vm().topLevelThreadGroups();
            ThreadGroupReference systemThreadGroup = groups.get(0);
            groups = systemThreadGroup.threadGroups();
            System.out.println("system child ThreadGroups: " + groups);
        } catch (VMDisconnectedException e) {
            // This usually eventually happens during shutdown.
            System.out.println("ClassPreparedEvent " + classPreparedCount +
                               ": Got VMDisconnectedException");
        }
    }

    public void vmDisconnected(VMDisconnectEvent event) {
        System.out.println("Got VMDisconnectEvent");
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        connect(new String[]{"EarlyThreadGroupChildrenTestTarg"});
        System.out.println("Connected: ");

        waitForVMStart();
        System.out.println("VM Started: ");

        // Do not add until after the VMStartEvent has arrived. Otherwise the debuggee
        // will be resumed after handling the VMStartEvent, and we don't want it resumed.
        addListener(this);

        // Create a ThreadStartRequest for the first ThreadStartEvent. When this event is
        // received, we will enable ClassPrepareEvents.
        tsRequest = eventRequestManager().createThreadStartRequest();
        tsRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        tsRequest.addCountFilter(1);
        tsRequest.enable();

        resumeToVMDisconnect();

        // Failure mode for this test is deadlocking, so there is no error to check for.
        System.out.println("EarlyThreadGroupChildrenTest: passed");
    }
}
