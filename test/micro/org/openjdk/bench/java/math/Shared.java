/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.math;

import java.math.BigInteger;
import java.util.Random;

///////////////////////////////////////////////////////////////////////////////
// THIS IS NOT A BENCHMARK
///////////////////////////////////////////////////////////////////////////////
public final class Shared {

    // General note
    // ============
    //
    // Isn't there a simple way to get a BigInteger of the specified number
    // of bits of magnitude? It does not seem like it.
    //
    // We cannot create a BigInteger of the specified number of bytes,
    // directly and *cheaply*. This constructor does not do what you
    // might think it does:
    //
    //      BigInteger(int numBits, Random rnd)
    //
    //  The only real direct option we have is this constructor:
    //
    //      BigInteger(int bitLength, int certainty, Random rnd)
    //
    //  But even with certainty == 0, it is not cheap. So, create the
    //  number with the closest number of bytes and then shift right
    //  the excess bits.

    private Shared() {
        throw new AssertionError("This is a utility class");
    }

    //
    // Creates a pair of same sign numbers x and y that minimally differ in
    // magnitude.
    //
    // More formally: x.bitLength() == nBits and x.signum() == y.signum()
    // and either
    //
    //   * y.bitLength() == nBits, and
    //   * x.testBit(0) != y.testBit(0)
    //
    // or
    //
    //   * y.bitLength() == nBits + 1
    //
    // By construction, such numbers are unequal to each other, but the
    // difference in magnitude is minimal. That way, the comparison
    // methods, such as equals and compareTo, are forced to examine
    // the _complete_ number representation.
    //
    // Assumptions on BigInteger mechanics
    // ===================================
    //
    // 1. bigLength() is not consulted with for short-circuiting; if it is,
    //    then we have a problem with nBits={0,1}
    // 2. new BigInteger(0, new byte[]{0}) and new BigInteger(1, new byte[]{1})
    //    are not canonicalized to BigInteger.ZERO and BigInteger.ONE,
    //    respectively; if they are, then internal optimizations might be
    //    possible (BigInteger is not exactly a value-based class).
    // 3. Comparison and equality are checked from the most significant bit
    //    to the least significant bit, not the other way around (for
    //    comparison it seems natural, but not for equality). If any
    //    of those are checked in the opposite direction, then the check
    //    might short-circuit.
    //
    public static Pair createPair(int nBits) {
        if (nBits < 0) {
            throw new IllegalArgumentException(String.valueOf(nBits));
        } else if (nBits == 0) {
            var zero = new BigInteger(nBits, new byte[0]);
            var one = new BigInteger(/* positive */ 1, new byte[]{1});
            return new Pair(zero, one);
        } else if (nBits == 1) {
            var one = new BigInteger(/* positive */ 1, new byte[]{1});
            var two = new BigInteger(/* positive */ 1, new byte[]{2});
            return new Pair(one, two);
        }
        int nBytes = (nBits + 7) / 8;
        var r = new Random();
        var bytes = new byte[nBytes];
        r.nextBytes(bytes);
        // Create a BigInteger of the exact bit length by:
        // 1. ensuring that the most significant bit is set so that
        //    no leading zeros are truncated, and
        // 2. explicitly specifying signum, so it's not calculated from
        //    the passed bytes, which must represent magnitude only
        bytes[0] |= (byte) 0b1000_0000;
        var x = new BigInteger(/* positive */ 1, bytes)
                .shiftRight(nBytes * 8 - nBits);
        var y = x.flipBit(0);
        // do not rely on the assert statement in benchmark
        if (x.bitLength() != nBits)
            throw new AssertionError(x.bitLength() + ", " + nBits);
        return new Pair(x, y);
    }

    public record Pair(BigInteger x, BigInteger y) {
        public Pair {
            if (x.signum() == -y.signum()) // if the pair comprises positive and negative
                throw new IllegalArgumentException("x.signum()=" + x.signum()
                        + ", y=signum()=" + y.signum());
            if (y.bitLength() - x.bitLength() > 1)
                throw new IllegalArgumentException("x.bitLength()=" + x.bitLength()
                        + ", y.bitLength()=" + y.bitLength());
        }
    }

    public static BigInteger createSingle(int nBits) {
        if (nBits < 0) {
            throw new IllegalArgumentException(String.valueOf(nBits));
        }
        if (nBits == 0) {
            return new BigInteger(nBits, new byte[0]);
        }
        int nBytes = (nBits + 7) / 8;
        var r = new Random();
        var bytes = new byte[nBytes];
        r.nextBytes(bytes);
        // Create a BigInteger of the exact bit length by:
        // 1. ensuring that the most significant bit is set so that
        //    no leading zeros are truncated, and
        // 2. explicitly specifying signum, so it's not calculated from
        //    the passed bytes, which must represent magnitude only
        bytes[0] |= (byte) 0b1000_0000;
        var x = new BigInteger(/* positive */ 1, bytes)
                .shiftRight(nBytes * 8 - nBits);
        if (x.bitLength() != nBits)
            throw new AssertionError(x.bitLength() + ", " + nBits);
        return x;
    }
}
