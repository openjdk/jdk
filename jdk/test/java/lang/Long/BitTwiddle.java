/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug     4495754
 * @summary Basic test for long bit twiddling
 * @author  Josh Bloch
 */

import java.util.Random;
import static java.lang.Long.*;

public class BitTwiddle {
    private static final int N = 1000; // # of repetitions per test

    public static void main(String args[]) {
        Random rnd = new Random();

        if (highestOneBit(0) != 0)
            throw new RuntimeException("a");
        if (highestOneBit(-1) != MIN_VALUE)
            throw new RuntimeException("b");
        if (highestOneBit(1) != 1)
            throw new RuntimeException("c");

        if (lowestOneBit(0) != 0)
            throw new RuntimeException("d");
        if (lowestOneBit(-1) != 1)
            throw new RuntimeException("e");
        if (lowestOneBit(MIN_VALUE) != MIN_VALUE)
            throw new RuntimeException("f");

        for (int i = 0; i < N; i++) {
            long x = rnd.nextLong();
            if (highestOneBit(x) != reverse(lowestOneBit(reverse(x))))
                throw new RuntimeException("g: " + toHexString(x));
        }

        if (numberOfLeadingZeros(0) != SIZE)
            throw new RuntimeException("h");
        if (numberOfLeadingZeros(-1) != 0)
            throw new RuntimeException("i");
        if (numberOfLeadingZeros(1) != (SIZE - 1))
            throw new RuntimeException("j");

        if (numberOfTrailingZeros(0) != SIZE)
            throw new RuntimeException("k");
        if (numberOfTrailingZeros(1) != 0)
            throw new RuntimeException("l");
        if (numberOfTrailingZeros(MIN_VALUE) != (SIZE - 1))
            throw new RuntimeException("m");

        for (int i = 0; i < N; i++) {
            long x = rnd.nextLong();
            if (numberOfLeadingZeros(x) != numberOfTrailingZeros(reverse(x)))
                throw new RuntimeException("n: " + toHexString(x));
        }

        if (bitCount(0) != 0)
                throw new RuntimeException("o");

        for (int i = 0; i < SIZE; i++) {
            long pow2 = 1L << i;
            if (bitCount(pow2) != 1)
                throw new RuntimeException("p: " + i);
            if (bitCount(pow2 -1) != i)
                throw new RuntimeException("q: " + i);
        }

        for (int i = 0; i < N; i++) {
            long x = rnd.nextLong();
            if (bitCount(x) != bitCount(reverse(x)))
                throw new RuntimeException("r: " + toHexString(x));
        }

        for (int i = 0; i < N; i++) {
            long x = rnd.nextLong();
            int dist = rnd.nextInt();
            if (bitCount(x) != bitCount(rotateRight(x, dist)))
                throw new RuntimeException("s: " + toHexString(x) +
                                           toHexString(dist));
            if (bitCount(x) != bitCount(rotateLeft(x, dist)))
                throw new RuntimeException("t: " + toHexString(x) +
                                           toHexString(dist));
            if (rotateRight(x, dist) != rotateLeft(x, -dist))
                throw new RuntimeException("u: " + toHexString(x) +
                                           toHexString(dist));
            if (rotateRight(x, -dist) != rotateLeft(x, dist))
                throw new RuntimeException("v: " + toHexString(x) +
                                           toHexString(dist));
        }

        if (signum(0) != 0 || signum(1) != 1 || signum(-1) != -1
            || signum(MIN_VALUE) != -1 || signum(MAX_VALUE) != 1)
            throw new RuntimeException("w");

        for (int i = 0; i < N; i++) {
            long x = rnd.nextLong();
            int sign = (x < 0 ? -1 : (x == 0 ? 0 : 1));
            if (signum(x) != sign)
                throw new RuntimeException("x: " + toHexString(x));
        }

        if(reverseBytes(0xaabbccdd11223344L) != 0x44332211ddccbbaaL)
            throw new RuntimeException("y");

        for (int i = 0; i < N; i++) {
            long x = rnd.nextLong();
            if (bitCount(x) != bitCount(reverseBytes(x)))
                throw new RuntimeException("z: " + toHexString(x));
        }
    }
}
