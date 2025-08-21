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
 * @bug 8074981
 * @summary Add C2 x86 Superword support for scalar product reduction optimizations : int test
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.ProdRed_Int
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

public class ProdRed_Int {
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

    @Run(test = {"prodReductionImplement"},
         mode = RunMode.STANDALONE)
    public void runTests() throws Exception {
        int[] a = new int[256 * 1024];
        int[] b = new int[256 * 1024];
        prodReductionInit(a, b);
        int valid = 419430401;
        int total = 1;
        for (int j = 0; j < 2000; j++) {
            total = prodReductionImplement(a, b, total);
        }
        if (total == valid) {
            System.out.println("Success");
        } else {
            System.out.println("Invalid sum of elements variable in total: " + total);
            System.out.println("Expected value = " + valid);
            throw new Exception("Failed");
        }
    }

    public static void prodReductionInit(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = i + 2;
            b[i] = i + 1;
        }
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.MUL_REDUCTION_VI})
    @IR(applyIfCPUFeature = {"sse4.1", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8"},
        counts = {IRNode.MUL_REDUCTION_VI, ">= 1", IRNode.MUL_REDUCTION_VI, "<= 2"}) // one for main-loop, one for vector-post-loop
    @IR(applyIfCPUFeature = {"rvv", "true"},
        applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8"},
        counts = {IRNode.MUL_REDUCTION_VI, ">= 1", IRNode.MUL_REDUCTION_VI, "<= 2"}) // one for main-loop, one for vector-post-loop
    public static int prodReductionImplement(int[] a, int[] b, int total) {
        for (int i = 0; i < a.length; i++) {
            total *= a[i] + b[i];
        }
        return total;
    }

}
