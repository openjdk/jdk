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

package compiler.c2.igvn;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8389579
 * @key randomness
 * @summary Test missing Ideal optimization opportunity for CompressBits and ExpandBits.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

public class TestMissingCompressBitsAndExpandBitsIdeal {
    private static final Generator<Integer> INTS = Generators.G.ints();
    private static final Generator<Long> LONGS = Generators.G.longs();

    private static final RestrictableGenerator<Integer> INT_SHIFTS = Generators.G.ints()
                                                                               .restricted(1, Integer.SIZE - 1);
    private static final RestrictableGenerator<Integer> LONG_SHIFTS = Generators.G.ints()
                                                                                .restricted(1, Long.SIZE - 1);

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions",
                                   "-XX:VerifyIterativeGVN=100");
    }

    // compress(x, 1 << n) == (x >> n) & 1
    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.COMPRESS_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static int testIntCompressWithOneLeftShift(int value, int shift) {
        int i;
        for (i = -10; i < 1; i++) {
        }
        int mask = i << shift;
        return Integer.compress(value, mask);
    }

    // compress(x, -1 << n) == x >>> n
    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.COMPRESS_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static int testIntCompressWithMinusOneLeftShift(int value, int shift) {
        int i;
        for (i = -10; i < -1; i++) {
        }
        int mask = i << shift;
        return Integer.compress(value, mask);
    }

    // compress(x, 1L << n) == (x >> n) & 1L
    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.COMPRESS_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static long testLongCompressWithOneLeftShift(long value, int shift) {
        long i;
        for (i = -10; i < 1; i++) {
        }
        long mask = i << shift;
        return Long.compress(value, mask);
    }

    // compress(x, -1L << n) == x >>> n
    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.COMPRESS_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static long testLongCompressWithMinusOneLeftShift(long value, int shift) {
        long i;
        for (i = -10; i < -1; i++) {
        }
        long mask = i << shift;
        return Long.compress(value, mask);
    }

    // compress(expand(x, m), m) == x & compress(m, m)
    @Test
    @IR(counts = {IRNode.EXPAND_BITS, "> 0",
                  IRNode.COMPRESS_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.EXPAND_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static int testIntCompressExpandWithSameMask(int value, int mask) {
        int i;
        for (i = -10; i < 1; i++) {
        }
        int expandMask = mask * i;
        return Integer.compress(Integer.expand(value, expandMask), mask);
    }

    // compress(expand(x, m), m) == x & compress(m, m)
    @Test
    @IR(counts = {IRNode.EXPAND_BITS, "> 0",
                  IRNode.COMPRESS_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.EXPAND_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static long testLongCompressExpandWithSameMask(long value, long mask) {
        long i;
        for (i = -10; i < 1; i++) {
        }
        long expandMask = mask * i;
        return Long.compress(Long.expand(value, expandMask), mask);
    }

    // expand(x, 1 << n) == (x & 1) << n
    @Test
    @IR(counts = {IRNode.EXPAND_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.EXPAND_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static int testIntExpandWithOneLeftShift(int value, int shift) {
        int result = 0;
        for (int i = 1; i >= 1; i--) {
            int x = value;
            result = Integer.expand(x, i << shift);
        }
        return result;
    }

    // expand(x, -1 << n) == x << n
    @Test
    @IR(counts = {IRNode.EXPAND_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.EXPAND_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static int testIntExpandWithMinusOneLeftShift(int value, int shift) {
        int result = 0;
        for (int i = -1; i >= -1; i--) {
            int x = value;
            result = Integer.expand(x, i << shift);
        }
        return result;
    }

    // expand(x, 1L << n) == (x & 1L) << n
    @Test
    @IR(counts = {IRNode.EXPAND_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.EXPAND_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static long testLongExpandWithOneLeftShift(long value, int shift) {
        long result = 0;
        for (long i = 1L; i >= 1L; i--) {
            long x = value;
            result = Long.expand(x, i << shift);
        }
        return result;
    }

    // expand(x, -1L << n) == x << n
    @Test
    @IR(counts = {IRNode.EXPAND_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.EXPAND_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static long testLongExpandWithMinusOneLeftShift(long value, int shift) {
        long result = 0;
        for (long i = -1L; i >= -1L; i--) {
            long x = value;
            result = Long.expand(x, i << shift);
        }
        return result;
    }

    // expand(compress(x, m), m) == x & m
    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, "> 0",
                  IRNode.EXPAND_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.EXPAND_BITS, IRNode.COMPRESS_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static int testIntExpandCompressWithSameMask(int value, int mask) {
        int result = 0;
        for (int i = 1; i >= 1; i--) {
            int x = value;
            result = Integer.expand(Integer.compress(x, mask * i), mask);
        }
        return result;
    }

    // expand(compress(x, m), m) == x & m
    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, "> 0",
                  IRNode.EXPAND_BITS, "> 0"},
        phase = {CompilePhase.AFTER_PARSING},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.EXPAND_BITS, IRNode.COMPRESS_BITS},
        applyIfCPUFeature = {"bmi2", "true"})
    public static long testLongExpandCompressWithSameMask(long value, long mask) {
        long result = 0;
        for (long i = 1L; i >= 1L; i--) {
            long x = value;
            result = Long.expand(Long.compress(x, mask * i), mask);
        }
        return result;
    }

    @Run(test = {"testIntCompressWithOneLeftShift",
                 "testIntCompressWithMinusOneLeftShift",
                 "testLongCompressWithOneLeftShift",
                 "testLongCompressWithMinusOneLeftShift",
                 "testIntCompressExpandWithSameMask",
                 "testLongCompressExpandWithSameMask",
                 "testIntExpandWithOneLeftShift",
                 "testIntExpandWithMinusOneLeftShift",
                 "testLongExpandWithOneLeftShift",
                 "testLongExpandWithMinusOneLeftShift",
                 "testIntExpandCompressWithSameMask",
                 "testLongExpandCompressWithSameMask"})
    public static void runTest() {
        for (int i = 0; i < 100; i++) {
            int intValue = INTS.next();
            int intShift = INT_SHIFTS.next();
            int intMask = INTS.next();
            long longValue = LONGS.next();
            int longShift = LONG_SHIFTS.next();
            long longMask = LONGS.next();

            Asserts.assertEQ(testIntCompressWithOneLeftShift(intValue, intShift),
                             (intValue >> intShift) & 1);
            Asserts.assertEQ(testIntCompressWithMinusOneLeftShift(intValue, intShift),
                             intValue >>> intShift);

            Asserts.assertEQ(testLongCompressWithOneLeftShift(longValue, longShift),
                             (longValue >> longShift) & 1L);
            Asserts.assertEQ(testLongCompressWithMinusOneLeftShift(longValue, longShift),
                             longValue >>> longShift);

            Asserts.assertEQ(testIntCompressExpandWithSameMask(intValue, intMask),
                             intValue & Integer.compress(intMask, intMask));
            Asserts.assertEQ(testLongCompressExpandWithSameMask(longValue, longMask),
                             longValue & Long.compress(longMask, longMask));

            Asserts.assertEQ(testIntExpandWithOneLeftShift(intValue, intShift),
                             (intValue & 1) << intShift);
            Asserts.assertEQ(testIntExpandWithMinusOneLeftShift(intValue, intShift),
                             intValue << intShift);

            Asserts.assertEQ(testLongExpandWithOneLeftShift(longValue, longShift),
                             (longValue & 1L) << longShift);
            Asserts.assertEQ(testLongExpandWithMinusOneLeftShift(longValue, longShift),
                             longValue << longShift);

            Asserts.assertEQ(testIntExpandCompressWithSameMask(intValue, intMask),
                             intValue & intMask);
            Asserts.assertEQ(testLongExpandCompressWithSameMask(longValue, longMask),
                             longValue & longMask);
        }
    }
}
