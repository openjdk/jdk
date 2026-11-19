/*
 * Copyright (c) 2025 Alibaba Group Holding Limited. All Rights Reserved.
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

package compiler.c2.gvn;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;
import compiler.lib.ir_framework.*;
import java.util.function.Function;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8360192
 * @summary Tests that count bits nodes are handled correctly.
 * @library /test/lib /
 * @run driver compiler.c2.gvn.TestCountBitsRange
 */
public class TestCountBitsRange {
    private static final Generator<Integer> INTS = Generators.G.ints();
    private static final Generator<Long> LONGS = Generators.G.longs();

    private static final RestrictableGenerator<Integer> INTS_32 = Generators.G.ints().restricted(0, 32);
    private static final RestrictableGenerator<Integer> INTS_64 = Generators.G.ints().restricted(0, 64);

    private static final int LIMITS_32_0 = INTS_32.next();
    private static final int LIMITS_32_1 = INTS_32.next();
    private static final int LIMITS_32_2 = INTS_32.next();
    private static final int LIMITS_32_3 = INTS_32.next();
    private static final int LIMITS_32_4 = INTS_32.next();
    private static final int LIMITS_32_5 = INTS_32.next();
    private static final int LIMITS_32_6 = INTS_32.next();
    private static final int LIMITS_32_7 = INTS_32.next();

    private static final int LIMITS_64_0 = INTS_64.next();
    private static final int LIMITS_64_1 = INTS_64.next();
    private static final int LIMITS_64_2 = INTS_64.next();
    private static final int LIMITS_64_3 = INTS_64.next();
    private static final int LIMITS_64_4 = INTS_64.next();
    private static final int LIMITS_64_5 = INTS_64.next();
    private static final int LIMITS_64_6 = INTS_64.next();
    private static final int LIMITS_64_7 = INTS_64.next();

