/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @build MockRandomnessSource
 * @run driver testlibrary_tests.generators.tests.TestGenerators
 */

package testlibrary_tests.generators.tests;

import compiler.lib.generators.EmptyGeneratorException;
import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;
import jdk.test.lib.Asserts;

import java.util.*;

import static compiler.lib.generators.Generators.G;


public class TestGenerators {
    // As it's hard to write tests with real randomness, we mock the randomness source so we can control which "random"
    // values are fed to the generators. Thus, a lot of the tests below are white-box tests, that have knowledge about
    // the internals of when randomness is consumed. There are also black-box tests which refer to Generators.G.
    // Please also see MockRandomness to learn more about this class.
    static MockRandomnessSource mockSource = new MockRandomnessSource();
    static Generators mockGS = new Generators(mockSource);

    public static void main(String[] args) {
        testEmptyGenerators();
        testUniformInts();
        testUniformLongs();
        testAnyBits();
        testUniformFloat();
        testUniformFloat16();
        testUniformDouble();
        testSingle();
        testMixed();
        testRandomElement();
        specialInt();
        specialLong();
        testSpecialFloat();
        testSpecialFloat16();
        testSpecialDouble();
        testSafeRestrict();
        testFill();
        testFuzzy();
    }

    static void testMixed() {
        mockSource
                .checkEmpty()
                .enqueueInteger(0, 10, 7)  // MixedGenerator chooses a generator: single
                // single was chosen but does not consume randomness
                .enqueueInteger(0, 10, 5)  // MixedGenerator chooses a generator: uniform ints
                .enqueueInteger(0, 31, 4)  // uniform ints samples
                .enqueueInteger(0, 10, 1)  // MixedGenerator chooses a generator: uniform ints
                .enqueueInteger(0, 31, 18); // uniform ints samples
        var g0 = mockGS.mixed(mockGS.uniformInts(0, 30), mockGS.single(-1), 7, 3);
        Asserts.assertEQ(g0.next(), -1);
        Asserts.assertEQ(g0.next(), 4);
        Asserts.assertEQ(g0.next(), 18);

        mockSource
                .checkEmpty()
                .enqueueInteger(0, 10, 1)  // MixedGenerator chooses a generator: the first uniform ints
                .enqueueInteger(0, 31, 24) // uniform ints (1) samples
                .enqueueInteger(0, 10, 2) // MixedGenerator chooses a generator: single
                // single does not use randomness
                .enqueueInteger(0, 10, 7) // MixedGenerator chooses a generator: the second uniform ints
                .enqueueInteger(-10, 0, -2) // uniform ints (2) samples
                .enqueueInteger(0, 10, 9) // MixedGenerator chooses a generator: the second uniform ints
                .enqueueInteger(-10, 0, -4) // uniform ints (2) samples
                .enqueueInteger(0, 10, 1) // MixedGenerator chooses a generator: the first uniform ints
                .enqueueInteger(0, 31, 29); // uniform ints (1) samples

        var g1 = mockGS.mixed(
                List.of(2, 5, 3),
                mockGS.uniformInts(0, 30), mockGS.single(-1), mockGS.uniformInts(-10, -1)
        );
        Asserts.assertEQ(g1.next(), 24);
        Asserts.assertEQ(g1.next(), -1);
        Asserts.assertEQ(g1.next(), -2);
        Asserts.assertEQ(g1.next(), -4);
        Asserts.assertEQ(g1.next(), 29);

        mockSource
            .checkEmpty()
            .enqueueInteger(0, 10, 7)  // MixedGenerator chooses a generator: single
            // single was chosen but does not consume randomness
            .enqueueInteger(0, 10, 5)  // MixedGenerator chooses a generator: uniform ints
            .enqueueInteger(0, 21, 18);  // uniform ints samples
        var g0r0 = g0.restricted(-1, 20);
        Asserts.assertEQ(g0r0.next(), -1);
        Asserts.assertEQ(g0r0.next(), 18);

        mockSource
            .checkEmpty()
            .enqueueInteger(0, 7, 6)  // MixedGenerator chooses a generator (weight for single will have been removed): uniform ints
            .enqueueInteger(4, 21, 9);  // MixedGenerator chooses a generator: uniform ints
        var g0r1 = g0.restricted(4, 20);
        Asserts.assertEQ(g0r1.next(), 9);

        mockSource
            .checkEmpty()
            .enqueueInteger(0, 10, 1)  // MixedGenerator chooses a generator: the first uniform ints
            .enqueueInteger(0, 21, 2) // uniform ints (1) samples
            .enqueueInteger(0, 10, 2) // MixedGenerator chooses a generator: single
            // single does not use randomness
            .enqueueInteger(0, 10, 7) // MixedGenerator chooses a generator: the second uniform ints
            .enqueueInteger(-1, 0, -1);
        var g1r0 = g1.restricted(-1, 20);
        Asserts.assertEQ(g1r0.next(), 2);
        Asserts.assertEQ(g1r0.next(), -1);
        Asserts.assertEQ(g1r0.next(), -1);

        mockSource
                .checkEmpty()
                .enqueueInteger(0, 10, 1)  // MixedGenerator chooses a generator: the first uniform ints
                .enqueueInteger(0, 21, 2) // uniform ints (1) samples
                .enqueueInteger(0, 10, 2) // MixedGenerator chooses a generator: single
                // single does not use randomness
                .enqueueInteger(0, 10, 7) // MixedGenerator chooses a generator: the second uniform ints
                .enqueueInteger(-1, 0, -1);
        var g1r1 = g1.restricted(-1, 20);
        Asserts.assertEQ(g1r1.next(), 2);
        Asserts.assertEQ(g1r1.next(), -1);
        Asserts.assertEQ(g1r1.next(), -1);
    }

