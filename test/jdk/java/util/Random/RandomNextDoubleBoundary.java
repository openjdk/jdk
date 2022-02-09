/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Verify nextDouble stays within range
 * @bug 8280550 8280950 8281183
 */

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

public class RandomNextDoubleBoundary {
    public static void main(String... args) {
        negativeBounds();
        positiveBounds();
    }

    private static void negativeBounds() {
        // Both bounds are negative
        double lowerBound = -1.0000000000000002;
        double upperBound = -1.0;
        var sr = new SplittableRandom(42L);
        var r = sr.nextDouble(lowerBound, upperBound);

        if (r >= upperBound) {
            System.err.println("r = " + r + "\t" + Double.toHexString(r));
            System.err.println("ub = " + upperBound + "\t" + Double.toHexString(upperBound));
            throw new RuntimeException("Greater than upper bound");
        }

        if (r < lowerBound) {
            System.err.println("r = " + r + "\t" + Double.toHexString(r));
            System.err.println("lb = " + lowerBound + "\t" + Double.toHexString(lowerBound));
            throw new RuntimeException("Less than lower bound");
        }
    }

    private static void positiveBounds() {
        double[][] originAndBounds = {{10, 100},
                                      {12345, 123456},
                                      {5432167.234, 54321678.1238}};
        for (double[] originAndBound : originAndBounds) {
            nextDoublesWithRange(originAndBound[0], originAndBound[1]);
        }
    }

    public static void nextDoublesWithRange(double origin, double bound) {
        RandomGenerator rg = new RandomGenerator() {
            @Override
            public double nextDouble() {
                return Double.MAX_VALUE;
            }

            @Override
            public long nextLong() {
                return 0;
            }
        };
        double value = rg.nextDouble(origin, bound);

        if (bound > 0) {
            value = rg.nextDouble(bound); // Equivalent to nextDouble(0.0, bound)
            assertTrue(value >= 0.0);
            assertTrue(value < bound);
        }
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }
}
