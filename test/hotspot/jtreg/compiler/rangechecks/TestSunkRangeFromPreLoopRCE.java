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
 * @bug 8342330
 * @summary C2: "node pinned on loop exit test?" assert failure
 * @requires vm.flavor == "server"
  *
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:-TieredCompilation
 *                   -XX:-UseLoopPredicate -XX:LoopMaxUnroll=0 TestSunkRangeFromPreLoopRCE
 *
 */


import java.util.Arrays;

public class TestSunkRangeFromPreLoopRCE {
    private static int[] array = new int[1000];
    private static A objectField = new A(42);

    public static void main(String[] args) {
        boolean[] allTrue = new boolean[1000];
        Arrays.fill(allTrue, true);
        boolean[] allFalse = new boolean[1000];
        for (int i = 0; i < 20_000; i++) {
            test1(array.length/4, allTrue, 1, 0);
            test1(array.length/4, allFalse, 1, 0);
        }
    }

    private static int test1(int stop, boolean[] flags, int otherScale, int x) {
        int scale;
        for (scale = 0; scale < 4; scale++) {
            for (int i = 0; i < 10; i++) {

            }
        }
        if (array == null) {
        }
        int v = 0;
        for (int i = 0; i < stop; i++) {
            v += array[i];
            v += array[scale * i];
            if (i * scale + (objectField.intField + 1) == x) {
            }
            v += (scale - 4) * (x-objectField.intField);
            if (flags[i]) {
                return (x-objectField.intField);
            }
        }
        return v;
    }

    private static class A {
        A(int field) {
            intField = field;
        }
        public int intField;
    }
}
