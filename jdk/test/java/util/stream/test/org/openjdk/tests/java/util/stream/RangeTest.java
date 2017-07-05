/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.tests.java.util.stream;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;
import java.util.stream.TestData;

import org.testng.annotations.Test;

/**
 * Primitive range tests
 *
 * @author Brian Goetz
 */
@Test
public class RangeTest extends OpTestCase {

    public void testInfiniteRangeFindFirst() {
        Integer first = Stream.iterate(0, i -> i + 1).filter(i -> i > 10000).findFirst().get();
        assertEquals(first, Stream.iterate(0, i -> i + 1).parallel().filter(i -> i > 10000).findFirst().get());

        // Limit is required to transform the infinite stream to a finite stream
        // since the exercising requires a finite stream
        withData(TestData.Factory.ofSupplier(
                "", () -> Stream.iterate(0, i -> i + 1).filter(i -> i > 10000).limit(20000))).
                terminal(s->s.findFirst()).expectedResult(Optional.of(10001)).exercise();
    }

    //

    public void testIntRangeErrors() {
        for (int start : Arrays.asList(1, 10, -1, -10)) {
            for (int end : Arrays.asList(1, 10, -1, -10)) {
                for (int step : Arrays.asList(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE)) {
                    if (step > 0)
                        executeAndNoCatch(() -> IntStream.range(start, end, step));
                    else
                        executeAndCatch(() -> IntStream.range(start, end, step));
                }
            }
        }
    }

    public void testIntRange() {
        // Without step
        for (int start : Arrays.asList(1, 10, -1, -10)) {
            for (int end : Arrays.asList(1, 10, -1, -10)) {
                int step = 1;
                int size = (start < end) ? end - start : 0;
                int[] exp = new int[size];
                if (start < end) {
                    for (int i = start, p = 0; i < end; i++, p++) {
                        exp[p] = i;
                    }
                }

                int[] inc = IntStream.range(start, end).toArray();
                assertEquals(inc.length, size);
                assertTrue(Arrays.equals(exp, inc));

                withData(intRangeData(start, end, step)).stream(s -> s).
                        expectedResult(exp).exercise();
            }
        }

        // With step
        for (int start : Arrays.asList(1, 10, -1, -10)) {
            for (int end : Arrays.asList(1, 10, -1, -10)) {
                for (int step : Arrays.asList(1, -1, -2, 2)) {
                    if (step > 0) {
                        int d = end - start;
                        int size = (start < end) ? (d / step) + ((d % step == 0) ? 0 : 1) : 0;
                        int[] exp = new int[size];
                        if (start < end) {
                            for (int i = start, p = 0; i < end; i += step, p++) {
                                exp[p] = i;
                            }
                        }

                        int[] inc = IntStream.range(start, end, step).toArray();
                        assertEquals(inc.length, size);
                        assertTrue(Arrays.equals(exp, inc));

                        withData(intRangeData(start, end, step)).stream(s -> s).
                                expectedResult(exp).exercise();
                    }
                }
            }
        }
    }

    TestData.OfInt intRangeData(int start, int end, int step) {
        return TestData.Factory.ofIntSupplier("int range", () -> IntStream.range(start, end, step));
    }

    public void tesIntRangeReduce() {
        withData(intRangeData(0, 10000, 1)).
                terminal(s -> s.reduce(0, Integer::sum)).exercise();
    }

    public void testIntInfiniteRangeLimit() {
        withData(TestData.Factory.ofIntSupplier(
                "int range", () -> IntStream.iterate(0, i -> i + 1).limit(10000))).
                terminal(s -> s.reduce(0, Integer::sum)).exercise();
    }

    public void testIntInfiniteRangeFindFirst() {
        int first = IntStream.iterate(0, i -> i + 1).filter(i -> i > 10000).findFirst().getAsInt();
        assertEquals(first, IntStream.iterate(0, i -> i + 1).parallel().filter(i -> i > 10000).findFirst().getAsInt());
    }

    //

    public void testLongRangeErrors() {
        for (long start : Arrays.asList(1, 10, -1, -10)) {
            for (long end : Arrays.asList(1, 10, -1, -10)) {
                for (long step : Arrays.asList(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE)) {
                    if (step > 0)
                        executeAndNoCatch(() -> LongStream.range(start, end, step));
                    else
                        executeAndCatch(() -> LongStream.range(start, end, step));
                }
            }
        }
    }

