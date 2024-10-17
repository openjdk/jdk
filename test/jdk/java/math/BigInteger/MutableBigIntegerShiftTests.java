/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.math.BigInteger;
import java.math.MutableBigIntegerBox;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.math.MutableBigIntegerBox.*;

/**
 * @test
 * @bug 8336274
 * @summary Tests for correctness of MutableBigInteger.leftShift(int)
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build java.base/java.math.MutableBigIntegerBox
 * @key randomness
 * @run junit MutableBigIntegerShiftTests
 */
public class MutableBigIntegerShiftTests {

    private static final int ORDER_SMALL = 60;
    private static final int ORDER_MEDIUM = 100;

    private static final Random random = RandomFactory.getRandom();

    private static int[] orders() {
        return new int[] { ORDER_SMALL, ORDER_MEDIUM };
    }

    @ParameterizedTest
    @MethodSource("orders")
    public void shift(int order) {
        for (int i = 0; i < 100; i++) {
            test(fetchNumber(order), random.nextInt(200));
        }
    }

    @ParameterizedTest
    @MethodSource("pathTargetedCases")
    public void test(MutableBigIntegerBox x, int n) {
        leftShiftAssertions(x, n);
    }

    private static Arguments[] pathTargetedCases() {
        return new Arguments[] {
                // intLen == 0
                Arguments.of(MutableBigIntegerBox.ZERO,
                        random.nextInt(33)),
                // intLen != 0 && n <= leadingZeros
                Arguments.of(new MutableBigIntegerBox(new int[] { (int) random.nextLong(1L, 1L << 16) }),
                        random.nextInt(1, 17)),
                // intLen != 0 && n > leadingZeros && nBits <= leadingZeros && value.length < newLen && nBits == 0
                Arguments.of(new MutableBigIntegerBox(new int[] { (int) random.nextLong(1L, 1L << 32) }),
                        32),
                // intLen != 0 && n > leadingZeros && nBits <= leadingZeros && value.length < newLen && nBits != 0
                Arguments.of(new MutableBigIntegerBox(new int[] { (int) random.nextLong(1L, 1L << 16) }),
                        32 + random.nextInt(1, 17)),
                // intLen != 0 && n > leadingZeros && nBits <= leadingZeros && value.length >= newLen && nBits == 0
                // && newOffset != offset
                Arguments.of(new MutableBigIntegerBox(new int[] { random.nextInt(), (int) random.nextLong(1L, 1L << 32) }, 1, 1),
                        32),
                // intLen != 0 && n > leadingZeros && nBits <= leadingZeros && value.length >= newLen && nBits == 0
                // && newOffset == offset
                Arguments.of(new MutableBigIntegerBox(new int[] { (int) random.nextLong(1L, 1L << 32), random.nextInt() }, 0, 1),
                        32),
                // intLen != 0 && n > leadingZeros && nBits <= leadingZeros && value.length >= newLen && nBits != 0
                // && newOffset != offset
                Arguments.of(new MutableBigIntegerBox(new int[] { random.nextInt(), (int) random.nextLong(1L, 1L << 16) }, 1, 1),
                        32 + random.nextInt(1, 17)),
                // intLen != 0 && n > leadingZeros && nBits <= leadingZeros && value.length >= newLen && nBits != 0
                // && newOffset == offset
                Arguments.of(new MutableBigIntegerBox(new int[] { (int) random.nextLong(1L, 1L << 16), random.nextInt() }, 0, 1),
                        32 + random.nextInt(1, 17)),
                // intLen != 0 && n > leadingZeros && nBits > leadingZeros && value.length < newLen
                Arguments.of(new MutableBigIntegerBox(new int[] { (int) random.nextLong(1L << 15, 1L << 32) }),
                        random.nextInt(17, 32)),
                // intLen != 0 && n > leadingZeros && nBits > leadingZeros && value.length >= newLen && newOffset != offset
                Arguments.of(new MutableBigIntegerBox(new int[] { random.nextInt(), (int) random.nextLong(1L << 15, 1L << 32) }, 1, 1),
                        random.nextInt(17, 32)),
                // intLen != 0 && n > leadingZeros && nBits > leadingZeros && value.length >= newLen && newOffset == offset
                Arguments.of(new MutableBigIntegerBox(new int[] { (int) random.nextLong(1L << 15, 1L << 32), random.nextInt() }, 0, 1),
                        random.nextInt(17, 32)),
        };
    }

    private static void leftShiftAssertions(MutableBigIntegerBox x, int n) {
        MutableBigIntegerBox xShifted = x.shiftLeft(n);
        assertEquals(x.multiply(new MutableBigIntegerBox(BigInteger.TWO.pow(n))), xShifted);
        assertEquals(x, xShifted.shiftRight(n));
    }

    /*
     * Get a random or boundary-case number. This is designed to provide
     * a lot of numbers that will find failure points, such as max sized
     * numbers, empty MutableBigIntegers, etc.
     *
     * If order is less than 2, order is changed to 2.
     */
    private static MutableBigIntegerBox fetchNumber(int order) {
        int numType = random.nextInt(8);
        MutableBigIntegerBox result = null;
        if (order < 2) order = 2;

        int[] val;
        switch (numType) {
            case 0: // Empty
                result = MutableBigIntegerBox.ZERO;
                break;

            case 1: // One
                result = MutableBigIntegerBox.ONE;
                break;

            case 2: // All bits set in number
                int numInts = (order + 31) >> 5;
                int[] fullBits = new int[numInts];
                Arrays.fill(fullBits, -1);

                fullBits[0] &= -1 >>> -order;
                result = new MutableBigIntegerBox(fullBits);
                break;

            case 3: // One bit in number
                result = MutableBigIntegerBox.ONE.shiftLeft(random.nextInt(order));
                break;

            case 4: // Random bit density
                val = new int[(order + 31) >> 5];
                int iterations = random.nextInt(order);
                for (int i = 0; i < iterations; i++) {
                    int bitIdx = random.nextInt(order);
                    val[bitIdx >> 5] |= 1 << bitIdx;
                }
                result = new MutableBigIntegerBox(val);
                break;
            case 5: // Runs of consecutive ones and zeros
                result = ZERO;
                int remaining = order;
                int bit = random.nextInt(2);
                while (remaining > 0) {
                    int runLength = Math.min(remaining, random.nextInt(order));
                    result = result.shiftLeft(runLength);
                    if (bit > 0)
                        result = result.add(ONE.shiftLeft(runLength).subtract(ONE));
                    remaining -= runLength;
                    bit = 1 - bit;
                }
                break;
            case 6: // random bits with trailing space
                int len = random.nextInt((order + 31) >> 5) + 1;
                int offset = random.nextInt(len);
                val = new int[len << 1];
                for (int i = 0; i < val.length; i++)
                    val[i] = random.nextInt();
                result = new MutableBigIntegerBox(val, offset, len);
                break;
            default: // random bits
                result = new MutableBigIntegerBox(new BigInteger(order, random));
        }

        return result;
    }
}
