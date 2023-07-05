/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8309902
 * @summary C2: assert(false) failed: Bad graph detected in build_loop_late after JDK-8305189
 * @run main/othervm  -Xcomp -XX:CompileCommand=compileonly,TestAssertPredicatePeeling::* TestAssertPredicatePeeling
 */


public class TestAssertPredicatePeeling {
    static volatile long instanceCount;

    public static void main(String[] strArr) {
        test();
    }

    static int test() {
        int i2 = 2, i17 = 3, i18 = 2, iArr[] = new int[10];

        int i15 = 1;
        while (i15 < 100000) {
            for (int i16 = i15; i16 < 1; ++i16) {
                try {
                    iArr[i16] = 5 / iArr[6];
                    i17 = iArr[5] / i2;
                    i2 = i15;
                } catch (ArithmeticException a_e) {
                }
                instanceCount -= i15;
            }
            i15++;
        }
        return i17;
    }
}

