/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8349637
 * @requires vm.flavor == "server" & (vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel == 4)
 * @summary Ensure that vectorization of numberOfLeadingZeros and numberOfTrailingZeros outputs correct values
 * @library /test/lib /
 * @run main/othervm compiler.vectorization.TestVectorZeroCount
 */

package compiler.vectorization;

import java.util.Random;

import jdk.test.lib.Utils;

public class TestVectorZeroCount {
    private static final int SIZE = 1024;
    private static final Random RANDOM = Utils.getRandomInstance();

    private static final int[] INT_VALUES = new int[SIZE];
    private static final int[] INT_EXPECTED_LEADING = new int[SIZE];
    private static final int[] INT_RESULT_LEADING = new int[SIZE];
    private static final int[] INT_EXPECTED_TRAILING = new int[SIZE];
    private static final int[] INT_RESULT_TRAILING = new int[SIZE];

    private static final long[] LONG_VALUES = new long[SIZE];
    private static final long[] LONG_EXPECTED_LEADING = new long[SIZE];
    private static final long[] LONG_RESULT_LEADING = new long[SIZE];
    private static final long[] LONG_EXPECTED_TRAILING = new long[SIZE];
    private static final long[] LONG_RESULT_TRAILING = new long[SIZE];

    private static final int INT_START_INDEX  = Integer.MIN_VALUE;
    private static final int INT_END_INDEX    = Integer.MAX_VALUE;
    private static final int LONG_START_INDEX = 0;
    private static final int LONG_END_INDEX   = 100_000_000;

    private static int intCounter;
    private static int longCounter;

    public static boolean testInt() {
        boolean done = false;

        // Non-vectorized loop as baseline (not vectorized because source array is initialized)
        for (int i = 0; i < SIZE; ++i) {
            INT_VALUES[i] = intCounter++;
            if (intCounter == INT_END_INDEX) {
                done = true;
            }
            INT_EXPECTED_LEADING[i] = Integer.numberOfLeadingZeros(INT_VALUES[i]);
            INT_EXPECTED_TRAILING[i] = Integer.numberOfTrailingZeros(INT_VALUES[i]);
        }
        // Vectorized loop
        for (int i = 0; i < SIZE; ++i) {
            INT_RESULT_LEADING[i] = Integer.numberOfLeadingZeros(INT_VALUES[i]);
        }
        for (int i = 0; i < SIZE; ++i) {
            INT_RESULT_TRAILING[i] = Integer.numberOfTrailingZeros(INT_VALUES[i]);
        }

        // Compare results
        for (int i = 0; i < SIZE; ++i) {
            if (INT_RESULT_LEADING[i] != INT_EXPECTED_LEADING[i]) {
                throw new RuntimeException("Unexpected result for Integer.numberOfLeadingZeros(" + INT_VALUES[i] + "): " + INT_RESULT_LEADING[i] + ", expected " + INT_EXPECTED_LEADING[i]);
            }
            if (INT_RESULT_TRAILING[i] != INT_EXPECTED_TRAILING[i]) {
                throw new RuntimeException("Unexpected result for Integer.numberOfTrailingZeros(" + INT_VALUES[i] + "): " + INT_RESULT_TRAILING[i] + ", expected " + INT_EXPECTED_TRAILING[i]);
            }
        }
        return done;
    }

    public static boolean testLong() {
        boolean done = false;

        // Non-vectorized loop as baseline (not vectorized because source array is initialized)
        for (int i = 0; i < SIZE; ++i) {
            // Use random values because the long range is too large to iterate over it
            LONG_VALUES[i] = RANDOM.nextLong();
            if (longCounter++ == LONG_END_INDEX) {
                done = true;
            }
            LONG_EXPECTED_LEADING[i] = Long.numberOfLeadingZeros(LONG_VALUES[i]);
            LONG_EXPECTED_TRAILING[i] = Long.numberOfTrailingZeros(LONG_VALUES[i]);
        }
        // Vectorized loop
        for (int i = 0; i < SIZE; ++i) {
            LONG_RESULT_LEADING[i] = Long.numberOfLeadingZeros(LONG_VALUES[i]);
        }
        for (int i = 0; i < SIZE; ++i) {
            LONG_RESULT_TRAILING[i] = Long.numberOfTrailingZeros(LONG_VALUES[i]);
        }

        // Compare results
        for (int i = 0; i < SIZE; ++i) {
            if (LONG_RESULT_LEADING[i] != LONG_EXPECTED_LEADING[i]) {
                throw new RuntimeException("Unexpected result for Long.numberOfLeadingZeros(" + LONG_VALUES[i] + "): " + LONG_RESULT_LEADING[i] + ", expected " + LONG_EXPECTED_LEADING[i]);
            }
            if (LONG_RESULT_TRAILING[i] != LONG_EXPECTED_TRAILING[i]) {
                throw new RuntimeException("Unexpected result for Long.numberOfTrailingZeros(" + LONG_VALUES[i] + "): " + LONG_RESULT_TRAILING[i] + ", expected " + LONG_EXPECTED_TRAILING[i]);
            }
        }
        return done;
    }

    public static void main(String[] args) {
        // Run twice to make sure compiled code is used from the beginning
        for (int i = 0; i < 2; ++i) {
            intCounter = INT_START_INDEX;
            longCounter = LONG_START_INDEX;
            while (!testLong()) ;
            while (!testInt()) ;
        }
    }
}
