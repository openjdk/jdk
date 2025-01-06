/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test functionality of the Generators library.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @build MockRandomness
 * @run driver testlibrary_tests.generators.tests.TestGenerators
 */

package testlibrary_tests.generators.tests;

import compiler.lib.generators.EmptyGeneratorException;
import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import jdk.test.lib.Asserts;

import java.util.*;

import static compiler.lib.generators.Generators.G;


public class TestGenerators {
    static MockRandomness mockRandomness = new MockRandomness();
    static Generators mockGS = new Generators(mockRandomness);

    public static void main(String[] args) {
        testEmptyGenerators();
        testUniformInts();
        testUniformLongs();
        testAnyBits();
        testUniformFloat();
        testUniformDouble();
        testSingle();
        testMixed();
        testRandomElement();
        specialInt();
        specialLong();
        testSpecialFloat();
        testSpecialDouble();
        testFill();
    }

    static void testMixed() {
        mockRandomness
                .checkEmpty()
                .enqueueInteger(0, 10, 7)
                .enqueueInteger(0, 10, 5)
                .enqueueInteger(0, 31, 4)
                .enqueueInteger(0, 10, 1)
                .enqueueInteger(0, 31, 18);
        var g = mockGS.mixed(mockGS.uniformInts(0, 30), mockGS.single(-1), 7, 3);
        Asserts.assertEQ(g.next(), -1);
        Asserts.assertEQ(g.next(), 4);
        Asserts.assertEQ(g.next(), 18);
    }

    static void testSpecialFloat() {
        mockRandomness
                .checkEmpty()
                .enqueueInteger(0, 10, 3)
                .enqueueDouble(0, 1, 3.4d)
                .enqueueInteger(0, 10, 6)
                .enqueueInteger(0, 9, 3);
        var g = mockGS.mixedWithSpecialDoubles(mockGS.uniformDoubles(), 5, 5);
        Asserts.assertEQ(g.next(), 3.4d);
        Asserts.assertEQ(g.next(), Double.POSITIVE_INFINITY);
    }

    static void testSpecialDouble() {
        mockRandomness
                .checkEmpty()
                .enqueueInteger(0, 10, 3)
                .enqueueFloat(0, 1, 3.4f)
                .enqueueInteger(0, 10, 6)
                .enqueueInteger(0, 9, 3);
        var g = mockGS.mixedWithSpecialFloats(mockGS.uniformFloats(), 5, 5);
        Asserts.assertEQ(g.next(), 3.4f);
        Asserts.assertEQ(g.next(), Float.POSITIVE_INFINITY);
    }

    static void testUniformFloat() {
        mockRandomness.checkEmpty().enqueueFloat(-1, 10, 3.14159f);
        Asserts.assertEQ(mockGS.uniformFloats(-1, 10).next(), 3.14159f);
        mockRandomness.checkEmpty().enqueueFloat(0, 1, 3.14159f);
        Asserts.assertEQ(mockGS.uniformFloats(0, 1).next(), 3.14159f);

        float lo = 0.13f, hi = 13.532f;
        var gb = G.uniformFloats(lo, hi);
        for (int i = 0; i < 10_000; i++) {
            float x = gb.next();
            Asserts.assertGreaterThanOrEqual(x, lo);
            Asserts.assertLessThan(x, hi);
        }
    }

    static void testUniformDouble() {
        mockRandomness.checkEmpty().enqueueDouble(-1, 10, 3.14159d);
        Asserts.assertEQ(mockGS.uniformDoubles(-1, 10).next(), 3.14159d);
        mockRandomness.checkEmpty().enqueueDouble(0, 1, 3.14159d);
        Asserts.assertEQ(mockGS.uniformDoubles(0, 1).next(), 3.14159d);

        double lo = 0.13, hi = 13.532;
        var gb = G.uniformDoubles(lo, hi);
        for (int i = 0; i < 10_000; i++) {
            double x = gb.next();
            Asserts.assertGreaterThanOrEqual(x, lo);
            Asserts.assertLessThan(x, hi);
        }
    }

