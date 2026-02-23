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
 * @bug 8378413
 * @summary ((x << C) + y) >>> C Ideal optimization missed after remix_address_expressions
 *          swaps AddI edges without notifying IGVN.
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:VerifyIterativeGVN=1110
 *                   -Xbatch ${test.main.class}
 */

public class MissedURShiftIAddILShiftIdeal {

    static int test(int y, int[] arr) {
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            sum += ((arr[i] << 3) + y) >>> 3;
        }
        return sum;
    }

    public static void main(String[] args) {
        int[] arr = new int[1];
        for (int i = 0; i < 20_000; i++) {
            test(42, arr);
        }
    }
}
