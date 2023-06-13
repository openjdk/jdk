/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @key stress randomness
 * @bug 8291466
 * @summary Infinite loop in PhaseIterGVN::transform_old with -XX:+StressIGVN
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *                   -XX:StressSeed=1 compiler.c2.TestMulNodeInfiniteGVN
 */

package compiler.c2;

public class TestMulNodeInfiniteGVN {

    private static int fun() {
        int sum = 0;
        for (int c = 0; c < 50000; c++) {
            int x = 9;
            while ((x += 2) < 12) {
                for (int k = 1; k < 2; k++) {
                    sum += x * k;
                }
            }
            int y = 11;
            while ((y += 2) < 14) {
                for (int k = 1; k < 2; k++) {
                    sum += y * k;
                }
            }
            int z = 17;
            while ((z += 2) < 20) {
                for (int k = 1; k < 2; k++) {
                    sum += z * k;
                }
            }
        }
        return sum;
    }

    public static void main(String[] args) {
        fun();
    }
}