    static void testSpecialDouble() {
        mockSource
                .checkEmpty()
                .enqueueInteger(0, 10, 3)
                .enqueueDouble(0, 1, 3.4d)
                .enqueueInteger(0, 10, 6)
                .enqueueInteger(0, 10, 1);
        var g = mockGS.mixedWithSpecialDoubles(mockGS.uniformDoubles(), 5, 5);
        Asserts.assertEQ(g.next(), 3.4d);
        Asserts.assertEQ(g.next(), -1d);
    }

    static void testSpecialFloat() {
        mockSource
                .checkEmpty()
                .enqueueInteger(0, 10, 3)
                .enqueueFloat(0, 1, 3.4f)
                .enqueueInteger(0, 10, 6)
                .enqueueInteger(0, 10, 1);
        var g = mockGS.mixedWithSpecialFloats(mockGS.uniformFloats(), 5, 5);
        Asserts.assertEQ(g.next(), 3.4f);
        Asserts.assertEQ(g.next(), -1f);
    }

    static void testSpecialFloat16() {
        mockSource
                .checkEmpty()
                .enqueueInteger(0, 8, 2)
                .enqueueFloat16((short)0, (short)15360, (short)17010)
                .enqueueInteger(0, 8, 6)
                .enqueueInteger(0, 8, 5)
                .enqueueInteger(0, 8, 7)
                .enqueueInteger(0, 8, 4);
        var g = mockGS.mixedWithSpecialFloat16s(mockGS.uniformFloat16s(), 4, 4);
        Asserts.assertEQ(g.next(), (short)17010);
        Asserts.assertEQ(g.next(), (short)31743);
        Asserts.assertEQ(g.next(), (short)1024);
    }

    static void testUniformFloat16() {
        mockSource.checkEmpty().enqueueFloat16((short)0, (short)10, (short)17664);
        Asserts.assertEQ(mockGS.uniformFloat16s((short)0, (short)10).next(), (short)17664);
        mockSource.checkEmpty().enqueueFloat16((short)0, (short)5, (short)31744);
        Asserts.assertEQ(mockGS.uniformFloat16s((short)0, (short)5).next(), (short)31744);
    }

    static void testUniformFloat() {
        mockSource.checkEmpty().enqueueFloat(-1, 10, 3.14159f);
        Asserts.assertEQ(mockGS.uniformFloats(-1, 10).next(), 3.14159f);
        mockSource.checkEmpty().enqueueFloat(0, 1, 3.14159f);
        Asserts.assertEQ(mockGS.uniformFloats(0, 1).next(), 3.14159f);
    }

