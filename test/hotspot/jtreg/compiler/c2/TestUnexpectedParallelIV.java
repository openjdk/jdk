/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All Rights Reserved.
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
 *
 */

/**
 * @test
 * @bug 8290432
 * @summary Unexpected parallel induction variable pattern was recongized
 *
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *           -XX:CompileCommand=compileonly,compiler.c2.TestUnexpectedParallelIV::test
 *           -XX:CompileCommand=compileonly,compiler.c2.TestUnexpectedParallelIV::test2
 *           -XX:CompileCommand=quiet
 *           -XX:CompileCommand=dontinline,compiler.c2.TestUnexpectedParallelIV::* compiler.c2.TestUnexpectedParallelIV
 */

package compiler.c2;

public class TestUnexpectedParallelIV {

    static boolean bFld;

    static int dontInline() {
        return 0;
    }

    static int test2(int i1) {
        int i2, i3 = 0, i4, i5 = 0, i6;
        for (i2 = 0; 4 > i2; ++i2) {
            for (i4 = 1; i4 < 5; ++i4) {
                i3 -= --i1;
                i6 = 1;
                while (++i6 < 2) {
                    dontInline();
                    if (bFld) {
                        i1 = 5;
                    }
                }
                if (bFld) {
                    break;
                }
            }
        }
        return i3;
    }

    static long test(int val, boolean b) {
        long ret = 0;
        long dArr[] = new long[100];
        for (int i = 15; 293 > i; ++i) {
            ret = val;
            int j = 1;
            while (++j < 6) {
                int k = (val--);
                for (long l = i; 1 > l; ) {
                    if (k != 0) {
                        ret += dontInline();
                    }
                }
                if (b) {
                    break;
                }
            }
        }
        return ret;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            test(0, false);
        }

        test2(5);
    }
}
