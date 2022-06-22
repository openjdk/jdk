/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
import jdk.test.lib.Utils;
import jdk.internal.misc.Unsafe;
import java.util.Objects;
import java.util.Random;

/*
 * @test
 * @bug 8286197
 * @key randomness
 * @summary C2: Optimize MemorySegment shape in int loop
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestConvI2LCastLongLoop
 */

public class TestConvI2LCastLongLoop {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", "-XX:LoopMaxUnroll=0", "-XX:-UseCountedLoopSafepoints");
    }

    static int size = 1024;
    static long base = UNSAFE.allocateMemory(size * 4);

    @Test
    @IR(failOn = { IRNode.CAST_LL })
    public static int test1() {
        // Make sure enough round of loop opts are executed
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                for (int k = 0; k < 10; k++) {
                }
            }
        }
        int v = 0;
        // In order to optimize the range check the loop is
        // transformed to:
        //
        // for (int i1;;) {
        //   for (int i2;;) {
        //     long j = (i1 + i2) * UNSAFE.ARRAY_INT_INDEX_SCALE; // (i1 + i2) << 2
        //     v += UNSAFE.getInt(base + j);
        //   }
        // }
        //
        // (i1 + i2) << 2 is transformed to (i1 << 2) + (i2 << 2)
        // because i1 is loop invariant in the inner loop.
        //
        // long j = ... really is (CastLL (Convi2L ...))
        //
        // With that transformed into (ConvI2L (CastII ...)), The AddL
        // (i1 << 2) + (i2 << 2) can be pushed through the CastII and
        // ConvI2L.
        // The address of the getInt is then:
        // (AddP base (AddL I V)) with I, loop invariant and V loop invariant
        // which can be transformed into:
        // (AddP (AddP base I) V)
        // The inner AddP is computed out of loop
        for (int i = 0; i < size; i++) {
            long j = i * UNSAFE.ARRAY_INT_INDEX_SCALE;

            j = Objects.checkIndex(j, size * 4);

            if (((base + j) & 3) != 0) {
                throw new RuntimeException();
            }

            v += UNSAFE.getInt(base + j);
        }
        return v;
    }

    @Test
    @IR(counts = { IRNode.CAST_II, ">=1", IRNode.CONV_I2L, ">=1" })
    @IR(failOn = { IRNode.CAST_LL })
    public static long test2(int i) {
        // Convert (CastLL (ConvI2L ...)) into (ConvI2L (CastII ...))
        long j = i * UNSAFE.ARRAY_INT_INDEX_SCALE;
        j = Objects.checkIndex(j, size * 4);
        return j;
    }

    @Run(test = "test2")
    public static void test2_runner() {
        int i = RANDOM.nextInt(size);
        long res = test2(i);
        if (res != i * UNSAFE.ARRAY_INT_INDEX_SCALE) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.PHI, "2" })
    public static int test3() {
        int v = 0;
        // splif if should not push LshiftI through the iv Phi
        for (int i = 0; i < 1024; i++) {
            v += i * UNSAFE.ARRAY_INT_INDEX_SCALE;
        }
        return v;
    }
}
