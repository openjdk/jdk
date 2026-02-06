/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8375633
 * @requires vm.debug == true & vm.compiler2.enabled
 * @summary Test that ConvF2HF::Ideal optimizations are not missed with StressIncrementalInlining.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.ConvF2HFIdealizationStress
 */
public class ConvF2HFIdealizationStress {

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("-XX:-TieredCompilation",
                               "-XX:+UnlockDiagnosticVMOptions",
                               "-XX:+StressIncrementalInlining",
                               "-XX:VerifyIterativeGVN=1110");
        testFramework.start();
    }

    // Pattern: ConvF2HF(AddF(ConvHF2F(x), ConvHF2F(y))) => AddHF(x, y)
    @Test
    @IR(counts = {IRNode.ADD_HF, ">=1"},
        failOn = {IRNode.ADD_F, IRNode.CONV_F2HF, IRNode.CONV_HF2F},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, ">=1"},
        failOn = {IRNode.ADD_F, IRNode.CONV_F2HF, IRNode.CONV_HF2F},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public static short testAddHF(short a, short b) {
        return Float.floatToFloat16(Float.float16ToFloat(a) + Float.float16ToFloat(b));
    }

    @Run(test = "testAddHF")
    public void runAddHF() {
        short a = Float.floatToFloat16(1.0f);
        short b = Float.floatToFloat16(2.0f);
        short result = testAddHF(a, b);
        float fResult = Float.float16ToFloat(result);
        if (fResult != 3.0f) {
            throw new RuntimeException("Expected 3.0f but got " + fResult);
        }
    }

    // Pattern: ConvF2HF(SubF(ConvHF2F(x), ConvHF2F(y))) => SubHF(x, y)
    @Test
    @IR(counts = {IRNode.SUB_HF, ">=1"},
        failOn = {IRNode.SUB_F, IRNode.CONV_F2HF, IRNode.CONV_HF2F},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.SUB_HF, ">=1"},
        failOn = {IRNode.SUB_F, IRNode.CONV_F2HF, IRNode.CONV_HF2F},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public static short testSubHF(short a, short b) {
        return Float.floatToFloat16(Float.float16ToFloat(a) - Float.float16ToFloat(b));
    }

    @Run(test = "testSubHF")
    public void runSubHF() {
        short a = Float.floatToFloat16(3.0f);
        short b = Float.floatToFloat16(1.0f);
        short result = testSubHF(a, b);
        float fResult = Float.float16ToFloat(result);
        if (fResult != 2.0f) {
            throw new RuntimeException("Expected 2.0f but got " + fResult);
        }
    }
}
