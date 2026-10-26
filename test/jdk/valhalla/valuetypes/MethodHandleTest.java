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
 * @summary test MethodHandle and VarHandle of value classes
 * @enablePreview
 * @run junit/othervm MethodHandleTest
 */

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class MethodHandleTest {
    @LooselyConsistentValue
    static value class Point {
        public int x;
        public int y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @LooselyConsistentValue
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
        List<String> list;
        ValueOptional vo;

        Ref(Point p, Line l) {
            this.p = p;
            this.l = l;
            super();
        }
    }

    static value class ValueOptional {
        private Object o;
        public ValueOptional(Object o) {
            this.o = o;
        }
    }

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static final Point P = new Point(1, 2);
    static final Line L = new Line(1, 2, 3, 4);
    static final Ref R = new Ref(P, null);

    static Stream<Arguments> fields() {
        return Stream.of(
                // value class with int fields
                Arguments.of("MethodHandleTest$Point", P, Set.of("x", "y")),
                // value class whose fields are null-restricted and of value class
                Arguments.of("MethodHandleTest$Line", L, Set.of("p1", "p2")),
                // identity class whose non-final fields are of value type,
                Arguments.of("MethodHandleTest$Ref", R, Set.of("p", "l", "list", "vo"))
        );
    }

    /**
     * Test MethodHandle invocation on the fields of a given class.
     * MethodHandle produced by Lookup::unreflectGetter, Lookup::findGetter,
     * Lookup::findVarHandle.
     */
    @ParameterizedTest
    @MethodSource("fields")
    public void testFieldGetter(String cn, Object o, Set<String> fields) throws Throwable  {
        Class<?> c = Class.forName(cn);
        for (String name : fields) {
            Field f = c.getDeclaredField(name);
            var mh = LOOKUP.findGetter(c, f.getName(), f.getType());
            var v1 = mh.invoke(o);

            var vh = LOOKUP.findVarHandle(c, f.getName(), f.getType());
            var v2 = vh.get(o);

            var mh3 = LOOKUP.unreflectGetter(f);
            var v3 = mh3.invoke(o);

            if (c.isValue())
                ensureImmutable(f, o);
        }
    }

    static Stream<Arguments> setters() {
        return Stream.of(
                Arguments.of(Ref.class, R, "p", true),
                Arguments.of(Ref.class, R, "l", false),
                Arguments.of(Ref.class, R, "list", false),
                Arguments.of(Ref.class, R, "vo", false)
        );
    }
    @ParameterizedTest
    @MethodSource("setters")
    public void testFieldSetter(Class<?> cls, Object o, String name, boolean nullRestricted) throws Throwable {
        Field f = cls.getDeclaredField(name);
        var mh = LOOKUP.findSetter(cls, f.getName(), f.getType());
        var vh = LOOKUP.findVarHandle(cls, f.getName(), f.getType());
        var mh3 = LOOKUP.unreflectSetter(f);

        if (nullRestricted) {
            assertThrows(NullPointerException.class, () -> mh.invoke(o, null));
            assertThrows(NullPointerException.class, () -> vh.set(o, null));
            assertThrows(NullPointerException.class, () -> mh3.invoke(o, null));
        } else {
            mh.invoke(o, null);
            vh.set(o, null);
            mh3.invoke(o, null);
        }
    }

    static Stream<Arguments> arrays() throws Throwable {
        return Stream.of(
                Arguments.of(Point[].class, newArray(Point[].class), P, false),
                Arguments.of(Point[].class, newNullRestrictedNonAtomicArray(Point.class, new Point(0, 0)), P, true),
                Arguments.of(Line[].class, newArray(Line[].class), L, false),
                Arguments.of(Line[].class, newNullRestrictedNonAtomicArray(Line.class, new Line(0, 0, 0, 0)), L, true),
                Arguments.of(Ref[].class, newArray(Ref[].class), R, false)
        );
    }

    private static final int ARRAY_SIZE = 5;
    private static Object[] newArray(Class<?> arrayClass) throws Throwable {
        MethodHandle ctor = MethodHandles.arrayConstructor(arrayClass);
        return (Object[])ctor.invoke(ARRAY_SIZE);
    }
    private static Object[] newNullRestrictedNonAtomicArray(Class<?> componentClass, Object initVal) throws Throwable {
        return ValueClass.newNullRestrictedNonAtomicArray(componentClass, ARRAY_SIZE, initVal);
    }

    @ParameterizedTest
    @MethodSource("arrays")
    public void testArrayElementSetterAndGetter(Class<?> arrayClass, Object[] array, Object element, boolean nullRestricted) throws Throwable {
        MethodHandle setter = MethodHandles.arrayElementSetter(array.getClass());
        MethodHandle getter = MethodHandles.arrayElementGetter(array.getClass());
        VarHandle vh = MethodHandles.arrayElementVarHandle(arrayClass);
        Class<?> componentType = arrayClass.getComponentType();

        for (int i=0; i < ARRAY_SIZE; i++) {
            var v = getter.invoke(array, i);
            if (nullRestricted) {
                assertTrue(v != null);
            } else {
                assertTrue(v == null);
            }
        }
        for (int i=0; i < ARRAY_SIZE; i++) {
            setter.invoke(array, i, element);
            assertTrue(getter.invoke(array, i) == element);
        }
        // set an array element to null
        if (nullRestricted) {
            assertTrue(vh.get(array, 1) != null);
            assertThrows(NullPointerException.class, () -> setter.invoke(array, 1, null));
            assertThrows(NullPointerException.class, () -> vh.set(array, 1, null));
        } else {
            setter.invoke(array, 1, null);
            assertNull(getter.invoke(array, 1));
            vh.set(array, 1, null);
        }
    }

    static void ensureImmutable(Field f, Object o) throws Throwable {
        Class<?> c = f.getDeclaringClass();
        assertTrue(Modifier.isFinal(f.getModifiers()));
        assertFalse(Modifier.isStatic(f.getModifiers()));
        assertTrue(f.trySetAccessible());

        Object v = f.get(o);

        assertThrows(IllegalAccessException.class, () -> LOOKUP.findSetter(c, f.getName(), f.getType()));
        assertThrows(IllegalAccessException.class, () -> LOOKUP.unreflectSetter(f));
        VarHandle vh = LOOKUP.findVarHandle(c, f.getName(), f.getType());

        // test var handle
        assertThrows(UnsupportedOperationException.class, () -> vh.set(o, v));
    }
}
