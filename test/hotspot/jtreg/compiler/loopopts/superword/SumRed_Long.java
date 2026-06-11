/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8076276
 * @summary Add C2 x86 Superword support for scalar sum reduction optimizations : long test
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.SumRed_Long
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

public class SumRed_Long {
    public static void main(String[] args) throws Exception {
        TestFramework framework = new TestFramework();
        framework.addFlags("-XX:+IgnoreUnrecognizedVMOptions",
                           "-XX:LoopUnrollLimit=250",
                           "-XX:CompileThresholdScaling=0.1");
        int i = 0;
        Scenario[] scenarios = new Scenario[4];
        for (String reductionSign : new String[] {"+", "-"}) {
            for (int maxUnroll : new int[] {4, 8}) {
                scenarios[i] = new Scenario(i, "-XX:" + reductionSign + "SuperWordReductions",
                                               "-XX:LoopMaxUnroll=" + maxUnroll);
                i++;
            }
        }
        framework.addScenarios(scenarios);
        framework.start();
    }

    @Run(test = {"sumReductionImplement"},
         mode = RunMode.STANDALONE)
    public static void runTests() throws Exception {
        long[] a = new long[256 * 1024];
        long[] b = new long[256 * 1024];
        long[] c = new long[256 * 1024];
        sumReductionInit(a, b, c);
        long total = 0;
        long valid = 262144000;
        for (int j = 0; j < 2000; j++) {
            total = sumReductionImplement(a, b, c, total);
        }
        total = (int) total;
        if (total == valid) {
            System.out.println("Success");
        } else {
            System.out.println("Invalid sum of elements variable in total: " + total);
            System.out.println("Expected value = " + valid);
            throw new Exception("Failed");
        }
    }

    public static void sumReductionInit(
            long[] a,
            long[] b,
            long[] c) {
        for (int j = 0; j < 1; j++) {
            for (int i = 0; i < a.length; i++) {
                a[i] = i * 1 + j;
                b[i] = i * 1 - j;
                c[i] = i + j;
            }
        }
    }

    /* Require avx2 due to filtering out small vector lengths
       for long and int (SuperWord::implemented). */
    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.ADD_REDUCTION_VL})
    @IR(applyIfPlatform = {"32-bit", "false"},
        applyIfCPUFeature = {"avx2", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8"},
        counts = {IRNode.ADD_REDUCTION_VL, ">= 1", IRNode.ADD_REDUCTION_VL, "<= 2"}) // one for main-loop, one for vector-post-loop
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfCPUFeature = {"rvv", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8", "MaxVectorSize", ">=32"},
        counts = {IRNode.ADD_REDUCTION_VL, ">= 1", IRNode.ADD_REDUCTION_VL, "<= 2"}) // one for main-loop, one for vector-post-loop
    public static long sumReductionImplement(
            long[] a,
            long[] b,
            long[] c,
            long total) {
        for (int i = 0; i < a.length; i++) {
            total += (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
        }
        return total;
    }

}
