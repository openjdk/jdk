/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.util
 * @run junit ImmutableBitSet
 */

import jdk.internal.util.ImmutableBitSetPredicate;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

public class ImmutableBitSet {

    @Test
    void empty() {
        BitSet bs = new BitSet();
        IntPredicate ibs = ImmutableBitSetPredicate.of(bs);
        test(bs, ibs);
    }

    @Test
    void negativeIndex() {
        BitSet bs = new BitSet();
        IntPredicate ibs = ImmutableBitSetPredicate.of(bs);
        assertThrows(IndexOutOfBoundsException.class, () -> {
            ibs.test(-1);
        });
    }

    @Test
    void basic() {
        BitSet bs = createReference(147);
        IntPredicate ibs = ImmutableBitSetPredicate.of(bs);
        test(bs, ibs);
    }

    @Test
    void clearedAtTheTail() {
        for (int i = Long.BYTES - 1; i < Long.BYTES + 2; i++) {
            BitSet bs = createReference(i);
            for (int j = bs.length() - 1; j > Long.BYTES - 1; j++) {
                bs.clear(j);
            }
            IntPredicate ibs = ImmutableBitSetPredicate.of(bs);
            test(bs, ibs);
        }
    }

    static void test(BitSet expected, IntPredicate actual) {
        for (int i = 0; i < expected.length() + 17; i++) {
            assertEquals(expected.get(i), actual.test(i), "at index " + i);
        }
    }

    private static BitSet createReference(int length) {
        BitSet result = new BitSet();
        Random random = new Random(length);
        for (int i = 0; i < length; i++) {
            result.set(i, random.nextBoolean());
        }
        return result;
    }

}