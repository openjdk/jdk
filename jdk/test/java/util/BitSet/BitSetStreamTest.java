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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.Integer;
import java.lang.Object;
import java.lang.System;
import java.util.BitSet;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;
import java.util.Random;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @test
 * @summary test BitSet stream
 * @bug 8012645
 * @run testng BitSetStreamTest
 */
public class BitSetStreamTest {
    static class Fibs implements IntSupplier {
        private int n1 = 0;
        private int n2 = 1;

        static int fibs(int n) {
            Fibs f = new Fibs();
            while (n-- > 0) f.getAsInt();
            return f.getAsInt();
        }

        public int getAsInt() { int s = n1; n1 = n2; n2 = s + n1; return s; }
    }

    private static final Object[][] testcases = new Object[][] {
        { "none", IntStream.empty() },
        { "index 0", IntStream.of(0) },
        { "index 255", IntStream.of(255) },
        { "every bit", IntStream.range(0, 255) },
        { "step 2", IntStream.range(0, 255).map(f -> f * 2) },
        { "step 3", IntStream.range(0, 255).map(f -> f * 3) },
        { "step 5", IntStream.range(0, 255).map(f -> f * 5) },
        { "step 7", IntStream.range(0, 255).map(f -> f * 7) },
        { "1, 10, 100, 1000", IntStream.of(1, 10, 100, 1000) },
        { "25 fibs", IntStream.generate(new Fibs()).limit(25) }
    };

    @DataProvider(name = "cases")
    public static Object[][] produceCases() {
        return testcases;
    }

    @Test
    public void testFibs() {
        Fibs f = new Fibs();
        assertEquals(0, f.getAsInt());
        assertEquals(1, f.getAsInt());
        assertEquals(1, f.getAsInt());
        assertEquals(2, f.getAsInt());
        assertEquals(3, f.getAsInt());
        assertEquals(5, f.getAsInt());
        assertEquals(8, f.getAsInt());
        assertEquals(13, f.getAsInt());
        assertEquals(987, Fibs.fibs(16));
    }

    @Test(dataProvider = "cases")
    public void testBitsetStream(String name, IntStream data) {
        BitSet bs = new BitSet();
        long setBits = data.distinct()
                           .peek(i -> bs.set(i))
                           .count();

        assertEquals(bs.cardinality(), setBits);
        assertEquals(bs.cardinality(), bs.stream().reduce(0, (s, i) -> s+1));

        PrimitiveIterator.OfInt it = bs.stream().iterator();
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
            assertTrue(it.hasNext());
            assertEquals(it.nextInt(), i);
        }
        assertFalse(it.hasNext());
    }

    @Test
    public void testRandomStream() {
        final int size = 1024 * 1024;
        final int[] seeds = {
                2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41,
                43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97};
        final byte[] bytes = new byte[size];
        for (int seed : seeds) {
            final Random random = new Random(seed);
            random.nextBytes(bytes);
            final BitSet bitSet = BitSet.valueOf(bytes);
            final int cardinality = bitSet.cardinality();
            final IntStream stream = bitSet.stream();
            final int[] array = stream.toArray();
            assertEquals(array.length, cardinality);
            int nextSetBit = -1;
            for (int i=0; i < cardinality; i++) {
                nextSetBit = bitSet.nextSetBit(nextSetBit + 1);
                assertEquals(array[i], nextSetBit);
            }
        }
    }
}
