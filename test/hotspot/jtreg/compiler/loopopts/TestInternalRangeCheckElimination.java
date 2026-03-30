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

package compiler.loopopts;

import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;
import compiler.lib.ir_framework.*;
import java.util.Arrays;
import java.util.List;
import jdk.test.lib.Asserts;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

/*
 * @test
 * @bug 8378860
 * @summary C2 can apply iteration splitting to internal loop branches derived from the iv
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class}
 */
public class TestInternalRangeCheckElimination {
    private static final int[] SRC = new int[1024];
    private static final int[] DST = new int[1024];
    private static final int SENTINEL = -999_999_937;
    private static final int RANDOM_ITERATIONS = 200;
    private static final Generators RND = Generators.G;

    private static final List<Integer> BASIC_LIMIT_SPECIALS = List.of(
            0, 1, 2, 7, 8, 15, 16, 17,
            31, 32, 33, 63, 64, 65, 127, 128, 129,
            255, 256, 257, 319, 320, 321, 511, 512);

    private static final List<Integer> LENGTH_SPECIALS = List.of(
            0, 1, 2, 3, 7, 8, 15, 16, 31, 32, 63, 64);

    private static final RestrictableGenerator<Integer> BASIC_LIMIT_GEN =
        RND.mixed(
            RND.orderedRandomElement(BASIC_LIMIT_SPECIALS),
            RND.safeRestrict(RND.ints(), 0, 512),
            3, 2);

    private static final RestrictableGenerator<Integer> LENGTH_GEN =
        RND.mixed(
            RND.orderedRandomElement(LENGTH_SPECIALS),
            RND.safeRestrict(RND.ints(), 0, 64),
            3, 2);

    private static final List<Integer> OFFSET_SPECIALS = List.of(
            0, 1, 2, 3, 7, 8, 15, 16, 31, 32, 63, 64);

    static {
        RND.fill(RND.safeRestrict(RND.ints(), -100_000, 100_000), SRC);
    }