    static void testRandomElement() {
        mockRandomness.checkEmpty().enqueueInteger(0, 3, 1).enqueueInteger(0, 3, 0);
        var g = mockGS.randomElement(List.of("a", "b", "c"));
        Asserts.assertEQ(g.next(), "b");
        Asserts.assertEQ(g.next(), "a");

        mockRandomness.checkEmpty().enqueueInteger(0, 8, 1).enqueueInteger(0, 8, 2);
        // The list below is intentionally not sorted and is equivalent to: 1, 4, 4, 8, 9, 10, 13, 18, 20
        // It contains 8 distinct values. Note that orderedRandomElement removes duplicates. Therefor the internal
        // value list is: 1, 4, 8, 9, 10, 13, 18, 20
        var g1 = mockGS.orderedRandomElement(List.of(10, 4, 1, 8, 9, 4, 20, 18, 13));
        Asserts.assertEQ(g1.next(), 4);
        Asserts.assertEQ(g1.next(), 8);

        mockRandomness.checkEmpty().enqueueInteger(0, 3, 1).enqueueInteger(0, 3, 2);
        // Ordered lists can also be restricted. Our new values are 9, 10, 13.
        var gr = g1.restricted(9, 13);
        Asserts.assertEQ(gr.next(), 10);
        Asserts.assertEQ(gr.next(), 13);

        mockRandomness.checkEmpty().enqueueInteger(0, 2, 1);
        var gs = mockGS.orderedRandomElement(List.of("Bob", "Alice", "Carol")).restricted("Al", "Bz");
        Asserts.assertEQ(gs.next(), "Bob");
    }

    static void specialInt() {
        mockRandomness.checkEmpty().enqueueInteger(0, 63, 1).enqueueInteger(0, 63, 32);
        var si = mockGS.specialInts(0);
        Asserts.assertEQ(si.next(), -(1 << 30));
        Asserts.assertEQ(si.next(), 1);

        mockRandomness.checkEmpty().enqueueInteger(0, 182, 1);
        var si1 = mockGS.specialInts(1);
        Asserts.assertEQ(si1.next(), -(1 << 31) + 1);
    }

    static void specialLong() {
        mockRandomness.checkEmpty().enqueueInteger(0, 127, 1).enqueueInteger(0, 127, 64);
        var si = mockGS.specialLongs(0);
        Asserts.assertEQ(si.next(), -(1L << 62));
        Asserts.assertEQ(si.next(), 1L);

        mockRandomness.checkEmpty().enqueueInteger(0, 374, 1);
        var si1 = mockGS.specialLongs(1);
        Asserts.assertEQ(si1.next(), -(1L << 63) + 1);
    }

    static void testSingle() {
        mockRandomness.checkEmpty();
        var g = mockGS.single(30);
        Asserts.assertEQ(g.next(), 30);
        Asserts.assertEQ(g.next(), 30);
        var gs = mockGS.single("hello");
        Asserts.assertEQ(gs.next(), "hello");
        Asserts.assertEQ(gs.next(), "hello");
    }

