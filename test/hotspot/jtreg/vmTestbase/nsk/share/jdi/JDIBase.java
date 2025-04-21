/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

package nsk.share.jdi;

import com.sun.jdi.IntegerValue;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import java.util.List;
import nsk.share.Log;

public class JDIBase {

    // Exit code constants
    public static final int PASSED = 0;
    public static final int FAILED = 2;
    public static final int PASS_BASE = 95;


    // Log helpers
    private final String sHeader1 = "\n=> " + this.getClass().getName().replace(".", "/") + " ";

    private static final String
            sHeader2 = "--> debugger: ",
            sHeader3 = "##> debugger: ";

    public final void log1(String message) {
        logHandler.display(sHeader1 + message);
    }

    public final void log2(String message) {
        logHandler.display(sHeader2 + message);
    }

    public final void log3(String message) {
        logHandler.complain(sHeader3 + message);
    }

    protected Log logHandler;

    // common variables used by tests
    protected Debugee debuggee;
    protected ArgumentHandler argsHandler;
    protected VirtualMachine vm;
    protected ReferenceType debuggeeClass;
    protected int testExitCode = PASSED;
    protected long waitTime;

    // used by tests with breakpoint communication
    protected EventRequestManager eventRManager;
    protected EventQueue eventQueue;
    protected EventSet eventSet;
    protected EventIterator eventIterator;

    // additional fields initialized during breakpoint communication
    protected Location breakpLocation = null;
    protected BreakpointEvent bpEvent;

    protected final BreakpointRequest settingBreakpoint(
                                                     ReferenceType testedClass,
                                                     String methodName,
                                                     String bpLine,
                                                     String property)
            throws JDITestRuntimeException {
        return settingBreakpoint_private(null, testedClass, methodName, bpLine, property);
    }

    protected final BreakpointRequest settingBreakpoint(ThreadReference thread,
                                                     ReferenceType testedClass,
                                                     String methodName,
                                                     String bpLine,
                                                     String property)
            throws JDITestRuntimeException {
        if (thread == null) {
            log3("ERROR:  TEST_ERROR_IN_settingBreakpoint(): thread is null");
        }
        return settingBreakpoint_private(thread, testedClass, methodName, bpLine, property);
    }

    private final BreakpointRequest settingBreakpoint_private(ThreadReference thread,
                                                     ReferenceType testedClass,
                                                     String methodName,
                                                     String bpLine,
                                                     String property)
            throws JDITestRuntimeException {

        log2("......setting up a breakpoint:");
        log2("       thread: " + thread + "; class: " + testedClass +
                "; method: " + methodName + "; line: " + bpLine);

        List alllineLocations = null;
        Location lineLocation = null;
        BreakpointRequest breakpRequest = null;

        try {
            Method method = (Method) testedClass.methodsByName(methodName).get(0);

            alllineLocations = method.allLineLocations();

            int n =
                    ((IntegerValue) testedClass.getValue(testedClass.fieldByName(bpLine))).value();
            if (n > alllineLocations.size()) {
                log3("ERROR:  TEST_ERROR_IN_settingBreakpoint(): number is out of bound of method's lines");
            } else {
                lineLocation = (Location) alllineLocations.get(n);
                breakpLocation = lineLocation;
                try {
                    breakpRequest = eventRManager.createBreakpointRequest(lineLocation);
                    breakpRequest.putProperty("number", property);
                    if (thread != null) {
                        breakpRequest.addThreadFilter(thread);
                    }
                    breakpRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                } catch (Exception e1) {
                    log3("ERROR: inner Exception within settingBreakpoint() : " + e1);
                    breakpRequest = null;
                    e1.printStackTrace(logHandler.getOutStream());
                }
            }
        } catch (Exception e2) {
            log3("ERROR: ATTENTION:  outer Exception within settingBreakpoint() : " + e2);
            breakpRequest = null;
        }

        if (breakpRequest == null) {
            log2("      A BREAKPOINT HAS NOT BEEN SET UP");
            throw new JDITestRuntimeException("**FAILURE to set up a breakpoint**");
        }

        log2("      a breakpoint has been set up");
        return breakpRequest;
    }

