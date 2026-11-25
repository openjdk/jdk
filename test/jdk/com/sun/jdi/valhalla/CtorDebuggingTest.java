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
 * @summary Test value class constructor debugging
 * @library ..
 * @enablePreview
 * @run main CtorDebuggingTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.OpaqueFrameException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;

/*
 * The test reproduces scenarios when "this" for a value object is fetched during
 * object construction. OpaqueFrameException is expected.
 */
public class CtorDebuggingTest extends TestScaffold {
    static abstract value class AbstractValueClass {
        int x;
        int y;
        AbstractValueClass(int x, int y) {
            this.x = x;                 // @0 breakpoint
            this.y = y;
            super();
            System.out.println("AbstractValueClass() after super() call");    // @1 breakpoint
      }
    }

    static value class ValueSubClass extends AbstractValueClass {
        int z;
        ValueSubClass(int x, int y, int z) {
            this.z = z;                 // @2 breakpoint
            super(x, y);
            System.out.println("ValueSubclass() after super() call");    // @3 breakpoint
       }
    }

    static class NonValueSubClass extends AbstractValueClass {
        int z;
        NonValueSubClass(int x, int y, int z) {
            this.z = z;                 // @4 breakpoint
            super(x, y);
            System.out.println("NonValueSubClass() after super() call");    // @5 breakpoint
       }
    }

    static value class FieldLessValueClass {
        FieldLessValueClass() {
            System.out.println("FieldLessValueClass() before super() call");    // @6 breakpoint
            super();
        }
    }

    static class TargetApp {
        public static void main(String[] args) throws Exception {
            // ensure the classes are loaded
            Class.forName(AbstractValueClass.class.getName());
            Class.forName(ValueSubClass.class.getName());
            Class.forName(NonValueSubClass.class.getName());
            Class.forName(FieldLessValueClass.class.getName());
            System.out.println(">>main"); // @prepared breakpoint
            ValueSubClass       v1 = new ValueSubClass(3, 6, 9);
            NonValueSubClass    v2 = new NonValueSubClass(3, 6, 9);
            FieldLessValueClass v3 = new FieldLessValueClass();
            System.out.println("<<main"); // @done breakpoint
        }
    }

    enum BreakpointResult {
        GOT_OFE,
        NO_OFE,
        NO_OFE_NULL_THIS
    }

    public static void main(String[] args) throws Exception {
        new CtorDebuggingTest(args).startTests();
    }

    CtorDebuggingTest(String args[]) {
        super(args);
    }

    ObjectReference getThisObject(BreakpointEvent bkptEvent) {
        try {
            return bkptEvent.thread().frame(0).thisObject();
        } catch (IncompatibleThreadStateException ex) {
            throw new RuntimeException("Cannot get 'this' object", ex);
        }
    }

    // Sanity testing for value object detection logic.
    void verifyClassIsValueClass(ClassType theClass, boolean expected) {
        // VM constants
        final int IDENTITY = 0x0020;
        final int INTERFACE = 0x00000200;
        final int ABSTRACT = 0x00000400;

        int modifiers = theClass.modifiers();
        boolean isIdentity = (modifiers & IDENTITY) != 0;
        boolean isInterface = (modifiers & INTERFACE) != 0;
        boolean isAbstract = (modifiers & ABSTRACT) != 0;
        boolean isValueClass = !isIdentity;
        System.out.println("Class " + theClass + " is value class: " + (isValueClass ? "YES" : "NO"));
        if (isValueClass != expected) {
            throw new RuntimeException("IsValueClass verification failed: "
                                     + " " + isValueClass + ", expected: " + expected
                                     + " (isIdentity: " + isIdentity
                                     + " (isInterface: " + isInterface
                                     + " (isAbstract: " + isAbstract + ")");
        }
    }

    // Parses the specified source file for "@{id} breakpoint" tags and returns <id, line_number> map.
    // Example:
    //   System.out.println("BP is here");  // @my_breakpoint breakpoint
    public static Map<String, Integer> parseBreakpoints(String filePath) {
        return parseTags("breakpoint", filePath);
    }

