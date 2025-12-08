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

import java.lang.foreign.*;
import java.util.Set;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;

/*
 * @test
 * @bug 8369902
 * @summary Bug in MemPointerParser::canonicalize_raw_summands let to wrong results or assert.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentFilterSummands
 */

public class TestMemorySegmentFilterSummands {

    static long init  = 1000;
    static long limit = 9000;

    static long invar0 = 0;
    static long invar1 = 0;
    static long invar2 = 0;
    static long invar3 = 0;
    static long invar4 = 0;
    static long invarX = 0;

    public static final long BIG = 0x200000000L;
    public static long big = -BIG;

    static MemorySegment a1 = Arena.ofAuto().allocate(10_000);
    static MemorySegment b1 = Arena.ofAuto().allocate(10_000);
    static {
        for (long i = init; i < limit; i++) {
            a1.set(ValueLayout.JAVA_BYTE, i, (byte)((i & 0xf) + 1));
        }
    }

    static MemorySegment a2 = MemorySegment.ofArray(new byte[40_000]);
    static MemorySegment b2 = a2;

    public static void main(String[] args) {
        TestFramework f = new TestFramework();
        f.addFlags("-XX:+IgnoreUnrecognizedVMOptions");
        f.addCrossProductScenarios(Set.of("-XX:-AlignVector", "-XX:+AlignVector"),
                                   Set.of("-XX:-ShortRunningLongLoop", "-XX:+ShortRunningLoop"));
        f.start();
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR,   "> 0",
                  IRNode.LOAD_VECTOR_B,  "> 0",
                  ".*multiversion.*",    "= 0"}, // AutoVectorization Predicate SUFFICES, there is no aliasing
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public static void test1() {
        long invar = 0;
        invar += invarX; // cancles out with above
        invar += invar0;
        invar += invar1;
        invar += invar2;
        invar += invar3;
        invar += invar4;
        invar -= invarX; // cancles out with above
        // invar contains a raw summand for invarX, which has a scaleL=0. It needs to be filtered out.
        // The two occurances of invarX are conveniently put in a long chain, so that IGVN cannot see
        // that they cancle out, so that they are not optimized out before loop-opts.
        for (long i = init; i < limit; i++) {
            byte v = a1.get(ValueLayout.JAVA_BYTE, i + invar);
            b1.set(ValueLayout.JAVA_BYTE, i + invar, v);
        }
    }

    @Check(test = "test1")
    static void check1() {
        Verify.checkEQ(a1, b1);
    }

    @Test
    @IR(failOn = {IRNode.STORE_VECTOR})
    // This test could in principle show vectorization, but it would probably need to do some special
    // tricks to only vectorize around the overlap. Still, it could happen that at some point we end
    // up multiversioning, and having a vectorized loop that is never entered.
    //
    // For now, the long constant BIG leads to an invalid VPointer, which means we do not vectorize.
    static void test2() {
        // At runtime, "BIG + big" is zero. But BIG is a long constant that cannot be represented as
        // an int, and so the scaleL NoOverflowInt is a NaN. We should not filter it out from the summands,
        // but instead make the MemPointer / VPointer invalid, which prevents vectorization.
        long adr = 4L * 5000 + BIG + big;

        for (long i = init; i < limit; i++) {
            // The reference to a2 iterates linearly, while the reference to "b2" stays at the same adr.
            // But the two alias: in the middle of the "a2" range it crosses over "b2" adr, so the
            // aliasing runtime check (if we generate one) should fail. But if "BIG" is just filtered
            // out from the summands, we instead just create a runtime check without it, which leads
            // to a wrong answer, and the check does not fail, and we get wrong results.
            a2.set(ValueLayout.JAVA_INT_UNALIGNED, 4L * i, 0);
            int v = b2.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            b2.set(ValueLayout.JAVA_INT_UNALIGNED, adr, v + 1);
        }
    }

    @Check(test = "test2")
    static void check2() {
        int s = 0;
        for (long i = init; i < limit; i++) {
            s += a2.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * i);
        }
        if (s != 4000) {
            throw new RuntimeException("wrong value");
        }
    }
}