    protected final void getEventSet() throws JDITestRuntimeException {
        try {
            eventSet = eventQueue.remove(waitTime);
            if (eventSet == null) {
                throw new JDITestRuntimeException("** TIMEOUT while waiting for event **");
            }
            eventIterator = eventSet.eventIterator();
        } catch (Exception e) {
            throw new JDITestRuntimeException("** EXCEPTION while waiting for event ** : " + e);
        }
    }

    // Special version of getEventSet for ThreadStartEvent/ThreadDeathEvent.
    // When ThreadStartRequest and/or ThreadDeathRequest are enabled,
    // we can get the events from system threads unexpected for tests.
    // The method skips ThreadStartEvent/ThreadDeathEvent events
    // for all threads except the expected one.
    // Note: don't limit ThreadStartRequest/ThreadDeathRequest request by addCountFilter(),
    // as it limits the requested event to be reported at most once.
    protected void getEventSetForThreadStartDeath(String threadName) throws JDITestRuntimeException {
        while (true) {
            getEventSet();
            Event event = eventIterator.nextEvent();
            if (event instanceof ThreadStartEvent evt) {
                if (evt.thread().name().equals(threadName)) {
                    log2("Got ThreadStartEvent for '" + evt.thread().name());
                    break;
                }
                log2("Got ThreadStartEvent for '" + evt.thread().name()
                        + "' instead of '" + threadName + "', skipping");
            } else if (event instanceof ThreadDeathEvent evt) {
                if (evt.thread().name().equals(threadName)) {
                    log2("Got ThreadDeathEvent for '" + evt.thread().name());
                    break;
                }
                log2("Got ThreadDeathEvent for '" + evt.thread().name()
                        + "' instead of '" + threadName + "', skipping");
            } else {
                // not ThreadStartEvent nor ThreadDeathEvent
                log2("Did't get ThreadStartEvent or ThreadDeathEvent: " + event);
                break;
            }
            eventSet.resume();
        }
        // reset the iterator before return
        eventIterator = eventSet.eventIterator();
    }

    // Sets up the standard breakpoint for communication. The breakpoint is set on
    // methodForCommunication() using the line number stored in the "lineForComm"
    // local variable. The breakpoint is enabled.
    protected BreakpointRequest setupBreakpointForCommunication(ReferenceType debuggeeClass) {
        String bPointMethod = "methodForCommunication";
        String lineForComm  = "lineForComm";

        BreakpointRequest bpRequest =
            settingBreakpoint(debuggeeClass, bPointMethod, lineForComm, "zero");
        bpRequest.enable();
        return bpRequest;
    }

    protected void breakpointForCommunication() throws JDITestRuntimeException {

        log2("breakpointForCommunication");
        while (true) {
            getEventSet();

            Event event = eventIterator.nextEvent();
            if (event instanceof BreakpointEvent) {
                bpEvent = (BreakpointEvent) event;
                return;
            }

            if (EventFilters.filtered(event)) {
                // We filter out spurious ThreadStartEvents
                continue;
            }

            throw new JDITestRuntimeException("** event '" + event + "' IS NOT a breakpoint **");
        }
    }

    // Similar to breakpointForCommunication, but skips Locatable events from unexpected locations.
    // It's useful for cases when enabled event requests can cause notifications from system threads
    // (like MethodEntryRequest, MethodExitRequest).
    protected void breakpointForCommunication(String debuggeeName) throws JDITestRuntimeException {
        log2("breakpointForCommunication");
        while (true) {
            getEventSet();

            Event event = eventIterator.nextEvent();
            if (event instanceof BreakpointEvent) {
                bpEvent = (BreakpointEvent) event;
                return;
            }
            if (EventFilters.filtered(event, debuggeeName)) {
                log2("  got unexpected event: " + event + ", skipping");
                eventSet.resume();
            } else {
                throw new JDITestRuntimeException("** event '" + event + "' IS NOT a breakpoint **");
            }
        }
    }

}
