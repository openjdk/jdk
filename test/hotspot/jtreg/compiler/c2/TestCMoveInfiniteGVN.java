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
 * @key stress randomness
 * @bug 8280123
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestCMoveInfiniteGVN::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=43739875
 *                   compiler.c2.TestCMoveInfiniteGVN
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestCMoveInfiniteGVN::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *                   compiler.c2.TestCMoveInfiniteGVN
 */

package compiler.c2;

public class TestCMoveInfiniteGVN {

    static int test(boolean b, int i) {
        int iArr[] = new int[2];

        double d = Math.max(i, i);
        for (int i1 = 1; i1 < 2; i1++) {
            if (i1 != 0) {
                return (b ? 1 : 0); // CMoveI
            }
            for (int i2 = 1; i2 < 2; i2++) {
                switch (i2) {
                    case 1: d -= Math.max(i1, i2); break;
                }
                d -= iArr[i1 - 1];
            }
        }
        return 0;
    }

    static void test() {
        test(true, 234);
    }

    public static void main(String[] strArr) {
        test(); // compilation, then nmethod invalidation during execution
        test(); // trigger crashing recompilation
    }
}