    static void testUniformDouble() {
        mockSource.checkEmpty().enqueueDouble(-1, 10, 3.14159d);
        Asserts.assertEQ(mockGS.uniformDoubles(-1, 10).next(), 3.14159d);
        mockSource.checkEmpty().enqueueDouble(0, 1, 3.14159d);
        Asserts.assertEQ(mockGS.uniformDoubles(0, 1).next(), 3.14159d);
    }

    static void testRandomElement() {
        mockSource.checkEmpty().enqueueInteger(0, 3, 1).enqueueInteger(0, 3, 0);
        var g = mockGS.randomElement(List.of("a", "b", "c"));
        Asserts.assertEQ(g.next(), "b");
        Asserts.assertEQ(g.next(), "a");

        mockSource.checkEmpty().enqueueInteger(0, 8, 1).enqueueInteger(0, 8, 2);
        // The list below is intentionally not sorted and is equivalent to: 1, 4, 4, 8, 9, 10, 13, 18, 20
        // It contains 8 distinct values. Note that orderedRandomElement removes duplicates. Therefore the internal
        // value list is: 1, 4, 8, 9, 10, 13, 18, 20
        var g1 = mockGS.orderedRandomElement(List.of(10, 4, 1, 8, 9, 4, 20, 18, 13));
        Asserts.assertEQ(g1.next(), 4);
        Asserts.assertEQ(g1.next(), 8);

        mockSource.checkEmpty().enqueueInteger(0, 3, 1).enqueueInteger(0, 3, 2);
        // Ordered lists can also be restricted. Our new values are 9, 10, 13.
        var gr = g1.restricted(9, 13);
        Asserts.assertEQ(gr.next(), 10);
        Asserts.assertEQ(gr.next(), 13);

        mockSource.checkEmpty().enqueueInteger(0, 2, 1);
        var gs = mockGS.orderedRandomElement(List.of("Bob", "Alice", "Carol")).restricted("Al", "Bz");
        Asserts.assertEQ(gs.next(), "Bob");
    }

    static void specialInt() {
        mockSource.checkEmpty().enqueueInteger(0, 63, 1).enqueueInteger(0, 63, 32);
        var si = mockGS.powerOfTwoInts(0);
        Asserts.assertEQ(si.next(), -(1 << 30));
        Asserts.assertEQ(si.next(), 1);

        mockSource.checkEmpty().enqueueInteger(0, 182, 1);
        var si1 = mockGS.powerOfTwoInts(1);
        Asserts.assertEQ(si1.next(), -(1 << 31) + 1);
    }

    static void specialLong() {
        mockSource.checkEmpty().enqueueInteger(0, 127, 1).enqueueInteger(0, 127, 64);
        var si = mockGS.powerOfTwoLongs(0);
        Asserts.assertEQ(si.next(), -(1L << 62));
        Asserts.assertEQ(si.next(), 1L);

        mockSource.checkEmpty().enqueueInteger(0, 374, 1);
        var si1 = mockGS.powerOfTwoLongs(1);
        Asserts.assertEQ(si1.next(), -(1L << 63) + 1);
    }

    static void testSingle() {
        mockSource.checkEmpty();
        var g = mockGS.single(30);
        Asserts.assertEQ(g.next(), 30);
        Asserts.assertEQ(g.next(), 30);
        Asserts.assertEQ(g.restricted(10, 50).next(), 30);
        var gs = mockGS.single("hello");
        Asserts.assertEQ(gs.next(), "hello");
        Asserts.assertEQ(gs.next(), "hello");
        Asserts.assertEQ(gs.restricted("a", "q").next(), "hello");
        var theObject = new Object();
        var go = mockGS.single(theObject);
        Asserts.assertEQ(go.next(), theObject);
        Asserts.assertEQ(go.next(), theObject);
    }

