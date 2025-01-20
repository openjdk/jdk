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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import jdk.internal.math.RelaxedMath;

/*
 * @test
 * @bug 8343597
 * @summary Test RelaxedMath with auto-vectorization.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestRelaxedMath
 */

public class TestRelaxedMath {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestRelaxedMath.class);
        framework.addFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.math=ALL-UNNAMED");
        framework.start();
    }

    public TestRelaxedMath() {
    }

    @Test
    //@IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
    //              IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
    //              IRNode.STORE_VECTOR, "> 0"},
    //    applyIf = {"MaxVectorSize", ">=8"},
    //    applyIfPlatform = {"64-bit", "true"},
    //    applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] test0(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE; i+=8) {
            // Safe to vectorize with AlignVector
            b[i+0] = (byte)(a[i+0] & mask); // offset 0, align 0
            b[i+1] = (byte)(a[i+1] & mask);
            b[i+2] = (byte)(a[i+2] & mask);
            b[i+3] = (byte)(a[i+3] & mask);
        }
        return new Object[]{ a, b };
    }
}
