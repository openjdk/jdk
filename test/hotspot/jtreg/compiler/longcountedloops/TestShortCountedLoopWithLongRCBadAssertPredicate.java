/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8366888
 * @summary C2: incorrect assertion predicate with short running long counted loop
  *
 * @run main/othervm -XX:-BackgroundCompilation TestShortCountedLoopWithLongRCBadAssertPredicate
 * @run main TestShortCountedLoopWithLongRCBadAssertPredicate
 */

import java.util.Objects;

public class TestShortCountedLoopWithLongRCBadAssertPredicate {
    public static void main(String[] args) {
        float[] floatArray = new float[1000];
        for (int i = 0; i < 20_000; i++) {
            test1(floatArray, 10000);
            test2(floatArray, 10000);
            test3(100, 1100, floatArray, 10000);
            test4(999, 0, floatArray, 10000);
        }
    }

    private static float test1(float[] floatArray, long longRange) {
        float v = 0;
        for (int i = 100; i < 1100; i++) {
            v += floatArray[i - 100];
            Objects.checkIndex(i, longRange);
        }
        return v;
    }

    private static float test2(float[] floatArray, long longRange) {
        float v = 0;
        for (int i = 999; i >= 0; i--) {
            v += floatArray[i];
            Objects.checkIndex(i, longRange);
        }
        return v;
    }

    private static float test3(int start, int stop, float[] floatArray, long longRange) {
        float v = 0;
        for (int i = start; i < stop; i++) {
            v += floatArray[i - 100];
            Objects.checkIndex(i, longRange);
        }
        return v;
    }

    private static float test4(int start, int stop, float[] floatArray, long longRange) {
        float v = 0;
        for (int i = start; i >= stop; i--) {
            v += floatArray[i];
            Objects.checkIndex(i, longRange);
        }
        return v;
    }
}
