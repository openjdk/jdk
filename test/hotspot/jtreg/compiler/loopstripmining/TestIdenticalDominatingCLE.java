/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8340532
 * @summary C2: assert(is_OuterStripMinedLoop()) failed: invalid node class: IfTrue
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:CompileOnly=TestIdenticalDominatingCLE::* -XX:CompileThreshold=100 -Xcomp -XX:-TieredCompilation
 *                   -XX:-RangeCheckElimination -XX:LoopMaxUnroll=0 TestIdenticalDominatingCLE
 *
 */


public class TestIdenticalDominatingCLE {
    boolean bFld;
    long lFld;
    float[][] fArr = new float[6][6];

    public static void main(String[] var0) {
        TestIdenticalDominatingCLE t = new TestIdenticalDominatingCLE();
        t.test();
    }

    void test() {
        int i = 0;
        do {
            for (int j = 0; j < 2; j++) {
                float f = fArr[j][3] / Float.valueOf((float)1.318095814E9);
                switch (i) {
                    case 1:
                        if (bFld ^ bFld) {
                        } else {
                            for (int k = 0; k < 600; k++) {
                            }
                        }
                        break;
                    default:
                        if (bFld) {
                        }
                }
            }
            lFld = ++i;
        } while (i < 6);
    }
}
