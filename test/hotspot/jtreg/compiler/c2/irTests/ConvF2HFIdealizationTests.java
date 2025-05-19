/*
 * Copyright (c) 2025, Arm Limited. All rights reserved.
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
import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8338061
 * @summary Test that Ideal transformations of ConvF2HF are being performed as expected.
 * @requires (os.arch != "ppc64" & os.arch != "ppc64le") | vm.cpu.features ~= ".*darn.*"
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.c2.irTests.ConvF2HFIdealizationTests
 */
public class ConvF2HFIdealizationTests {
    private short[] sin;
    private short[] sout;
    private static final int SIZE = 65504;
    public ConvF2HFIdealizationTests() {
        sin  = new short[SIZE];
        sout = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            sin[i] = Float.floatToFloat16((float)i);
        }
    }
    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:-UseSuperWord");
    }

    @Test
    @IR(counts = {IRNode.REINTERPRET_S2HF, ">=1", IRNode.REINTERPRET_HF2S, ">=1", IRNode.ADD_HF, ">=1" },
        failOn = {IRNode.ADD_F, IRNode.CONV_HF2F, IRNode.CONV_F2HF},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.REINTERPRET_S2HF, ">=1", IRNode.REINTERPRET_HF2S, ">=1", IRNode.ADD_HF, ">=1" },
        failOn = {IRNode.ADD_F, IRNode.CONV_HF2F, IRNode.CONV_F2HF},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    // Test pattern - ConvHF2F -> AddF -> ConvF2HF is optimized to ReinterpretS2HF -> AddHF -> ReinterpretHF2S
    public void test1() {
        for (int i = 0; i < SIZE; i++) {
            sout[i] = Float.floatToFloat16(Float.float16ToFloat(sin[i]) + Float.float16ToFloat(sin[i]));
        }
    }

    @Check(test="test1")
    public void checkResult() {
        for (int i = 0; i < SIZE; i++) {
            short expected = Float16.float16ToRawShortBits(Float16.add(Float16.shortBitsToFloat16(sin[i]), Float16.shortBitsToFloat16(sin[i])));
            if (expected != sout[i]) {
                throw new RuntimeException("Invalid result: sout[" + i + "] = " + sout[i] + " != " + expected);
            }
        }
    }
}
