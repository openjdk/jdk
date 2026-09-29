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
 * @bug 8349479
 * @summary C2: when a Type node becomes dead, make CFG path that uses it unreachable
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:CompileCommand=dontinline,TestGuardOfCastIIDoesntFold::notInlined
 *                   TestGuardOfCastIIDoesntFold
 * @run main TestGuardOfCastIIDoesntFold
 */

public class TestGuardOfCastIIDoesntFold {
    private static volatile int volatileField;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(1, 0, 0, false);
            helper1(1, 0, 0, true);
            helper2(0, 0);
        }
    }

    private static int test1(int i, int j, int k, boolean flag) {
        int l;
        for (l = 0; l < 10; l++) {
        }
        j = helper2(j, l);
        return helper1(i, j, k, flag);
    }

    private static int helper2(int j, int l) {
        if (l == 10) {
            j = Integer.MAX_VALUE-1;
        }
        return j;
    }

    private static int helper1(int i, int j, int k, boolean flag) {
        if (flag) {
            k = Integer.max(k, -2);
            int[] array = new int[i + k];
            notInlined(array);
            return array[j];
        }
        volatileField = 42;
        return volatileField;
    }

    private static void notInlined(int[] array) {
    }
}
