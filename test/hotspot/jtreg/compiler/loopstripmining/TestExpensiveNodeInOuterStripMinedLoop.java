/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8388592
 * @summary The early control of an expensive node must not be computed above
 *          its control input when verifying, otherwise it can end up in an
 *          outer strip mined loop.
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

/*
 * The Sqrt below is an expensive node pinned right after the strip mined loop
 * nest. get_early_ctrl() ignores the control input of an expensive node because
 * get_early_ctrl_for_expensive() normally adjusts it, but that adjustment
 * modifies the graph and is therefore skipped when verifying. The early control
 * was then taken from the data inputs only and could land inside the outer strip
 * mined loop, above the control input. The Sqrt itself is pinned and so is not
 * checked, but its ConvI2D input inherited that block and
 * verify_strip_mined_scheduling() hit ShouldNotReachHere().
 *
 * Only a debug VM verifies loop optimizations, so this test can only fail there.
 * The reduced shape comes from a JavaFuzzer test and needs OSR to trigger.
 */

package compiler.loopstripmining;

public class TestExpensiveNodeInOuterStripMinedLoop {

    short sFld;

    public static void main(String[] args) {
        TestExpensiveNodeInOuterStripMinedLoop t =
            new TestExpensiveNodeInOuterStripMinedLoop();
        for (int i = 0; i < 10_000; i++) {
            t.test();
        }
    }

    void test() {
        int n = 8;
        int m = 0;
        int k = 2;
        for (int i = 55; i > 8; --i) {
            for (int j = 71; j > 8; j--) {
                n = sFld;
            }
            do {
                m = (int)(m - Math.sqrt(n));
            } while (++k < 71);
            for (int j = 6; 71 > j; ++j) {}
        }
    }
}
