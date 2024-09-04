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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.math.MutableBigIntegerBox;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.math.MutableBigIntegerBox.*;

/**
 * @test
 * @bug 8336274
 * @summary Tests for correctness of MutableBigInteger.leftShift(int)
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build java.base/java.math.MutableBigIntegerBox
 * @key randomness
 * @run junit/othervm -DmaxDurationMillis=3000 MutableBigIntegerShiftTests
 */
public class MutableBigIntegerShiftTests {

    private static final int DEFAULT_MAX_DURATION_MILLIS = 3_000;

    static final int ORDER_SMALL = 60;
    static final int ORDER_MEDIUM = 100;

    private static int maxDurationMillis;
    private static Random random = RandomFactory.getRandom();

    static boolean failure = false;

    @BeforeAll
    static void setMaxDurationMillis() {
        maxDurationMillis = Math.max(maxDurationMillis(), 0);
    }

    public static void shift(int order) {
        for (int i = 0; i < 100; i++) {
            MutableBigIntegerBox x = fetchNumber(order);
            int n = Math.abs(random.nextInt() % 200);

            assertTrue(x.shiftLeft(n).compare
                    (x.multiply(new MutableBigIntegerBox(BigInteger.TWO.pow(n)))) == 0,
                    "Inconsistent left shift: " + x + "<<" + n + " != " + x + "*2^" + n);

            assertTrue(x.shiftLeft(n).shiftRight(n).compare(x) == 0,
                    "Inconsistent left shift: (" + x + "<<" + n + ")>>" + n + " != " + x);
        }
    }

    @Test
    public static void testShift() {
        shift(ORDER_SMALL);
        shift(ORDER_MEDIUM);
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
                for(int i = 0; i < numInts; i++)
                    fullBits[i] = -1;

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

    private static int maxDurationMillis() {
        try {
            return Integer.parseInt(System.getProperty("maxDurationMillis",
                    Integer.toString(DEFAULT_MAX_DURATION_MILLIS)));
        } catch (NumberFormatException ignore) {
        }
        return DEFAULT_MAX_DURATION_MILLIS;
    }
}
