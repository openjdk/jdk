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
 * @summary Reported issue: JDK-8360204: C2 SuperWord: missing RCE with MemorySegment.getAtIndex
 *          The examples are generated from TestAliasingFuzzer.java
 *          So if you see something change here, you may want to investigate if we
 *          can also tighten up the IR rules there.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment_ReassociateInvariants1
 */

public class TestMemorySegment_ReassociateInvariants1 {

    public static MemorySegment a = Arena.ofAuto().allocate(10_000);
    public static MemorySegment b = Arena.ofAuto().allocate(10_000);

    private static long invar0_1159 = 0;
    private static long invar1_1159 = 0;

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
        return new Object[] { a, -19125L, b, 71734L + 2_000L, 0, 1_000 };
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.STORE_VECTOR,  "= 0",
                  ".*multiversion.*",   "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    // We could imagine that this would eventually vectorize, but since one counts up, and the other down,
    // we would have to implement shuffle first.
    public static void test(MemorySegment container_0, long invar0_0, MemorySegment container_1, long invar0_1, long ivLo, long ivHi) {
        for (long i = ivLo; i < ivHi; i+=1) {
            var v = container_0.getAtIndex(ValueLayout.JAVA_INT_UNALIGNED, 19125L + 1L * i + 1L * invar0_0 + 0L * invar0_1159 + 1L * invar1_1159);
            container_1.setAtIndex(ValueLayout.JAVA_INT_UNALIGNED, -71734L + -1L * i + 1L * invar0_1 + 1L * invar0_1159 + 0L * invar1_1159, v);
        }
    }
}
