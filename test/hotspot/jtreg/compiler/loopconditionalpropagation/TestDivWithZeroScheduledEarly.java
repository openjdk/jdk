/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8275202
 * @summary C2: optimize out more redundant conditions
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:CompileOnly=TestDivWithZeroScheduledEarly::test* -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressGCM -XX:StressSeed=77497032 -XX:+LoopConditionalPropagationALot TestDivWithZeroScheduledEarly
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:CompileOnly=TestDivWithZeroScheduledEarly::test* -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressGCM -XX:+LoopConditionalPropagationALot TestDivWithZeroScheduledEarly
 */

public class TestDivWithZeroScheduledEarly {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            inlined1(0, 0, 100, 1, 1000);
            inlined1(1, 0, 100, 1, 0);
            inlined1(100, 0, 100, 1, 0);
            test1(0, 100, 1, 0);
            inlined2(0, 0, 100, 1, 1000, 0);
            inlined2(1, 0, 100, 1, 0, 0);
            inlined2(100, 0, 100, 1, 0, 0);
            test2(0, 100, 1, 0, 0);
        }
    }

    private static int test1(int start, int stop, int j, int early) {
        int l;
        for (l = 10; l > 0; l--) {

        }
        return inlined1(l, start, stop, j, early);
    }

    private static int inlined1(int k, int start, int stop, int j, int early) {
        int res = 0;
        for (int i = start; i < stop; i++) {
            int div = i / (i + j);
            if (i == early) {
                if (i + j == k) {
                    res += div;
                }
                break;
            }
        }
        return res;
    }

    private static int test2(int start, int stop, int j, int early, int l) {
        int k;
        for (k = 10; k > 0; k--) {

        }
        return inlined2(k, start, stop, j, early, l);
    }

    private static int inlined2(int k, int start, int stop, int j, int early, int l) {
        int res = 0;
        for (int i = start; i < stop; i++) {
            int div = i / (i + j - l);
            if (i == early) {
                if (i + j == k) {
                    res += div;
                }
                break;
            }
        }
        return res;
    }
}
