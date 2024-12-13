/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8342498
 * @summary Test SuperWord, when it aligns to field-store, and the corresponding allocation is eliminated.
 * @run driver compiler.loopopts.superword.TestEliminateAllocationWithCastP2XUse
 * @run main/othervm -Xbatch
 *                   -XX:-SplitIfBlocks -XX:LoopMaxUnroll=8
 *                   -XX:+UnlockDiagnosticVMOptions -XX:DominatorSearchLimit=45
 *                   compiler.loopopts.superword.TestEliminateAllocationWithCastP2XUse
 */

public class TestEliminateAllocationWithCastP2XUse {
    public static void main(String args[]) {
        byte[] a = new byte[10_000];
        for (int i = 0; i < 10000; i++) {
            test(a);
        }
    }

    // Summary:
    //  - Some B allocations are detected as NoEscape, but cannot be removed because of a field load.
    //  - The field loads cannot be LoadNode::split_through_phi because DominatorSearchLimit is too low
    //    for the dominates query to look through some IfNode / IfProj path.
    //  - We go into loop-opts.
    //  - In theory, the Stores of B::offset would be moved out of the loop. But we disable
    //    PhaseIdealLoop::try_move_store_after_loop by setting -XX:-SplitIfBlocks.
    //  - The field loads are folded away because of some MaxUnroll trick, where the val constant folds to 1.
    //  - SuperWord eventually kicks in, and vectorizes the array stores.
    //  - Since some vectorization has happened, SuperWord wants to align the main loop with a memory reference
    //    in the loop. The code here is not very smart, and just picks the memory reference that occurs the
    //    most often. But the B::offset stores occur more often than the array stores, and so we align to
    //    one of the B::offset stores. This inserts a CastP2X under the CheckCastPP of the B allocation.
    //  - Once loop opts is over, we eventually go into macro expansion.
    //  - During macro expansion, we now discover that the Allocations were marked NoEscape, and that by now
    //    there are no field loads any more: yay, we can remove the allocation!
    //  - ... except that there is the CastP2X from SuperWord alignment ...
    //  - The Allocation removal code wants to pattern match the CastP2X as part of a GC barrier, but then
    //    the pattern does not conform to the expecatation - it is after all from SuperWord. This leads to
    //    an assert, and SIGSEGV in product, at least with G1GC.
    public static long test(byte[] a) {
        // Delay val == 1 until loop-opts, with MaxUnroll trick.
        int val = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                val = 1;
            }
        }
        // during loop opts, we learn val == 1
        // But we don't know that during EscapeAnalysis (EA) yet.

        // 9 Allocations, discovered as NoEscape during EA.
        B b1 = new B();
        B b2 = new B();
        B b3 = new B();
        B b4 = new B();
        B b5 = new B();
        B b6 = new B();
        B b7 = new B();
        B b8 = new B();
        B b9 = new B();

        // Some path of IfNode / IfProj.
        // Only folds away once we know val == 1
        // This delays the LoadNode::split_through_phi, because it needs a dominates call
        // to succeed, but it cannot look through this path because we set -XX:DominatorSearchLimit=45
        // i.e. just a little too low to be able to look through.
        // Without the LoadNode::split_through_phi before the end of EA, the Allocation cannot yet be
        // removed, due to a "Field load", i.e. that Load for B::offset.
        // But later, this path can actually fold away, when we know that val == 1. At that point,
        // also the Load from B::offset folds away because LoadNode::split_through_phi succeeds
        // At that point the B allocations have no Loads any more, and can be removed... but this only
        // happens at macro expansion, after all loop opts.
        if (val == 1010) { throw new RuntimeException("never"); }
        if (val == 1020) { throw new RuntimeException("never"); }
        if (val == 1030) { throw new RuntimeException("never"); }
        if (val == 1040) { throw new RuntimeException("never"); }
        if (val == 1060) { throw new RuntimeException("never"); }
        if (val == 1070) { throw new RuntimeException("never"); }
        if (val == 1080) { throw new RuntimeException("never"); }
        if (val == 1090) { throw new RuntimeException("never"); }

        if (val == 2010) { throw new RuntimeException("never"); }
        if (val == 2020) { throw new RuntimeException("never"); }
        if (val == 2030) { throw new RuntimeException("never"); }
        if (val == 2040) { throw new RuntimeException("never"); }
        if (val == 2060) { throw new RuntimeException("never"); }
        if (val == 2070) { throw new RuntimeException("never"); }
        if (val == 2080) { throw new RuntimeException("never"); }
        if (val == 2090) { throw new RuntimeException("never"); }

        if (val == 3010) { throw new RuntimeException("never"); }
        if (val == 3020) { throw new RuntimeException("never"); }
        if (val == 3030) { throw new RuntimeException("never"); }
        if (val == 3040) { throw new RuntimeException("never"); }
        if (val == 3060) { throw new RuntimeException("never"); }
        if (val == 3070) { throw new RuntimeException("never"); }
        if (val == 3080) { throw new RuntimeException("never"); }
        if (val == 3090) { throw new RuntimeException("never"); }

        if (val == 4010) { throw new RuntimeException("never"); }
        if (val == 4020) { throw new RuntimeException("never"); }
        if (val == 4030) { throw new RuntimeException("never"); }
        if (val == 4040) { throw new RuntimeException("never"); }
        if (val == 4060) { throw new RuntimeException("never"); }
        if (val == 4070) { throw new RuntimeException("never"); }
        if (val == 4080) { throw new RuntimeException("never"); }
        if (val == 4090) { throw new RuntimeException("never"); }

        long mulVal = 1;
        for (int i = 0; i < a.length; i++) {
            mulVal *= 3;
            // We do some vector store, so that SuperWord succeeds, and creates the
            // alignment code, which emits the CastP2X.
            a[i]++;
            // But we also have 9 Stores for the B::offset.
            // SuperWord now sees more of these stores than of the array stores, and picks
            // one of the B::offset stores as the alignment reference... creating a CastP2X
            // for the CheckCastPP of the B allocation.
            b1.offset = mulVal;
            b2.offset = mulVal;
            b3.offset = mulVal;
            b4.offset = mulVal;
            b5.offset = mulVal;
            b6.offset = mulVal;
            b7.offset = mulVal;
            b8.offset = mulVal;
            b9.offset = mulVal;
        }

        // This folds the loads away, once we know val == 1
        // That happens during loop-opts, so after EA, but before macro expansion.
        long ret = 0;
        if (val == 42) {
            ret = b1.offset +
                  b2.offset +
                  b3.offset +
                  b4.offset +
                  b5.offset +
                  b6.offset +
                  b7.offset +
                  b8.offset +
                  b9.offset;
        }

        return ret;
    }

    static class B {
        // Add padding so that the old SuperWord::can_create_pairs accepts the field store to B.offset
        long pad1 = 0;   // at 16
        long pad2 = 0;   // at 24
        long pad3 = 0;   // at 32
        long pad4 = 0;   // at 40
        long pad5 = 0;   // at 48
        long pad6 = 0;   // at 56
        long offset = 0; // offset at 64 bytes
    }
}
