/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8135028
 * @summary Add C2 x86 Superword support for scalar sum reduction optimizations : double sqrt test
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.SumRedSqrt_Double
 */


package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

public class SumRedSqrt_Double {
    public static void main(String[] args) throws Exception {
        TestFramework framework = new TestFramework();
        framework.addFlags("-XX:+IgnoreUnrecognizedVMOptions",
                           "-XX:LoopUnrollLimit=250",
                           "-XX:CompileThresholdScaling=0.1");
        int i = 0;
        Scenario[] scenarios = new Scenario[6];
        for (String reductionSign : new String[] {"+", "-"}) {
            for (int maxUnroll : new int[] {4, 8, 16}) {
                scenarios[i] = new Scenario(i, "-XX:" + reductionSign + "SuperWordReductions",
                                               "-XX:LoopMaxUnroll=" + maxUnroll);
                i++;
            }
        }
        framework.addScenarios(scenarios);
        framework.start();
    }

    @Run(test = {"sumReductionWithStoreImplement"},
         mode = RunMode.STANDALONE)
    public void runTests() throws Exception {
        double[] a = new double[256 * 1024];
        double[] b = new double[256 * 1024];
        double[] c = new double[256 * 1024];
        double[] d = new double[256 * 1024];
        sumReductionInit(a, b, c);
        double total = 0;
        double valid = 2.06157643776E14;
        for (int j = 0; j < 2000; j++) {
            total = sumReductionWithStoreImplement(a, b, c, d, total);
        }
        testCorrectness(total, valid, "sumReductionWithStore");
    }

    public static void sumReductionInit(
            double[] a,
            double[] b,
            double[] c) {
        for (int j = 0; j < 1; j++) {
            for (int i = 0; i < a.length; i++) {
                a[i] = i * 1 + j;
                b[i] = i * 1 - j;
                c[i] = i + j;
            }
        }
    }

    /* Vectorization is expected but not enabled without store.
       The store could be removed later.
       Require avx for SQRT_VD. */
    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.ADD_REDUCTION_VD, IRNode.SQRT_VD})
    @IR(applyIfCPUFeature = {"avx", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8"},
        counts = {IRNode.ADD_REDUCTION_VD, ">= 1", IRNode.SQRT_VD, ">= 1"})
    public static double sumReductionWithStoreImplement(
            double[] a,
            double[] b,
            double[] c,
            double[] d,
            double total) {
        for (int i = 0; i < a.length; i++) {
            d[i] = Math.sqrt(a[i] * b[i]) + Math.sqrt(a[i] * c[i]) + Math.sqrt(b[i] * c[i]);
            total += d[i];
        }
        return total;
    }

    public static void testCorrectness(
            double total,
            double valid,
            String op) throws Exception {
        if (total != valid) {
            throw new Exception(
                "Invalid total: " + total + " " +
                "Expected value = " + valid + " " + op + ": Failed");
        }
    }

}