    public void testLongRange() {
        // Without step
        for (long start : Arrays.asList(1, 1000, -1, -1000)) {
            for (long end : Arrays.asList(1, 1000, -1, -1000)) {
                long step = 1;
                long size = start < end ? end - start : 0;
                long[] exp = new long[(int) size];
                if (start < end) {
                    for (long i = start, p = 0; i < end; i++, p++) {
                        exp[(int) p] = i;
                    }
                }

                long[] inc = LongStream.range(start, end).toArray();
                assertEquals(inc.length, size);
                assertTrue(Arrays.equals(exp, inc));

                withData(longRangeData(start, end, step)).stream(s -> s).
                        expectedResult(exp).exercise();
            }
        }

        // With step
        for (long start : Arrays.asList(1, 1000, -1, -1000)) {
            for (long end : Arrays.asList(1, 1000, -1, -1000)) {
                for (long step : Arrays.asList(1, -1, -2, 2)) {
                    if (step > 0) {

                        long d = end - start;
                        long size = start < end ? (d / step) + ((d % step == 0) ? 0 : 1) : 0;
                        long[] exp = new long[(int) size];
                        if (start < end) {
                            for (long i = start, p = 0; i < end; i += step, p++) {
                                exp[(int) p] = i;
                            }
                        }

                        long[] inc = LongStream.range(start, end, step).toArray();
                        assertEquals(inc.length, size);
                        assertTrue(Arrays.equals(exp, inc));

                        withData(longRangeData(start, end, step)).stream(s -> s).
                                expectedResult(exp).exercise();
                    }
                }
            }
        }
    }

    TestData.OfLong longRangeData(long start, long end, long step) {
        return TestData.Factory.ofLongSupplier("long range", () -> LongStream.range(start, end, step));
    }

    public void testLongRangeReduce() {
        withData(longRangeData(0, 10000, 1)).
                terminal(s -> s.reduce(0, Long::sum)).exercise();
    }

    public void testLongInfiniteRangeLimit() {
        withData(TestData.Factory.ofLongSupplier(
                "long range", () -> LongStream.iterate(0, i -> i + 1).limit(10000))).
                terminal(s -> s.reduce(0, Long::sum)).exercise();
    }

    public void testLongInfiniteRangeFindFirst() {
        long first = LongStream.iterate(0, i -> i + 1).filter(i -> i > 10000).findFirst().getAsLong();
        assertEquals(first, LongStream.iterate(0, i -> i + 1).parallel().filter(i -> i > 10000).findFirst().getAsLong());
    }

    //

