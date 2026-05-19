/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package compiler.vectorapi;

import jdk.incubator.vector.*;
import static jdk.incubator.vector.VectorOperators.*;

/*
 * @test
 * @bug 8384963
 * @summary C2: Incorrect uint constant match mishandles negative values in vectors
 * @modules jdk.incubator.vector
 * @run main/othervm -Xbatch -XX:-TieredCompilation compiler.vectorapi.TestVectorNegativeUint
 */

public class TestVectorNegativeUint {
    static final int SIZE = 64; // 8*64 = 512, should be enough

    static final long[] S1 = new long[SIZE];
    static final long[] S2 = new long[SIZE];
    static final LongVector V1;
    static final LongVector V2;
    static final long MASK = -2L;

    static {
        // Upper 32-bits should be non-zero to trigger the bug.
        S1[0] = 0x1_0000_0001L;
        S2[0] = 0x2_0000_0002L;
        V1 = LongVector.fromArray(LongVector.SPECIES_PREFERRED, S1, 0);
        V2 = LongVector.fromArray(LongVector.SPECIES_PREFERRED, S2, 0);
    }

    public static void main(String... args) {
        for (int c = 0; c < 100_000; c++) {
            test();
        }
    }

    static void test() {
        long[] res = V1.lanewise(AND, MASK).lanewise(MUL, V2.lanewise(AND, MASK)).toArray();
        long actual = res[0];
        long expected = (S1[0] & MASK) * (S2[0] & MASK);
        if (expected != actual) {
            throw new AssertionError("expected: " + Long.toHexString(expected) + ", actual: " + Long.toHexString(actual));
        }
    }
}
