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
 * @summary Sanity test for ClassType (getValue, setValue, newInstance) with value objects
 *
 * @library ..
 * @enablePreview
 * @run main/othervm ValueClassTypeTest
 */
import com.sun.jdi.Field;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.BreakpointEvent;
import java.util.List;

class ValueClassTypeTarg {
    static value class Value {
        static Value staticField = new Value(1);

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

    static Value staticField = new Value(2);

    public static void main(String[] args) {
        System.out.println("Hello and goodbye from main");
    }
}

public class ValueClassTypeTest extends TestScaffold {

    ValueClassTypeTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new ValueClassTypeTest(args).startTests();
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

    protected void runTests() throws Exception {
        try {
            BreakpointEvent bpe = startToMain("ValueClassTypeTarg");
            ThreadReference mainThread = bpe.thread();
            ClassType testClass = (ClassType)bpe.location().declaringType();
            Field testField = testClass.fieldByName("staticField");
            ObjectReference value1 = (ObjectReference)testClass.getValue(testField);

            ClassType valueClass = (ClassType)value1.type();

            Method valueCtor = valueClass.concreteMethodByName("<init>", "(I)V");

            ObjectReference newValue1 = valueClass.newInstance(mainThread, valueCtor, List.of(vm().mirrorOf(10)), 0);
            // sanity check for enableCollection/disableCollection
            newValue1.disableCollection();

            testClass.setValue(testField, newValue1);

            ObjectReference updatedValue1 = (ObjectReference)testClass.getValue(testField);

            assertEqual(updatedValue1, newValue1);
            assertNotEqual(value1, newValue1);

            // sanity check for enableCollection/disableCollection
            newValue1.enableCollection();
        } finally {
            // Resume the target until end
            listenUntilVMDisconnect();
        }
    }
}
