/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8271056
 * @summary A dead data loop is created when applying an unsafe case of Cmov'ing identity.
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,compiler.c2.TestDeadDataLoopCmoveIdentity::*
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=359948366 compiler.c2.TestDeadDataLoopCmoveIdentity
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,compiler.c2.TestDeadDataLoopCmoveIdentity::*
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN compiler.c2.TestDeadDataLoopCmoveIdentity
 */

package compiler.c2;

public class TestDeadDataLoopCmoveIdentity {
    static boolean bFld;

    public static void main(String[] strArr) {
        test();
        test2();
    }

    static void test() {
        int i33 = 51925, iArr3[] = new int[10];
        if (bFld) {
            ;
        } else if (bFld) {
            for (int i = 0; i < 100; i++) { }
            do {
                if (i33 != 0) {
                }
                int i34 = 1;
                do {
                    switch (0) {
                        case 122: { }
                    }
                } while (i34 < 1);
                i33 += i33 + 3;
            } while (i33 < 5);
        }
    }

    static void test2() {
        int i33 = 51925, iArr3[] = new int[10];
        if (bFld) {
            ;
        } else if (bFld) {
            do {
                if (i33 != 0) {
                }
                int i34 = 1;
                do {
                    switch (0) {
                        case 122: {}
                    }
                } while (i34 < 1);
            } while (++i33 < 5);
        }
    }
}
