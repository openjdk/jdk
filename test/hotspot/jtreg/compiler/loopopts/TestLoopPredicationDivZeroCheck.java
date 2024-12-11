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

/**
 * @test
 * @bug 8331717
 * @summary C2: Crash with SIGFPE
 *
 * @run main/othervm -XX:CompileCommand=compileonly,*TestLoopPredicationDivZeroCheck*::* -XX:-TieredCompilation -Xbatch TestLoopPredicationDivZeroCheck
 */

public class TestLoopPredicationDivZeroCheck {
    static int iArr[] = new int[100];

    public static void main(String[] strArr) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    static void test() {
        int i1 = 0;

        for (int i4 : iArr) {
            i4 = i1;
            try {
                iArr[0] = 1 / i4;  // Also reproduces with %
                i4 = iArr[2 / i4]; // Also reproduces with %
           } catch (ArithmeticException a_e) {
           }
       }
    }
}
