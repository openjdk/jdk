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
 *
 */

import java.util.Arrays;
import jdk.internal.value.ValueClass;

public class ArchivedArrayLayoutsApp {

    public static class ArchivedData {
        Point[] pointArray;
        Point[] nullRestrictedArray;
        Point[] nullableAtomicArray;
    }

    public static value class Point implements Comparable<Point> {
        int x, y;

        public int compareTo(Point p) {
            return (x - p.x) - (y - p.y);
        }

        public String toString() {
            return "(" + x + ", " + y + ")";
        }

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static ArchivedData archivedObjects;
    static boolean restored;
    static {
        if (archivedObjects == null) {
            restored = false;
            System.out.println("Not archived");
            archivedObjects = new ArchivedData();

            // Point cannot be flattened and so will create a RefArrayKlass
            archivedObjects.pointArray = new Point[3];
            archivedObjects.pointArray[0] = new Point(0, 1);
            archivedObjects.pointArray[1] = new Point(1, 0);
            archivedObjects.pointArray[2] = new Point(1, 1);


            // An array of null-restricted Point can be flattened
            archivedObjects.nullRestrictedArray = (Point[])ValueClass.newNullRestrictedAtomicArray(Point.class, 3, new Point(0, 0));
            archivedObjects.nullRestrictedArray[0] = new Point(0, 1);
            archivedObjects.nullRestrictedArray[1] = new Point(1, 0);
            archivedObjects.nullRestrictedArray[2] = new Point(1, 1);

            // A nullable array of Point cannot be flattened so it will a RefArrayKlass with a different layout
            archivedObjects.nullableAtomicArray = (Point[])ValueClass.newNullableAtomicArray(Point.class, 3);
            archivedObjects.nullableAtomicArray[0] = new Point(0, 1);
            archivedObjects.nullableAtomicArray[1] = new Point(1, 0);
            archivedObjects.nullableAtomicArray[2] = new Point(1, 1);
        } else {
            restored = true;
            System.out.println("Initialized from CDS");
        }
    }


    public static void checkFlat(Point[] p, boolean shouldBe) throws Exception {
        boolean isFlat = ValueClass.isFlatArray(p);
        if (isFlat && !shouldBe) {
            throw new RuntimeException("Should not be flat");
        }
        if (!isFlat && shouldBe) {
            throw new RuntimeException("Should be flat");
        }
    }

    public static void checkArray(Point[] p0, Point[] p1) {
        if (Arrays.compare(p0, p1) != 0) {
            for (Point i : p0) {
                System.out.println(i);
            }
            System.out.println("vs");
            for (Point i : p1) {
                System.out.println(i);
            }
            System.out.println();
            throw new RuntimeException("Array not restored correctly");
        }
    }

    public static void main(String[] args) throws Exception {

        checkFlat(archivedObjects.pointArray, false);
        checkFlat(archivedObjects.nullRestrictedArray, true);
        checkFlat(archivedObjects.nullableAtomicArray, false);

        if (restored) {
            Point[] runtimeArray = new Point[3];
            runtimeArray[0] = new Point(0, 1);
            runtimeArray[1] = new Point(1, 0);
            runtimeArray[2] = new Point(1, 2);

            checkArray(archivedObjects.pointArray, runtimeArray);
            checkArray(archivedObjects.nullRestrictedArray, runtimeArray);
            checkArray(archivedObjects.nullableAtomicArray, runtimeArray);
        }

        System.out.println("PASSED");
    }
}
