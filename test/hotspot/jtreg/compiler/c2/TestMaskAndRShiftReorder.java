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
 * @bug 8361700
 * @summary An expression of the form "(x & mask) >> shift", where the mask
 *          is a constant, should be transformed to "(x >> shift) & (mask >> shift)"
 *          VerifyIterativeGVN checks that this optimization was applied
 * @run main/othervm -Xcomp -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,compiler.c2.TestMaskAndRShiftReorder::test
 *      -XX:VerifyIterativeGVN=1110 compiler.c2.TestMaskAndRShiftReorder
 * @run main compiler.c2.TestMaskAndRShiftReorder
 *
 */

package compiler.c2;

public class TestMaskAndRShiftReorder {
    static long lFld;


    public static void main(String[] strArr) {
        test();
    }

    static long test() {
        int x = 10;
        int y = -17;
        int iArr[] = new int[10];
        for (int i = 1; i < 7; i++) {
            for (int j = 1; j < 2; j++) {
                x <<= lFld;
            }
            y &= x;
            y >>= 1;
        }
        return iArr.length;
    }
}
