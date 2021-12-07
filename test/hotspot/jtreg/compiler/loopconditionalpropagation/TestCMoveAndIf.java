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
 * @bug 8275202
 * @summary C2: optimize out more redundant conditions
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:CompileOnly=TestCMoveAndIf::test -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=2017015930 TestCMoveAndIf
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:CompileOnly=TestCMoveAndIf::test -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN TestCMoveAndIf
 */


public class TestCMoveAndIf {
    private static volatile int barrier;

    public static void main(String[] args) {
        int[] array = new int[1000];
        for (int i = 0; i < 20_000; i++) {
            test(-100);
            test(100);
            testHelper(1000, array);
        }
    }

    private static void test(int stop) {
        int[] src = new int[8];
        if (stop > 6) {
            stop = 6;
        }
        stop = stop + 1;
        barrier = 0x42;
        if (stop <= 0) {
            stop = 0;
        }
        barrier = 0x42;
        testHelper(stop+1, src);
    }

    private static void testHelper(int stop, int[] src) {
        for (int i = 0; i < stop; i += 2) {
            int v = src[i];
            if (v != 0) {
            }
        }
    }
}
