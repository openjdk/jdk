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
 * @bug 8308660
 * @summary C2 compilation hits 'node must be dead' assert
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:-UseOnStackReplacement
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=242006623 TestFoldIfRemovesTopNode
 * @run main/othervm -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:-UseOnStackReplacement
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN TestFoldIfRemovesTopNode
 *
 */

public class TestFoldIfRemovesTopNode {

    public static void main(String[] args) {
        int[] array = new int[100];
        for (int i = 0; i < 20_000; i++) {
            test(false, true, 0, array);
            test(false, false, 0, array);
            testHelper2(false, false, 0, array);
            testHelper(0, true, array);
        }
    }

    private static void test(boolean flag, boolean flag2, int k, int[] array) {
        if (flag2) {
            testHelper2(flag, flag2, k, array);
        }
    }

    private static void testHelper2(boolean flag, boolean flag2, int k, int[] array) {
        if (flag2) {
            k = -1;
        }
        testHelper(k, flag, array);
    }

    private static void testHelper(int k, boolean flag, int[] array) {
        if (flag) {
            k = new int[k].length;
            int j = k + 3;
            if (j >= 0 && j <= array.length) {
            }
        }
    }
}
