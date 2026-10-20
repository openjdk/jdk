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
 * @summary Sanity test for AccessWatchpoint/ModificationWatchpoint and InstanceFilter with value objects
 *
 * @library ..
 * @enablePreview
 * @run main/othervm FieldWatchpointsTest
 */
import com.sun.jdi.Field;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.AccessWatchpointEvent;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ModificationWatchpointEvent;
import com.sun.jdi.event.WatchpointEvent;
import com.sun.jdi.request.WatchpointRequest;
import java.util.ArrayList;
import java.util.List;

value class FieldWatchpointsTarg {
    static value class Value {
        int v;
        Value() {
            this(0);
        }
        Value(int v) {
            this.v = v;
        }
        public int getValue() {
            return v;
        }
    }

    static Value staticField = new Value(1);
    // flattened field
    Value instanceField = new Value(2);

    public static void main(String[] args) {
        System.out.println(">>Targ.main");
        // modify FieldWatchpointsTarg.instanceField and FieldWatchpointsTarg.instanceField.v
        FieldWatchpointsTarg targ = new FieldWatchpointsTarg();
        // access FieldWatchpointsTarg.instanceField and FieldWatchpointsTarg.instanceField.v
        System.out.println("obj value = " + targ.instanceField.v);
        // access FieldWatchpointsTarg.staticField and FieldWatchpointsTarg.staticField.v
        System.out.println("staticField value = " + staticField.v);
        System.out.println("<<Targ.main");
    }
}

public class FieldWatchpointsTest extends TestScaffold {

    FieldWatchpointsTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new FieldWatchpointsTest(args).startTests();
    }

    boolean equals(ObjectReference obj1, ObjectReference obj2) throws Exception {
        return obj1.equals(obj2);
    }

    void assertEqual(ObjectReference obj1, ObjectReference obj2) throws Exception {
        if (!equals(obj1, obj2)) {
            throw new RuntimeException("Values are not equal: " + obj1 + " and " + obj2);
        }
    }

    void assertNotEqual(ObjectReference obj1, ObjectReference obj2) throws Exception {
        if (equals(obj1, obj2)) {
            throw new RuntimeException("Values are equal: " + obj1 + " and " + obj2);
        }
    }

    public void fieldAccessed(AccessWatchpointEvent event) {
        TestCase.watchpoint(event);
    }

    public void fieldModified(ModificationWatchpointEvent event) {
        TestCase.watchpoint(event);
    }

    protected void runTests() throws Exception {
        List<TestCase> testCases = new ArrayList<>();
        try {
            BreakpointEvent bpe = startToMain("FieldWatchpointsTarg");
            ThreadReference mainThread = bpe.thread();
            ClassType testClass = (ClassType)bpe.location().declaringType();
            Field staticValueField = testClass.fieldByName("staticField");
            Field instanceValueField = testClass.fieldByName("instanceField");
            ClassType valueClass = (ClassType)staticValueField.type();
            ObjectReference staticFieldValue = (ObjectReference)testClass.getValue(staticValueField);

            Field watchField = valueClass.fieldByName("v");

            WatchpointRequest request = eventRequestManager().createModificationWatchpointRequest(watchField);
            testCases.add(new TestCase("modify", 1, request)); // instanceField ctor

            request = eventRequestManager().createAccessWatchpointRequest(watchField);
            testCases.add(new TestCase("access", 2, request)); // staticField, instanceField

            request = eventRequestManager().createModificationWatchpointRequest(instanceValueField);
            testCases.add(new TestCase("modify flat", 1, request)); // instanceField ctor

            request = eventRequestManager().createAccessWatchpointRequest(instanceValueField);
            testCases.add(new TestCase("access flat", 1, request)); // println(targ.instanceField.v)

            request = eventRequestManager().createAccessWatchpointRequest(watchField);
            request.addInstanceFilter(staticFieldValue);
            testCases.add(new TestCase("access+instanceFilter", 1, request)); // only staticField
        } finally {
            listenUntilVMDisconnect();
        }

        for (TestCase test: testCases) {
            String msg = "Testcase: " + test.name
                        + ", count = " + test.count
                        + ", expectedCount = " + test.expectedCount;
            System.out.println(msg);
            if (test.count != test.expectedCount) {
                throw new RuntimeException("FAILED " + msg);
            }
        }
    }

    class TestCase {
        String name;
        int count;
        int expectedCount;
        TestCase(String name, int expectedCount, WatchpointRequest request) {
            this.name = name;
            this.expectedCount = expectedCount;
            request.putProperty("testcase", this);
            request.enable();
        }

        static void watchpoint(WatchpointEvent event) {
            TestCase test = (TestCase)event.request().getProperty("testcase");
            test.count++;
        }
    }
}