    static void testUniformInts() {
        mockRandomness.checkEmpty().enqueueInteger(0, 11, 1).enqueueInteger(0, 11, 4);
        var g0 = mockGS.uniformInts(0, 10);
        Asserts.assertEQ(g0.next(), 1);
        Asserts.assertEQ(g0.next(), 4);

        mockRandomness.checkEmpty().enqueueInteger(0, 1, 0).enqueueInteger(0, 1, 0);
        var g1 = mockGS.uniformInts(0, 0);
        Asserts.assertEQ(g1.next(), 0);
        Asserts.assertEQ(g1.next(), 0);

        mockRandomness.checkEmpty().enqueueInteger(-1, Integer.MAX_VALUE, 10);
        Asserts.assertEQ(mockGS.uniformInts(0, Integer.MAX_VALUE).next(), 11);

        mockRandomness.checkEmpty().enqueueInteger(Integer.MIN_VALUE, 13, -33);
        Asserts.assertEQ(mockGS.uniformInts(Integer.MIN_VALUE, 12).next(), -33);

        mockRandomness.checkEmpty().enqueueInteger(11);
        Asserts.assertEQ(mockGS.uniformInts(Integer.MIN_VALUE, Integer.MAX_VALUE).next(), 11);

        mockRandomness.checkEmpty().enqueueInteger(10, 29, 17);
        Asserts.assertEQ(mockGS.uniformInts(Integer.MIN_VALUE, Integer.MAX_VALUE).restricted(10, 28).next(), 17);

        mockRandomness.checkEmpty().enqueueInteger(19, 29, 17);
        Asserts.assertEQ(mockGS.uniformInts(Integer.MIN_VALUE, Integer.MAX_VALUE).restricted(10, 28).restricted(19, 33).next(), 17);

        mockRandomness.checkEmpty().enqueueInteger(144);
        Asserts.assertEQ(mockGS.uniformInts().next(), 144);

        mockRandomness.checkEmpty();

        int lo = -345555, hi = 11123;
        var gb = G.uniformInts(lo, hi);
        for (int i = 0; i < 10_000; i++) {
            int x = gb.next();
            Asserts.assertGreaterThanOrEqual(x, lo);
            Asserts.assertLessThanOrEqual(x, hi);
        }
    }

    static void testUniformLongs() {
        mockRandomness.checkEmpty().enqueueLong(0, 11, 1).enqueueLong(0, 11, 4);
        var g0 = mockGS.uniformLongs(0, 10);
        Asserts.assertEQ(g0.next(), 1L);
        Asserts.assertEQ(g0.next(), 4L);

        mockRandomness.checkEmpty().enqueueLong(0, 1, 0).enqueueLong(0, 1, 0);
        var g1 = mockGS.uniformLongs(0, 0);
        Asserts.assertEQ(g1.next(), 0L);
        Asserts.assertEQ(g1.next(), 0L);

        mockRandomness.checkEmpty().enqueueLong(-1, Long.MAX_VALUE, 10);
        Asserts.assertEQ(mockGS.uniformLongs(0, Long.MAX_VALUE).next(), 11L);

        mockRandomness.checkEmpty().enqueueLong(Long.MIN_VALUE, 13, -33);
        Asserts.assertEQ(mockGS.uniformLongs(Long.MIN_VALUE, 12).next(), -33L);

        mockRandomness.checkEmpty().enqueueLong(11);
        Asserts.assertEQ(mockGS.uniformLongs(Long.MIN_VALUE, Long.MAX_VALUE).next(), 11L);

        mockRandomness.checkEmpty().enqueueLong(10, 29, 17);
        Asserts.assertEQ(mockGS.uniformLongs(Long.MIN_VALUE, Long.MAX_VALUE).restricted(10L, 28L).next(), 17L);

        mockRandomness.checkEmpty().enqueueLong(19, 29, 17);
        Asserts.assertEQ(mockGS.uniformLongs(Long.MIN_VALUE, Long.MAX_VALUE).restricted(10L, 28L).restricted(19L, 33L).next(), 17L);

        mockRandomness.checkEmpty().enqueueLong(144);
        Asserts.assertEQ(mockGS.uniformLongs().next(), 144L);

        mockRandomness.checkEmpty();

        long lo = -344223244511L, hi = 29;
        var gb = G.uniformLongs(lo, hi);
        for (int i = 0; i < 10_000; i++) {
            long x = gb.next();
            Asserts.assertGreaterThanOrEqual(x, lo);
            Asserts.assertLessThanOrEqual(x, hi);
        }
    }

    static void testAnyBits() {
        mockRandomness.checkEmpty().enqueueInteger(Float.floatToIntBits(3.14159f));
        Asserts.assertEQ(mockGS.anyBitsFloats().next(), 3.14159f);

        mockRandomness.checkEmpty().enqueueLong(Double.doubleToLongBits(3.14159d));
        Asserts.assertEQ(mockGS.anyBitsDouble().next(), 3.14159d);
    }

