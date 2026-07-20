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
 * @bug 8335747
 * @summary Integer overflow in LoopLimit::Value during PhaseIdealLoop::split_thru_phi
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,TestLoopLimitOverflowDuringSplitThruPhi::test
 *                   compiler.loopopts.TestLoopLimitOverflowDuringSplitThruPhi
 * @run driver compiler.loopopts.TestLoopLimitOverflowDuringSplitThruPhi
 */

package compiler.loopopts;

public class TestLoopLimitOverflowDuringSplitThruPhi {
    public static void main(String[] args) {
        int[] a = new int[1005];
        for (int i = 0; i < 20_000; i++) {
            test(i % 2 == 0, a);
        }
    }

    static void test(boolean flag, int[] a) {
        int x = flag ? 1000 : 2147483647;
        // Creates a Phi(1000, 2147483647)

        // We do loop-predication, and add a
        //   LoopLimitNode(init=0, limit=x, stride=4)
        //
        // Later, we find try to PhaseIdealLoop::split_thru_phi
        // the LoopLimitNode, through the Phi(1000, 2147483647).
        //
        // This creates a temporary
        //   LoopLimitNode(init=0, limit=2147483647, stride=4)
        //
        // And then we get this:
        //   init_con 0
        //   limit_con 2147483647
        //   stride_con 4
        //   trip_count 536870912
        //   final_int -2147483648
        //   final_con 2147483648
        //
        for (int i = 0; i < x; i+=4 /* works for at least 2..64 */) {
            // Break before going out of bounds
            // but with quadratic check to not affect limit.
            if (i * i > 1000_000) { return; }
            a[i] = 34;
        }
    }
}
