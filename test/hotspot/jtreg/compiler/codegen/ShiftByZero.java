/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8288445
 * @summary Test shift by 0
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 * compiler.codegen.ShiftByZero
 */

package compiler.codegen;

public class ShiftByZero {

    public static final int N = 64;

    public static int[] i32 = new int[N];

    public static void bMeth() {
        int shift = i32[0];
        // This loop is to confuse the optimizer, so that "shift" is
        // optimized to 0 only after loop vectorization.
        for (int i8 = 279; i8 > 1; --i8) {
            shift <<= 6;
        }
        // low 6 bits of shift are 0, so shift can be
        // simplified to constant 0
        {
            for (int i = 0; i < N; ++i) {
                i32[i] += i32[i] >>= shift;
            }
            for (int i = 0; i < N; ++i) {
                i32[i] += i32[i] >>>= shift;
            }
            for (int i = 0; i < N; ++i) {
                i32[i] >>>= shift;
            }
            for (int i = 0; i < N; ++i) {
                i32[i] >>= shift;
            }
            for (int i = 0; i < N; ++i) {
                i32[i] <<= shift;
            }
        }
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 20_000; i++) {
            bMeth();
        }
    }
}