    static void testUniformInts() {
        mockSource.checkEmpty().enqueueInteger(0, 11, 1).enqueueInteger(0, 11, 4);
        var g0 = mockGS.uniformInts(0, 10);
        Asserts.assertEQ(g0.next(), 1);
        Asserts.assertEQ(g0.next(), 4);

        mockSource.checkEmpty().enqueueInteger(0, 1, 0).enqueueInteger(0, 1, 0);
        var g1 = mockGS.uniformInts(0, 0);
        Asserts.assertEQ(g1.next(), 0);
        Asserts.assertEQ(g1.next(), 0);

        mockSource.checkEmpty().enqueueInteger(-1, Integer.MAX_VALUE, 10);
        Asserts.assertEQ(mockGS.uniformInts(0, Integer.MAX_VALUE).next(), 11);

        mockSource.checkEmpty().enqueueInteger(Integer.MIN_VALUE, 13, -33);
        Asserts.assertEQ(mockGS.uniformInts(Integer.MIN_VALUE, 12).next(), -33);

        mockSource.checkEmpty().enqueueInteger(11);
        Asserts.assertEQ(mockGS.uniformInts(Integer.MIN_VALUE, Integer.MAX_VALUE).next(), 11);

        mockSource.checkEmpty().enqueueInteger(10, 29, 17);
        Asserts.assertEQ(mockGS.uniformInts(Integer.MIN_VALUE, Integer.MAX_VALUE).restricted(10, 28).next(), 17);

        mockSource.checkEmpty().enqueueInteger(19, 29, 17);
        Asserts.assertEQ(mockGS.uniformInts(Integer.MIN_VALUE, Integer.MAX_VALUE).restricted(10, 28).restricted(19, 33).next(), 17);

        // inside interval positive
        mockSource.checkEmpty().enqueueInteger(12, 19, 17);
        Asserts.assertEQ(mockGS.uniformInts(10, 20).restricted(12, 18).next(), 17);

        // inside interval negative
        mockSource.checkEmpty().enqueueInteger(-18, -11, -17);
        Asserts.assertEQ(mockGS.uniformInts(-20, -10).restricted(-18, -12).next(), -17);

        // left interval positive
        mockSource.checkEmpty().enqueueInteger(10, 13, 11);
        Asserts.assertEQ(mockGS.uniformInts(10, 20).restricted(5, 12).next(), 11);

        // left interval negative
        mockSource.checkEmpty().enqueueInteger(-12, -9, -11);
        Asserts.assertEQ(mockGS.uniformInts(-20, -10).restricted(-12, -5).next(), -11);

        // right interval positive
        mockSource.checkEmpty().enqueueInteger(17, 21, 19);
        Asserts.assertEQ(mockGS.uniformInts(10, 20).restricted(17, 22).next(), 19);

        // right interval negative
        mockSource.checkEmpty().enqueueInteger(-20, -16, -19);
        Asserts.assertEQ(mockGS.uniformInts(-20, -10).restricted(-22, -17).next(), -19);

        mockSource.checkEmpty().enqueueInteger(144);
        Asserts.assertEQ(mockGS.uniformInts().next(), 144);

        mockSource.checkEmpty();
    }

    static void testUniformLongs() {
        mockSource.checkEmpty().enqueueLong(0, 11, 1).enqueueLong(0, 11, 4);
        var g0 = mockGS.uniformLongs(0, 10);
        Asserts.assertEQ(g0.next(), 1L);
        Asserts.assertEQ(g0.next(), 4L);

        mockSource.checkEmpty().enqueueLong(0, 1, 0).enqueueLong(0, 1, 0);
        var g1 = mockGS.uniformLongs(0, 0);
        Asserts.assertEQ(g1.next(), 0L);
        Asserts.assertEQ(g1.next(), 0L);

        mockSource.checkEmpty().enqueueLong(-1, Long.MAX_VALUE, 10);
        Asserts.assertEQ(mockGS.uniformLongs(0, Long.MAX_VALUE).next(), 11L);

        mockSource.checkEmpty().enqueueLong(Long.MIN_VALUE, 13, -33);
        Asserts.assertEQ(mockGS.uniformLongs(Long.MIN_VALUE, 12).next(), -33L);

        mockSource.checkEmpty().enqueueLong(11);
        Asserts.assertEQ(mockGS.uniformLongs(Long.MIN_VALUE, Long.MAX_VALUE).next(), 11L);

        mockSource.checkEmpty().enqueueLong(10, 29, 17);
        Asserts.assertEQ(mockGS.uniformLongs(Long.MIN_VALUE, Long.MAX_VALUE).restricted(10L, 28L).next(), 17L);

        mockSource.checkEmpty().enqueueLong(19, 29, 17);
        Asserts.assertEQ(mockGS.uniformLongs(Long.MIN_VALUE, Long.MAX_VALUE).restricted(10L, 28L).restricted(19L, 33L).next(), 17L);

        mockSource.checkEmpty().enqueueLong(144);
        Asserts.assertEQ(mockGS.uniformLongs().next(), 144L);

        mockSource.checkEmpty();
    }

