/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 *  @bug 6426034
 *  @summary Instance filter doesn't filter event if it occurs in native method
 *
 *  @author Keith McGuigan
 *
 *  @library scaffold
 *  @run build JDIScaffold VMConnection
 *  @compile -XDignore.symbol.file NativeInstanceFilterTarg.java
 *  @run main/othervm NativeInstanceFilter
 */

/*
 *  This test tests instance filters for events generated from a native method
 */

import java.util.*;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

public class NativeInstanceFilter extends JDIScaffold {

    static int unfilteredEvents = 0;

    public static void main(String args[]) throws Exception {
        new NativeInstanceFilter().startTests();
    }

    public NativeInstanceFilter() {
        super();
    }

    static EventRequestManager requestManager = null;
    static MethodExitRequest request = null;

    private void listen() {
        TargetAdapter adapter = new TargetAdapter() {
            EventSet set = null;
            ObjectReference instance = null;

            public boolean eventSetReceived(EventSet set) {
                this.set = set;
                return false;
            }

            public boolean methodExited(MethodExitEvent event) {
                String name = event.method().name();
                if (instance == null && name.equals("latch")) {
                    // Grab the instance (return value) and set up as filter
                    System.out.println("Setting up instance filter");
                    instance = (ObjectReference)event.returnValue();
                    requestManager.deleteEventRequest(request);
                    request = requestManager.createMethodExitRequest();
                    request.addInstanceFilter(instance);
                    request.enable();
                } else if (instance != null && name.equals("intern")) {
                    // If not for the filter, this will be called twice
                    System.out.println("method exit event (String.intern())");
                    ++unfilteredEvents;
                }
                set.resume();
                return false;
            }
        };
        addListener(adapter);
    }


    protected void runTests() throws Exception {
        String[] args = new String[2];
        args[0] = "-connect";
        args[1] = "com.sun.jdi.CommandLineLaunch:main=NativeInstanceFilterTarg";

        connect(args);
        waitForVMStart();

        // VM has started, but hasn't started running the test program yet.
        requestManager = vm().eventRequestManager();
        ReferenceType referenceType =
            resumeToPrepareOf("NativeInstanceFilterTarg").referenceType();

        request = requestManager.createMethodExitRequest();
        request.enable();

        listen();

        vm().resume();

        waitForVMDeath();

        if (unfilteredEvents != 1) {
            throw new Exception(
                "Failed: Event from native frame not filtered out.");
        }
        System.out.println("Passed: Event filtered out.");
    }
}
