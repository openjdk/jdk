/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.DontCompile;
import compiler.lib.ir_framework.ForceInline;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8356813
 * @summary Test that Value method of ModLNode is working as expected.
 * @key randomness
 * @library /test/lib /
 * @run driver compiler.c2.gvn.ModLNodeValueTests
 */
public class ModLNodeValueTests {
    private static final Generator<Long> LONG_GEN = Generators.G.longs();
    private static final long POS_LONG = Generators.G.longs().restricted(1L, Long.MAX_VALUE).next();
    private static final long NEG_LONG = Generators.G.longs().restricted(Long.MIN_VALUE, -1L).next();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {
        "nonNegativeDividend", "nonNegativeDividendInRange",
        "negativeDividend", "negativeDividendInRange",
        "modByKnownBoundsUpper", "modByKnownBoundsUpperInRange",
        "modByKnownBoundsLower", "modByKnownBoundsLowerInRange",
        "modByKnownBoundsLimitedByDividendUpper", "modByKnownBoundsLimitedByDividendUpperInRange",
        "modByKnownBoundsLimitedByDividendLower", "modByKnownBoundsLimitedByDividendLowerInRange",
        "testRandomLimits"
    })
    public void runMethod() {
        long a = LONG_GEN.next();
        long b = LONG_GEN.next();

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(0, 0);
        assertResult(a, b);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(long x, long y) {
        Asserts.assertEQ(x != 0 && POS_LONG % x < 0, nonNegativeDividend(x));
        Asserts.assertEQ(x != 0 && POS_LONG % x <= 0, nonNegativeDividendInRange(x));
        Asserts.assertEQ(x != 0 && NEG_LONG % x > 0, negativeDividend(x));
        Asserts.assertEQ(x != 0 && NEG_LONG % x >= 0, negativeDividendInRange(x));
        Asserts.assertEQ(x % (((byte) y) + 129L) > 255, modByKnownBoundsUpper(x, y));
        Asserts.assertEQ(x % (((byte) y) + 129L) >= 255, modByKnownBoundsUpperInRange(x, y));
        Asserts.assertEQ(x % (((byte) y) + 129L) < -255, modByKnownBoundsLower(x, y));
        Asserts.assertEQ(x % (((byte) y) + 129L) <= -255, modByKnownBoundsLowerInRange(x, y));
        Asserts.assertEQ(((byte) x) % (((char) y) + 1L) > 127, modByKnownBoundsLimitedByDividendUpper(x, y));
        Asserts.assertEQ(((byte) x) % (((char) y) + 1L) >= 127, modByKnownBoundsLimitedByDividendUpperInRange(x, y));
        Asserts.assertEQ(((byte) x) % (((char) y) + 1L) < -128, modByKnownBoundsLimitedByDividendLower(x, y));
        Asserts.assertEQ(((byte) x) % (((char) y) + 1L) <= -128, modByKnownBoundsLimitedByDividendLowerInRange(x, y));

        int res;
        try {
            res = testRandomLimitsInterpreted(x, y);
        } catch (ArithmeticException _) {
            try {
                testRandomLimits(x, y);
                Asserts.fail("Expected ArithmeticException");
                return; // unreachable
            } catch (ArithmeticException _) {
                return; // test succeeded, no result to assert
            }
        }
        Asserts.assertEQ(res, testRandomLimits(x, y));
    }

    @Test
    @IR(failOn = {IRNode.MOD_L, IRNode.CMP_L})
    // The sign of the result of % is the same as the sign of the dividend,
    // i.e., posVal % x < 0 => false.
    public boolean nonNegativeDividend(long x) {
        return x != 0 && POS_LONG % x < 0;
    }

    @Test
    @IR(counts = {IRNode.MOD_L, "1"})
    // The sign of the result of % is the same as the sign of the dividend,
    // i.e., posVal % x < 0 => false.
    // This uses <= to verify the % is not optimized away
    public boolean nonNegativeDividendInRange(long x) {
        return x != 0 && POS_LONG % x <= 0;
    }

    @Test
    @IR(failOn = {IRNode.MOD_L, IRNode.CMP_L})
    // The sign of the result of % is the same as the sign of the dividend,
    // i.e., negValue % x > 0 => false.
    public boolean negativeDividend(long x) {
        return x != 0 && NEG_LONG % x > 0;
    }

    @Test
    @IR(counts = {IRNode.MOD_L, "1"})
    // The sign of the result of % is the same as the sign of the dividend,
    // i.e., negValue % x > 0 => false.
    // This uses >= to verify the % is not optimized away
    public boolean negativeDividendInRange(long x) {
        return x != 0 && NEG_LONG % x >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MOD_L, IRNode.CMP_L})
    // The magnitude of the result is less than the divisor.
    public boolean modByKnownBoundsUpper(long x, long y) {
        // d = ((byte) y) + 129 => [1, 256] divisor
        // x % d => [-255, 255]
        return x % (((byte) y) + 129L) > 255;
    }

    @Test
    @IR(counts = {IRNode.MOD_L, "1"})
    // The magnitude of the result is less than the divisor.
    public boolean modByKnownBoundsUpperInRange(long x, long y) {
        // d = ((byte) y) + 129 => [1, 256] divisor
        // x % d => [-255, 255]
        // in bounds, cannot optimize
        return x % (((byte) y) + 129L) >= 255;
    }

    @Test
    @IR(failOn = {IRNode.MOD_L, IRNode.CMP_L})
    // The magnitude of the result is less than the divisor
    public boolean modByKnownBoundsLower(long x, long y) {
        // d = ((byte) y) + 129 => [1, 256] divisor
        // x % d => [-255, 255]
        return x % (((byte) y) + 129L) < -255;
    }

    @Test
    @IR(counts = {IRNode.MOD_L, "1"})
    // The magnitude of the result is less than the divisor
    public boolean modByKnownBoundsLowerInRange(long x, long y) {
        // d = ((byte) y) + 129 => [1, 256] divisor
        // x % d => [-255, 255]
        // in bounds, cannot optimize
        return x % (((byte) y) + 129L) <= -255;
    }

    @Test
    @IR(failOn = {IRNode.MOD_L, IRNode.CMP_L})
    // The result is closer to zero than or equal to the dividend.
    public boolean modByKnownBoundsLimitedByDividendUpper(long x, long y) {
        // d = ((char) y) + 1 => [1, 65536] divisor
        // e = ((byte) x) => [-128, 127]
        // e % d => [-128, 127]
        return ((byte) x) % (((char) y) + 1L) > 127;
    }

    @Test
    @IR(counts = {IRNode.MOD_L, "1"})
    // The result is closer to zero than or equal to the dividend.
    public boolean modByKnownBoundsLimitedByDividendUpperInRange(long x, long y) {
        // d = ((char) y) + 1 => [1, 65536] divisor
        // e = ((byte) x) => [-128, 127]
        // e % d => [-128, 127]
        // in bounds, cannot optimize
        return ((byte) x) % (((char) y) + 1L) >= 127;
    }

    @Test
    @IR(failOn = {IRNode.MOD_L, IRNode.CMP_L})
    // The result is closer to zero than or equal to the dividend.
    public boolean modByKnownBoundsLimitedByDividendLower(long x, long y) {
        // d = ((char) y) + 1 => [1, 65536] divisor
        // e = ((byte) x) => [-128, 127]
        // e % d => [-128, 127]
        return ((byte) x) % (((char) y) + 1L) < -128;
    }

    @Test
    @IR(counts = {IRNode.MOD_L, "1"})
    // The result is closer to zero than or equal to the dividend.
    public boolean modByKnownBoundsLimitedByDividendLowerInRange(long x, long y) {
        // d = ((char) y) + 1 => [1, 65536] divisor
        // e = ((byte) x) => [-128, 127]
        // e % d => [-128, 127]
        // in bounds, cannot optimize
        return ((byte) x) % (((char) y) + 1L) <= -128;
    }


    private static final long LIMIT_1 = LONG_GEN.next();
    private static final long LIMIT_2 = LONG_GEN.next();
    private static final long LIMIT_3 = LONG_GEN.next();
    private static final long LIMIT_4 = LONG_GEN.next();
    private static final long LIMIT_5 = LONG_GEN.next();
    private static final long LIMIT_6 = LONG_GEN.next();
    private static final long LIMIT_7 = LONG_GEN.next();
    private static final long LIMIT_8 = LONG_GEN.next();
    private static final Range RANGE_1 = Range.generate(LONG_GEN);
    private static final Range RANGE_2 = Range.generate(LONG_GEN);

    @Test
    public int testRandomLimits(long x, long y) {
        x = RANGE_1.clamp(x);
        y = RANGE_2.clamp(y);
        long z = x % y;

        int sum = 0;
        if (z < LIMIT_1) sum += 1;
        if (z < LIMIT_2) sum += 2;
        if (z < LIMIT_3) sum += 4;
        if (z < LIMIT_4) sum += 8;
        if (z > LIMIT_5) sum += 16;
        if (z > LIMIT_6) sum += 32;
        if (z > LIMIT_7) sum += 64;
        if (z > LIMIT_8) sum += 128;

        return sum;
    }

    @DontCompile
    public int testRandomLimitsInterpreted(long x, long y) {
        x = RANGE_1.clamp(x);
        y = RANGE_2.clamp(y);
        long z = x % y;

        int sum = 0;
        if (z < LIMIT_1) sum += 1;
        if (z < LIMIT_2) sum += 2;
        if (z < LIMIT_3) sum += 4;
        if (z < LIMIT_4) sum += 8;
        if (z > LIMIT_5) sum += 16;
        if (z > LIMIT_6) sum += 32;
        if (z > LIMIT_7) sum += 64;
        if (z > LIMIT_8) sum += 128;

        return sum;
    }

    record Range(long lo, long hi) {
        Range {
            if (lo > hi) {
                throw new IllegalArgumentException("lo > hi");
            }
        }

        @ForceInline
        long clamp(long v) {
            return Math.min(hi, Math.max(v, lo));
        }

        static Range generate(Generator<Long> g) {
            var a = g.next();
            var b = g.next();
            if (a > b) {
                var tmp = a;
                a = b;
                b = tmp;
            }
            return new Range(a, b);
        }
    }
}