    private static final IntRange RANGE_INT = IntRange.generate(INTS);
    private static final LongRange RANGE_LONG = LongRange.generate(LONGS);

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {
        "clzConstInts", "clzCompareInt", "clzDiv8Int", "clzRandLimitInt",
        "clzConstLongs", "clzCompareLong", "clzDiv8Long", "clzRandLimitLong",
        "ctzConstInts", "ctzCompareInt", "ctzDiv8Int", "ctzRandLimitInt",
        "ctzConstLongs", "ctzCompareLong", "ctzDiv8Long", "ctzRandLimitLong",
    })
    public void runTest() {
        int randInt = INTS.next();
        long randLong = LONGS.next();
        assertResult(randInt, randLong);
    }

    @DontCompile
    public void assertResult(int randInt, long randLong) {
        checkConstResults(clzConstInts(), x -> Integer.numberOfLeadingZeros(x.intValue()));
        Asserts.assertEQ(Integer.numberOfLeadingZeros(randInt) < 0
                         || Integer.numberOfLeadingZeros(randInt) > 32,
                         clzCompareInt(randInt));
        Asserts.assertEQ(Integer.numberOfLeadingZeros(randInt) / 8,
                         clzDiv8Int(randInt));
        Asserts.assertEQ(clzRandLimitInterpretedInt(randInt), clzRandLimitInt(randInt));

        checkConstResults(clzConstLongs(), x -> Long.numberOfLeadingZeros(x.longValue()));
        Asserts.assertEQ(Long.numberOfLeadingZeros(randLong) < 0
                         || Long.numberOfLeadingZeros(randLong) > 64,
                         clzCompareLong(randLong));
        Asserts.assertEQ(Long.numberOfLeadingZeros(randLong) / 8,
                         clzDiv8Long(randLong));
        Asserts.assertEQ(clzRandLimitInterpretedLong(randLong), clzRandLimitLong(randLong));

        checkConstResults(ctzConstInts(), x -> Integer.numberOfTrailingZeros(x.intValue()));
        Asserts.assertEQ(Integer.numberOfTrailingZeros(randInt) < 0
                         || Integer.numberOfTrailingZeros(randInt) > 32,
                         ctzCompareInt(randInt));
        Asserts.assertEQ(Integer.numberOfTrailingZeros(randInt) / 8,
                         ctzDiv8Int(randInt));
        Asserts.assertEQ(ctzRandLimitInterpretedInt(randInt), ctzRandLimitInt(randInt));

        checkConstResults(ctzConstLongs(), x -> Long.numberOfTrailingZeros(x.longValue()));
        Asserts.assertEQ(Long.numberOfTrailingZeros(randLong) < 0
                         || Long.numberOfTrailingZeros(randLong) > 64,
                         ctzCompareLong(randLong));
        Asserts.assertEQ(Long.numberOfTrailingZeros(randLong) / 8,
                         ctzDiv8Long(randLong));
        Asserts.assertEQ(ctzRandLimitInterpretedLong(randLong), ctzRandLimitLong(randLong));
    }

    @DontCompile
    public void checkConstResults(int[] results, Function<Long, Integer> op) {
        Asserts.assertEQ(op.apply(Long.valueOf(0)), results[0]);
        for (int i = 0; i < results.length - 1; ++i) {
            Asserts.assertEQ(op.apply(Long.valueOf(1l << i)), results[i + 1]);
        }
    }

    // Test CLZ with constant integer inputs.
    // All CLZs in this test are expected to be optimized away.
    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_I)
    public int[] clzConstInts() {
        return new int[] {
            Integer.numberOfLeadingZeros(0),
            Integer.numberOfLeadingZeros(1 << 0),
            Integer.numberOfLeadingZeros(1 << 1),
            Integer.numberOfLeadingZeros(1 << 2),
            Integer.numberOfLeadingZeros(1 << 3),
            Integer.numberOfLeadingZeros(1 << 4),
            Integer.numberOfLeadingZeros(1 << 5),
            Integer.numberOfLeadingZeros(1 << 6),
            Integer.numberOfLeadingZeros(1 << 7),
            Integer.numberOfLeadingZeros(1 << 8),
            Integer.numberOfLeadingZeros(1 << 9),
            Integer.numberOfLeadingZeros(1 << 10),
            Integer.numberOfLeadingZeros(1 << 11),
            Integer.numberOfLeadingZeros(1 << 12),
            Integer.numberOfLeadingZeros(1 << 13),
            Integer.numberOfLeadingZeros(1 << 14),
            Integer.numberOfLeadingZeros(1 << 15),
            Integer.numberOfLeadingZeros(1 << 16),
            Integer.numberOfLeadingZeros(1 << 17),
            Integer.numberOfLeadingZeros(1 << 18),
            Integer.numberOfLeadingZeros(1 << 19),
            Integer.numberOfLeadingZeros(1 << 20),
            Integer.numberOfLeadingZeros(1 << 21),
            Integer.numberOfLeadingZeros(1 << 22),
            Integer.numberOfLeadingZeros(1 << 23),
            Integer.numberOfLeadingZeros(1 << 24),
            Integer.numberOfLeadingZeros(1 << 25),
            Integer.numberOfLeadingZeros(1 << 26),
            Integer.numberOfLeadingZeros(1 << 27),
            Integer.numberOfLeadingZeros(1 << 28),
            Integer.numberOfLeadingZeros(1 << 29),
            Integer.numberOfLeadingZeros(1 << 30),
            Integer.numberOfLeadingZeros(1 << 31),
        };
    }

    // Test the range of CLZ with random integer input.
    // The result of CLZ should be in range [0, 32], so CLZs in this test are
    // expected to be optimized away, and the test should always return false.
    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_I)
    public boolean clzCompareInt(int randInt) {
        return Integer.numberOfLeadingZeros(randInt) < 0
               || Integer.numberOfLeadingZeros(randInt) > 32;
    }

    // Test the combination of CLZ and division by 8.
    // The result of CLZ should be positive, so the division by 8 should be
    // optimized to a simple right shift without rounding.
    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_I, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int clzDiv8Int(int randInt) {
        return Integer.numberOfLeadingZeros(randInt) / 8;
    }

    // Test the output range of CLZ with random input range.
    @Test
     public int clzRandLimitInt(int randInt) {
        randInt = RANGE_INT.clamp(randInt);
        int result = Integer.numberOfLeadingZeros(randInt);
        return getResultChecksum32(result);
    }

    @DontCompile
    public int clzRandLimitInterpretedInt(int randInt) {
        randInt = RANGE_INT.clamp(randInt);
        int result = Integer.numberOfLeadingZeros(randInt);
        return getResultChecksum32(result);
    }

    // Test CLZ with constant long inputs.
    // All CLZs in this test are expected to be optimized away.
    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_L)
    public int[] clzConstLongs() {
        return new int[] {
            Long.numberOfLeadingZeros(0),
            Long.numberOfLeadingZeros(1l << 0),
            Long.numberOfLeadingZeros(1l << 1),
            Long.numberOfLeadingZeros(1l << 2),
            Long.numberOfLeadingZeros(1l << 3),
            Long.numberOfLeadingZeros(1l << 4),
            Long.numberOfLeadingZeros(1l << 5),
            Long.numberOfLeadingZeros(1l << 6),
            Long.numberOfLeadingZeros(1l << 7),
            Long.numberOfLeadingZeros(1l << 8),
            Long.numberOfLeadingZeros(1l << 9),
            Long.numberOfLeadingZeros(1l << 10),
            Long.numberOfLeadingZeros(1l << 11),
            Long.numberOfLeadingZeros(1l << 12),
            Long.numberOfLeadingZeros(1l << 13),
            Long.numberOfLeadingZeros(1l << 14),
            Long.numberOfLeadingZeros(1l << 15),
            Long.numberOfLeadingZeros(1l << 16),
            Long.numberOfLeadingZeros(1l << 17),
            Long.numberOfLeadingZeros(1l << 18),
            Long.numberOfLeadingZeros(1l << 19),
            Long.numberOfLeadingZeros(1l << 20),
            Long.numberOfLeadingZeros(1l << 21),
            Long.numberOfLeadingZeros(1l << 22),
            Long.numberOfLeadingZeros(1l << 23),
            Long.numberOfLeadingZeros(1l << 24),
            Long.numberOfLeadingZeros(1l << 25),
            Long.numberOfLeadingZeros(1l << 26),
            Long.numberOfLeadingZeros(1l << 27),
            Long.numberOfLeadingZeros(1l << 28),
            Long.numberOfLeadingZeros(1l << 29),
            Long.numberOfLeadingZeros(1l << 30),
            Long.numberOfLeadingZeros(1l << 31),
            Long.numberOfLeadingZeros(1l << 32),
            Long.numberOfLeadingZeros(1l << 33),
            Long.numberOfLeadingZeros(1l << 34),
            Long.numberOfLeadingZeros(1l << 35),
            Long.numberOfLeadingZeros(1l << 36),
            Long.numberOfLeadingZeros(1l << 37),
            Long.numberOfLeadingZeros(1l << 38),
            Long.numberOfLeadingZeros(1l << 39),
            Long.numberOfLeadingZeros(1l << 40),
            Long.numberOfLeadingZeros(1l << 41),
            Long.numberOfLeadingZeros(1l << 42),
            Long.numberOfLeadingZeros(1l << 43),
            Long.numberOfLeadingZeros(1l << 44),
            Long.numberOfLeadingZeros(1l << 45),
            Long.numberOfLeadingZeros(1l << 46),
            Long.numberOfLeadingZeros(1l << 47),
            Long.numberOfLeadingZeros(1l << 48),
            Long.numberOfLeadingZeros(1l << 49),
            Long.numberOfLeadingZeros(1l << 50),
            Long.numberOfLeadingZeros(1l << 51),
            Long.numberOfLeadingZeros(1l << 52),
            Long.numberOfLeadingZeros(1l << 53),
            Long.numberOfLeadingZeros(1l << 54),
            Long.numberOfLeadingZeros(1l << 55),
            Long.numberOfLeadingZeros(1l << 56),
            Long.numberOfLeadingZeros(1l << 57),
            Long.numberOfLeadingZeros(1l << 58),
            Long.numberOfLeadingZeros(1l << 59),
            Long.numberOfLeadingZeros(1l << 60),
            Long.numberOfLeadingZeros(1l << 61),
            Long.numberOfLeadingZeros(1l << 62),
            Long.numberOfLeadingZeros(1l << 63),
        };
    }

    // Test the range of CLZ with random long input.
    // The result of CLZ should be in range [0, 64], so CLZs in this test are
    // expected to be optimized away, and the test should always return false.
    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_L)
    public boolean clzCompareLong(long randLong) {
        return Long.numberOfLeadingZeros(randLong) < 0
               || Long.numberOfLeadingZeros(randLong) > 64;
    }

    // Test the combination of CLZ and division by 8.
    // The result of CLZ should be positive, so the division by 8 should be
    // optimized to a simple right shift without rounding.
    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_L, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int clzDiv8Long(long randLong) {
        return Long.numberOfLeadingZeros(randLong) / 8;
    }

    // Test the output range of CLZ with random input range.
    @Test
    public int clzRandLimitLong(long randLong) {
        randLong = RANGE_LONG.clamp(randLong);
        int result = Long.numberOfLeadingZeros(randLong);
        return getResultChecksum64(result);
    }

    @DontCompile
    public int clzRandLimitInterpretedLong(long randLong) {
        randLong = RANGE_LONG.clamp(randLong);
        int result = Long.numberOfLeadingZeros(randLong);
        return getResultChecksum64(result);
    }

    // Test CTZ with constant integer inputs.
    // All CTZs in this test are expected to be optimized away.
    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_I)
    public int[] ctzConstInts() {
        return new int[] {
            Integer.numberOfTrailingZeros(0),
            Integer.numberOfTrailingZeros(1 << 0),
            Integer.numberOfTrailingZeros(1 << 1),
            Integer.numberOfTrailingZeros(1 << 2),
            Integer.numberOfTrailingZeros(1 << 3),
            Integer.numberOfTrailingZeros(1 << 4),
            Integer.numberOfTrailingZeros(1 << 5),
            Integer.numberOfTrailingZeros(1 << 6),
            Integer.numberOfTrailingZeros(1 << 7),
            Integer.numberOfTrailingZeros(1 << 8),
            Integer.numberOfTrailingZeros(1 << 9),
            Integer.numberOfTrailingZeros(1 << 10),
            Integer.numberOfTrailingZeros(1 << 11),
            Integer.numberOfTrailingZeros(1 << 12),
            Integer.numberOfTrailingZeros(1 << 13),
            Integer.numberOfTrailingZeros(1 << 14),
            Integer.numberOfTrailingZeros(1 << 15),
            Integer.numberOfTrailingZeros(1 << 16),
            Integer.numberOfTrailingZeros(1 << 17),
            Integer.numberOfTrailingZeros(1 << 18),
            Integer.numberOfTrailingZeros(1 << 19),
            Integer.numberOfTrailingZeros(1 << 20),
            Integer.numberOfTrailingZeros(1 << 21),
            Integer.numberOfTrailingZeros(1 << 22),
            Integer.numberOfTrailingZeros(1 << 23),
            Integer.numberOfTrailingZeros(1 << 24),
            Integer.numberOfTrailingZeros(1 << 25),
            Integer.numberOfTrailingZeros(1 << 26),
            Integer.numberOfTrailingZeros(1 << 27),
            Integer.numberOfTrailingZeros(1 << 28),
            Integer.numberOfTrailingZeros(1 << 29),
            Integer.numberOfTrailingZeros(1 << 30),
            Integer.numberOfTrailingZeros(1 << 31),
        };
    }

    // Test the range of CTZ with random integer input.
    // The result of CTZ should be in range [0, 32], so CTZs in this test are
    // expected to be optimized away, and the test should always return false.
    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_I)
    public boolean ctzCompareInt(int randInt) {
        return Integer.numberOfTrailingZeros(randInt) < 0
               || Integer.numberOfTrailingZeros(randInt) > 32;
    }

    // Test the combination of CTZ and division by 8.
    // The result of CTZ should be positive, so the division by 8 should be
    // optimized to a simple right shift without rounding.
    @Test
    @IR(counts = {IRNode.COUNT_TRAILING_ZEROS_I, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int ctzDiv8Int(int randInt) {
        return Integer.numberOfTrailingZeros(randInt) / 8;
    }

    // Test the output range of CTZ with random input range.
    @Test
    public int ctzRandLimitInt(int randInt) {
        randInt = RANGE_INT.clamp(randInt);
        int result = Integer.numberOfTrailingZeros(randInt);
        return getResultChecksum32(result);
    }

    @DontCompile
    public int ctzRandLimitInterpretedInt(int randInt) {
        randInt = RANGE_INT.clamp(randInt);
        int result = Integer.numberOfTrailingZeros(randInt);
        return getResultChecksum32(result);
    }

    // Test CTZ with constant long inputs.
    // All CTZs in this test are expected to be optimized away.
    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_L)
    public int[] ctzConstLongs() {
        return new int[] {
            Long.numberOfTrailingZeros(0),
            Long.numberOfTrailingZeros(1l << 0),
            Long.numberOfTrailingZeros(1l << 1),
            Long.numberOfTrailingZeros(1l << 2),
            Long.numberOfTrailingZeros(1l << 3),
            Long.numberOfTrailingZeros(1l << 4),
            Long.numberOfTrailingZeros(1l << 5),
            Long.numberOfTrailingZeros(1l << 6),
            Long.numberOfTrailingZeros(1l << 7),
            Long.numberOfTrailingZeros(1l << 8),
            Long.numberOfTrailingZeros(1l << 9),
            Long.numberOfTrailingZeros(1l << 10),
            Long.numberOfTrailingZeros(1l << 11),
            Long.numberOfTrailingZeros(1l << 12),
            Long.numberOfTrailingZeros(1l << 13),
            Long.numberOfTrailingZeros(1l << 14),
            Long.numberOfTrailingZeros(1l << 15),
            Long.numberOfTrailingZeros(1l << 16),
            Long.numberOfTrailingZeros(1l << 17),
            Long.numberOfTrailingZeros(1l << 18),
            Long.numberOfTrailingZeros(1l << 19),
            Long.numberOfTrailingZeros(1l << 20),
            Long.numberOfTrailingZeros(1l << 21),
            Long.numberOfTrailingZeros(1l << 22),
            Long.numberOfTrailingZeros(1l << 23),
            Long.numberOfTrailingZeros(1l << 24),
            Long.numberOfTrailingZeros(1l << 25),
            Long.numberOfTrailingZeros(1l << 26),
            Long.numberOfTrailingZeros(1l << 27),
            Long.numberOfTrailingZeros(1l << 28),
            Long.numberOfTrailingZeros(1l << 29),
            Long.numberOfTrailingZeros(1l << 30),
            Long.numberOfTrailingZeros(1l << 31),
            Long.numberOfTrailingZeros(1l << 32),
            Long.numberOfTrailingZeros(1l << 33),
            Long.numberOfTrailingZeros(1l << 34),
            Long.numberOfTrailingZeros(1l << 35),
            Long.numberOfTrailingZeros(1l << 36),
            Long.numberOfTrailingZeros(1l << 37),
            Long.numberOfTrailingZeros(1l << 38),
            Long.numberOfTrailingZeros(1l << 39),
            Long.numberOfTrailingZeros(1l << 40),
            Long.numberOfTrailingZeros(1l << 41),
            Long.numberOfTrailingZeros(1l << 42),
            Long.numberOfTrailingZeros(1l << 43),
            Long.numberOfTrailingZeros(1l << 44),
            Long.numberOfTrailingZeros(1l << 45),
            Long.numberOfTrailingZeros(1l << 46),
            Long.numberOfTrailingZeros(1l << 47),
            Long.numberOfTrailingZeros(1l << 48),
            Long.numberOfTrailingZeros(1l << 49),
            Long.numberOfTrailingZeros(1l << 50),
            Long.numberOfTrailingZeros(1l << 51),
            Long.numberOfTrailingZeros(1l << 52),
            Long.numberOfTrailingZeros(1l << 53),
            Long.numberOfTrailingZeros(1l << 54),
            Long.numberOfTrailingZeros(1l << 55),
            Long.numberOfTrailingZeros(1l << 56),
            Long.numberOfTrailingZeros(1l << 57),
            Long.numberOfTrailingZeros(1l << 58),
            Long.numberOfTrailingZeros(1l << 59),
            Long.numberOfTrailingZeros(1l << 60),
            Long.numberOfTrailingZeros(1l << 61),
            Long.numberOfTrailingZeros(1l << 62),
            Long.numberOfTrailingZeros(1l << 63),
        };
    }

    // Test the range of CTZ with random long input.
    // The result of CTZ should be in range [0, 64], so CTZs in this test are
    // expected to be optimized away, and the test should always return false.
    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_L)
    public boolean ctzCompareLong(long randLong) {
        return Long.numberOfTrailingZeros(randLong) < 0
               || Long.numberOfTrailingZeros(randLong) > 64;
    }

    // Test the combination of CTZ and division by 8.
    // The result of CTZ should be positive, so the division by 8 should be
    // optimized to a simple right shift without rounding.
    @Test
    @IR(counts = {IRNode.COUNT_TRAILING_ZEROS_L, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int ctzDiv8Long(long randLong) {
        return Long.numberOfTrailingZeros(randLong) / 8;
    }

    // Test the output range of CTZ with random input range.
    @Test
    public int ctzRandLimitLong(long randLong) {
        randLong = RANGE_LONG.clamp(randLong);
        int result = Long.numberOfLeadingZeros(randLong);
        return getResultChecksum64(result);
    }

    @DontCompile
    public int ctzRandLimitInterpretedLong(long randLong) {
        randLong = RANGE_LONG.clamp(randLong);
        int result = Long.numberOfLeadingZeros(randLong);
        return getResultChecksum64(result);
    }

    record IntRange(int lo, int hi) {
        IntRange {
            if (lo > hi) {
                throw new IllegalArgumentException("lo > hi");
            }
        }

        @ForceInline
        int clamp(int v) {
            return v < lo ? lo : v > hi ? hi : v;
        }

        static IntRange generate(Generator<Integer> g) {
            int a = g.next(), b = g.next();
            return a < b ? new IntRange(a, b) : new IntRange(b, a);
        }
    }

    record LongRange(long lo, long hi) {
        LongRange {
            if (lo > hi) {
                throw new IllegalArgumentException("lo > hi");
            }
        }

        @ForceInline
        long clamp(long v) {
            return v < lo ? lo : v > hi ? hi : v;
        }

        static LongRange generate(Generator<Long> g) {
            long a = g.next(), b = g.next();
            return a < b ? new LongRange(a, b) : new LongRange(b, a);
        }
    }

    @ForceInline
    int getResultChecksum32(int result) {
        int sum = 0;
        if (result < LIMITS_32_0) sum += 1;
        if (result < LIMITS_32_1) sum += 2;
        if (result < LIMITS_32_2) sum += 4;
        if (result < LIMITS_32_3) sum += 8;
        if (result > LIMITS_32_4) sum += 16;
        if (result > LIMITS_32_5) sum += 32;
        if (result > LIMITS_32_6) sum += 64;
        if (result > LIMITS_32_7) sum += 128;
        return sum;
    }

    @ForceInline
    int getResultChecksum64(int result) {
        int sum = 0;
        if (result < LIMITS_64_0) sum += 1;
        if (result < LIMITS_64_1) sum += 2;
        if (result < LIMITS_64_2) sum += 4;
        if (result < LIMITS_64_3) sum += 8;
        if (result > LIMITS_64_4) sum += 16;
        if (result > LIMITS_64_5) sum += 32;
        if (result > LIMITS_64_6) sum += 64;
        if (result > LIMITS_64_7) sum += 128;
        return sum;
    }
}
