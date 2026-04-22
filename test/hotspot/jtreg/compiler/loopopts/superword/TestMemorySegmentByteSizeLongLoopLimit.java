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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

/*
 * @test
 * @bug 8356176
 * @summary Test vectorization of loops over MemorySegment with unaligned access
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentByteSizeLongLoopLimit
 */


public class TestMemorySegmentByteSizeLongLoopLimit {
    public static int SIZE = 10_000;

    public static int[] a = new int[SIZE];
    public static long[] b = new long[SIZE];

    public static MemorySegment msA = MemorySegment.ofArray(a);
    public static MemorySegment msB = MemorySegment.ofArray(b);

    public static void main(String[] args) {
        TestFramework f = new TestFramework();
        f.addFlags("-XX:+IgnoreUnrecognizedVMOptions");
        f.addScenarios(new Scenario(0, "-XX:-AlignVector", "-XX:-ShortRunningLongLoop"),
                       new Scenario(1, "-XX:+AlignVector", "-XX:-ShortRunningLongLoop"),
                       new Scenario(2, "-XX:-AlignVector", "-XX:+ShortRunningLongLoop"),
                       new Scenario(3, "-XX:+AlignVector", "-XX:+ShortRunningLongLoop"));
        f.start();
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.ADD_VI, IRNode.VECTOR_SIZE + "min(max_int, max_long)",        "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public static void test() {
        for (long i = 0; i < msA.byteSize() / 8L; i++) {
            int v = msA.get(ValueLayout.JAVA_INT_UNALIGNED, i * 4L);
            msB.set(ValueLayout.JAVA_LONG_UNALIGNED, i * 8L, v + 1);
        }
    }
}
