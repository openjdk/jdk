/*
 * Copyright (c) 2002, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4726380 8037097
 * @summary Check that different sorts give equivalent results.
 * @key randomness
 * @library /test/lib
 * @run junit Correct
 */

import java.util.*;

import jdk.test.lib.valueclass.AsValueClass;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Correct {

    static final Random rnd = new Random();
    static final int ITERATIONS = 1000;
    static final int TEST_SIZE = 1000;

    @AsValueClass
    record Point(int x, int y) implements Comparable<Point> {
        public int compareTo(Point o) {
            int c = Integer.compare(x, o.x);
            return c != 0 ? c : Integer.compare(y, o.y);
        }
    }

    @Test
    public void testDefaultSortPoint() {
        for (int i=0; i<ITERATIONS; i++) {
            int size = rnd.nextInt(TEST_SIZE) + 1;
            Point[] array1 = getPointArray(size);
            Point[] array2 = Arrays.copyOf(array1, array1.length);
            Arrays.sort(array1, array1.length/3, array1.length/2);
            stupidSort(array2, array2.length/3, array2.length/2);
            Assertions.assertArrayEquals(array2, array1, "Arrays did not match. size=" + size);
        }
    }

    @Test
    public void testDefaultSort() {
        for (int i=0; i<ITERATIONS; i++) {
            int size = rnd.nextInt(TEST_SIZE) + 1;
            Comparable[] array1 = getIntegerArray(size);
            Comparable[] array2 = Arrays.copyOf(array1, array1.length);
            Arrays.sort(array1, array1.length/3, array1.length/2);
            stupidSort(array2, array2.length/3, array2.length/2);
            Assertions.assertArrayEquals(array2, array1, "Arrays did not match. size=" + size);
        }
    }

    @ParameterizedTest
    @MethodSource("pointComparators")
    public void testComparatorSortPoint(Comparator<Point> comparator) {
        for (int i=0; i<ITERATIONS; i++) {
            int size = rnd.nextInt(TEST_SIZE) + 1;
            Point[] array1 = getPointArray(size);
            Point[] array2 = Arrays.copyOf(array1, array1.length);
            Arrays.sort(array1, array1.length/3, array1.length/2, comparator);
            stupidSort(array2, array2.length/3, array2.length/2, comparator);
            Assertions.assertArrayEquals(array2, array1, "Arrays did not match. size=" + size);
        }
    }

    static Point[] getPointArray(int size) {
        Point[] blah = new Point[size];
        for (int x=0; x<size; x++) {
            blah[x] = new Point(rnd.nextInt(), rnd.nextInt());
        }
        return blah;
    }

    @ParameterizedTest
    @MethodSource("comparators")
    public void testComparatorSort(Comparator<Integer> comparator) {
        for (int i=0; i<ITERATIONS; i++) {
            int size = rnd.nextInt(TEST_SIZE) + 1;
            Integer[] array1 = getIntegerArray(size);
            Integer[] array2 = Arrays.copyOf(array1, array1.length);
            Arrays.sort(array1, array1.length/3, array1.length/2, comparator);
            stupidSort((Object[])array2, array2.length/3, array2.length/2, comparator);
            Assertions.assertArrayEquals(array2, array1, "Arrays did not match. size=" + size);
        }
    }

    static Integer[] getIntegerArray(int size) {
        Integer[] blah = new Integer[size];
        for (int x=0; x<size; x++) {
            blah[x] = new Integer(rnd.nextInt());
        }
        return blah;
    }

    static void stupidSort(Comparable[] a1, int from, int to) {
        if (from > to - 1)
            return;

        for (int x=from; x<to; x++) {
            Comparable lowest = a1[x];
            int lowestIndex = x;
            for (int y=x + 1; y<to; y++) {
                if (a1[y].compareTo(lowest) < 0) {
                    lowest = a1[y];
                    lowestIndex = y;
                }
            }
            if (lowestIndex != x) {
                swap(a1, x, lowestIndex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void stupidSort(Object[] a1, int from, int to, Comparator comparator) {
        if (from > to - 1)
            return;

        for (int x=from; x<to; x++) {
            Object lowest = a1[x];
            int lowestIndex = x;
            for (int y=x + 1; y<to; y++) {
                if (comparator.compare(a1[y], lowest) < 0) {
                    lowest = a1[y];
                    lowestIndex = y;
                }
            }
            if (lowestIndex != x) {
                swap(a1, x, lowestIndex);
            }
        }
    }

    static <T> void swap(T[] x, int a, int b) {
        T t = x[a];
        x[a] = x[b];
        x[b] = t;
    }

    public static Iterator<Object[]> pointComparators() {
        Object[][] comparators = new Object[][] {
            new Object[] { Comparator.naturalOrder() },
            new Object[] { Comparator.<Point>naturalOrder().reversed() },
            new Object[] { Comparator.comparingInt(Point::x).thenComparingInt(Point::y) },
            new Object[] { Comparator.comparingInt(Point::y).thenComparingInt(Point::x) }
        };

        return Arrays.asList(comparators).iterator();
    }

    public static Iterator<Object[]> comparators() {
        Object[][] comparators = new Object[][] {
            new Object[] { Comparator.naturalOrder() },
            new Object[] { Comparator.<Integer>naturalOrder().reversed() },
            new Object[] { STANDARD_ORDER },
            new Object[] { STANDARD_ORDER.reversed() },
            new Object[] { REVERSE_ORDER },
            new Object[] { REVERSE_ORDER.reversed() },
            new Object[] { Comparator.comparingInt(Integer::intValue) }
        };

        return Arrays.asList(comparators).iterator();
    }

    private static final Comparator<Integer> STANDARD_ORDER = new Comparator<Integer>() {
        public int compare(Integer o1, Integer o2) {
            return  o1.compareTo(o2);
        }
    };

    private static final Comparator<Integer> REVERSE_ORDER = new Comparator<Integer>() {
        public int compare(Integer o1, Integer o2) {
            return - o1.compareTo(o2);
        }
    };
}
