/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @modules java.base/java.lang.runtime:open
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run junit/othervm SubstitutabilityTest
 */

import java.lang.reflect.Method;
import java.util.stream.Stream;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class SubstitutabilityTest {
    static value class Point {
        public int x;
        public int y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static value class Line {
        @NullRestricted
        Point p1;
        @NullRestricted
        Point p2;

        Line(Point p1, Point p2) {
            this.p1 = p1;
            this.p2 = p2;
        }
        Line(int x1, int y1, int x2, int y2) {
            this(new Point(x1, y1), new Point(x2, y2));
        }
    }

    // contains null-reference and null-restricted fields
    static value class MyValue {
        MyValue2 v1;
        @NullRestricted
        MyValue2 v2;
        public MyValue(MyValue2 v1, MyValue2 v2) {
            this.v1 = v1;
            this.v2 = v2;
        }
    }

    static value class MyValue2 {
        static int cnt = 0;
        int x;
        MyValue2(int x) {
            this.x = x;
        }
    }

    static value class MyFloat {
        public static float NaN1 = Float.intBitsToFloat(0x7ff00001);
        public static float NaN2 = Float.intBitsToFloat(0x7ff00002);
        float x;
        MyFloat(float x) {
            this.x = x;
        }
        public String toString() {
            return Float.toString(x);
        }
    }

    static value class MyDouble {
        public static double NaN1 = Double.longBitsToDouble(0x7ff0000000000001L);
        public static double NaN2 = Double.longBitsToDouble(0x7ff0000000000002L);
        double x;
        MyDouble(double x) {
            this.x = x;
        }
        public String toString() {
            return Double.toString(x);
        }
    }

    static Stream<Arguments> substitutableCases() {
        Point p1 = new Point(10, 10);
        Point p2 = new Point(20, 20);
        Line l1 = new Line(p1, p2);
        MyValue v1 = new MyValue(null, new MyValue2(0));
        MyValue v2 = new MyValue(new MyValue2(2), new MyValue2(3));
        MyValue2 value2 = new MyValue2(2);
        MyValue2 value3 = new MyValue2(3);
        MyValue[] va = new MyValue[1];
        return Stream.of(
                Arguments.of(new MyFloat(1.0f), new MyFloat(1.0f)),
                Arguments.of(new MyDouble(1.0), new MyDouble(1.0)),
                Arguments.of(new MyFloat(Float.NaN), new MyFloat(Float.NaN)),
                Arguments.of(new MyDouble(Double.NaN), new MyDouble(Double.NaN)),
                Arguments.of(p1, new Point(10, 10)),
                Arguments.of(p2, new Point(20, 20)),
                Arguments.of(l1, new Line(10,10, 20,20)),
                Arguments.of(v2, new MyValue(value2, value3))
        );
    }

    @ParameterizedTest
    @MethodSource("substitutableCases")
    public void substitutableTest(Object a, Object b) {
        assertTrue(isSubstitutable(a, b));
    }

    static Stream<Arguments> notSubstitutableCases() {
        return Stream.of(
                Arguments.of(new MyFloat(1.0f), new MyFloat(2.0f)),
                Arguments.of(new MyDouble(1.0), new MyDouble(2.0)),
                Arguments.of(new MyFloat(MyFloat.NaN1), new MyFloat(MyFloat.NaN2)),
                Arguments.of(new MyDouble(MyDouble.NaN1), new MyDouble(MyDouble.NaN2)),
                Arguments.of(new Point(10, 10), new Point(20, 20)),
                Arguments.of(Integer.valueOf(10), Integer.valueOf(20))
        );
    }

    @ParameterizedTest
    @MethodSource("notSubstitutableCases")
    public void notSubstitutableTest(Object a, Object b) {
        assertFalse(isSubstitutable(a, b));
    }

    private static final Method IS_SUBSTITUTABLE;
    static {
        Method m = null;
        try {
            Class<?> c = Class.forName("java.lang.runtime.ValueObjectMethods");
            m = c.getDeclaredMethod("isSubstitutable", Object.class, Object.class);
            m.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        IS_SUBSTITUTABLE = m;
    }
    private static boolean isSubstitutable(Object a, Object b) {
        try {
            return (boolean) IS_SUBSTITUTABLE.invoke(null, a, b);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static value class IntValue {
        int value;

        static final int[] EDGE_CASES = {
                0, -1, 1,
                Integer.MIN_VALUE, Integer.MAX_VALUE
        };

        public IntValue(int index) {
            value = EDGE_CASES[index];
        }

        public String toString() {
            return "IntValue(" + value +
                    ", bits=0x" + Integer.toHexString(value) + ")";
        }

        static boolean cmp(int i, int j) {
            return EDGE_CASES[i] == EDGE_CASES[j];
        }
    }

    static value class NestedValue {
        IntValue value;

        static final IntValue[] EDGE_CASES = {
                null, new IntValue(0), new IntValue(1), new IntValue(2),
                new IntValue(3), new IntValue(0)
        };

        public NestedValue(int index) {
            value = EDGE_CASES[index];
        }

        public String toString() {
            return "NestedValue(" + value + ")";
        }

        static boolean cmp(int i, int j) {
            return EDGE_CASES[i] == EDGE_CASES[j];
        }
    }

    public static boolean testNestedValue(NestedValue v1, NestedValue v2) {
        return v1 == v2;
    }

    @Test
    void testNestedValue() {
        // NestedValue
        for (int i = 0; i < NestedValue.EDGE_CASES.length; ++i) {
            for (int j = 0; j < NestedValue.EDGE_CASES.length; ++j) {
                NestedValue val1 = new NestedValue(i);
                NestedValue val2 = new NestedValue(j);
                boolean res = testNestedValue(val1, val2);
                assertEquals(NestedValue.cmp(i, j), res, () -> val1 + " == " + val2);
            }
        }
    }
}
