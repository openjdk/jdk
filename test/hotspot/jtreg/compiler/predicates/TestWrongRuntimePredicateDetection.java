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
 *
 */

/*
 * @test
 * @bug 8317723
 * @library /test/lib
 * @summary Test that CountedLoopEndNodes and zero trip guard check If nodes are not treated as Runtime Predicates.
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestWrongRuntimePredicateDetection::test*
 *                   compiler.predicates.TestWrongRuntimePredicateDetection
 */

package compiler.predicates;

public class TestWrongRuntimePredicateDetection {
    static int[] iArr = new int[50];
    static long instanceCount;
    static boolean bFld = true;
    static volatile byte byFld;
    static long[][] lArrFld;


    public static void main(String[] x) {
        for (int i = 0; i < 1000; i++) {
            testCountedLoopEndAsRuntimePredicate();
        }
        for (int i = 0; i < 10; i++) {
            testZeroTripGuardAsRuntimePredicate();
        }
    }

    static void testCountedLoopEndAsRuntimePredicate() {
        int i22 = 7, i26, i28, i29 = 8, i31 = 1;
        float f4;
        do {
            for (int i = 0; i < 10000; i++) {
                if (bFld) {
                    break;
                }
                instanceCount = byFld;
            }
            for (i26 = 4; 80 > i26; i26 += 2) ;
        } while (++i22 < 315);
        i28 = 6;
        while ((i28 -= 3) > 0) {
            for (f4 = i28; f4 < 53; f4++) {
                bFld = false;
            }
            instanceCount = i26;
            do {
                switch ((i26 >>> 1) % 2 * 5 + 6) {
                    case 12:
                    case 10:
                        lArrFld[i31][1] = i29;
                }
            } while (++i31 < 53);
        }
    }

    static void testZeroTripGuardAsRuntimePredicate() {
        int m;
        int a[] = new int[50];
        for (int j = 0; j < a.length; j++) {
            a[j] = j;
        }

        for (int j = 4; j < 42; j++) {
            for (int k = 1; k < 5; k++) {
                iArr[1] = 34;
                switch (j % 4) {
                    case 0:
                        iArr = iArr;
                    case 1:
                    case 3:
                        m = 3;
                }
            }
        }
    }

}
