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
 * @bug 8360561
 * @summary Ranges can be proven to be disjoint but not orderable (thanks to unsigned range)
 *          Comparing such values in such range with != should always be true.
 * @library /test/lib /
 * @run main compiler.igvn.CmpDisjointButNonOrderedRangesLong
 */
package compiler.igvn;

import compiler.lib.ir_framework.*;

public class CmpDisjointButNonOrderedRangesLong {
    static boolean bFld;
    static double dFld1;
    static double dFld2;

    public static void main(String[] strArr) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.PHI})
    @Warmup(0)
    static int test() {
        long x = 7;
        if (bFld) {
            x = -195;
        }

        dFld1 = dFld2 % 2.5;

        if (x == 0) {
            return 0;
        }
        return 1;
    }
}
