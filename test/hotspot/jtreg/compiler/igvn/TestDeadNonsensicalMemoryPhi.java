/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package compiler.igvn;

/*
 * @test
 * @bug 8375645
 * @summary A dead memory Phi has nonsensical inputs, it is a Phi in a loop but both of its inputs
 *          are not in that loop.
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:CompileOnly=${test.main.class}::test -XX:-TieredCompilation -Xcomp
 *                   -XX:+StressIGVN -XX:+StressCCP -XX:+StressLoopPeeling -XX:RepeatCompilation=500
 *                   ${test.main.class}
 */
public class TestDeadNonsensicalMemoryPhi {
    long x;
    int[] iArrFld;

    public static void main(String[] args) {
        TestDeadNonsensicalMemoryPhi obj = new TestDeadNonsensicalMemoryPhi();
        for (int i = 0; i < 100; i++) {
            try {
                obj.test();
            } catch (NullPointerException e) {}
        }
    }

    void test() {
        for (int i = 7; i < 152; i++) {
            x = 0;
        }
        for (int i = 3; i < 273; i++) {
            for (long j = 3; j < 97; j++) {
                if (i == 7) {
                    for (int k = 2; k > 1; k--) {
                        iArrFld[1] = i;
                    }
                }
            }
        }
    }
}
