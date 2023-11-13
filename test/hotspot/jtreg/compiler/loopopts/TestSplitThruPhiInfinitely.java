/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8287284
 * @summary The phi of cnt is split from the inner to the outer loop,
 *          and then from outer loop to the inner loop again.
 *          This ended in a endless optimization cycle.
 * @library /test/lib /
 * @run driver compiler.c2.loopopts.TestSplitThruPhiInfinitely
 */

package compiler.c2.loopopts;

import compiler.lib.ir_framework.*;

public class TestSplitThruPhiInfinitely {

    public static int cnt = 1;

    @Test
    @IR(counts = {IRNode.PHI, " <= 10"})
    public static void test() {
        int j = 0;
        do {
            j = cnt;
            for (int k = 0; k < 20000; k++) {
                cnt += 2;
            }
        } while (++j < 10);
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-PartialPeelLoop");
    }
}
