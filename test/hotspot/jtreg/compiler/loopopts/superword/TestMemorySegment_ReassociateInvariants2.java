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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8324751 8369258
 * @summary Reported issue: JDK-8365982: C2 SuperWord: missing RCE / strange Multiversioning with MemorySegment.set
 *          The examples are generated from TestAliasingFuzzer.java
 *          So if you see something change here, you may want to investigate if we
 *          can also tighten up the IR rules there.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment_ReassociateInvariants2
 */

public class TestMemorySegment_ReassociateInvariants2 {

    public static MemorySegment a = MemorySegment.ofArray(new short[100_000]);
    public static MemorySegment b = MemorySegment.ofArray(new short[100_000]);

    private static long invar0_853 = 0;
    private static long invar1_853 = 0;
    private static long invar2_853 = 0;

    public static void main(String[] args) {
        TestFramework f = new TestFramework();
        f.addFlags("-XX:+IgnoreUnrecognizedVMOptions");
        f.addScenarios(new Scenario(0, "-XX:-AlignVector", "-XX:-ShortRunningLongLoop"),
                       new Scenario(1, "-XX:+AlignVector", "-XX:-ShortRunningLongLoop"),
                       new Scenario(2, "-XX:-AlignVector", "-XX:+ShortRunningLongLoop"),
                       new Scenario(3, "-XX:+AlignVector", "-XX:+ShortRunningLongLoop"));
        f.start();
    }

    @Setup
    static Object[] setup() {
        return new Object[] { a, -50_000, b, -30_000, 0, 10_000 };
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.STORE_VECTOR, "> 0",
                  IRNode.REPLICATE_S,  "> 0",
                  ".*multiversion.*",  "= 0"}, // Good: The AutoVectorization predicate suffices.
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "ShortRunningLongLoop", "false"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    //
    @IR(counts = {IRNode.STORE_VECTOR, "> 0",
                  IRNode.REPLICATE_S,  "> 0",
                  ".*multiversion.*",  "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "ShortRunningLongLoop", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public static void test(MemorySegment container_0, long invar0_0, MemorySegment container_1, long invar0_1, long ivLo, long ivHi) {
        for (long i = ivHi-1; i >= ivLo; i-=1) {
            container_0.set(ValueLayout.JAVA_CHAR_UNALIGNED, -47143L + -2L * i + -2L * invar0_0 + -1L * invar0_853 + -1L * invar1_853 + 0L * invar2_853, (char)0x0102030405060708L);
            container_1.set(ValueLayout.JAVA_CHAR_UNALIGNED, 74770L + 2L * i + 2L * invar0_1 + 0L * invar0_853 + 0L * invar1_853 + 0L * invar2_853, (char)0x1112131415161718L);
        }
    }
}
