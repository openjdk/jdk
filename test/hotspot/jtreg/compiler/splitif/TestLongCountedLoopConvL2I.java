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

/*
 * @test
 * @bug 8331575
 * @summary C2: crash when ConvL2I is split thru phi at LongCountedLoop
 * @run main/othervm -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:-UseOnStackReplacement
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=92643864 TestLongCountedLoopConvL2I
 * @run main/othervm -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:-UseOnStackReplacement
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM TestLongCountedLoopConvL2I
 * @run main/othervm -XX:-BackgroundCompilation -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM TestLongCountedLoopConvL2I
 */

public class TestLongCountedLoopConvL2I {
    private static volatile int volatileField;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            testHelper1(42);
            test1(0);
        }
    }

    private static int test1(int res) {
        int k = 1;
        for (; k < 2; k *= 2) {
        }
        long i = testHelper1(k);
        for (; i > 0; i--) {
            res += 42 / ((int) i);
            for (int j = 1; j < 10; j *= 2) {

            }
        }
        return res;
    }

    private static long testHelper1(int k) {
        if (k == 2) {
            return 100;
        } else {
            return 99;
        }
    }
}
