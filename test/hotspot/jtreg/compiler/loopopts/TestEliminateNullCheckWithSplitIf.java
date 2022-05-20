/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @key stress randomness
 * @bug 8275610
 * @summary Null check for field access of object floats above null check resulting in a segfault.
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,compiler.loopopts.TestEliminateNullCheckWithSplitIf::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=42 compiler.loopopts.TestEliminateNullCheckWithSplitIf
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,compiler.loopopts.TestEliminateNullCheckWithSplitIf::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:+StressIGVN compiler.loopopts.TestEliminateNullCheckWithSplitIf
 */

package compiler.loopopts;

public class TestEliminateNullCheckWithSplitIf {
    public static int[] iArrFld = new int[20];
    public static int[] iArrFld2 = new int[20];
    public static int iFld = 10;
    public static MyClass obj;

    public static void main(String[] strArr) {
        for (int i = 0; i < 10000; i++) {
            obj = (i % 100 == 0 ? null : new MyClass());
            test();
        }
    }

    // The field access obj.iFld requires a null check NC3 and adds a not-null CastPP node on the succeeded projection.
    // In the first IGVN after parsing, the null check NC3 can be subsumed by the explicit null check NC2.
    // (done in IfNode::simple_subsuming()). The Bool node of NC2 is also shared with the same null check NC1 earlier.
    // However, C2 cannot remove the null check NC2, yet, because the IR in between the two checks are too complex
    // (IfNode::search_identical() fails).
    // Now, loopopts are applied:
    // (1) First, the split if optimization is done. It recognizes that NC1 and NC2 are back to back null checks and removes
    // the null check NC2 by splitting it through the region R which is removed afterwards. In this process, control dependent
    // data nodes on the out projections of NC2 end up at the new regions R1/R2 created for each projection for R. They get
    // the last nodes of the if and else block as input. For this example, R1 is a control input to the CastPP node which
    // will merge both true projections.
    // (2) Later in loop opts, the loop L is transformed into normal code and y will become a constant 1.
    // After loopopts, another round of IGVN is done:
    // (These steps also depend on the order in which they are applied in order to trigger the bug)
    // (1) The region R is removed because one path is dead (a result of the split if optimization).
    // (2) The new If node added by the above split if optimization is also folded. This rewires the CastPP node to
    // the last control node in the If block which is the true projection of range check RC2. Up until now, the CastPP
    // is still after the null check NC1.
    // (3) The range check RC2 is removed because the range check RC1 already covers this range (see RangeCheck::Ideal()).
    // All data nodes which are control dependent on RC2 will be rewired to the dominating range check RC1, including
    // the non-null CastPP node - which now has a control input above the null check NC1. This also means that the field
    // load obj.iFld now has the same early control as the CastPP (CastPP -> AddP -> LoadI). Using StressGCM can
    // now schedule the obj.iFld load before the null check NC1 because the early control allows it which leads to a
    // segmentation fault if obj is null.
    public static void test() {
        int x = iArrFld[17]; // Emits range check RC1
        if (obj != null) { // Null check NC1
            int y = 0;
            for (int i = 0; i < 1; i++) { // Loop L
                y++;
            }
            // Use additional loop to keep the rangecheck for iArrFld[y] in before loopopts.
            // y will become constant 1 but only once the loop above is removed in loopopts.
            x = iArrFld[y]; // Emits range check RC2
        } else {
            x = iArrFld2[18];
        }
        // Region R merging the if and else paths above.
        if (obj != null) { // Null check NC2
            x = iArrFld2[obj.iFld]; // Emits Null check NC3 for obj.iFld
        }
    }
}

class MyClass {
    int iFld;
}




