/*
 * Copyright (c) 2026 IBM Corp. All rights reserved.
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
 * @bug 8371187
 * @summary Randomized test for lane swapping issue in LongVector reinterpretation on BE platforms
 * @modules jdk.incubator.vector
 * @run main/othervm --add-modules=jdk.incubator.vector TestLongVectorReinterpret
 */

import jdk.incubator.vector.*;
import java.util.SplittableRandom;

public class TestLongVectorReinterpret {

    static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_128;
    static final SplittableRandom RAND = new SplittableRandom(42);

    public static void main(String[] args) {
        for (int iter = 0; iter < 1000; iter++) {
            verifyLaneOrdering();
        }
    }

    static void verifyLaneOrdering() {
        long[] a = new long[SPECIES.length()];
        long[] b = new long[SPECIES.length()];

        for (int i = 0; i < a.length; i++) {
            a[i] = RAND.nextLong();
            b[i] = RAND.nextLong();
        }

        LongVector v1 = LongVector.fromArray(SPECIES, a, 0);
        LongVector v2 = LongVector.fromArray(SPECIES, b, 0);

        IntVector result = v2.reinterpretAsInts().add(v1.reinterpretAsInts());

        int[] expected = new int[SPECIES.length() * 2];

        for (int i = 0; i < a.length; i++) {
            int loA = (int) a[i];
            int hiA = (int) (a[i] >>> 32);

            int loB = (int) b[i];
            int hiB = (int) (b[i] >>> 32);

            expected[2 * i]     = loA + loB;
            expected[2 * i + 1] = hiA + hiB;
        }

        for (int i = 0; i < expected.length; i++) {
            int actual = result.lane(i);
            if (actual != expected[i]) {
                throw new AssertionError(
                    "Mismatch at lane " + i +
                    " expected=" + expected[i] +
                    " actual=" + actual +
                    " vector=" + result);
            }
        }
    }
}
