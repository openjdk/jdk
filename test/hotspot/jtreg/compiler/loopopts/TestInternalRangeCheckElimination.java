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
    private static final Generators RND = Generators.G;

    private static final RestrictableGenerator<Integer> BASIC_LIMIT_GEN =
        RND.mixed(
            RND.orderedRandomElement(List.of(0, 1, 2, 7, 8, 15, 16, 17,
                             31, 32, 33, 63, 64, 65, 127, 128, 129,
                             255, 256, 257, 319, 320, 321, 511, 512)),
            RND.safeRestrict(RND.ints(), 0, 512),
            3, 2);

    private static final RestrictableGenerator<Integer> LENGTH_GEN =
        RND.mixed(
            RND.orderedRandomElement(List.of(0, 1, 2, 3, 7, 8, 15, 16, 31, 32, 63, 64)),
            RND.safeRestrict(RND.ints(), 0, 64),
            3, 2);

    private static int lastBasicLimit;
    private static int lastBasicOffset;
    private static int lastFalseHotLimit;
    private static int lastFalseHotOffset;
    private static int lastIndexInRangeLikeLimit;
    private static int lastIndexInRangeLikeLength;
    private static int lastVectorLimit;
    private static int lastVectorSpeciesLength;

    static {
        RND.fill(RND.safeRestrict(RND.ints(), -100_000, 100_000), SRC);
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:LoopUnrollLimit=0",
                                   "--add-modules=jdk.incubator.vector");
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

    @Setup
    public static Object[] setupBasic(SetupInfo info) {
        int limit = BASIC_LIMIT_GEN.next();
        int maxOffset = Math.min(limit, 64);
        RestrictableGenerator<Integer> offsetGen = RND.mixed(
                RND.orderedRandomElement(List.of(0, 1, 2, 3, 7, 8, 15, 16, 31, 32, 63, 64)),
                RND.safeRestrict(RND.ints(), 0, maxOffset),
                3, 2);
        int offset = RND.safeRestrict(offsetGen, 0, maxOffset).next();
        return new Object[] {limit, offset};
    }

    @Test
    @Arguments(setup = "setupBasic")
    public static int basicInternalIf(int limit, int offset) {
        lastBasicLimit = limit;
        lastBasicOffset = offset;
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

    @Check(test = "basicInternalIf")
    public static void checkBasicInternalIf(int actual) {
        int expected = basicInternalIfRef(lastBasicLimit, lastBasicOffset);
        if (actual != expected) {
            throw new RuntimeException("basicInternalIf mismatch: actual=" + actual
                                       + " expected=" + expected
                                       + " limit=" + lastBasicLimit
                                       + " offset=" + lastBasicOffset);
        }
    }

    @Setup
    public static Object[] setupFalseHot(SetupInfo info) {
        int limit = BASIC_LIMIT_GEN.next();
        int maxOffset = Math.min(limit, 64);
        RestrictableGenerator<Integer> offsetGen = RND.mixed(
                RND.orderedRandomElement(List.of(0, 1, 2, 3, 7, 8, 15, 16, 31, 32, 63, 64)),
                RND.safeRestrict(RND.ints(), 0, maxOffset),
                3, 2);
        int offset = RND.safeRestrict(offsetGen, 0, maxOffset).next();
        return new Object[] {limit, offset};
    }

    @Test
    @Arguments(setup = "setupFalseHot")
    public static int internalIfFalseHot(int limit, int offset) {
        lastFalseHotLimit = limit;
        lastFalseHotOffset = offset;
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

    @Check(test = "internalIfFalseHot")
    public static void checkInternalIfFalseHot(int actual) {
        int expected = internalIfFalseHotRef(lastFalseHotLimit, lastFalseHotOffset);
        if (actual != expected) {
            throw new RuntimeException("internalIfFalseHot mismatch: actual=" + actual
                                       + " expected=" + expected
                                       + " limit=" + lastFalseHotLimit
                                       + " offset=" + lastFalseHotOffset);
        }
    }

    @Setup
    public static Object[] setupIndexInRange(SetupInfo info) {
        int limit = RND.safeRestrict(BASIC_LIMIT_GEN, 0, 512).next();
        int length = LENGTH_GEN.next();
        return new Object[] {limit, length};
    }

    @Test
    @Arguments(setup = "setupIndexInRange")
    public static int indexInRangeLike(int limit, int length) {
        lastIndexInRangeLikeLimit = limit;
        lastIndexInRangeLikeLength = length;
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

    @Check(test = "indexInRangeLike")
    public static void checkIndexInRangeLike(int actual) {
        int expected = indexInRangeLikeRef(lastIndexInRangeLikeLimit, lastIndexInRangeLikeLength);
        if (actual != expected) {
            throw new RuntimeException("indexInRangeLike mismatch: actual=" + actual
                                       + " expected=" + expected
                                       + " limit=" + lastIndexInRangeLikeLimit
                                       + " length=" + lastIndexInRangeLikeLength);
        }
    }

    @Setup
    public static Object[] setupVectorIndexInRange(SetupInfo info) {
        int len = IntVector.SPECIES_PREFERRED.length();
        List<Integer> cases = List.of(
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
        RestrictableGenerator<Integer> vectorLimitGen = RND.mixed(
            RND.orderedRandomElement(cases),
            RND.safeRestrict(RND.ints(), 0, 320),
            4, 1);
        int limit = vectorLimitGen.next();
        return new Object[] {limit};
    }

    @Test
    @Arguments(setup = "setupVectorIndexInRange")
    @IR(counts = {IRNode.LOAD_VECTOR_I, ">= 1",
                  IRNode.STORE_VECTOR, ">= 1",
                  IRNode.VECTOR_STORE_MASK, ">= 1"},
        applyIfCPUFeatureOr = {"avx512", "true", "sve", "true", "asimd", "true"})
    public static int vectorIndexInRange(int limit) {
        Arrays.fill(DST, SENTINEL);
        int acc = 0;
        VectorSpecies<Integer> species = IntVector.SPECIES_PREFERRED;
        lastVectorLimit = limit;
        lastVectorSpeciesLength = species.length();
        for (int i = 0; i < limit; i += species.length()) {
            VectorMask<Integer> m = species.indexInRange(i, limit);
            IntVector v = IntVector.fromArray(species, SRC, i, m);
            v.add(1).intoArray(DST, i, m);
            acc += DST[i];
        }
        return acc;
    }

    @Check(test = "vectorIndexInRange")
    public static void checkVectorIndexInRange(int actual) {
        int expected = vectorIndexInRangeRef(lastVectorLimit, lastVectorSpeciesLength);
        if (actual != expected) {
            throw new RuntimeException("vectorIndexInRange mismatch: actual=" + actual
                                       + " expected=" + expected
                                       + " limit=" + lastVectorLimit
                                       + " speciesLen=" + lastVectorSpeciesLength);
        }
        for (int i = 0; i < lastVectorLimit; i++) {
            int expectedValue = SRC[i] + 1;
            if (DST[i] != expectedValue) {
                throw new RuntimeException("vectorIndexInRange wrong lane at index=" + i
                                           + " got=" + DST[i]
                                           + " expected=" + expectedValue);
            }
        }
        for (int i = lastVectorLimit; i < DST.length; i++) {
            if (DST[i] != SENTINEL) {
                throw new RuntimeException("vectorIndexInRange wrote past limit at index=" + i
                                           + " value=" + DST[i]);
            }
        }
    }
}
