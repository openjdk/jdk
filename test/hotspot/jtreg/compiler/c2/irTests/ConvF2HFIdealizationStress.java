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
import jdk.test.lib.Asserts;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8375633
 * @key randomness
 * @summary Test that ConvF2HF::Ideal optimization is not missed with incremental inlining.
 *          AlwaysIncrementalInline is not required but deterministically defers even
 *          small methods, making this test reliable.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class ConvF2HFIdealizationStress {

    private static final Random RANDOM = Utils.getRandomInstance();

    private short srcBits;
    private short twoBits;
    private short actualBits;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:+IgnoreUnrecognizedVMOptions",
                                   "-XX:+AlwaysIncrementalInline",
                                   "-XX:VerifyIterativeGVN=1110");
    }

    public ConvF2HFIdealizationStress() {
        srcBits = Float.floatToFloat16(RANDOM.nextFloat());
        twoBits = Float.floatToFloat16(2.0f);
    }

    // Deferred by AlwaysIncrementalInline; ConvHF2F appears only after inlining.
    static float toFloat(short hf) {
        return Float.float16ToFloat(hf);
    }

    // ConvF2HF(MulF(ConvHF2F(a), ConvHF2F(b))) => MulHF(a, b)
    // Float.floatToFloat16 (intrinsic) is expanded at parse time; toFloat is deferred.
    @Test
    @IR(counts = {IRNode.MUL_HF, "1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"},
        failOn = {IRNode.CONV_F2HF})
    @IR(counts = {IRNode.MUL_HF, "1"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"},
        failOn = {IRNode.CONV_F2HF})
    public void testMultiply() {
        actualBits = Float.floatToFloat16(toFloat(srcBits) * toFloat(twoBits));
    }

    @Check(test = "testMultiply")
    public void checkMultiply() {
        float twice = Float.float16ToFloat(srcBits) * Float.float16ToFloat(twoBits);
        short expected = Float.floatToFloat16(twice);
        Asserts.assertEQ(expected, actualBits);
    }
}
