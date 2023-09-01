/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Basic tests of immutable BitSets
 * @run junit ImmutableBitSet
 */

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.BitSetReadOps;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class ImmutableBitSet {


    @Test
    void empty() {
        BitSet bs = new BitSet();
        BitSetReadOps ibs = bs.asImmutable();
        test(bs, ibs);
    }

    @Test
    void basic() {
        BitSet bs = createReference(147);
        BitSetReadOps ibs = bs.asImmutable();
        test(bs, ibs);
    }

    @Test
    void clearedAtTheTail() {
        BitSet bs = createReference(147);
        for (int i = bs.length() - 1; i > 100; i++) {
            bs.clear(i);
        }
        BitSetReadOps ibs = bs.asImmutable();
        test(bs, ibs);
    }

    static void test(BitSetReadOps expected, BitSetReadOps actual) {
        for (int i = 0; i < expected.length() + 117; i++) {
            assertEquals(expected.get(i), actual.get(i), "at index " + i);
        }
        assertEquals(expected.cardinality(), actual.cardinality());
        assertEquals(expected.length(), actual.length());
        assertEquals(expected.hashCode(), actual.hashCode());
        assertTrue(expected.size() >= actual.size()); // immutable can be more efficient
        assertEquals(expected.toString(), actual.toString());
        for (int i = 0; i < expected.length(); i++) {
            assertEquals(expected.nextSetBit(i), actual.nextSetBit(i));
            assertEquals(expected.nextClearBit(i), actual.nextClearBit(i));
            assertEquals(expected.previousSetBit(i), actual.previousSetBit(i));
            assertEquals(expected.previousClearBit(i), actual.previousClearBit(i));
        }

    }

    private static BitSet createReference(int length) {
        BitSet result = new BitSet();
        Random random = new Random(length);
        for (int i = 0; i < length; i++) {
            result.set(i, random.nextBoolean());
        }
        return result;
        //return BitSet.valueOf(new byte[]{(byte) 0xff, 0x08, 0x00, 0x01, 0x02});
    }

}
