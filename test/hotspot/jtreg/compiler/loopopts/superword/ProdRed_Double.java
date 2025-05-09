/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8074981
 * @summary Add C2 x86 Superword support for scalar product reduction optimizations : float test
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.ProdRed_Double
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

public class ProdRed_Double {
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

    @Run(test = {"prodReductionImplement",
                 "prodReductionWithStoreImplement"},
         mode = RunMode.STANDALONE)
    public void runTests() throws Exception {
        double[] a = new double[256 * 1024];
        double[] b = new double[256 * 1024];
        double[] c = new double[256 * 1024];
        prodReductionInit(a, b);
        double valid = 2000;
        double total = 0;
        for (int j = 0; j < 2000; j++) {
            total = j + 1;
            total = prodReductionImplement(a, b, total);
        }
        testCorrectness(valid, total, "prodReduction");
        total = 0;
        for (int j = 0; j < 2000; j++) {
            total = j + 1;
            total = prodReductionWithStoreImplement(a, b, c, total);
        }
        testCorrectness(valid, total, "prodReductionWithStore");
    }

    public static void prodReductionInit(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = i + 2;
            b[i] = i + 1;
        }
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.MUL_REDUCTION_VD})
    @IR(applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8"},
        applyIfCPUFeature = {"sse2", "true"},
        counts = {IRNode.MUL_REDUCTION_VD, ">= 1"})
    // There is no efficient way to implement strict-ordered version on riscv64.
    @IR(applyIf = {"SuperWordReductions", "true"},
        applyIfCPUFeature = {"rvv", "true"},
        failOn = {IRNode.MUL_REDUCTION_VD})
    public static double prodReductionImplement(double[] a, double[] b, double total) {
        for (int i = 0; i < a.length; i++) {
            total *= a[i] - b[i];
        }
        return total;
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.MUL_REDUCTION_VD})
    @IR(applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8"},
        applyIfCPUFeature = {"sse2", "true"},
        counts = {IRNode.MUL_REDUCTION_VD, ">= 1"})
    // There is no efficient way to implement strict-ordered version on riscv64.
    @IR(applyIf = {"SuperWordReductions", "true"},
        applyIfCPUFeature = {"rvv", "true"},
        failOn = {IRNode.MUL_REDUCTION_VD})
    public static double prodReductionWithStoreImplement(double[] a, double[] b, double[] c, double total) {
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] - b[i];
            total *= c[i];
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
