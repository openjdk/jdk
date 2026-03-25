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
 * @summary Test value class constructor debugging
 * @library ..
 * @enablePreview
 *
 * @comment No other references
 * @run main CtorDebuggingTest
 *
 * @comment All references exist
 * @run main CtorDebuggingTest 1 2 3
 *
 * @comment No reference at step 2
 * @run main CtorDebuggingTest 1 3
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
 * The test reproduces scenarios when ObjectReference for value object is fetched during the object construction
 * and the object content is changed later. When "this" ObjectReference for value object is requested,
 * JDI returns existing reference if there are other references to the equal value object or create a new one otherwise.
 * The test debugs "new Value(3,6)" statement by setting breakpoints in Value class constructor at locations
 * when the object being constructed is (0,0), (3,0) and (3,6).
 *
 * Test scenarios are defined by test arguments; TestScaffold passes them creating debuggee process (TargetApp class).
 * Debugsee initializes static fields with value objects (0,0), (3,0), (3,6) depending on the passed arguments.
 * Debugger gets ObjectReferences for the fields before testing.
 *
 * Tested scenarios:
 * - no existing references;
 * - all 3 references exists;
 * - there are references for 1 and 3 breakpoints.
 *
 */
public class CtorDebuggingTest extends TestScaffold {

    static value class Value {
        int x;
        int y;
        Value(int x, int y) {
            this.x = x;                 // @1 breakpoint
            this.y = y;                 // @2 breakpoint
            System.out.println(".");    // @3 breakpoint
        }
    }

    static class TargetApp {
        public static Value v1;
        public static Value v2;
        public static Value v3;

        public static void main(String[] args) throws Exception {
            // ensure the class is loaded
            Class.forName(Value.class.getName());
            List<String> argList = Arrays.asList(args);
            if (argList.contains("1")) {
                v1 = new Value(0, 0);
            }
            if (argList.contains("2")) {
                v2 = new Value(3, 0);
            }
            if (argList.contains("3")) {
                v3 = new Value(3, 6);
            }
            System.out.println(">>main"); // @prepared breakpoint
            Value v = new Value(3, 6);
            System.out.println("<<main"); // @done breakpoint
        }
    }


    public static void main(String[] args) throws Exception {
        new CtorDebuggingTest(args).startTests();
    }

    Field xField;
    Field yField;

    CtorDebuggingTest(String args[]) {
        super(args);
    }

    ObjectReference getStaticFieldObject(ReferenceType cls, String fieldName) throws Exception {
        Field field = cls.fieldByName(fieldName);
        ObjectReference result = (ObjectReference)cls.getValue(field);
        System.out.println(fieldName + " static field: " + valueString(result));
        return result;
    }

    ObjectReference getThisObject(BreakpointEvent bkptEvent) {
        try {
            return bkptEvent.thread().frame(0).thisObject();
        } catch (IncompatibleThreadStateException ex) {
            throw new RuntimeException("Cannot get 'this' object", ex);
        }
    }

    String valueString(ObjectReference obj) {
        if (obj == null) {
            return "null";
        }
        IntegerValue ix = (IntegerValue)obj.getValue(xField);
        IntegerValue iy = (IntegerValue)obj.getValue(yField);
        return obj + " (" + "x: " + ix + ", y: " + iy + ")";
    }

    void assertEquals(Object obj1, Object obj2) {
        if (!Objects.equals(obj1, obj2)) {
            throw new RuntimeException("Must be equal: " + obj1 + " and " + obj2);
        }
        // Sanity check that equal objects has equal hashCode.
        if (obj1 != null) {
            if (obj1.hashCode() != obj2.hashCode()) {
                throw new RuntimeException("Equal objects have different hashCode: "
                                           + obj1.hashCode() + " and " + obj2.hashCode());
            }
        }
    }

