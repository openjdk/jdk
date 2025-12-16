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
 * @test id=with-flags
 * @bug 8373502
 * @summary Test where a VPointer variable was pinned at the pre-loop, but not available at the
 *          Auto_Vectorization_Check, and so it should not be used for the auto vectorization
 *          aliasing check, to avoid a bad (circular) graph.
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,*TestAliasingCheckVPointerVariablesNotAvailable::test
 *      -XX:-TieredCompilation
 *      -Xcomp
 *      ${test.main.class}
 */

/*
 * @test id=vanilla
 * @bug 8373502
 * @run main ${test.main.class}
 */

package compiler.loopopts.superword;

public class TestAliasingCheckVPointerVariablesNotAvailable {
    static int iFld;

    public static void main(String[] strArr) {
        test();
    }

    static void test() {
        int iArr[] = new int[400];
        boolean flag = false;
        for (int i = 6; i < 50000; i++) { // Trigger OSR
            try {
                int x = 234 / iFld;
                iFld = iArr[3];
            } catch (ArithmeticException a_e) {
            }
            for (int j = i; j < 2; j++) {
                if (flag) {
                    iArr[j] = 117;
                } else {
                    iArr[1] = 34;
                }
                iArr[1] += i;
            }
        }
    }
}
