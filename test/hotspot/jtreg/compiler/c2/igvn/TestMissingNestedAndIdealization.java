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

package compiler.c2.igvn;

/*
 * @test
 * @bug 8395378
 * @summary Test notification for nested AndI/AndL optimization: (x & c1) & c2 -> x & (c1 & c2)
 * @run main/othervm -Xbatch
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingNestedAndIdealization {
    static int valueI = 0xffff_ffff;
    static long valueL = 0xffff_ffff;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            testI();
            testL();
        }
    }

    public static int testI() {
        int v = 0xffff;
        for (int i = 0; i < 200; i++) {
            v &= valueI;
            for (int j = 0; j < 10; j++) {
                valueI = 0xff;
            }
        }
        return v;
    }

    public static long testL() {
        long v = 0xffff;
        for (int i = 0; i < 200; i++) {
            v &= valueL;
            for (int j = 0; j < 10; j++) {
                valueL = 0xff;
            }
        }
        return v;
    }
}