    void assertNotEquals(Object obj1, Object obj2) {
        if (Objects.equals(obj1, obj2)) {
            throw new RuntimeException("Must be different: " + obj1 + " and " + obj2);
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
        // the map stores "this" in all breakpoints
        Map<BreakpointRequest, ObjectReference> thisObjects = new HashMap<>();

        @Override
        public void eventSetComplete(EventSet set) {
            if (needToResume) {
                set.resume();
                needToResume = false;
            }
        }

        BreakpointRequest addBreakpoint(Location loc, ObjectReference filterObject) {
            final BreakpointRequest request = eventRequestManager().createBreakpointRequest(loc);
            if (filterObject != null) {
                request.addInstanceFilter(filterObject);
            }
            request.enable();

            TargetAdapter adapter = new TargetAdapter() {
                @Override
                public void breakpointReached(BreakpointEvent event) {
                    if (request.equals(event.request())) {
                        ObjectReference thisObject = getThisObject(event);
                        System.out.println("BreakpointEvent: " + event
                                           + " (instanceFilter: " + valueString(filterObject) + ")"
                                           + ", this = " + valueString(thisObject));
                        thisObjects.put((BreakpointRequest)event.request(), thisObject);
                        needToResume = true;
                        removeThisListener();
                    }
                }
            };

            addListener(adapter);

            System.out.println("Breakpoint added: " + loc
                               + " (instanceFilter: " + valueString(filterObject) + ")");

            return request;
        }

        // Resumes the debuggee and goes through all breackpoints until the location specified is reached.
        void resumeTo(Location loc) {
            final BreakpointRequest request = eventRequestManager().createBreakpointRequest(loc);
            request.enable();

            class EventNotification {
                boolean completed = false;
                boolean disconnected = false;
            }
            final EventNotification en = new EventNotification();

            TargetAdapter adapter = new TargetAdapter() {
                public void eventReceived(Event event) {
                    if (request.equals(event.request())) {
                        synchronized (en) {
                            en.completed = true;
                            en.notifyAll();
                        }
                        removeThisListener();
                    } else if (event instanceof VMDisconnectEvent) {
                        synchronized (en) {
                            en.disconnected = true;
                            en.notifyAll();
                        }
                        removeThisListener();
                    }
                }
            };

            addListener(adapter);
            // this must be the last listener (as it resumes the debuggee)
            addListener(this);

            try {
                synchronized (en) {
                    vm().resume();
                    while (!en.completed && !en.disconnected) {
                        en.wait();
                    }
                }
            } catch (InterruptedException e) {
            }

            removeListener(this);

            if (en.disconnected) {
                throw new RuntimeException("VM Disconnected before requested event occurred");
            }
        }

        // check if the breakpoint was hit.
        boolean breakpointHit(BreakpointRequest bkpt) {
            return thisObjects.containsKey(bkpt);
        }

        ObjectReference thisAtBreakpoint(BreakpointRequest bkpt) {
            return thisObjects.get(bkpt);
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

        ClassType valueClass = (ClassType)findReferenceType(Value.class.getName());
        System.out.println(Value.class.getName() + ": " + valueClass);
        xField = valueClass.fieldByName("x");
        yField = valueClass.fieldByName("y");

        verifyClassIsValueClass(valueClass, true);
        verifyClassIsValueClass(targetClass, false);

        // Get references for pre-created objects created by debuggee.
        ObjectReference v1 = getStaticFieldObject(targetClass, "v1");
        ObjectReference v2 = getStaticFieldObject(targetClass, "v2");
        ObjectReference v3 = getStaticFieldObject(targetClass, "v3");

        MultiBreakpointHandler breakpointHandler = new MultiBreakpointHandler();

        // Breakpoints for location when "this" is (0,0).
        Location loc1 = findLocation(valueClass, breakpoints.get("1"));
        BreakpointRequest bkpt1 = breakpointHandler.addBreakpoint(loc1, null);
        BreakpointRequest bkpt1_v1 = v1 == null ? null : breakpointHandler.addBreakpoint(loc1, v1);
        BreakpointRequest bkpt1_v2 = v2 == null ? null : breakpointHandler.addBreakpoint(loc1, v2);
        BreakpointRequest bkpt1_v3 = v3 == null ? null : breakpointHandler.addBreakpoint(loc1, v3);

        // Breakpoints for location when "this" is (3,0).
        Location loc2 = findLocation(valueClass, breakpoints.get("2"));
        BreakpointRequest bkpt2 = breakpointHandler.addBreakpoint(loc2, null);
        BreakpointRequest bkpt2_v1 = v1 == null ? null : breakpointHandler.addBreakpoint(loc2, v1);
        BreakpointRequest bkpt2_v2 = v2 == null ? null : breakpointHandler.addBreakpoint(loc2, v2);
        BreakpointRequest bkpt2_v3 = v3 == null ? null : breakpointHandler.addBreakpoint(loc2, v3);

        // Breakpoints for location when "this" is (3,6).
        Location loc3 = findLocation(valueClass, breakpoints.get("3"));
        BreakpointRequest bkpt3 = breakpointHandler.addBreakpoint(loc3, null);
        BreakpointRequest bkpt3_v1 = v1 == null ? null : breakpointHandler.addBreakpoint(loc3, v1);
        BreakpointRequest bkpt3_v2 = v2 == null ? null : breakpointHandler.addBreakpoint(loc3, v2);
        BreakpointRequest bkpt3_v3 = v3 == null ? null : breakpointHandler.addBreakpoint(loc3, v3);

        // Go through all breakpoints.
        breakpointHandler.resumeTo(locDone);

        System.out.println("DONE");

        // Analyze gathered data depending on the testcase.
        if (v1 == null && v2 == null && v3 == null) {
            // No other references.
            // ObjectID is generated at the 1st breakpoint (reference to heap object being constructed),
            // and later we get the same oop (although it's content is changing).
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt1), breakpointHandler.thisAtBreakpoint(bkpt2));
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt2), breakpointHandler.thisAtBreakpoint(bkpt3));
            // There is no breakpoints with instance filter.
        } else if (v1 != null && v2 != null && v3 != null) {
            // Existing references to value objects with the same content as the object being constructed.
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt1), v1);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt1_v1), v1);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt1_v2), null);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt1_v3), null);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt2), v2);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt2_v1), null);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt2_v2), v2);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt2_v3), null);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt3), v3);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt3_v1), null);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt3_v2), null);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt3_v3), v3);
        } else if (v1 != null && v2 == null && v3 != null) {
            // At 2nd breakpoint new ObjectID is generated.
            ObjectReference thisAt2 = breakpointHandler.thisAtBreakpoint(bkpt2);
            assertNotEquals(thisAt2, null);
            assertNotEquals(thisAt2, v1);
            // Now thisAt2 has the same content as v3.
            assertEquals(thisAt2, v3);
            // At breakpoint 1 this == v1.
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt1), v1);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt1_v1), v1);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt1_v3), null);
            // At breakpoint 3 this == v3.
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt3), v3);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt3_v1), null);
            assertEquals(breakpointHandler.thisAtBreakpoint(bkpt3_v3), v3);
        } else {
            throw new RuntimeException("Unknown test case");
        }

        resumeToVMDisconnect();
    }
}
