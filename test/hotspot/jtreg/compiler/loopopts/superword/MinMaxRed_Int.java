/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8302673
 * @summary [SuperWord] MaxReduction and MinReduction should vectorize for int
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.MinMaxRed_Int
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

public class MinMaxRed_Int {
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
        int[] a = new int[1024];
        int[] b = new int[1024];
        ReductionInit(a, b);
        int res = 0;
        for (int j = 0; j < 2000; j++) {
            res = maxReductionImplement(a, b, res);
        }
        if (res == 0) {
            System.out.println("Success");
        } else {
            throw new AssertionError("Failed");
        }
    }


    @Run(test = {"minReductionImplement"},
         mode = RunMode.STANDALONE)
    public void runMinTest() {
        int[] a = new int[1024];
        int[] b = new int[1024];
        ReductionInit(a, b);
        int res = 1;
        for (int j = 0; j < 2000; j++) {
            res = minReductionImplement(a, b, res);
        }
        if (res == -1023*1023) {
            System.out.println("Success");
        } else {
            throw new AssertionError("Failed");
        }
    }

    public static void ReductionInit(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = -i;
            b[i] = i;
        }
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "true"},
        applyIfCPUFeatureOr = { "sse4.1", "true" , "asimd" , "true"},
        counts = {IRNode.MIN_REDUCTION_V, " > 0"})
    public static int minReductionImplement(int[] a, int[] b, int res) {
        for (int i = 0; i < a.length; i++) {
            res = Math.min(res, a[i] * b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "true"},
        applyIfCPUFeatureOr = { "sse4.1", "true" , "asimd" , "true"},
        counts = {IRNode.MAX_REDUCTION_V, " > 0"})
    public static int maxReductionImplement(int[] a, int[] b, int res) {
        for (int i = 0; i < a.length; i++) {
            res = Math.max(res, a[i] * b[i]);
        }
        return res;
    }
}
