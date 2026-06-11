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

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.RunMode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import java.util.Arrays;

public class MinMaxRed_Long {

    private static final int SIZE = 1024;
    private static final Generator<Long> GEN_LONG = Generators.G.longs();

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
        long[] longs = new long[SIZE];
        Generators.G.fill(GEN_LONG, longs);

        long res = 0;
        for (int j = 0; j < 2000; j++) {
            res = maxReductionImplement(longs, res);
        }

        final long expected = Arrays.stream(longs).map(l -> l * 11).max().getAsLong();
        if (res == expected) {
            System.out.println("Success");
        } else {
            throw new AssertionError("Failed, got result " + res + " but expected " + expected);
        }
    }

    @Run(test = {"minReductionImplement"},
         mode = RunMode.STANDALONE)
    public void runMinTest() {
        long[] longs = new long[SIZE];
        Generators.G.fill(GEN_LONG, longs);

        long res = 0;
        for (int j = 0; j < 2000; j++) {
            res = minReductionImplement(longs, res);
        }

        final long expected = Arrays.stream(longs).map(l -> l * 11).min().getAsLong();
        if (res == expected) {
            System.out.println("Success");
        } else {
            throw new AssertionError("Failed, got result " + res + " but expected " + expected);
        }
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
