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
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=compileonly,compiler.igvn.CmpDisjointButNonOrderedRanges2::*
 *                   -XX:-TieredCompilation
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:PerMethodTrapLimit=0
 *                   compiler.igvn.CmpDisjointButNonOrderedRanges2
 * @run main compiler.igvn.CmpDisjointButNonOrderedRanges2
 */
package compiler.igvn;

public class CmpDisjointButNonOrderedRanges2 {
    int array[];

    void test() {
        int val = 2;
        for (int i = 0; i < 10; i++) {
            // val = 2 \/ val = -12 => val \in [-12, 2] as a signed value
            // but [2, bitwise_cast_uint(-12)] as unsigned
            // So 0 is not possible.
            if (val != 0) {
                return;
            }
            // val is tighten to be in the meet (so Hotspot's join) of [0, 0] and [2, bitwise_cast_uint(-12)]
            // that is bottom (Hotspot's top). Data is dead, control needs to be dead as well.
            for (int j = 0; j < 10; j++) {
                array[1] = val;
                val = -12;
            }
        }
    }

    static public void main(String[] args) {
        var c = new CmpDisjointButNonOrderedRanges2();
        for (int i = 0; i < 1000; ++i) {
            c.test();
            for (int j = 0; j < 100; ++j) {
            }
        }
    }
}
