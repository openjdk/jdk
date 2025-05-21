/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
 * @bug 8215265
 * @summary C2: range check elimination may allow illegal out of bound access
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:-UseLoopPredicate RangeCheckEliminationScaleNotOne
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:+UseLoopPredicate RangeCheckEliminationScaleNotOne
 *
 */

import java.util.Arrays;

public class RangeCheckEliminationScaleNotOne {
    public static void main(String[] args) {
        {
            int[] array = new int[199];
            boolean[] flags = new boolean[100];
            Arrays.fill(flags, true);
            flags[0] = false;
            flags[1] = false;
            for (int i = 0; i < 20_000; i++) {
                test1(100, array, 0, flags);
            }
            boolean ex = false;
            try {
                test1(100, array, -5, flags);
            } catch (ArrayIndexOutOfBoundsException aie) {
                ex = true;
            }
            if (!ex) {
                throw new RuntimeException("no AIOOB exception");
            }
        }

        {
            int[] array = new int[199];
            boolean[] flags = new boolean[100];
            Arrays.fill(flags, true);
            flags[0] = false;
            flags[1] = false;
            for (int i = 0; i < 20_000; i++) {
                test2(100, array, 198, flags);
            }
            boolean ex = false;
            try {
                test2(100, array, 203, flags);
            } catch (ArrayIndexOutOfBoundsException aie) {
                ex = true;
            }
            if (!ex) {
                throw new RuntimeException("no AIOOB exception");
            }
        }

        {
            int[] array = new int[299];
            boolean[] flags = new boolean[100];
            Arrays.fill(flags, true);
            flags[0] = false;
            flags[1] = false;
            for (int i = 0; i < 20_000; i++) {
                test3(100, array, 1, flags);
            }
            boolean ex = false;
            try {
                test3(100, array, -8, flags);
            } catch (ArrayIndexOutOfBoundsException aie) {
                ex = true;
            }
            if (!ex) {
                throw new RuntimeException("no AIOOB exception");
            }
        }

        {
            int[] array = new int[1000];
            boolean[] flags = new boolean[100];
            Arrays.fill(flags, true);
            flags[0] = false;
            flags[1] = false;
            for (int i = 0; i < 20_000; i++) {
                test4(100, array, 302, flags);
            }
            boolean ex = false;
            try {
                test4(100, array, 320, flags);
            } catch (ArrayIndexOutOfBoundsException aie) {
                ex = true;
            }
            if (!ex) {
                throw new RuntimeException("no AIOOB exception");
            }
        }
    }

    private static int test1(int stop, int[] array, int offset, boolean[] flags) {
        if (array == null) {}
        int res = 0;
        for (int i = 0; i < stop; i++) {
            if (flags[i]) {
                res += array[2 * i + offset];
            }
        }
        return res;
    }

    private static int test2(int stop, int[] array, int offset, boolean[] flags) {
        if (array == null) {}
        int res = 0;
        for (int i = 0; i < stop; i++) {
            if (flags[i]) {
                res += array[-2 * i + offset];
            }
        }
        return res;
    }

    private static int test3(int stop, int[] array, int offset, boolean[] flags) {
        if (array == null) {}
        int res = 0;
        for (int i = 0; i < stop; i++) {
            if (flags[i]) {
                res += array[3 * i + offset];
            }
        }
        return res;
    }

    private static int test4(int stop, int[] array, int offset, boolean[] flags) {
        if (array == null) {}
        int res = 0;
        for (int i = 0; i < stop; i++) {
            if (flags[i]) {
                res += array[7 * i + offset];
            }
        }
        return res;
    }
}
