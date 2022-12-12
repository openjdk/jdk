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

/*
 * @test
 * @bug 8297951
 * @summary Test that crashes because we do not emit skeleton predicates for normal If nodes for which a range check
 *          predicate is created in loop predication.
 * @requires vm.debug == true & vm.compiler2.enabled
 * @run main/othervm -XX:-TieredCompilation -Xbatch -XX:-RangeCheckElimination -XX:+BailoutToInterpreterForThrows
                     compiler.loopopts.TestMissingSkeletonPredicateForIfNode
 */
package compiler.loopopts;

public class TestMissingSkeletonPredicateForIfNode {
    static int iFld = 2, x;
    static short limit = 10;

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 5000; i++) {
            try {
                test(i % 2 == 0, i % 3);
            } catch (Exception e) {
                // Expected
            }
        }
    }

    public static void test(boolean flag, int arg) throws Exception {
        int sum = 1;
        int[] iArr2 = new int[4];
        RuntimeException r = new RuntimeException();

        for (int i = 0; i < limit; i+=2) { // Pre/main/post + Unrolled once. This results in the following type for the iv phi i: [2..SHORT_MAX]
            x = 5 / sum;
            if (Integer.compareUnsigned(i, iArr2.length) < 0) { // (**) Loop predication creates a RC predicate for this check
                // After unrolling, we have:
                //
                // iArr2[i]
                // iArr2[i+2]
                //
                // The type of iArr2[i+2] is [4..SHORT_MAX+2] (we need limit to be short such that we do not have an integer overflow
                // which would set the type to int). However, the type of the CastII node for the index i+2 is [0..3] because its size
                // is only 4. Since the type of i+2 is outside the range of the CastII node, the CastII node is replaced by top and
                // some of the data nodes and memory nodes die. We are left with a broken graph and later assert because of that.
                iFld += iArr2[i]; // RangeCheck node is removed because it shares the same bool as the If (**).
                sum += iFld;
            } else {
                // Emits an UCT with -XX:+BailoutToInterpreterForThrows and therefore the If (**) satisfies the condition of being a
                // range check if with one of its blocks being an UCT.
                throw r;
            }
            if (i > 50) {
                break;
            }
        }
    }
}
