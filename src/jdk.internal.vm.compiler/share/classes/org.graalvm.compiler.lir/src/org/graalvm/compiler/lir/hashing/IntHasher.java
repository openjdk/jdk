/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.hashing;

import java.util.Arrays;
import java.util.Optional;

/**
 * Generates an injective hash function for the provided keys. The cardinality is the next power of
 * two of the length of the keys array. The hash can be obtained by calling the `hash` method or
 * using: (value * factor) >> shift & (cardinality - 1) If `factor` is 1, it can be omitted. If
 * `shift` is 0, it can be omitted.
 */
public final class IntHasher {

    public final int cardinality;
    public final short factor;
    public final byte shift;

    private IntHasher(int cardinality, short factor, byte shift) {
        this.cardinality = cardinality;
        this.factor = factor;
        this.shift = shift;
    }

    // 1 + the first 9999 prime numbers
    private static final Short[] factors = new Short[1000];

    static {
        // simple prime number generation
        int pos = 0;
        short i = 1;
        while (pos < factors.length) {
            boolean factor = true;
            for (int j = 2; j < i; j++) {
                // ignore if a previous number
                // is a factor
                if (i % j == 0) {
                    factor = false;
                    break;
                }
            }
            if (factor) {
                factors[pos] = i;
                pos++;
            }
            i++;
        }

        // prioritize simplifiable factors
        Arrays.sort(factors, (a, b) -> {
            if (canSimplify(a) && !canSimplify(b)) {
                return -1;
            } else if (!canSimplify(a) && canSimplify(b)) {
                return 1;
            } else {
                return 0;
            }
        });
    }

    // returns true if the factor multiplication can be simplified to cheaper operations
    private static boolean canSimplify(short factor) {
        return isPowerOf2(factor - 1) || isPowerOf2(factor + 1) || isPowerOf2(-factor);
    }

    private static boolean isPowerOf2(int n) {
        return (n & n - 1) == 0;
    }

    private static final double log2 = Math.log(2);

    public static Optional<IntHasher> forKeys(int[] keys) {

        int length = keys.length;

        // next power of 2 of keys.length
        int cardinality = 1 << ((int) (Math.log(keys.length - 1) / log2)) + 1;

        // number of bits required for the mask
        int maskBits = (int) (Math.log(cardinality) / log2 + 1);

        int[] scratch = new int[length];
        boolean[] seen = new boolean[cardinality];

        for (short factor : factors) {

            // apply factor to keys
            for (int i = 0; i < length; i++) {
                scratch[i] = keys[i] * factor;
            }

            // find a shift that makes the function injective
            outer: for (byte shift = 0; shift < Integer.SIZE - maskBits; shift++) {
                Arrays.fill(seen, false);
                for (int h = 0; h < length; h++) {
                    int idx = scratch[h] >> shift & (cardinality - 1);
                    if (!seen[idx]) {
                        seen[idx] = true;
                    } else {
                        continue outer;
                    }
                }
                return Optional.of(new IntHasher(cardinality, factor, shift));
            }
        }
        return Optional.empty();
    }

    public int hash(int value) {
        return ((value * factor) >> shift) & (cardinality - 1);
    }

    @Override
    public String toString() {
        return "IntHasher [cardinality=" + cardinality + ", factor=" + factor + ", shift=" + shift + "]";
    }
}
