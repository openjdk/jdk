/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
 * @bug 8270366
 * @summary Test transformation (-a)*(-b) = a*b
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement TestNegMultiply
 *
 */

public class TestNegMultiply {
    private static final int[][] intParams = {
        {Integer.MAX_VALUE, Integer.MAX_VALUE},
        {Integer.MIN_VALUE, Integer.MIN_VALUE},
        {Integer.MAX_VALUE, Integer.MIN_VALUE},
        {232, 34},
        {-23, 445},
        {-244, -84},
        {233, -99}
    };

    private static int intTest(int a, int b) {
        return (-a) * (-b);
    }

    private static void runIntTest() {
        for (int index = 0; index < intParams.length; index ++) {
            int result = intTest(intParams[index][0], intParams[index][1]);
            for (int i = 0; i < 20_000; i++) {
                if (result != intTest(intParams[index][0], intParams[index][1])) {
                    throw new RuntimeException("incorrect result");
                }
            }
        }
    }

    private static final long[][] longParams = {
        {Long.MAX_VALUE, Long.MAX_VALUE},
        {Long.MIN_VALUE, Long.MIN_VALUE},
        {Long.MAX_VALUE, Long.MIN_VALUE},
        {232L, 34L},
        {-23L, 445L},
        {-244L, -84L},
        {233L, -99L}
    };

    private static long longTest(long a, long b) {
        return (-a) * (-b);
    }

    private static void runLongTest() {
        for (int index = 0; index < intParams.length; index ++) {
            long result = longTest(longParams[index][0], longParams[index][1]);
            for (int i = 0; i < 20_000; i++) {
                if (result != longTest(longParams[index][0], longParams[index][1])) {
                    throw new RuntimeException("incorrect result");
                }
            }
        }
    }

    public static void main(String[] args) {
        runIntTest();
        runLongTest();
    }
}
