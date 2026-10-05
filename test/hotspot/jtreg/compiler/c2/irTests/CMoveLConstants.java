/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * bug 8336860
 * @summary Verify codegen for CMoveL with constants 0 and 1
 * @library /test/lib /
 * @run driver compiler.c2.irTests.CMoveLConstants
 */
public class CMoveLConstants {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = {IRNode.X86_CMOVEL_IMM01, "1"},
        applyIfPlatform = {"x64", "true"},
        phase = CompilePhase.FINAL_CODE)
    public static long testSigned(int a, int b) {
        return a > b ? 1L : 0L;
    }

    @Test
    @IR(counts = {IRNode.X86_CMOVEL_IMM01U, "1"},
        applyIfPlatform = {"x64", "true"},
        phase = CompilePhase.FINAL_CODE)
    public static long testUnsigned(int a, int b) {
        return Integer.compareUnsigned(a, b) > 0 ? 1L : 0L;
    }

    @Test
    @IR(counts = {IRNode.X86_CMOVEL_IMM01UCF, "1"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeatureOr = {"apx_f", "false", "avx10_2", "false"},
        phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_CMOVEL_IMM01UCFE, "1"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeatureAnd = {"apx_f", "true", "avx10_2", "true"},
        phase = CompilePhase.FINAL_CODE)
    public static long testFloat(float a, float b) {
        return a > b ? 1L : 0L;
    }

    @Test
    @IR(counts = {IRNode.X86_CMOVEL_IMM01UCF, "1"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeatureOr = {"apx_f", "false", "avx10_2", "false"},
        phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_CMOVEL_IMM01UCFE, "1"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeatureAnd = {"apx_f", "true", "avx10_2", "true"},
        phase = CompilePhase.FINAL_CODE)
    public static long testDouble(double a, double b) {
        return a > b ? 1L : 0L;
    }

    @DontCompile
    public void assertResults(int a, int b) {
        Asserts.assertEQ(a > b ? 1L : 0L, testSigned(a, b));
        Asserts.assertEQ(Integer.compareUnsigned(a, b) > 0 ? 1L : 0L, testUnsigned(a, b));
        Asserts.assertEQ((float) a > (float) b ? 1L : 0L, testFloat(a, b));
        Asserts.assertEQ((double) a > (double) b ? 1L : 0L, testDouble(a, b));
    }

    @Run(test = {"testSigned", "testUnsigned", "testFloat", "testDouble"})
    public void runMethod() {
        assertResults(10, 20);
        assertResults(20, 10);
    }
}
