/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8307513
 * @summary [SuperWord] MaxReduction and MinReduction should vectorize for long
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.MinMaxRed_Long
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.LongStream;

public class MinMaxRed_Long {

    private static final Random random = Utils.getRandomInstance();

    public static void main(String[] args) throws Exception {
        TestFramework framework = new TestFramework();
        framework.addFlags("-XX:+IgnoreUnrecognizedVMOptions",
                           "-XX:LoopUnrollLimit=250",
                           "-XX:CompileThresholdScaling=0.1");
        framework.start();
    }

    @Run(test = {"maxReductionImplement"},
         mode = RunMode.STANDALONE)
    public void runMaxTest() {
        runMaxTest(50);
        runMaxTest(80);
        runMaxTest(100);
    }

    private static void runMaxTest(int probability) {
        long[] longs = new long[1024];
        ReductionInit(longs, probability);
        long res = 0;
        for (int j = 0; j < 2000; j++) {
            res = maxReductionImplement(longs, res);
        }
        if (res == 11 * Arrays.stream(longs).max().getAsLong()) {
            System.out.println("Success");
        } else {
            throw new AssertionError("Failed");
        }
    }

    @Run(test = {"minReductionImplement"},
         mode = RunMode.STANDALONE)
    public void runMinTest() {
        runMinTest(50);
        runMinTest(80);
        runMinTest(100);
    }

    private static void runMinTest(int probability) {
        long[] longs = new long[1024];
        ReductionInit(longs, probability);
        // Negating the values generated for controlling max branching
        // allows same logic to be used for min tests.
        longs = negate(longs);
        long res = 0;
        for (int j = 0; j < 2000; j++) {
            res = minReductionImplement(longs, res);
        }
        if (res == 11 * Arrays.stream(longs).min().getAsLong()) {
            System.out.println("Success");
        } else {
            throw new AssertionError("Failed");
        }
    }

    static long[] negate(long[] nums) {
        return LongStream.of(nums).map(l -> -l).toArray();
    }

    public static void ReductionInit(long[] longs, int probability) {
        int aboveCount, abovePercent;

        // Iterate until you find a set that matches the requirement probability
        do {
            long max = random.nextLong(10);
            longs[0] = max;

            aboveCount = 0;
            for (int i = 1; i < longs.length; i++) {
                long value;
                if (random.nextLong(101) <= probability) {
                    long increment = random.nextLong(10);
                    value = max + increment;
                    aboveCount++;
                } else {
                    // Decrement by at least 1
                    long decrement = random.nextLong(10) + 1;
                    value = max - decrement;
                }
                longs[i] = value;
                max = Math.max(max, value);
            }

            abovePercent = ((aboveCount + 1) * 100) / longs.length;
        } while (abovePercent != probability);
    }

    @Test
    @IR(applyIfAnd = {"SuperWordReductions", "true", "MaxVectorSize", ">=32"},
        counts = {IRNode.MIN_REDUCTION_V, " > 0"})
    public static long minReductionImplement(long[] a, long res) {
        for (int i = 0; i < a.length; i++) {
            final long v = 11 * a[i];
            res = Math.min(res, v);
        }
        return res;
    }

    @Test
    @IR(applyIfAnd = {"SuperWordReductions", "true", "MaxVectorSize", ">=32"},
        counts = {IRNode.MAX_REDUCTION_V, " > 0"})
    public static long maxReductionImplement(long[] a, long res) {
        for (int i = 0; i < a.length; i++) {
            final long v = 11 * a[i];
            res = Math.max(res, v);
        }
        return res;
    }
}
