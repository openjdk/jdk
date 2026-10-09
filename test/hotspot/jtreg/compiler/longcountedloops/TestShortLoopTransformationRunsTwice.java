/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @bug 8391917
 * @run main/othervm -XX:-BackgroundCompilation ${test.main.class}
 */

package compiler.longcountedloops;
import java.util.Objects;

public class TestShortLoopTransformationRunsTwice {
    public static void main(String[] args) {
        float[] array = new float[1000];
        for (int i = 0; i < 20_000; i++) {
            test1(1000, array, array, 4000);
        }
    }

    private static float test1(int stop, float[] array1, float[] array2, long longLength) {
        int j = 2;
        for (; j < 4; j *= 2) {
        }
        float res = 0;
        for (int i = 1; i < stop; i++) {
            res += array1[i] + array2[i];
            Objects.checkIndex(i, longLength);
            Objects.checkIndex(i * (j - 3), longLength);
        }
        return res;
    }
}
