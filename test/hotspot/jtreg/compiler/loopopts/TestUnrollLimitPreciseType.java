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
 * @test id=test1
 * @bug 8298935
 * @summary CMoveI for underflow protection of the limit did not compute a type that was precise enough.
 *          This lead to dead data but zero-trip-guard control did not die -> "malformed control flow".
 * @requires vm.compiler2.enabled
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,compiler.loopopts.TestUnrollLimitPreciseType::test1
 *      -XX:CompileCommand=dontinline,compiler.loopopts.TestUnrollLimitPreciseType::*
 *      -XX:MaxVectorSize=64
 *      -Xcomp
 *      -XX:+UnlockExperimentalVMOptions -XX:PerMethodSpecTrapLimit=0 -XX:PerMethodTrapLimit=0
 *      compiler.loopopts.TestUnrollLimitPreciseType test1
 */

/*
 * @test id=test2
 * @bug 8298935
 * @summary CMoveI for underflow protection of the limit did not compute a type that was precise enough.
 *          This lead to dead data but zero-trip-guard control did not die -> "malformed control flow".
 * @requires vm.compiler2.enabled
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,compiler.loopopts.TestUnrollLimitPreciseType::*
 *      -Xcomp
 *      compiler.loopopts.TestUnrollLimitPreciseType test2
 */


package compiler.loopopts;

public class TestUnrollLimitPreciseType {
    static final int RANGE = 512;

    public static void main(String args[]) {
        if (args.length != 1) {
            throw new RuntimeException("Need exactly one argument.");
        }
        if (args[0].equals("test1")) {
            byte[] data = new byte[RANGE];
            test1(data);
        } else if (args[0].equals("test2")) {
            test2();
        } else {
            throw new RuntimeException("Do not have: " + args[0]);
        }
    }

    public static void test1(byte[] data) {
        // Did not fully analyze this. But it is also unrolled, SuperWorded,
        // and further unrolled with vectorlized post loop.
        // Only seems to reproduce with avx512, and not with avx2.
        for (int j = 192; j < RANGE; j++) {
            data[j - 192] = (byte)(data[j] * 11);
        }
    }

    static void test2() {
        // Loop is SuperWord'ed.
        // We unroll more afterwards, and so add vectorized post loop.
        // But it turns out that the vectorized post loop is never entered.
        // This lead to assert, because the zero-trip-guard did not collaspse,
        // but the CastII with the trip count did die.
        // Only seems to reproduce with avx512, and not with avx2.
        double dArr[][] = new double[100][100];
        for (int i = 2, j = 2; j < 68; j++) {
            dArr[i][j] = 8;
        }
    }
}
