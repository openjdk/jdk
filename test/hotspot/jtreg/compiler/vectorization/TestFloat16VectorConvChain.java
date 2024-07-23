/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
* @summary Test Float16 vector conversion chain.
* @requires (vm.cpu.features ~= ".*avx512vl.*" | vm.cpu.features ~= ".*f16c.*") | os.arch == "aarch64"
*           | (os.arch == "riscv64" & vm.cpu.features ~= ".*zfh.*")
* @library /test/lib /
* @run driver compiler.vectorization.TestFloat16VectorConvChain
*/

package compiler.vectorization;

import compiler.lib.ir_framework.*;
import java.util.Random;
import java.util.Arrays;


public class TestFloat16VectorConvChain {

    @Test
    @IR(applyIfCPUFeatureOr = {"f16c", "true", "avx512vl", "true"}, counts = {IRNode.VECTOR_CAST_HF2F, IRNode.VECTOR_SIZE_ANY, ">= 1", IRNode.VECTOR_CAST_F2HF, IRNode.VECTOR_SIZE_ANY, " >= 1"})
    public static void test(short [] res, short [] src1, short [] src2) {
        for (int i = 0; i < res.length; i++) {
            res[i] = (short)Float.float16ToFloat(Float.floatToFloat16(Float.float16ToFloat(src1[i]) + Float.float16ToFloat(src2[i])));
        }
    }

    @Run(test = {"test"})
    @Warmup(1000)
    public static void micro() {
        short [] res = new short[1024];
        short [] src1 = new short[1024];
        short [] src2 = new short[1024];
        Arrays.fill(src1, (short)Float.floatToFloat16(1.0f));
        Arrays.fill(src2, (short)Float.floatToFloat16(2.0f));
        for (int i = 0; i < 1000; i++) {
            test(res, src1, src2);
        }
    }

    public static void main(String [] args) {
        TestFramework.run(TestFloat16VectorConvChain.class);
    }
}
