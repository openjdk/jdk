/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @summary  Basic tests of BitSet.OfImmutable
 * @run junit ImmutableBitSetTest
 */

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class ImmutableBitSetTest {

    @Test
    void basicTest() {
        BitSet bs = new BitSet();
        primes().limit(10)
                        .forEach(bs::set);

        BitSet.OfImmutable ibs = bs.asImmutable();

        for (int i = 0; i < 100; i++) {
            assertEquals(bs.get(i), ibs.get(i));
        }

    }

    static IntStream primes() {
        return IntStream.iterate(1, i -> i + 1)
                .filter(ImmutableBitSetTest::isPrime);
    }

    /**
     * Returns if the given parameter is a prime number.
     *
     * @param n the given prime number candidate
     * @return if the given parameter is a prime number
     */
    static boolean isPrime(long n) {
        // primes are equal or greater than 2
        if (n < 2) {
            return false;
        }
        // check if n is even
        if (n % 2 == 0) {
            // 2 is the only even prime
            // all other even n:s are not
            return n == 2;
        }
        // if odd, then just check the odds
        // up to the square root of n
        // for (int i = 3; i * i <= n; i += 2) {
        //
        // Make the methods purposely slow by
        // checking all the way up to n
        for (int i = 3; i <= n; i += 2) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }

}
