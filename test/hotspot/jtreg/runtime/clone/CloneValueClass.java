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

/*
 * @test
 * @bug 8384108
 * @summary Verify Object.clone with value class fields (migrated wrapper classes
 *          and custom value classes) and arrays of value class elements
 * @library /test/lib
 * @modules java.base/java.lang:open
 * @run main CloneValueClass
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jdk.test.lib.Asserts;
import jdk.test.lib.valueclass.AsValueClass;

public class CloneValueClass {

    @AsValueClass
    static class Point {
        int x;
        int y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    @AsValueClass
    static class MixedValue {
        int id;
        String name;
        MixedValue(int id, String name) { this.id = id; this.name = name; }
    }

    // Identity class holding migrated wrapper fields, custom value class fields,
    // arrays of value class elements and a primitive.
    static class Holder implements Cloneable {
        Integer intVal;
        Long longVal;
        Double doubleVal;
        Short shortVal;
        Point point;
        MixedValue mixed;
        Point[] pointArray;
        MixedValue[] mixedArray;
        int primitive;

        Holder(Integer i, Long l, Double d, Short s,
               Point p, MixedValue m, Point[] pa, MixedValue[] ma, int prim) {
            intVal = i;
            longVal = l;
            doubleVal = d;
            shortVal = s;
            point = p;
            mixed = m;
            pointArray = pa;
            mixedArray = ma;
            primitive = prim;
        }

        public Holder clone() throws CloneNotSupportedException {
            return (Holder) super.clone();
        }
    }

    // Value fields declared at two levels of an inheritance chain.
    static class Parent implements Cloneable {
        Integer intVal;
        Long longVal;

        Parent(Integer i, Long l) {
            intVal = i;
            longVal = l;
        }

        public Parent clone() throws CloneNotSupportedException {
            return (Parent) super.clone();
        }
    }

    static class Child extends Parent {
        Float floatVal;
        Byte byteVal;

        Child(Integer i, Long l, Float f, Byte b) {
            super(i, l);
            floatVal = f;
            byteVal = b;
        }

        public Child clone() throws CloneNotSupportedException {
            return (Child) super.clone();
        }
    }

    private static Holder newHolder() {
        return new Holder(Integer.valueOf(42), Long.valueOf(100L),
                          Double.valueOf(3.14), Short.valueOf((short) 7),
                          new Point(1, 2), new MixedValue(10, "hello"),
                          new Point[] { new Point(5, 6), new Point(7, 8) },
                          new MixedValue[] { new MixedValue(20, "a"), new MixedValue(30, "b") },
                          9);
    }

    public static void main(String[] args) throws Exception {
        testValueObjectCloneThrows();
        testCloneCopiesFields();
        testCloneIndependence();
        testNullFields();
        testInheritedValueFields();
        testShallowArraySharing();
        testValueArrayClone();
        testMixedArrayClone();
        testCloneSurvivesGC();
    }

    // Cloning a value object directly should throw CloneNotSupportedException.
    static void testValueObjectCloneThrows() throws Exception {
        Point p = new Point(1, 2);
        Method m = Object.class.getDeclaredMethod("clone");
        m.setAccessible(true);
        try {
            m.invoke(p);
            throw new RuntimeException("Expected CloneNotSupportedException for value object");
        } catch (InvocationTargetException e) {
            Asserts.assertTrue(e.getCause() instanceof CloneNotSupportedException,
                    "Expected CloneNotSupportedException, got " + e.getCause());
        }
    }

    // Every wrapper field, custom value field, array and primitive is copied.
    static void testCloneCopiesFields() throws Exception {
        Holder orig = newHolder();
        Holder copy = orig.clone();

        Asserts.assertTrue(orig != copy, "clone returned the same object");
        Asserts.assertEQ(orig.intVal, copy.intVal);
        Asserts.assertEQ(orig.longVal, copy.longVal);
        Asserts.assertEQ(orig.doubleVal, copy.doubleVal);
        Asserts.assertEQ(orig.shortVal, copy.shortVal);
        Asserts.assertEQ(orig.point.x, copy.point.x);
        Asserts.assertEQ(orig.point.y, copy.point.y);
        Asserts.assertEQ(orig.mixed.id, copy.mixed.id);
        Asserts.assertEQ(orig.mixed.name, copy.mixed.name);
        Asserts.assertEQ(copy.pointArray.length, 2);
        Asserts.assertEQ(copy.pointArray[0].x, 5);
        Asserts.assertEQ(copy.mixedArray.length, 2);
        Asserts.assertEQ(copy.mixedArray[1].name, "b");
        Asserts.assertEQ(orig.primitive, copy.primitive);
    }

    // Replacing fields of the original does not affect the clone.
    static void testCloneIndependence() throws Exception {
        Holder orig = newHolder();
        Holder copy = orig.clone();

        orig.intVal = Integer.valueOf(99);
        orig.longVal = Long.valueOf(200L);
        orig.doubleVal = Double.valueOf(2.71);
        orig.shortVal = Short.valueOf((short) 0);
        orig.point = new Point(99, 99);
        orig.mixed = new MixedValue(0, "changed");
        orig.primitive = 0;

        Asserts.assertEQ(copy.intVal, Integer.valueOf(42));
        Asserts.assertEQ(copy.longVal, Long.valueOf(100L));
        Asserts.assertEQ(copy.doubleVal, Double.valueOf(3.14));
        Asserts.assertEQ(copy.shortVal, Short.valueOf((short) 7));
        Asserts.assertEQ(copy.point.x, 1);
        Asserts.assertEQ(copy.point.y, 2);
        Asserts.assertEQ(copy.mixed.id, 10);
        Asserts.assertEQ(copy.mixed.name, "hello");
        Asserts.assertEQ(copy.primitive, 9);
    }

    // null value fields and arrays are cloned as null.
    static void testNullFields() throws Exception {
        Holder copy = new Holder(null, null, null, null, null, null, null, null, 0).clone();

        Asserts.assertNull(copy.intVal);
        Asserts.assertNull(copy.longVal);
        Asserts.assertNull(copy.doubleVal);
        Asserts.assertNull(copy.shortVal);
        Asserts.assertNull(copy.point);
        Asserts.assertNull(copy.mixed);
        Asserts.assertNull(copy.pointArray);
        Asserts.assertNull(copy.mixedArray);
    }

    // Value fields declared in a superclass and in a subclass are both cloned.
    static void testInheritedValueFields() throws Exception {
        Child orig = new Child(Integer.valueOf(1), Long.valueOf(2L),
                               Float.valueOf(3.0f), Byte.valueOf((byte) 4));
        Child copy = orig.clone();

        Asserts.assertEQ(orig.intVal, copy.intVal);
        Asserts.assertEQ(orig.longVal, copy.longVal);
        Asserts.assertEQ(orig.floatVal, copy.floatVal);
        Asserts.assertEQ(orig.byteVal, copy.byteVal);

        orig.intVal = Integer.valueOf(99);
        orig.floatVal = Float.valueOf(99.0f);

        Asserts.assertEQ(copy.intVal, Integer.valueOf(1));
        Asserts.assertEQ(copy.floatVal, Float.valueOf(3.0f));
    }

    // clone() is shallow, so array fields are shared with the original.
    static void testShallowArraySharing() throws Exception {
        Holder orig = newHolder();
        Holder copy = orig.clone();

        Asserts.assertTrue(orig.pointArray == copy.pointArray,
                           "shallow clone should share the pointArray reference");
        Asserts.assertTrue(orig.mixedArray == copy.mixedArray,
                           "shallow clone should share the mixedArray reference");

        orig.pointArray = new Point[] { new Point(99, 99) };

        Asserts.assertEQ(copy.pointArray.length, 2);
        Asserts.assertEQ(copy.pointArray[0].x, 5);
    }

    // Cloning arrays of value class elements gives independent arrays.
    static void testValueArrayClone() {
        Point[] points = { new Point(1, 2), new Point(3, 4), new Point(5, 6) };
        Point[] pointsCopy = points.clone();

        Asserts.assertTrue(points != pointsCopy, "array clone returned the same array");
        for (int i = 0; i < points.length; i++) {
            Asserts.assertEQ(points[i].x, pointsCopy[i].x);
            Asserts.assertEQ(points[i].y, pointsCopy[i].y);
        }
        points[0] = new Point(99, 99);
        Asserts.assertEQ(pointsCopy[0].x, 1);

        assertArrayIndependent(new Integer[] { 1, 2, 3, 4, 5 }, "Integer array");
        assertArrayIndependent(new Long[] { 10L, 20L, 30L }, "Long array");
        assertArrayIndependent(new Double[] { 1.1, 2.2, 3.3 }, "Double array");
    }

    static void assertArrayIndependent(Object[] orig, String name) {
        Object[] copy = orig.clone();
        Asserts.assertTrue(orig != copy, name + " clone returned the same array");
        Asserts.assertEQ(orig.length, copy.length, name + " length mismatch");
        for (int i = 0; i < orig.length; i++) {
            Asserts.assertEQ(orig[i], copy[i], name + " element " + i + " mismatch");
        }
        Object saved = copy[0];
        orig[0] = null;
        Asserts.assertTrue(copy[0] == saved, name + " clone not independent");
    }

    // Clone an array containing a mix of value objects and identity objects.
    static void testMixedArrayClone() {
        Object identity = new Object();
        Object[] orig = { Integer.valueOf(1), Long.valueOf(2L), "three", identity, null };
        Object[] copy = orig.clone();

        Asserts.assertTrue(orig != copy, "mixed array clone returned the same array");
        Asserts.assertEQ(orig.length, copy.length);
        Asserts.assertEQ(copy[0], Integer.valueOf(1));
        Asserts.assertEQ(copy[1], Long.valueOf(2L));
        Asserts.assertEQ(copy[2], "three");
        Asserts.assertTrue(copy[3] == identity, "identity element should be shared");
        Asserts.assertNull(copy[4]);
    }

    // Clones holding value fields stay intact across a garbage collection.
    static void testCloneSurvivesGC() throws Exception {
        int count = 1000;
        Holder[] copies = new Holder[count];
        for (int i = 0; i < count; i++) {
            copies[i] = new Holder(Integer.valueOf(i), Long.valueOf(i * 10L),
                                   Double.valueOf(i * 1.1), Short.valueOf((short) (i % 100)),
                                   new Point(i, i + 1), new MixedValue(i, "n" + i),
                                   new Point[] { new Point(i, i) },
                                   new MixedValue[] { new MixedValue(i, "m" + i) },
                                   i).clone();
        }

        System.gc();

        for (int i = 0; i < count; i++) {
            Asserts.assertEQ(copies[i].intVal, Integer.valueOf(i));
            Asserts.assertEQ(copies[i].longVal, Long.valueOf(i * 10L));
            Asserts.assertEQ(copies[i].doubleVal, Double.valueOf(i * 1.1));
            Asserts.assertEQ(copies[i].shortVal, Short.valueOf((short) (i % 100)));
            Asserts.assertEQ(copies[i].point.x, i);
            Asserts.assertEQ(copies[i].mixed.name, "n" + i);
            Asserts.assertEQ(copies[i].primitive, i);
        }
    }
}