    public void testDoubleRangeErrors() {
        for (double start : Arrays.asList(1, 10, -1, -10)) {
            for (double end : Arrays.asList(1, 10, -1, -10)) {
                for (double step : Arrays.asList(0.0, +0.0, -0.0, 1.0, -1.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                    try {
                        if (step > 0)
                            executeAndNoCatch(() -> DoubleStream.range(start, end, step));
                        else
                            executeAndCatch(() -> DoubleStream.range(start, end, step));
                    }
                    catch (AssertionError e) {
                        System.out.printf("start=%f, end=%f, step=%f%n", start, end, step);
                        throw e;
                    }
                }
            }
        }

        for (double start : Arrays.asList(0.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            for (double end : Arrays.asList(0.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
                for (double step : Arrays.asList(1.0, -1.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
                    try {
                        if ((start == 0.0 && end == 0.0 && step > 0)
                            || (start > end && step > 0)) {
                            executeAndNoCatch(() -> DoubleStream.range(start, end, step));
                        }
                        else {
                            executeAndCatch(() -> DoubleStream.range(start, end, step));
                        }
                    }
                    catch (AssertionError e) {
                        System.out.printf("start=%f, end=%f, step=%f%n", start, end, step);
                        throw e;
                    }
                }
            }
        }
    }

    public void testDoubleRange() {
        // Without step
        for (double start : Arrays.asList(1, 1000, -1, -1000)) {
            for (double end : Arrays.asList(1, 1000, -1, -1000)) {
                double step = 1;
                double size = start < end ? Math.ceil((end - start) / step) : 0;
                double[] exp = new double[(int) size];
                for (long i = 0; i < size; i++) {
                    exp[(int) i] = start + i * step;
                }

                double[] inc = DoubleStream.range(start, end).toArray();
                assertEquals(inc.length, (int) size);
                assertTrue(Arrays.equals(exp, inc));

                withData(doubleRangeData(start, end, step)).stream(s -> s).
                        expectedResult(exp).exercise();
            }
        }

        // With step
        for (double start : Arrays.asList(1, 1000, -1, -1000)) {
            for (double end : Arrays.asList(1, 1000, -1, -1000)) {
                for (double step : Arrays.asList(1, -1, -2, 2)) {
                    if (step <= 0)
                        continue;
                    double size = start < end ? Math.ceil((end - start) / step) : 0;
                    double[] exp = new double[(int) size];
                    for (long i = 0; i < size; i++) {
                        exp[(int) i] = start + i * step;
                    }

                    double[] inc = DoubleStream.range(start, end, step).toArray();
                    assertEquals(inc.length, (int) size);
                    assertTrue(Arrays.equals(exp, inc));

                    withData(doubleRangeData(start, end, step)).stream(s -> s).
                            expectedResult(exp).exercise();
                }
            }
        }

        // With non-integer values
        for (double step : Arrays.asList(Math.PI / 1000.0, Math.PI / 1000.0, Math.PI / 10000.0)) {
            double start = -Math.PI;
            double end = Math.PI;
            double size = start < end ? Math.ceil((end - start) / step) : 0;
            double[] exp = new double[(int) size];
            for (long i = 0; i < size; i++) {
                exp[(int) i] = start + i * step;
            }

            withData(doubleRangeData(start, end, step)).stream(s -> s).
                    expectedResult(exp).exercise();
        }
    }

    TestData.OfDouble doubleRangeData(double start, double end, double step) {
        return TestData.Factory.ofDoubleSupplier("double range", () -> DoubleStream.range(start, end, step));
    }

    public void tesDoubleRangeReduce() {
        withData(doubleRangeData(0, 10000, 1)).
                terminal(s -> s.reduce(0, Double::sum)).exercise();
    }

    public void testDoubleInfiniteRangeLimit() {
        withData(TestData.Factory.ofDoubleSupplier(
                "double range", () -> DoubleStream.iterate(0, i -> i + 1).limit(10000))).
                terminal(s -> s.reduce(0, Double::sum)).exercise();
    }

    public void testDoubleInfiniteRangeFindFirst() {
        double first = DoubleStream.iterate(0, i -> i + 1).filter(i -> i > 10000).findFirst().getAsDouble();
        assertEquals(first, DoubleStream.iterate(0, i -> i + 1).parallel().filter(i -> i > 10000).findFirst().getAsDouble());
    }

    //

    private static int[] reverse(int[] a) {
        int[] b = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            b[b.length - i - 1] = a[i];
        }
        return b;
    }

    private static long[] reverse(long[] a) {
        long[] b = new long[a.length];
        for (int i = 0; i < a.length; i++) {
            b[b.length - i - 1] = a[i];
        }
        return b;
    }

    private static double[] reverse(double[] a) {
        double[] b = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            b[b.length - i - 1] = a[i];
        }
        return b;
    }

    private void executeAndCatch(Runnable r) {
        executeAndCatch(IllegalArgumentException.class, r);
    }

    private void executeAndNoCatch(Runnable r) {
        executeAndCatch(null, r);
    }

    private void executeAndCatch(Class<? extends Exception> expected, Runnable r) {
        Exception caught = null;
        try {
            r.run();
        }
        catch (Exception e) {
            caught = e;
        }

        if (expected != null) {
            assertNotNull(caught,
                          String.format("No Exception was thrown, expected an Exception of %s to be thrown",
                                        expected.getName()));
            assertTrue(expected.isInstance(caught),
                       String.format("Exception thrown %s not an instance of %s",
                                     caught.getClass().getName(), expected.getName()));
        }
        else {
            if (caught != null) {
                assertNull(caught,
                           String.format("Unexpected exception of %s was thrown",
                                         caught.getClass().getName()));
            }
        }
    }

}
