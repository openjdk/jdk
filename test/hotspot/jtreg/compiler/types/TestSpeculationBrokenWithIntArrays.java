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

/**
 * @test
 * @bug 8301630
 * @summary C2: 8297933 broke type speculation in some cases
 *
 * @run main/othervm -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:TypeProfileLevel=222 -XX:CompileOnly=TestSpeculationBrokenWithIntArrays::testHelper
 *                   -XX:CompileOnly=TestSpeculationBrokenWithIntArrays::testHelper2
 *                   -XX:CompileOnly=TestSpeculationBrokenWithIntArrays::test TestSpeculationBrokenWithIntArrays
 *
 */

public class TestSpeculationBrokenWithIntArrays {
    static int[] int_array = new int[10];
    static short[] short_array = new short[10];
    static byte[] byte_array = new byte[10];

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            testHelper(int_array);
            testHelper2(short_array);
        }
        for (int i = 0; i < 20_000; i++) {
            test(int_array);
            test(short_array);
            test(byte_array);
        }
    }

    private static void test(Object o) {
        testHelper(o);
        if (o instanceof short[]) {
            testHelper2(o);
        }
    }

    private static void testHelper(Object o) {
    }

    private static void testHelper2(Object o) {
    }
}
