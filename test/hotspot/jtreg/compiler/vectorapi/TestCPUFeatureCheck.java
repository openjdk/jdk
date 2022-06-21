/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;

/*
 * @test 8287525
 * @summary Extend IR annotation with new options to test specific target feature.
 * @requires vm.cpu.features ~= ".*avx512f.*"
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestCPUFeatureCheck
 */

public class TestCPUFeatureCheck {
    private static int a[] = new int[1000];
    private static int b[] = new int[1000];
    private static int res[] = new int[1000];

    public static void setup() {
        for (int i = 0; i < 1000; i++) {
            a[i] = i;
            b[i] = i;
        }
    }

    public static void main(String args[]) {
        setup();
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:UseAVX=3",
                                   "-XX:+UseKNLSetting",
                                   "-XX:CompileThresholdScaling=0.3");
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0"}, applyIfCPUFeature = {"avx512bw", "false"})
    public static void test1() {
        for (int i = 0; i < 1000; i++) {
            res[i] = a[i] + b[i];
        }
    }

    // IR rule is enforced if all the feature conditions holds good
    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0"}, applyIfCPUFeatureAnd = {"avx512bw", "false", "avx512f", "true"})
    public static void test2() {
        for (int i = 0; i < 1000; i++) {
            res[i] = a[i] + b[i];
        }
    }

    // IR rule is enforced if any of the feature condition holds good
    @Test
    @IR(counts = {IRNode.ADD_VI,  "> 0"}, applyIfCPUFeatureOr = {"avx512bw", "true", "avx512f", "true"})
    public static void test3() {
        for (int i = 0; i < 1000; i++) {
            res[i] = a[i] + b[i];
        }
    }
}