    static void testEmptyGenerators() {
        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.uniformInts(1, 0));
        Asserts.assertNotNull(G.uniformInts(0, 0));
        Asserts.assertNotNull(G.uniformInts(0, 1));
        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.uniformInts(0, 1).restricted(2, 5));
        Asserts.assertNotNull(G.uniformInts(0, 1).restricted(0, 1));
        Asserts.assertNotNull(G.uniformInts(0, 1).restricted(1, 5));
        Asserts.assertNotNull(G.uniformInts(0, 10).restricted(1, 2));

        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.uniformLongs(1, 0));
        Asserts.assertNotNull(G.uniformLongs(0, 0));
        Asserts.assertNotNull(G.uniformLongs(0, 1));
        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.uniformLongs(0, 1).restricted(2L, 5L));
        Asserts.assertNotNull(G.uniformLongs(0, 1).restricted(0L, 1L));
        Asserts.assertNotNull(G.uniformLongs(0, 1).restricted(1L, 5L));
        Asserts.assertNotNull(G.uniformLongs(0, 10).restricted(1L, 2L));

        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.uniformDoubles(1, 0));
        Asserts.assertNotNull(G.uniformDoubles(0, 1));
        Asserts.assertNotNull(G.uniformDoubles(0, 0));
        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.uniformDoubles(0, 1).restricted(1.1d, 2.4d));
        Asserts.assertNotNull(G.uniformDoubles(0, 1).restricted(0.9d, 2.4d));

        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.uniformFloats(1, 0));
        Asserts.assertNotNull(G.uniformFloats(0, 1));
        Asserts.assertNotNull(G.uniformFloats(0, 0));
        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.uniformFloats(0, 1).restricted(1.1f, 2.4f));
        Asserts.assertNotNull(G.uniformFloats(0, 1).restricted(0.9f, 2.4f));

        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.randomElement(List.of()));
        Asserts.assertNotNull(G.randomElement(List.of("a", "b", "c")));

        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.orderedRandomElement(new ArrayList<Integer>()));
        Asserts.assertNotNull(G.orderedRandomElement(List.of(48, 29, 17)));
        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.orderedRandomElement(List.of(48, 29, 17)).restricted(-12, 10));
    }

    static void testFill() {
        // All we need to test really is that fill calls the generators sequentially and correctly write the values
        // into the arrays. Since fill with arrays uses memory segments internally, these are also tested.

        Generator<Double> doubleGen = new Generator<>() {
            private double i = 1;

            @Override
            public Double next() {
                i /= 2;
                return i;
            }
        };

        double[] doubles = new double[5];
        mockGS.fill(doubleGen, doubles);
        Asserts.assertTrue(Arrays.equals(doubles, (new double[] {0.5, 0.25, 0.125, 0.0625, 0.03125})));

        Generator<Float> floatGen = new Generator<>() {
            private float i = 1;

            @Override
            public Float next() {
                i /= 2;
                return i;
            }
        };

        float[] floats = new float[5];
        mockGS.fill(floatGen, floats);
        Asserts.assertTrue(Arrays.equals(floats, (new float[] {0.5f, 0.25f, 0.125f, 0.0625f, 0.03125f})));

        Generator<Long> longGen = new Generator<>() {
            private long i = 1;

            @Override
            public Long next() {
                return i++;
            }
        };

        long[] longs = new long[5];
        mockGS.fill(longGen, longs);
        Asserts.assertTrue(Arrays.equals(longs, (new long[] {1, 2, 3, 4, 5})));

        Generator<Integer> intGen = new Generator<>() {
            private int i = 1;

            @Override
            public Integer next() {
                return i++;
            }
        };

        int[] ints = new int[5];
        mockGS.fill(intGen, ints);
        Asserts.assertTrue(Arrays.equals(ints, (new int[] {1, 2, 3, 4, 5})));
    }
}
