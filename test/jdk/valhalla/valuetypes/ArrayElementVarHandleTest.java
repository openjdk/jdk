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
 * @summary test VarHandle on value class array
 * @enablePreview
 * @run junit/othervm -XX:+UseArrayFlattening ArrayElementVarHandleTest
 * @run junit/othervm -XX:-UseArrayFlattening  ArrayElementVarHandleTest
 */

import java.lang.invoke.*;
import java.util.stream.Stream;

import jdk.internal.vm.annotation.NullRestricted;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

public class ArrayElementVarHandleTest {
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

    private static final Point[] POINTS = new Point[]{
            new Point(1, 2),
            new Point(10, 20),
            new Point(100, 200),
            null
    };

    private static final Line[] LINES = new Line[]{
            new Line(1, 2, 3, 4),
            new Line(10, 20, 30, 40),
            null
    };

    static Stream<Arguments> testCases() throws Throwable {
        int plen = POINTS.length;
        int llen = LINES.length;
        return Stream.of(
                Arguments.of(newArray(Object[].class, plen),    POINTS),
                Arguments.of(newArray(Object[].class, plen),    new Object[] { "abc", new Point(1, 2) }),
                Arguments.of(newArray(Point[].class, plen),     POINTS),
                Arguments.of(new Point[plen],                   POINTS),

                Arguments.of(newArray(Object[].class, llen),    LINES),
                Arguments.of(newArray(Line[].class, llen),      LINES),
                Arguments.of(new Line[llen],                    LINES)
        );
    }

    /*
     * Constructs a new array of the specified type and size using
     * MethodHandle.
     */
    private static Object[] newArray(Class<?> arrayType, int size) throws Throwable {
        MethodHandle ctor = MethodHandles.arrayConstructor(arrayType);
        return (Object[]) ctor.invoke(size);
    }

    /*
     * Test VarHandle to set elements of the given array with
     * various access mode.
     */
    @ParameterizedTest
    @MethodSource("testCases")
    public void testSetArrayElements(Object[] array, Object[] elements) {
        Class<?> arrayType = array.getClass();
        assertTrue(array.length >= elements.length);

        VarHandle vh = MethodHandles.arrayElementVarHandle(arrayType);
        set(vh, array.clone(), elements);
        setVolatile(vh, array.clone(), elements);
        setOpaque(vh, array.clone(), elements);
        setRelease(vh, array.clone(), elements);
        getAndSet(vh, array.clone(), elements);
        compareAndSet(vh, array.clone(), elements);
        compareAndExchange(vh, array.clone(), elements);
    }

    // VarHandle::set
    void set(VarHandle vh, Object[] array, Object[] elements) {
        for (int i = 0; i < elements.length; i++) {
            vh.set(array, i, elements[i]);
        }
        for (int i = 0; i < elements.length; i++) {
            Object v = (Object) vh.get(array, i);
            assertEquals(elements[i], v);
        }
    }

    // VarHandle::setVolatile
    void setVolatile(VarHandle vh, Object[] array, Object[] elements) {
        for (int i = 0; i < elements.length; i++) {
            vh.setVolatile(array, i, elements[i]);
        }
        for (int i = 0; i < elements.length; i++) {
            Object v = (Object) vh.getVolatile(array, i);
            assertEquals(elements[i], v);
        }
    }

    // VarHandle::setOpaque
    void setOpaque(VarHandle vh, Object[] array, Object[] elements) {
        for (int i = 0; i < elements.length; i++) {
            vh.setOpaque(array, i, elements[i]);
        }
        for (int i = 0; i < elements.length; i++) {
            Object v = (Object) vh.getOpaque(array, i);
            assertEquals(elements[i], v);
        }
    }

    // VarHandle::setRelease
    void setRelease(VarHandle vh, Object[] array, Object[] elements) {
        for (int i = 0; i < elements.length; i++) {
            vh.setRelease(array, i, elements[i]);
        }
        for (int i = 0; i < elements.length; i++) {
            Object v = (Object) vh.getAcquire(array, i);
            assertEquals(elements[i], v);
        }
    }

    void getAndSet(VarHandle vh, Object[] array, Object[] elements) {
        for (int i = 0; i < elements.length; i++) {
            Object o = vh.getAndSet(array, i, elements[i]);
        }
        for (int i = 0; i < elements.length; i++) {
            Object v = (Object) vh.get(array, i);
            assertEquals(elements[i], v);
        }
    }

    // sanity CAS test
    // see test/jdk/java/lang/invoke/VarHandles tests
    void compareAndSet(VarHandle vh, Object[] array, Object[] elements) {
        // initialize to some values
        for (int i = 0; i < elements.length; i++) {
            vh.set(array, i, elements[i]);
        }
        // shift to the right element
        for (int i = 0; i < elements.length; i++) {
            Object v = elements[i + 1 < elements.length ? i + 1 : 0];
            boolean cas = vh.compareAndSet(array, i, elements[i], v);
            if (!cas)
                System.out.format("cas = %s array[%d] = %s vs old = %s new = %s%n", cas, i, array[i], elements[i], v);
            assertTrue(cas);
        }
    }

    void compareAndExchange(VarHandle vh, Object[] array, Object[] elements) {
        // initialize to some values
        for (int i = 0; i < elements.length; i++) {
            vh.set(array, i, elements[i]);
        }
        // shift to the right element
        for (int i = 0; i < elements.length; i++) {
            Object v = elements[i + 1 < elements.length ? i + 1 : 0];
            assertEquals(elements[i], vh.compareAndExchange(array, i, elements[i], v));
        }
    }
}