    private static int nextOffset(int limit) {
        int maxOffset = Math.min(limit, 64);
        RestrictableGenerator<Integer> offsetGen = RND.mixed(
                RND.orderedRandomElement(OFFSET_SPECIALS),
                RND.safeRestrict(RND.ints(), 0, maxOffset),
                3, 2);
        return RND.safeRestrict(offsetGen, 0, maxOffset).next();
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:LoopUnrollLimit=0",
                                   "--add-modules=jdk.incubator.vector");
    }

    private static List<Integer> vectorLimitSpecials(int len) {
        return List.of(
                0,
                1,
                Math.max(1, len - 1),
                len,
                len + 1,
                Math.max(0, 2 * len - 1),
                2 * len,
                2 * len + 1,
                255,
                256,
                257,
                319
        );
    }

    private static RestrictableGenerator<Integer> vectorLimitGen(int len) {
        return RND.mixed(
                RND.orderedRandomElement(vectorLimitSpecials(len)),
                RND.safeRestrict(RND.ints(), 0, 320),
                4, 1);
    }

    @DontCompile
    private static int basicInternalIfRef(int limit, int offset) {
        int expected = 0;
        int bound = limit - offset;
        for (int i = 0; i < limit; i++) {
            expected += (i < bound) ? i : -i;
        }
        return expected;
    }

    @DontCompile
    private static int internalIfFalseHotRef(int limit, int offset) {
        int expected = 0;
        int bound = limit - offset;
        for (int i = 0; i < limit; i++) {
            expected += (i >= bound) ? i : -i;
        }
        return expected;
    }

    @DontCompile
    private static int indexInRangeLikeRef(int limit, int length) {
        int expected = 0;
        for (int i = 0; i < limit; i++) {
            int value = ((limit - i) >= length) ? (SRC[i] + 1) : (SRC[i] + 2);
            expected += value;
        }
        return expected;
    }

    @DontCompile
    private static int vectorIndexInRangeRef(int limit, int speciesLen) {
        int expected = 0;
        for (int i = 0; i < limit; i += speciesLen) {
            expected += SRC[i] + 1;
        }
        return expected;
    }

    @Test
    public static int basicInternalIf(int limit, int offset) {
        int sum = 0;
        int bound = limit - offset;
        for (int i = 0; i < limit; i++) {
            if (i < bound) {
                sum += i;
            } else {
                sum -= i;
            }
        }
        return sum;
    }

    private static void checkBasicInternalIf(int limit, int offset) {
        int actual = basicInternalIf(limit, offset);
        int expected = basicInternalIfRef(limit, offset);
        Asserts.assertEQ(actual, expected,
                "basicInternalIf mismatch: limit=" + limit + " offset=" + offset);
    }

    @Run(test = "basicInternalIf")
    public void runBasicInternalIf() {
        for (int limit : BASIC_LIMIT_SPECIALS) {
            int maxOffset = Math.min(limit, 64);
            for (int offset : OFFSET_SPECIALS) {
                if (offset <= maxOffset) {
                    checkBasicInternalIf(limit, offset);
                }
            }
        }
        for (int i = 0; i < RANDOM_ITERATIONS; i++) {
            int limit = BASIC_LIMIT_GEN.next();
            int offset = nextOffset(limit);
            checkBasicInternalIf(limit, offset);
        }
    }

    @Test
    public static int internalIfFalseHot(int limit, int offset) {
        int sum = 0;
        int bound = limit - offset;
        for (int i = 0; i < limit; i++) {
            if (i >= bound) {
                sum += i;
            } else {
                sum -= i;
            }
        }
        return sum;
    }

    private static void checkInternalIfFalseHot(int limit, int offset) {
        int actual = internalIfFalseHot(limit, offset);
        int expected = internalIfFalseHotRef(limit, offset);
        Asserts.assertEQ(actual, expected,
                "internalIfFalseHot mismatch: limit=" + limit + " offset=" + offset);
    }

    @Run(test = "internalIfFalseHot")
    public void runInternalIfFalseHot() {
        for (int limit : BASIC_LIMIT_SPECIALS) {
            int maxOffset = Math.min(limit, 64);
            for (int offset : OFFSET_SPECIALS) {
                if (offset <= maxOffset) {
                    checkInternalIfFalseHot(limit, offset);
                }
            }
        }
        for (int i = 0; i < RANDOM_ITERATIONS; i++) {
            int limit = BASIC_LIMIT_GEN.next();
            int offset = nextOffset(limit);
            checkInternalIfFalseHot(limit, offset);
        }
    }

    @Test
    public static int indexInRangeLike(int limit, int length) {
        int acc = 0;
        for (int i = 0; i < limit; i++) {
            boolean mask = (limit - i) >= length;
            if (mask) {
                DST[i] = SRC[i] + 1;
            } else {
                DST[i] = SRC[i] + 2;
            }
            acc += DST[i];
        }
        return acc;
    }

    private static void checkIndexInRangeLike(int limit, int length) {
        int actual = indexInRangeLike(limit, length);
        int expected = indexInRangeLikeRef(limit, length);
        Asserts.assertEQ(actual, expected,
                "indexInRangeLike mismatch: limit=" + limit + " length=" + length);
    }

    @Run(test = "indexInRangeLike")
    public void runIndexInRangeLike() {
        for (int limit : BASIC_LIMIT_SPECIALS) {
            for (int length : LENGTH_SPECIALS) {
                checkIndexInRangeLike(limit, length);
            }
        }
        for (int i = 0; i < RANDOM_ITERATIONS; i++) {
            int limit = RND.safeRestrict(BASIC_LIMIT_GEN, 0, 512).next();
            int length = LENGTH_GEN.next();
            checkIndexInRangeLike(limit, length);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">= 1",
                  IRNode.STORE_VECTOR, ">= 1",
                  IRNode.VECTOR_STORE_MASK, ">= 1"},
        applyIfCPUFeatureOr = {"avx512", "true", "sve", "true", "asimd", "true"})
    public static int vectorIndexInRange(int limit) {
        Arrays.fill(DST, SENTINEL);
        int acc = 0;
        VectorSpecies<Integer> species = IntVector.SPECIES_PREFERRED;
        for (int i = 0; i < limit; i += species.length()) {
            VectorMask<Integer> m = species.indexInRange(i, limit);
            IntVector v = IntVector.fromArray(species, SRC, i, m);
            v.add(1).intoArray(DST, i, m);
            acc += DST[i];
        }
        return acc;
    }

    private static void checkVectorIndexInRange(int limit) {
        int actual = vectorIndexInRange(limit);
        int speciesLen = IntVector.SPECIES_PREFERRED.length();
        int expected = vectorIndexInRangeRef(limit, speciesLen);
        Asserts.assertEQ(actual, expected,
                "vectorIndexInRange mismatch: limit=" + limit + " speciesLen=" + speciesLen);
        for (int i = 0; i < limit; i++) {
            int expectedValue = SRC[i] + 1;
            Asserts.assertEQ(DST[i], expectedValue,
                    "vectorIndexInRange wrong lane at index=" + i);
        }
        for (int i = limit; i < DST.length; i++) {
            Asserts.assertEQ(DST[i], SENTINEL,
                    "vectorIndexInRange wrote past limit at index=" + i);
        }
    }

    @Run(test = "vectorIndexInRange")
    public void runVectorIndexInRange() {
        int len = IntVector.SPECIES_PREFERRED.length();
        for (int limit : vectorLimitSpecials(len)) {
            checkVectorIndexInRange(limit);
        }
        RestrictableGenerator<Integer> vlimitGen = vectorLimitGen(len);
        for (int i = 0; i < RANDOM_ITERATIONS; i++) {
            checkVectorIndexInRange(vlimitGen.next());
        }
    }
}
