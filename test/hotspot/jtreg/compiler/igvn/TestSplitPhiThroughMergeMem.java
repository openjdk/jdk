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

/**
 * @test
 * @bug 8351833
 * @summary Splitting Phi memory nodes through MergeMem nodes in PhiNode::Ideal
 *          could sometimes result in a too large number of added nodes within
 *          a single iteration of the main loop in PhaseIterGVN::optimize. This
 *          test, reduced from TestScalarReplacementMaxLiveNodes, triggers an
 *          assert added as part of the fix for the linked bug/issue (the
 *          assert naturally triggers only before the fix). The test's ability
 *          to trigger the issue is quite sensitive to the specific String
 *          constants used. The current set of chosen String constants happened
 *          to work particularly well.
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=CompileOnly,compiler.igvn.TestSplitPhiThroughMergeMem::test
 *                   compiler.igvn.TestSplitPhiThroughMergeMem
 * @run main compiler.igvn.TestSplitPhiThroughMergeMem
 */

package compiler.igvn;

public class TestSplitPhiThroughMergeMem {

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            int val = i % 50;
            test(val == 0, val % 10, val % 20);
        }
    }

    static void test(boolean flag, int param1, int param2) {
        if (flag) {
            new String("tenth" + param1);
            new String("eleventh" + param2);
            new String("fifteenth" + param2);
            new String("sixteenth" + param1);
            new String("seventeenth" + param1);
            new String("nineteenth" + param2);
            new String("tweenth" + param1);
            new String("nineth" + param1);
            new String("nineth" + param1);
            new String("eighteenth" + param1);
            new String("abcdef" + param2);
            new String("ghijklmn" + param1);
            new String("ghijklmn" + param1);
        }
    }
}
