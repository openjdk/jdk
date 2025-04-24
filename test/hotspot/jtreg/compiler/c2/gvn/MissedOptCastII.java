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
 * @bug 8319372 8320909
 * @summary Missed optimization in IGVN because `CastIINode::Value` used to
 *          look for deep structures. Reported in 8320909. Fixed in 8319372.
 *
 * @run main/othervm
 *           -XX:CompileCommand=quiet
 *           -XX:CompileCommand=compileonly,MissedOptCastII::*
 *           -XX:-TieredCompilation -Xcomp
 *           -XX:+IgnoreUnrecognizedVMOptions
 *           -XX:+UnlockDiagnosticVMOptions
 *           -XX:+StressIGVN -XX:VerifyIterativeGVN=10
 *           MissedOptCastII
 */

/*
 * @test
 * @bug 8319372 8320909
 *
 * @run main/othervm MissedOptCastII
 */

public class MissedOptCastII {
    static long res = 0;

    static void test() {
        int i, i1 = 0, k, l = -4;
        for (i = 0; i < 100; i++) {
            for (int j = 0; j < 10; j++) {
                for (k = 1; k < 2; k++) {
                    i1 = l;
                    l += k * k;
                    if (l != 0) {
                        res = i + i1 + Float.floatToIntBits(2);
                    }
                }
            }
        }
        res = i + i1;
    }

    public static void main(String[] args) {
        test();
    }
}