    static void testAnyBits() {
        mockSource.checkEmpty().enqueueInteger(Float.floatToIntBits(3.14159f));
        Asserts.assertEQ(mockGS.anyBitsFloats().next(), 3.14159f);

        mockSource.checkEmpty().enqueueLong(Double.doubleToLongBits(3.14159d));
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

        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.single(10).restricted(0, 1));
        Asserts.assertNotNull(G.single(10).restricted(9, 10));

        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.mixed(G.uniformInts(0, 10), G.uniformInts(15, 20), 5, 5).restricted(30, 34));
        Asserts.assertNotNull(G.mixed(G.uniformInts(0, 10), G.uniformInts(15, 20), 5, 5).restricted(5, 18));
        Asserts.assertNotNull(G.mixed(G.uniformInts(0, 10), G.uniformInts(15, 20), 5, 5).restricted(5, 7));
        Asserts.assertNotNull(G.mixed(G.uniformInts(0, 10), G.uniformInts(15, 20), 5, 5).restricted(16, 18));

        Asserts.assertThrows(EmptyGeneratorException.class, () -> G.mixed(
                List.of(3, 4, 6),
                G.uniformInts(0, 10), G.uniformInts(15, 20), G.uniformInts(30, 40)
        ).restricted(80, 83));
        Asserts.assertNotNull(G.mixed(
                List.of(3, 4, 6),
                G.uniformInts(0, 10), G.uniformInts(15, 20), G.uniformInts(30, 40)
        ).restricted(10, 35));
        Asserts.assertNotNull(G.mixed(
                List.of(3, 4, 6),
                G.uniformInts(0, 10), G.uniformInts(15, 20), G.uniformInts(30, 40)
        ).restricted(5, 8));
        Asserts.assertNotNull(G.mixed(
                List.of(3, 4, 6),
                G.uniformInts(0, 10), G.uniformInts(15, 20), G.uniformInts(30, 40)
        ).restricted(17, 19));
        Asserts.assertNotNull(G.mixed(
                List.of(3, 4, 6),
                G.uniformInts(0, 10), G.uniformInts(15, 20), G.uniformInts(30, 40)
        ).restricted(31, 38));
    }

    static void testSafeRestrict() {
        // normal restrictions
        mockSource.checkEmpty().enqueueInteger(4, 6, 4);
        var g1 = mockGS.safeRestrict(mockGS.uniformInts(4, 5), 2, 5);
        Asserts.assertEQ(g1.next(), 4);

        mockSource.checkEmpty().enqueueLong(4, 6, 4);
        var g2 = mockGS.safeRestrict(mockGS.uniformLongs(4, 5), 2, 5);
        Asserts.assertEQ(g2.next(), 4L);

        mockSource.checkEmpty().enqueueDouble(4, 5, 4);
        var g3 = mockGS.safeRestrict(mockGS.uniformDoubles(4, 5), 2, 5);
        Asserts.assertEQ(g3.next(), 4d);

        mockSource.checkEmpty().enqueueFloat(4, 5, 4);
        var g4 = mockGS.safeRestrict(mockGS.uniformFloats(4, 5), 2, 5);
        Asserts.assertEQ(g4.next(), 4f);

        // fallbacks
        mockSource.checkEmpty().enqueueInteger(2, 6, 4);
        var f1 = mockGS.safeRestrict(mockGS.uniformInts(0, 1), 2, 5);
        Asserts.assertEQ(f1.next(), 4);

        mockSource.checkEmpty().enqueueLong(2, 6, 4);
        var f2 = mockGS.safeRestrict(mockGS.uniformLongs(0, 1), 2, 5);
        Asserts.assertEQ(f2.next(), 4L);

        mockSource.checkEmpty().enqueueDouble(2, 5, 4);
        var f3 = mockGS.safeRestrict(mockGS.uniformDoubles(0, 1), 2, 5);
        Asserts.assertEQ(f3.next(), 4d);

        mockSource.checkEmpty().enqueueFloat(2, 5, 4);
        var f4 = mockGS.safeRestrict(mockGS.uniformFloats(0, 1), 2, 5);
        Asserts.assertEQ(f4.next(), 4f);
    }

    static void testFill() {
        // All we need to test really is that fill calls the generators sequentially and correctly writes the values
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

    static void testFuzzy() {
        var intBoundGen = G.uniformInts();
        for (int j = 0; j < 500; j++) {
            int a = intBoundGen.next(), b = intBoundGen.next();
            int lo = Math.min(a, b), hi = Math.max(a, b);
            RestrictableGenerator<Integer> gb;
            try {
                gb = G.ints().restricted(lo, hi);
            } catch (EmptyGeneratorException e) {
                continue;
            }
            for (int i = 0; i < 10_000; i++) {
                int x = gb.next();
                Asserts.assertGreaterThanOrEqual(x, lo);
                Asserts.assertLessThanOrEqual(x, hi);
            }
        }

        for (int j = 0; j < 500; j++) {
            int a = intBoundGen.next(), b = intBoundGen.next();
            int lo = Math.min(a, b), hi = Math.max(a, b);
            var gb = G.uniformInts(lo, hi);
            for (int i = 0; i < 10_000; i++) {
                int x = gb.next();
                Asserts.assertGreaterThanOrEqual(x, lo);
                Asserts.assertLessThanOrEqual(x, hi);
            }
        }

        var longBoundGen = G.uniformLongs();
        for (int j = 0; j < 500; j++) {
            long a = longBoundGen.next(), b = longBoundGen.next();
            long lo = Math.min(a, b), hi = Math.max(a, b);
            RestrictableGenerator<Long> gb;
            try {
                gb = G.longs().restricted(lo, hi);
            } catch (EmptyGeneratorException e) {
                continue;
            }
            for (int i = 0; i < 10_000; i++) {
                long x = gb.next();
                Asserts.assertGreaterThanOrEqual(x, lo);
                Asserts.assertLessThanOrEqual(x, hi);
            }
        }

        for (int j = 0; j < 500; j++) {
            long a = longBoundGen.next(), b = longBoundGen.next();
            long lo = Math.min(a, b), hi = Math.max(a, b);
            var gb = G.uniformLongs(lo, hi);
            for (int i = 0; i < 10_000; i++) {
                long x = gb.next();
                Asserts.assertGreaterThanOrEqual(x, lo);
                Asserts.assertLessThanOrEqual(x, hi);
            }
        }

        var floatBoundGen = G.uniformFloats();
        for (int j = 0; j < 500; j++) {
            float a = floatBoundGen.next(), b = floatBoundGen.next();
            float lo = Math.min(a, b), hi = Math.max(a, b);
            var gb = G.uniformFloats(lo, hi);
            for (int i = 0; i < 10_000; i++) {
                float x = gb.next();
                Asserts.assertGreaterThanOrEqual(x, lo);
                Asserts.assertLessThan(x, hi);
            }
        }

        var doubleBoundGen = G.uniformDoubles();
        for (int j = 0; j < 500; j++) {
            double a = doubleBoundGen.next(), b = doubleBoundGen.next();
            double lo = Math.min(a, b), hi = Math.max(a, b);
            var gb = G.uniformDoubles(lo, hi);
            for (int i = 0; i < 10_000; i++) {
                double x = gb.next();
                Asserts.assertGreaterThanOrEqual(x, lo);
                Asserts.assertLessThan(x, hi);
            }
        }
    }
}
