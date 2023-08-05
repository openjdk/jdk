/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8298353
 * @summary C2 fails with assert(opaq->outcnt() == 1 && opaq->in(1) == limit) failed
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation TestBadCountedLoopLimit
 *
 */

import java.util.Arrays;

public class TestBadCountedLoopLimit {
    private static volatile int barrier;
    private static int field;

    public static void main(String[] args) {
        boolean[] flag1 = new boolean[100];
        boolean[] flag2 = new boolean[100];
        Arrays.fill(flag2, true);
        for (int i = 0; i < 20_000; i++) {
            test(0, flag1, flag1);
            test(0, flag2, flag2);
            testHelper(true, 0, 0);
            testHelper(false, 0, 0);
        }
    }

    private static int test(int v, boolean[] flag, boolean[] flag2) {
        int j = testHelper(flag2[0], 0, 1);
        int i = 1;
        int limit = 0;
        for (;;) {
            synchronized (new Object()) {
            }
            limit = j;
            if (i >= 100) {
                break;
            }

            if (flag[i]) {
                return limit - 3;
            }

            j = testHelper(flag2[i], 100, 101);
            i *= 2;
        };
        for (int k = 0; k < limit; k++) {
            barrier = 0x42;
        }
        return j;
    }

    private static int testHelper(boolean flag2, int x, int x1) {
        return flag2 ? x : x1;
    }
}
