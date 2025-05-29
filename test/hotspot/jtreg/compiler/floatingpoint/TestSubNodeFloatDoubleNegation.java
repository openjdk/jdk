/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8351515
 * @summary The compiler must not fold `0-(0-x)` for x: float | double
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver compiler.floatingpoint.TestSubNodeFloatDoubleNegation
 */
package compiler.floatingpoint;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.Float16;
import jdk.test.lib.Asserts;

public class TestSubNodeFloatDoubleNegation {

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:CompileCommand=inline,jdk.incubator.vector.Float16::*");
    }

    @Run(test = { "testHalfFloat", "testFloat", "testDouble" })
    public static void assertResults() {
        short halfFloatRes = Float16.float16ToShortBits(testHalfFloat(Float16.valueOf(-0.0f)));
        int floatRes = Float.floatToIntBits(testFloat(-0.0f));
        long doubleRes = Double.doubleToLongBits(testDouble(-0.0));

        Asserts.assertEQ((short) 0, halfFloatRes);
        Asserts.assertEQ((int) 0, floatRes);
        Asserts.assertEQ((long) 0, doubleRes);
    }

    @Test
    @IR(counts = { IRNode.SUB, "2" }, applyIfPlatform = {"x64", "true"}, applyIfCPUFeature = {"avx512_fp16", "false"})
    @IR(counts = { IRNode.SUB_HF, "2" }, applyIfPlatform = {"x64", "true"}, applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = { IRNode.SUB, "2" }, applyIfPlatform = {"aarch64", "true"}, applyIfCPUFeatureAnd = {"fphp", "false", "asimdhp", "false"})
    @IR(counts = { IRNode.SUB_HF, "2" }, applyIfPlatform = {"aarch64", "true"}, applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @IR(counts = { IRNode.SUB, "2" }, applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"zfh", "false"})
    @IR(counts = { IRNode.SUB_HF, "2" }, applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"zfh", "true"})
    @IR(counts = { IRNode.SUB, "2" }, applyIfPlatformAnd = {"x64", "false", "aarch64", "false", "riscv64", "false"})
    // Checks that the subtractions in 0 - (0 - hf) do not get eliminiated
    public static Float16 testHalfFloat(Float16 hf) {
        return Float16.subtract(
                Float16.shortBitsToFloat16((short) 0),
                Float16.subtract(Float16.shortBitsToFloat16((short) 0), hf));
    }

    @Test
    @IR(counts = { IRNode.SUB_F, "2" })
    // Checks that the subtractions in 0 - (0 - f) do not get eliminated
    public static float testFloat(float f) {
        return 0 - (0 - f);
    }

    @Test
    @IR(counts = { IRNode.SUB_D, "2" })
    // Checks that the subtractions in 0 - (0 - d) do not get eliminated
    public static double testDouble(double d) {
        return 0 - (0 - d);
    }

}
