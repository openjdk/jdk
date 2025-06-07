/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

package compiler.loopopts;

import java.util.Arrays;
import java.util.Objects;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

 /*
  * @test
  * @bug 8357726
  * @summary Improve C2 to recognize counted loops with multiple casts in trip counter
  * @library /test/lib /
  * @run driver compiler.loopopts.TestCountedLoopCastIV  DisableUnroll
  * @run driver compiler.loopopts.TestCountedLoopCastIV
  */

public class TestCountedLoopCastIV {
    private static final int LEN = 1024;
    private static final Generators random = Generators.G;

    private static int[] in;
    private static int[] out;

    static {
        in = new int[LEN];
        out = new int[LEN];
        random.fill(random.ints(), in);
    }

    private static void cleanUp() {
        Arrays.fill(out, 0);
    }

    private static void verify(int[] ref, int[] res, int start,
                               int limit, int stride,
                               int in_offset, int out_offset) {
        for (int i = start; i < limit; i += stride) {
            Asserts.assertEquals(ref[i + in_offset], res[i + out_offset]);
        }
    }

    // Test a counted loop with two explicit range checkes
    // which will create CastIINodes for loop induction variable.
    // In this case, the loop start, limit and stride are
    // all constants.
    //
    // The first IR check with "-XX:LoopUnrollLimit=0" makes sure
    // the loop is transformed into exactly one CountedLoopNode,
    // verifying the CastII recognition works correctly.
    //
    // The second IR check ensures the optimization works properly
    // with default vm settings.
    //
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "1" }, applyIf = {"LoopUnrollLimit", "0"})
    @IR(counts = {IRNode.COUNTED_LOOP, ">0" })
    static void test1() {
        for (int i = 0; i < LEN; i += 16) {
            Objects.checkIndex(i, LEN - 3);
            int a = in[i + 3];
            Objects.checkIndex(i, LEN - 15);
            out[i + 15] = a;
        }
    }

    @Run(test = "test1")
    public static void runTest1() {
        test1();
        verify(in, out, 0, LEN, 16, 3, 15);
    }

    // Similar to test1, but the loop limit is a variable.
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "1" }, applyIf = {"LoopUnrollLimit", "0"})
    @IR(counts = {IRNode.COUNTED_LOOP, ">0" })
    static void test2(int limit) {
        for (int i = 0; i < limit; i += 16) {
            Objects.checkIndex(i, LEN - 3);
            int a = in[i + 3];
            Objects.checkIndex(i, LEN - 15);
            out[i + 15] = a;
        }
    }

    @Run(test = "test2")
    private void runTest2() {
        cleanUp();
        test2(100);
        verify(in, out, 0, 100, 16, 3, 15);

        cleanUp();
        test2(500);
        verify(in, out, 0, 500, 16, 3, 15);

        cleanUp();
        test2(LEN);
        verify(in, out, 0, LEN, 16, 3, 15);
    }

    // Similar to test1 and test2, but the loop is a
    // while loop with a variable start and limit.
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "1" }, applyIf = {"LoopUnrollLimit", "0"})
    @IR(counts = {IRNode.COUNTED_LOOP, ">0" })
    static void test3(int start, int limit) {
        int i = start;
        while (i < limit) {
            Objects.checkIndex(i, LEN);
            int a = in[i];
            Objects.checkIndex(i, LEN - 3);
            out[i + 3] = a;
            i++;
        }
    }

    @Run(test = "test3")
    private void runTest3() {
        cleanUp();
        test3(0, 100);
        verify(in, out, 0, 100, 1, 0, 3);

        cleanUp();
        test3(128, 500);
        verify(in, out, 128, 500, 1, 0, 3);

        cleanUp();
        test3(LEN - 128, LEN - 3);
        verify(in, out, LEN - 128, LEN - 3, 1, 0, 3);
    }

    // Similar to test3, but the type of induction variable
    // is long.
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "1" }, applyIf = {"LoopUnrollLimit", "0"})
    @IR(counts = {IRNode.COUNTED_LOOP, ">0" })
    static void test4(long start, long limit) {
        for (long i = start; i < limit; i++) {
            Objects.checkIndex(i, LEN);
            int a = in[(int) i];
            Objects.checkIndex(i, LEN - 3);
            out[(int) i + 3] = a;
        }
    }

    @Run(test = "test4")
    private void runTest4() {
        cleanUp();
        test3(0, 100);
        verify(in, out, 0, 100, 1, 0, 3);

        cleanUp();
        test3(128, 500);
        verify(in, out, 128, 500, 1, 0, 3);

        cleanUp();
        test3(LEN - 128, LEN - 3);
        verify(in, out, LEN - 128, LEN - 3, 1, 0, 3);
    }

    public static void main(String[] args) {
        if (args != null && args.length > 0 && args[0].equals("DisableUnroll")) {
            TestFramework.runWithFlags("-XX:LoopUnrollLimit=0");
        } else {
            if (args != null && args.length != 0) {
                throw new RuntimeException("Unexpected args");
            }
            TestFramework.run();
        }
    }
}
