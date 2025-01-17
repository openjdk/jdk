/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
        long[] longs = reductionInit(probability);
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
        long[] longs = reductionInit(probability);
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

    public static long[] reductionInit(int probability) {
        int aboveCount, abovePercent;
        long[] longs = new long[1024];

        // Generates an array of numbers such that as the array is iterated
        // there is P probability of finding a new max value,
        // and 100-P probability of not finding a new max value.
        // The algorithm loops around if the distribution does not match the probability,
        // but it approximates the probability as the array sizes increase.
        // The worst case of this algorithm is when the desired array size is 100
        // and the aim is to get 50% of probability, which can only be satisfied
        // with 50 elements being a new max. This situation can take 15 rounds.
        // As sizes increase, say 10'000 elements,
        // the number of elements that have to satisfy 50% increases,
        // so the algorithm will stop as an example when 5027 elements are a new max values.
        // Also, probability values in the edges will achieve their objective quicker,
        // with 0% or 100% probability doing it in a single loop.
        // To support the same algorithm for min calculations,
        // negating the array elements achieves the same objective.
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
                    long diffToMax = random.nextLong(10) + 1;
                    value = max - diffToMax;
                }
                longs[i] = value;
                max = Math.max(max, value);
            }

            abovePercent = ((aboveCount + 1) * 100) / longs.length;
        } while (abovePercent != probability);

        return longs;
    }

    @Test
    @IR(applyIfAnd = {"SuperWordReductions", "true", "MaxVectorSize", ">=32"},
        applyIfCPUFeatureOr = {"avx512", "true", "asimd" , "true"},
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
        applyIfCPUFeatureOr = {"avx512", "true", "asimd" , "true"},
        counts = {IRNode.MAX_REDUCTION_V, " > 0"})
    public static long maxReductionImplement(long[] a, long res) {
        for (int i = 0; i < a.length; i++) {
            final long v = 11 * a[i];
            res = Math.max(res, v);
        }
        return res;
    }
}
