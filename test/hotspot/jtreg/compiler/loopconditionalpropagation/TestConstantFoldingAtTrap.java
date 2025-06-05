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
 * @bug 8275202
 * @summary C2: optimize out more redundant conditions
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement TestConstantFoldingAtTrap
 */

public class TestConstantFoldingAtTrap {
    public static void main(String[] args) {
        int[] array = new int[2];
        for (int i = 0; i < 20_000; i++) {
            if (test1(array, true) != 2) {
                throw new RuntimeException();
            }
            if (test2(2, true) != 2) {
                throw new RuntimeException();
            }
        }
        array = new int[1];
        final int res = test1(array, false);
        if (res != 1) {
            throw new RuntimeException("Wrong result :" + res);
        }
        if (test2(1, false) != 1) {
            throw new RuntimeException();
        }
    }

    private static int test1(int[] array, boolean flag) {
        final int length = array.length;
        if (length <= 2) {
            int v = array[0]; // length = 0 at rc unc
            v += length;
            if (flag) {
                v += array[1]; // range check replaces dominating one
            }
            return v;
        }
        return 0;
    }

    // Same without LoadRange node. Doesn't fail.
    private static int test2(int length, boolean flag) {
        int[] array = new int[length];
        length = array.length;
        if (length <= 2) {
            int v = array[0];
            v += length;
            if (flag) {
                v += array[1];
            }
            return v;
        }
        return 0;
    }
}
