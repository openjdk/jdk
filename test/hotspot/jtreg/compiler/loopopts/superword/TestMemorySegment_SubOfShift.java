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

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8324751 8369435
 * @summary Reported issue: JDK-8359688: C2 SuperWord: missing RCE with MemorySegment
 *          The examples are generated from TestAliasingFuzzer.java
 *          So if you see something change here, you may want to investigate if we
 *          can also tighten up the IR rules there.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment_SubOfShift
 */


public class TestMemorySegment_SubOfShift {

    public static MemorySegment b = MemorySegment.ofArray(new long[4 * 30_000]);

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
        return new Object[] { b, 0, 5_000, 0 };
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.STORE_VECTOR, "> 0",
                  IRNode.REPLICATE_L,  "= 1",
                  ".*multiversion.*",  "= 0"}, // AutoVectorization Predicate SUFFICES, there is no aliasing
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public static void test1(MemorySegment b, int ivLo, int ivHi, int invar) {
        for (int i = ivLo; i < ivHi; i++) {
            b.setAtIndex(ValueLayout.JAVA_LONG_UNALIGNED, 30_000L - (long)i + (long)invar, 42);
            //                                                    ^ subtraction here
        }
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.STORE_VECTOR, "> 0",
                  IRNode.REPLICATE_L,  "> 0",
                  ".*multiversion.*",  "= 0"}, // AutoVectorization Predicate SUFFICES, there is no aliasing
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    // Is fully RFE'd and vectorized
    public static void test2(MemorySegment b, int ivLo, int ivHi, int invar) {
        for (int i = ivLo; i < ivHi; i++) {
            b.setAtIndex(ValueLayout.JAVA_LONG_UNALIGNED, 1_000L + 1L * i + (long)invar, 42);
            //                                                   ^ addition here
        }
    }
}