    public static Map<String, Integer> parseTags(String tag, String filePath) {
        final String regexp = "\\@(.*?) " + tag;
        Pattern pattern = Pattern.compile(regexp);
        int lineNum = 1;
        Map<String, Integer> result = new HashMap<>();
        try {
            for (String line: Files.readAllLines(Paths.get(filePath))) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    result.put(matcher.group(1), lineNum);
                }
                lineNum++;
            }
        } catch (IOException ex) {
            throw new RuntimeException("failed to parse " + filePath, ex);
        }
        return result;
    }

    public static String getTestSourcePath(String fileName) {
        return Paths.get(System.getProperty("test.src")).resolve(fileName).toString();
    }

    public static String getThisTestFile() {
        return System.getProperty("test.file");
    }

    // TestScaffold is not very good in handling multiple breakpoints.
    // This helper class is a listener which resumes debuggee after breakpoints.
    class MultiBreakpointHandler extends TargetAdapter {
        boolean needToResume = false;
        Map<BreakpointRequest, BreakpointResult> breakpointResults = new HashMap<>();

        @Override
        public void eventSetComplete(EventSet set) {
            if (needToResume) {
                set.resume();
                needToResume = false;
            }
        }

        BreakpointRequest addBreakpoint(Location loc) {
            final BreakpointRequest request = eventRequestManager().createBreakpointRequest(loc);
            request.enable();

            TargetAdapter adapter = new TargetAdapter() {
                @Override
                public void breakpointReached(BreakpointEvent event) {
                    if (request.equals(event.request())) {
                        BreakpointRequest eventReq = (BreakpointRequest)event.request();
                        System.out.println("BreakpointEvent: " + event);
                        BreakpointResult result;
                        try {
                            ObjectReference thisObject = getThisObject(event);
                            result = thisObject != null ? BreakpointResult.NO_OFE
                                                        : BreakpointResult.NO_OFE_NULL_THIS;
                        } catch (OpaqueFrameException e) {
                            result = BreakpointResult.GOT_OFE;
                        }
                        breakpointResults.put(eventReq, result);
                        System.out.println("BreakpointEvent: result = " + result);
                        needToResume = true;
                        event.request().disable();
                        removeThisListener();
                    }
                }
            };

            addListener(adapter);
            System.out.println("Breakpoint added: " + loc);
            return request;
        }

        // Resumes the debuggee and goes through all breakpoints until the location specified is reached.
        void resumeTo(Location loc) {
            final BreakpointRequest request = eventRequestManager().createBreakpointRequest(loc);
            request.enable();

            class EventNotification {
                boolean completed = false;
            }
            final EventNotification en = new EventNotification();

            TargetAdapter adapter = new TargetAdapter() {
                public void eventReceived(Event event) {
                    if (request.equals(event.request())) {
                        synchronized (CtorDebuggingTest.this) {
                            en.completed = true;
                            CtorDebuggingTest.this.notifyAll();
                            System.out.println("resumeTo: reached resumeTo point: " + event);
                        }
                        removeThisListener();
                    } else if (event instanceof VMDisconnectEvent) {
                        synchronized (CtorDebuggingTest.this) {
                            CtorDebuggingTest.this.notifyAll();
                            System.out.println("resumeTo: got VMDisconnectEvent");
                        }
                        removeThisListener();
                    }
                }
            };

            addListener(adapter);
            // this must be the last listener (as it resumes the debuggee)
            addListener(this);

            try {
                synchronized (CtorDebuggingTest.this) {
                    vm().resume();
                    while (!en.completed && !vmDisconnected) {
                        CtorDebuggingTest.this.wait();
                    }
                }
            } catch (InterruptedException e) {
            }

            removeListener(this);

            if (!en.completed) {
                throw new RuntimeException("JDI event handler failed");
            }
            if (vmDisconnected) {
                throw new RuntimeException("VM Disconnected before requested event occurred");
            }
        }
    }

    @Override
    protected void runTests() throws Exception {
        BreakpointEvent bpe = startToMain(TargetApp.class.getName());
        ClassType targetClass = (ClassType)bpe.location().declaringType();

        Map<String, Integer> breakpoints = parseBreakpoints(getThisTestFile());
        System.out.println("breakpoints:");
        for (var entry : breakpoints.entrySet()) {
            System.out.println("  tag " + entry.getKey() + ", line " + entry.getValue());
        }

        Location locPrepared = findLocation(targetClass, breakpoints.get("prepared"));
        Location locDone = findLocation(targetClass, breakpoints.get("done"));

        resumeTo(locPrepared);
        System.out.println("PREPARED");

        ClassType abstractValueClass = (ClassType)findReferenceType(AbstractValueClass.class.getName());
        System.out.println(AbstractValueClass.class.getName() + ": " + abstractValueClass);
        ClassType valueSubClass = (ClassType)findReferenceType(ValueSubClass.class.getName());
        System.out.println(ValueSubClass.class.getName() + ": " + valueSubClass);
        ClassType nonValueSubClass = (ClassType)findReferenceType(NonValueSubClass.class.getName());
        System.out.println(NonValueSubClass.class.getName() + ": " + nonValueSubClass);
        ClassType fieldLessValueClass = (ClassType)findReferenceType(FieldLessValueClass.class.getName());
        System.out.println(FieldLessValueClass.class.getName() + ": " + fieldLessValueClass);

        verifyClassIsValueClass(abstractValueClass, true);
        verifyClassIsValueClass(valueSubClass, true);
        verifyClassIsValueClass(nonValueSubClass, false);
        verifyClassIsValueClass(fieldLessValueClass, true);
        verifyClassIsValueClass(targetClass, false);

        MultiBreakpointHandler breakpointHandler = new MultiBreakpointHandler();

        final int NUM_BREAKPOINTS = 7;
        BreakpointRequest bpReqs[] = new BreakpointRequest[NUM_BREAKPOINTS];
        Location loc;

        loc = findLocation(abstractValueClass, breakpoints.get("0"));
        bpReqs[0] = breakpointHandler.addBreakpoint(loc);
        loc = findLocation(abstractValueClass, breakpoints.get("1"));
        bpReqs[1] = breakpointHandler.addBreakpoint(loc);

        loc = findLocation(valueSubClass, breakpoints.get("2"));
        bpReqs[2] = breakpointHandler.addBreakpoint(loc);
        loc = findLocation(valueSubClass, breakpoints.get("3"));
        bpReqs[3] = breakpointHandler.addBreakpoint(loc);

        loc = findLocation(nonValueSubClass, breakpoints.get("4"));
        bpReqs[4] = breakpointHandler.addBreakpoint(loc);
        loc = findLocation(nonValueSubClass, breakpoints.get("5"));
        bpReqs[5] = breakpointHandler.addBreakpoint(loc);

        loc = findLocation(fieldLessValueClass, breakpoints.get("6"));
        bpReqs[6] = breakpointHandler.addBreakpoint(loc);

        // Go through all breakpoints.
        breakpointHandler.resumeTo(locDone);

        resumeToVMDisconnect();
        System.out.println("DONE");

        // Check to make sure each breakpoint was hit and resulted in OpaqueFrameException when
        // accessing "this", except for breakoint 6, which should not result in OpaqueFrameException
        boolean failed = false;
        for (int i = 0; i < NUM_BREAKPOINTS; i++) {
            BreakpointResult result = breakpointHandler.breakpointResults.get(bpReqs[i]);
            if (result == null) {
                System.out.println("ERROR: Missed breakpoint " + i);
                failed = true;
            } else if (i == 6) {
                if (result == BreakpointResult.GOT_OFE) {
                    System.out.println("ERROR: Got OpaqueFrameException at breakpoint " + i);
                    failed = true;
                } else if (result == BreakpointResult.NO_OFE_NULL_THIS) {
                    System.out.println("ERROR: Got null 'this' at breakpoint " + i);
                    failed = true;
                } else {
                    System.out.println("PASSED: Got `this` at breakpoint " + i);
                }
            } else if (i != 6 && result != BreakpointResult.GOT_OFE) {
                System.out.println("ERROR: Failed to get OpaqueFrameException at breakpoint " + i);
                failed = true;
            } else {
                System.out.println("PASSED: Got OpaqueFrameException at breakpoint " + i);
            }
        }
        if (failed) {
            throw new RuntimeException("One or more missing or unexpected OpaqueFrameExceptions");
        }
    }
}
