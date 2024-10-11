/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8279622
 * @summary Test that reduction nodes peeled out of an inner loop are not
 *          vectorized as reductions within the outer loop.
 * @library /test/lib
 * @comment The test is run with -XX:LoopUnrollLimit=32 to prevent unrolling
 *          from fully replacing vectorization.
 * @run main/othervm -Xbatch
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:LoopUnrollLimit=32
 *                   compiler.loopopts.superword.TestPeeledReductionNode
 */
package compiler.loopopts.superword;

import jdk.test.lib.Asserts;

public class TestPeeledReductionNode {
    static final int N = 32;
    static final int M = 65; // Must be odd and >= 65 to trigger the failure.
    static final int INPUT = 0b0000_0000_0000_0000_0000_0000_0000_0001;
    static final int MASK  = 0b0000_0000_1000_0000_0000_0000_0000_0000;
    static final int EXPECTED = (M % 2 == 0 ? INPUT : INPUT ^ MASK);
    static int mask = 0;
    public static void main(String[] args) {
        int r[] = new int[N];
        for (int i = 0; i < N; i++) {
            r[i] = INPUT;
        }
        // Trigger the relevant OSR compilation and set
        // TestPeeledReductionNode.mask to MASK.
        for (int k = 0; k < MASK; k++) {
            TestPeeledReductionNode.mask++;
        }
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {
                // Before the fix, this reduction is peeled out of its loop and
                // wrongly remains marked as a reduction within the outer loop.
                r[i] ^= TestPeeledReductionNode.mask;
            }
        }
        for (int i = 0; i < N; i++) {
            Asserts.assertEquals(r[i], EXPECTED);
        }
        return;
    }
}
