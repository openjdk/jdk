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
 * @bug 8323274
 * @summary converting an array copy to a series of loads/stores add loads that can float
 * @run main/othervm -XX:-UseOnStackReplacement -XX:-TieredCompilation -XX:-BackgroundCompilation TestArrayAccessAboveRCForArrayCopyLoad
 */

public class TestArrayAccessAboveRCForArrayCopyLoad {
    public static void main(String[] args) {
        int[] array = new int[10];
        for (int i = 0; i < 20_000; i++) {
            test(array, 0, array, 1, false);
            test(array, 0, array, 1, true);
        }
        try {
            test(array, -1, array, 0, true);
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {

        }
    }

    private static void test(int[] src, int srcPos, int[] dst, int dstPos, boolean flag) {
        if (src == null) {
        }
        if (srcPos < dstPos) {
            if (flag) {
                System.arraycopy(src, srcPos, dst, dstPos, 2);
            } else {
                System.arraycopy(src, srcPos, dst, dstPos, 2);
            }
        }
    }
}
