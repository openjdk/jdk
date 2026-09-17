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
 * @summary Sanity test for ArrayReference (getValue/setValue) with flat arrays
 *
 * @modules java.base/jdk.internal.value
 * @library ..
 * @enablePreview
 * @run main/othervm ValueArrayReferenceTest
 *                   --add-modules java.base --add-exports java.base/jdk.internal.value=ALL-UNNAMED
 */
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import com.sun.jdi.event.BreakpointEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jdk.internal.value.ValueClass;

class ValueArrayReferenceTarg {
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

    static int ARRAY_SIZE = 5;
    static void initArray(Value[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new Value(i);
        }
    }

    static Value[] testRegularArray;
    static Value[] testNullableAtomicArray;
    static Value[] testNonNullNonAtomicArray;
    static Value[] testNonNullAtomicArray;

    static Value otherValue = new Value(25);

    static {
        try {
            testRegularArray = new Value[ARRAY_SIZE];
            initArray(testRegularArray);

            testNullableAtomicArray = (Value[])ValueClass.newNullableAtomicArray(Value.class, ARRAY_SIZE);
            initArray(testNullableAtomicArray);

            testNonNullNonAtomicArray = (Value[])ValueClass.newNullRestrictedNonAtomicArray(Value.class, ARRAY_SIZE, Value.class.newInstance());
            initArray(testNonNullNonAtomicArray);

            testNonNullAtomicArray = (Value[])ValueClass.newNullRestrictedAtomicArray(Value.class, ARRAY_SIZE, Value.class.newInstance());
            initArray(testNonNullAtomicArray);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) {
        System.out.println("Hello and goodbye from main");
    }
}

public class ValueArrayReferenceTest extends TestScaffold {

    ValueArrayReferenceTest (String args[]) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        new ValueArrayReferenceTest(args).startTests();
    }

    String arrayToString(ArrayReference array) {
        List<Value> values = array.getValues();
        // Mirror.toString reports object type and reference id,
        // it should be enough to see if objects are different.
        return values.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    Value getFieldValue(ReferenceType cls, String fieldName) {
        System.out.println("Getting value from " + fieldName);
        Value value = cls.getValue(cls.fieldByName(fieldName));
        System.out.println(" - " + value);
        return value;
    }

    ArrayReference getArrayFromField(ReferenceType cls, Field field) throws Exception {
        System.out.println("Getting array from " + field.name());
        ArrayReference array = (ArrayReference)cls.getValue(field);
        System.out.println(" - " + array);
        System.out.println("   " + arrayToString(array));
        return array;
    }

    boolean arraysEquals(ArrayReference arr1, ArrayReference arr2) throws Exception {
        // Compare string representation of the array (contains object type and id for each element).
        String s1 = arrayToString(arr1);
        String s2 = arrayToString(arr2);
        return s1.equals(s2);
    }

    void fillArrayWithOtherValue(ArrayReference arr, Value value) throws Exception {
        for (int i = 0; i < arr.length(); i++) {
            arr.setValue(i, value);
        }
    }

    void verifyArraysEqual(List<ArrayReference> arrays) throws Exception {
        // Compare 1st and 2nd, 2nd and 3rd, etc.
        for (int i = 1; i < arrays.size(); i++) { // start from 1
            ArrayReference arr1 = arrays.get(i - 1);
            ArrayReference arr2 = arrays.get(i);
            if (!arraysEquals(arr1, arr2)) {
                System.out.println("Arrays are different (" + (i - 1) + " and " + i + "):"
                                 + "\n    - " + arrayToString(arr1)
                                 + "\n    - " + arrayToString(arr2));
                throw new RuntimeException("Arrays are different");
            }
        }
    }

    protected void runTests() throws Exception {
        try {
            BreakpointEvent bpe = startToMain("ValueArrayReferenceTarg");
            ReferenceType cls = bpe.location().declaringType();

            // Get all arrays.
            List<ArrayReference> arrays = new ArrayList<>();
            List<Field> fields = cls.allFields();
            for (Field field: fields) {
                if (field.name().startsWith("test")) {
                    arrays.add(getArrayFromField(cls, field));
                }
            }

            // Ensure elements in all arrays are equal.
            verifyArraysEqual(arrays);

            // Update arrays.
            Value otherValue = getFieldValue(cls, "otherValue");
            for (ArrayReference arr: arrays) {
                fillArrayWithOtherValue(arr, otherValue);
                System.out.println("Array after update:");
                System.out.println("   " + arrayToString(arr));
            }

            // Ensure elements in all arrays are equal.
            verifyArraysEqual(arrays);
        } finally {
            // Resume the target until end.
            listenUntilVMDisconnect();
        }
    }
}
