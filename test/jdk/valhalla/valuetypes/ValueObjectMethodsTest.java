/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8357373 8370714
 * @summary test Object methods on value classes
 * @enablePreview
 * @run junit/othervm ValueObjectMethodsTest
 * @run junit/othervm -XX:+UseFieldFlattening ValueObjectMethodsTest
 * @run junit/othervm -XX:+UseAtomicValueFlattening ValueObjectMethodsTest
 */
import java.lang.classfile.ClassFile;
import java.util.Optional;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import java.lang.reflect.AccessFlag;

import jdk.internal.vm.annotation.NullRestricted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class ValueObjectMethodsTest {
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

        Line(int x1, int y1, int x2, int y2) {
            this.p1 = new Point(x1, y1);
            this.p2 = new Point(x2, y2);
        }
    }

    static class Ref {
        @NullRestricted
        Point p;
        Line l;
        Ref(Point p, Line l) {
            this.p = p;
            this.l = l;
            super();
        }
    }

    static value class Value {
        @NullRestricted
        Point p;
        @NullRestricted
        Line l;
        Ref r;
        String s;
        Value(Point p, Line l, Ref r, String s) {
            this.p = p;
            this.l = l;
            this.r = r;
            this.s = s;
        }
    }

    static value class ValueOptional {
        private Object o;
        public ValueOptional(Object o) {
            this.o = o;
        }
    }

    value record ValueRecord(int i, String name) {}

    static final Point P1 = new Point(1, 2);
    static final Point P2 = new Point(30, 40);
    static final Line L1 = new Line(1, 2, 3, 4);
    static final Line L2 = new Line(10, 20, 3, 4);
    static final Ref R1 = new Ref(P1, L1);
    static final Ref R2 = new Ref(P2, null);
    static final Value V = new Value(P1, L1, R1, "value");

    // Instances to test, classes of each instance are tested too
    static Stream<Arguments> identitiesData() {
        Function<String, String> lambda1 = (a) -> "xyz";
        return Stream.of(
                Arguments.of(lambda1, true, false),         // a lambda (Identity for now)
                Arguments.of(new Object(), true, false),    // java.lang.Object
                Arguments.of("String", true, false),
                Arguments.of(L1, false, true),
                Arguments.of(V, false, true),
                Arguments.of(new ValueRecord(1, "B"), false, true),
                Arguments.of(new int[0], true, false),     // arrays of primitive type are identity objects
                Arguments.of(new Object[0], true, false),  // arrays of identity classes are identity objects
                Arguments.of(new String[0], true, false),  // arrays of identity classes are identity objects
                Arguments.of(new Value[0], true, false)    // arrays of value classes are identity objects
        );
    }

    // Classes to test
    static Stream<Arguments> classesData() {
        return Stream.of(
                Arguments.of(int.class, false, true),       // Fabricated primitive classes
                Arguments.of(long.class, false, true),
                Arguments.of(short.class, false, true),
                Arguments.of(byte.class, false, true),
                Arguments.of(float.class, false, true),
                Arguments.of(double.class, false, true),
                Arguments.of(char.class, false, true),
                Arguments.of(void.class, false, true),
                Arguments.of(String.class, true, false),
                Arguments.of(Object.class, true, false),
                Arguments.of(Function.class, false, true),  // Interface
                Arguments.of(Optional.class, false, true),  // Concrete value classes...
                Arguments.of(Character.class, false, true)
        );
    }

    @ParameterizedTest
    @MethodSource("identitiesData")
    public void identityTests(Object obj, boolean identityClass, boolean valueClass) {
        Class<?> clazz = obj.getClass();
        assertEquals(identityClass, Objects.hasIdentity(obj), "Objects.hasIdentity(" + obj + ")");

        // Run tests on the class
        classTests(clazz, identityClass, valueClass);
    }

    @ParameterizedTest
    @MethodSource("classesData")
    public void classTests(Class<?> clazz, boolean identityClass, boolean valueClass) {
        assertEquals(identityClass, clazz.isIdentity(), "Class.isIdentity(): " + clazz);

        assertEquals(valueClass, clazz.isValue(), "Class.isValue(): " + clazz);

        assertEquals(clazz.accessFlags().contains(AccessFlag.IDENTITY),
                identityClass, "AccessFlag.IDENTITY: " + clazz);

        int modifiers = clazz.getModifiers();
        assertEquals(clazz.isIdentity(), (modifiers & ClassFile.ACC_IDENTITY) != 0, "Class.getModifiers() & ACC_IDENTITY != 0");
        assertEquals(clazz.isValue(), (modifiers & ClassFile.ACC_IDENTITY) == 0, "Class.getModifiers() & ACC_IDENTITY == 0");
    }

    @Test
    public void identityTestNull() {
        assertFalse(Objects.hasIdentity(null), "Objects.hasIdentity(null)");
        assertFalse(Objects.isValueObject(null), "Objects.isValueObject(null)");
    }

    static Stream<Arguments> equalsTests() {
        return Stream.of(
                Arguments.of(P1, P1, true),
                Arguments.of(P1, new Point(1, 2), true),
                Arguments.of(P1, P2, false),
                Arguments.of(P1, L1, false),
                Arguments.of(L1, new Line(1, 2, 3, 4), true),
                Arguments.of(L1, L2, false),
                Arguments.of(L1, L1, true),
                Arguments.of(V, new Value(P1, L1, R1, "value"), true),
                Arguments.of(V, new Value(new Point(1, 2), new Line(1, 2, 3, 4), R1, "value"), true),
                Arguments.of(V, new Value(P1, L1, new Ref(P1, L1), "value"), false),
                Arguments.of(new Value(P1, L1, R2, "value2"), new Value(P1, L1, new Ref(P2, null), "value2"), false),
                Arguments.of(new ValueRecord(50, "fifty"), new ValueRecord(50, "fifty"), true),

                // reference classes containing fields of value class
                Arguments.of(R1, new Ref(P1, L1), false),   // identity object

                // uninitialized default value
                Arguments.of(new ValueOptional(L1), new ValueOptional(L1), true),
                Arguments.of(new ValueOptional(List.of(P1)), new ValueOptional(List.of(P1)), false)
        );
    }

    @ParameterizedTest
    @MethodSource("equalsTests")
    public void testEquals(Object o1, Object o2, boolean expected) {
        assertEquals(expected, o1.equals(o2), "equality");
        if (expected) {
            // If the values are equals, then the hashcode should equal
            assertEquals(o1.hashCode(), o2.hashCode(), "obj.hashCode");
            assertEquals(System.identityHashCode(o1), System.identityHashCode(o2), "System.identityHashCode");
        }
    }

    static Stream<Arguments> toStringTests() {
        return Stream.of(
                Arguments.of(new Point(100, 200)),
                Arguments.of(new Line(1, 2, 3, 4)),
                Arguments.of(V),
                Arguments.of(R1),
                // enclosing instance field `this$0` should be filtered
                Arguments.of(new Value(P1, L1, null, null)),
                Arguments.of(new Value(P2, L2, new Ref(P1, null), "value")),
                Arguments.of(new ValueOptional(P1))
        );
    }

    @ParameterizedTest
    @MethodSource("toStringTests")
    public void testToString(Object o) {
        String expected = String.format("%s@%s", o.getClass().getName(), Integer.toHexString(o.hashCode()));
        assertEquals(o.toString(), expected);
    }

    @Test
    public void testValueRecordToString() {
        ValueRecord o = new ValueRecord(30, "thirty");
        assertEquals("ValueRecord[i=30, name=thirty]", o.toString());
    }

    static Stream<List<Object>> hashcodeTests() {
        Point p1 = new Point(0, 1);
        Point p2 = new Point(0, 2);
        Point p3 = new Point(1, 1);
        Point p4 = new Point(2, 2);

        Line l1 = new Line(0, 1, 2, 3);
        Line l2 = new Line(9, 1, 2, 3);
        Line l3 = new Line(0, 9, 2, 3);
        Line l4 = new Line(0, 1, 9, 3);
        Line l5 = new Line(0, 1, 2, 9);

        Ref r1 = new Ref(p1, l1);
        Ref r2 = new Ref(p1, l2);
        Ref r3 = new Ref(p2, l1);
        Ref r4 = new Ref(p2, l2);
        Value v1 = new Value(p1, l1, r1, "s1");
        Value v2 = new Value(p2, l1, r1, "s1");
        Value v3 = new Value(p1, l2, r1, "s1");
        Value v4 = new Value(p1, l1, r2, "s1");
        Value v5 = new Value(p1, l1, r1, "s2");
        ValueOptional vo1 = new ValueOptional(p1);
        ValueOptional vo2 = new ValueOptional(p2);

        // Each list has objects that differ from each other so the hashCodes must differ too
        return Stream.of(
                List.of(10, 20, 30, 40),
                List.of(0x0001000100020002L, 0x0002000200040004L, 0x0008000800100010L, 0x0020002000400040L),
                List.of(p1, p2, p3, p4),
                List.of(l1, l2, l3, l4, l5),
                List.of(r1, r2, r3, r4),
                List.of(v1, v2, v3, v4, v5),
                List.of(vo1, vo2)
        );
    }

    @ParameterizedTest
    @MethodSource("hashcodeTests")
    public void testHashCode(List<Object> objects) {
        assertTrue(objects.size() > 1, "More than one object is required: " + objects);

        long count = objects.stream().map(System::identityHashCode).distinct().count();
        assertEquals(objects.size(), count, "System.identityHashCode must not repeat: " + objects);
        count = objects.stream().map(Object::hashCode).distinct().count();
        assertEquals(objects.size(), count, "Object.hashCode must not repeat: "  + objects);
    }

    interface Number {
        int value();
    }

    static class ReferenceType implements Number {
        int i;
        public ReferenceType(int i) {
            this.i = i;
        }
        public int value() {
            return i;
        }
        @Override
        public boolean equals(Object o) {
            if (o instanceof Number) {
                return this.value() == ((Number)o).value();
            }
            return false;
        }
    }

    static value class ValueType1 implements Number {
        int i;
        public ValueType1(int i) {
            this.i = i;
        }
        public int value() {
            return i;
        }
    }

    static value class ValueType2 implements Number {
        int i;
        public ValueType2(int i) {
            this.i = i;
        }
        public int value() {
            return i;
        }
        @Override
        public boolean equals(Object o) {
            if (o instanceof Number) {
                return this.value() == ((Number)o).value();
            }
            return false;
        }
    }

    static Stream<Arguments> interfaceEqualsTests() {
        return Stream.of(
                Arguments.of(new ReferenceType(10), new ReferenceType(10), false, true),
                Arguments.of(new ValueType1(10),    new ValueType1(10),    true,  true),
                Arguments.of(new ValueType2(10),    new ValueType2(10),    true,  true),
                Arguments.of(new ValueType1(20),    new ValueType2(20),    false, false),
                Arguments.of(new ValueType2(20),    new ValueType1(20),    false, true),
                Arguments.of(new ReferenceType(30), new ValueType1(30),    false, true),
                Arguments.of(new ReferenceType(30), new ValueType2(30),    false, true)
        );
    }

    @ParameterizedTest
    @MethodSource("interfaceEqualsTests")
    public void testNumber(Number n1, Number n2, boolean isSubstitutable, boolean isEquals) {
        assertEquals(isSubstitutable, (n1 == n2));
        assertEquals(isEquals, n1.equals(n2));
    }
}
