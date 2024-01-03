/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8310886
 * @summary Test MulAddS2I vectorization.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMulAddS2I
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;

public class TestMulAddS2I {
    static final int RANGE = 1024;
    static final int ITER  = RANGE/2 - 1;

    static short[] sArr1 = new short[RANGE];
    static short[] sArr2 = new short[RANGE];
    static final int[] GOLDEN;

    static {
        for (int i = 0; i < RANGE; i++) {
            sArr1[i] = (short)(AbstractInfo.getRandom().nextInt());
            sArr2[i] = (short)(AbstractInfo.getRandom().nextInt());
        }
        GOLDEN = test();
    }


    public static void main(String[] args) {
        if (Platform.isX64() || Platform.isX86()) {
            TestFramework.runWithFlags("-XX:+UseUnalignedLoadStores");
            TestFramework.runWithFlags("-XX:-UseUnalignedLoadStores");
        } else {
            TestFramework.run();
        }
    }

    @Run(test = "test")
    @Warmup(0)
    public static void run() {
        compare(test());
    }

    public static void compare(int[] out) {
        for (int i = 0; i < ITER; i++) {
            Asserts.assertEQ(out[i], GOLDEN[i], "wrong result for out[" + i + "]");
        }
    }

    @Test
    @IR(applyIfCPUFeature = {"sse2", "true"},
        applyIfPlatform = {"64-bit", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"asimd", "true"},
        applyIf = {"MaxVectorSize", "16"}, // AD file requires vector_length = 16
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI, "> 0"})
    @IR(applyIfCPUFeature = {"avx512_vnni", "true"},
        counts = {IRNode.MUL_ADD_S2I, "> 0", IRNode.MUL_ADD_VS2VI_VNNI, "> 0"})
    public static int[] test() {
        int[] out = new int[ITER];
        int[] out2 = new int[ITER];
        for (int i = 0; i < ITER; i++) {
            out[i] += ((sArr1[2*i] * sArr1[2*i]) + (sArr1[2*i+1] * sArr1[2*i+1]));
            out2[i] += out[i];
        }
        return out;
    }
}
