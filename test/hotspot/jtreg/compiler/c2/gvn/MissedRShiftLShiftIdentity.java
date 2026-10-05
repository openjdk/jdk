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
package compiler.c2.gvn;

/*
 * @test
 * @bug 8374798
 * @summary RShift(LShift(x, C), C) Identity missed when shift counts are different
 *          constant nodes for the same effective count (e.g. -1 vs 31) due to
 *          mask_and_replace_shift_amount normalizing them at different times.
 *
 * @run main ${test.main.class}
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressIGVN -XX:+StressCCP -XX:VerifyIterativeGVN=1000 -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test* ${test.main.class}
 */

public class MissedRShiftLShiftIdentity {
    public static int iFld = 0;

    public static void test() {
        int[] iArr = new int[10];
        int i2 = -1, i3 = 0;

        for (int i11 : iArr) {
            iFld = i11;
            for (int i1 = 0; i1 < 10; i1++) {
                iFld <<= i3;
                iFld >>= i2; // RShift
                i3 = i2;
            }
            int i16 = 0;
            do {
                for (int f3 = 1; f3 < 1; f3 += 3) {
                    i2 = -1;
                }
            } while (++i16 < 5);
        }
    }

    public static void main(String[] args) {
        test();
    }
}
