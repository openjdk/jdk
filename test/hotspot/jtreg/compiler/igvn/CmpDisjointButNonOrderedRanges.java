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
 * @run main/othervm -Xcomp
 *                   -XX:CompileCommand=compileonly,compiler.igvn.CmpDisjointButNonOrderedRanges::*
 *                   compiler.igvn.CmpDisjointButNonOrderedRanges
 * @run main compiler.igvn.CmpDisjointButNonOrderedRanges
 */
package compiler.igvn;

public class CmpDisjointButNonOrderedRanges {
    static boolean bFld;

    public static void main(String[] strArr) {
        test();
    }

    static void test() {
        int x = 7;
        int y = 4;
        for (int i = 3; i < 12; i++) {
            // x = 7 \/ x = -195 => x \in [-195, 7] as a signed value
            // but [7, bitwise_cast_uint(-195)] as unsigned
            // So 0 is not possible.
            if (x != 0) {
                A.foo();
                // Because A is not loaded, A.foo() traps and this point is not reachable.
            }
            // x is tighten to be in the meet (so Hotspot's join) of [0, 0] and [7, bitwise_cast_uint(-195)]
            // that is bottom (Hotspot's top). Data is dead, control needs to be dead as well.
            for (int j = 1; j < 8; j++) {
                x = -195;
                if (bFld) {
                    y += 2;
                }
            }
        }
    }

    static void foo() {
    }
}


class A {
    static void foo() {
    }
}
