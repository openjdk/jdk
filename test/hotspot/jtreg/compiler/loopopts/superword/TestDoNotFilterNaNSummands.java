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

/*
 * @test
 * @bug 8369902
 * @summary Bug in MemPointerParser::canonicalize_raw_summands let to wrong result, because a
 *          NaN summand was filtered out, instead of making the MemPointer / VPointer invalid.
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestDoNotFilterNaNSummands::test
 *      -Xbatch
 *      compiler.loopopts.superword.TestDoNotFilterNaNSummands
 * @run main compiler.loopopts.superword.TestDoNotFilterNaNSummands
 */

package compiler.loopopts.superword;

// This was the test found by the fuzzer. If you are looking for a simpler example with the same issue,
// please look at TestMemorySegmentFilterSummands::test2.
public class TestDoNotFilterNaNSummands {
    static final int N = 100;
    static int zero = 0;

    static int[] test() {
        int x = -4;
        int aI[] = new int[N];
        for (int k = 0; k < N; k++) {
            // Note that x is always "-4", and N is a compile time constant. The modulo "%"
            // gets optimized with magic numbers and shift/mul/sub trick, in the long domain,
            // which somehow creates some large long constant that cannot be represented
            // as an int.
            int idx = (x >>> 1) % N;
            // This is the CountedLoop that we may try to auto vectorize.
            // We have a linear access (i) and a constant index access (idx), which eventually
            // cross, so there is aliasing. If there is vectorization with an aliasing runtime
            // check, this check must fail.
            for (int i = 1; i < 63; i++) {
                aI[i] = 2;
                // The MemPointer / VPointer for the accesses below contain a large constant
                // long constant offset that cannot be represented as an int, so the scaleL
                // NoOverflowInt becomes NaN. In MemPointerParser::canonicalize_raw_summands
                // we are supposed to filter out zero summands, but since we WRONGLY filtered
                // out NaNs instead, this summand got filtered out, and later we did not detect
                // that the MemPointer contains a NaN. Instead, we just get a "valid" looking
                // VPointer, and generate runtime checks that are missing the long constant
                // offset, leading to wrong decisions, and hence vectorization even though
                // we have aliasing. This means that the accesses from above and below get
                // reordered in an illegal way, leading to wrong results.
                aI[idx] += 1;
            }
            for (int i = 0; i < 100; i++) {
                // It is a no-op, but the compiler can't know statically that zero=0.
                // Seems to be required in the graph, no idea why.
                x >>= zero;
            }
        }
        return aI;
    }

    // Use the sum as an easy way to compare the results.
    public static int sum(int[] aI) {
        int sum = 0;
        for (int i = 0; i < aI.length; i++) { sum += aI[i]; }
        return sum;
    }

    public static void main(String[] args) {
        // Run once, hopefully before compilation, so get interpreter results.
        int[] aIG = test();
        int gold = sum(aIG);

        // Repeat execution, until eventually compilation happens, compare
        // compiler results to interpreter results.
        for (int k = 0; k < 1000; k++) {
            int[] aI = test();
            int val = sum(aI);
            if (gold != val) {
                System.out.println("Detected wrong result, printing values of arrays:");
                for (int i = 0; i < aI.length; i++) {
                    System.out.println("at " + i + ": " + aIG[i] + " vs " + aI[i]);
                }
                throw new RuntimeException("wrong result: " + gold + " " + val);
            }
        }
    }
}
