/*
 * Copyright (c) 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

package compiler.vectorapi;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorShuffle;

/*
 * @test
 * @bug 8265907
 * @modules jdk.incubator.vector
 * @run main/othervm -XX:CompileThresholdScaling=0.3 -XX:-TieredCompilation compiler.vectorapi.TestVectorShuffleIota
 */

public class TestVectorShuffleIota {
    static final VectorSpecies<Integer> SPECIESi = IntVector.SPECIES_128;
    static final VectorSpecies<Short> SPECIESs = ShortVector.SPECIES_128;
    static final VectorSpecies<Byte> SPECIESb = ByteVector.SPECIES_128;

    static final int INVOC_COUNT = 5000;

    static int[] ai = {87, 65, 78, 71};

    static long expected_value(VectorSpecies<?> SPECIES, int start, int step, boolean wrap) {
        long res = 0;
        int lanesM1 = SPECIES.length() - 1;
        if (wrap) {
            res = (lanesM1 & (start + step * lanesM1));
        } else {
            int effective_index = start + step * lanesM1;
            int wrapped_effective_index = effective_index & lanesM1;
            res = (effective_index == wrapped_effective_index ?
                   wrapped_effective_index :
                   -SPECIES.length() + wrapped_effective_index);
        }
        return res;
    }

    static void validateTests(long actual, VectorSpecies<?> SPECIES, int start, int step, boolean wrap) {
        long expected = expected_value(SPECIES, start, step, wrap);
        if (actual != expected) {
            throw new AssertionError("Result Mismatch!, actual = " + actual + " expected = " + expected);
        }
    }

    static void testShuffleIotaB128(int start, int step, boolean wrap) {
        long res = SPECIESb.iotaShuffle(start, step, wrap)
                           .laneSource(SPECIESb.length()-1);
        validateTests(res, SPECIESb, start, step, wrap);
    }

    static void testShuffleIotaS128(int start, int step, boolean wrap) {
        long res = SPECIESs.iotaShuffle(start, step, wrap)
                           .laneSource(SPECIESs.length()-1);
        validateTests(res, SPECIESs, start, step, wrap);
    }

    static void testShuffleIotaI128(int start, int step, boolean wrap) {
        long res = SPECIESi.iotaShuffle(start, step, wrap)
                           .laneSource(SPECIESi.length()-1);
        validateTests(res, SPECIESi, start, step, wrap);
    }

    static void testShuffleIotaConst0B128() {
        long res = SPECIESb.iotaShuffle(-32, 1, false)
                           .laneSource(SPECIESb.length()-1);
        validateTests(res, SPECIESb, -32, 1, false);
    }

    static void testShuffleIotaConst0S128() {
        long res = SPECIESs.iotaShuffle(-32, 1, false)
                           .laneSource(SPECIESs.length()-1);
        validateTests(res, SPECIESs, -32, 1, false);
    }

    static void testShuffleIotaConst0I128() {
        long res = SPECIESi.iotaShuffle(-32, 1, false)
                           .laneSource(SPECIESi.length()-1);
        validateTests(res, SPECIESi, -32, 1, false);
    }

    static void testShuffleIotaConst1B128() {
        long res = SPECIESb.iotaShuffle(-32, 1, true)
                           .laneSource(SPECIESb.length()-1);
        validateTests(res, SPECIESb, -32, 1, true);
    }

    static void testShuffleIotaConst1S128() {
        long res = SPECIESs.iotaShuffle(-32, 1, true)
                           .laneSource(SPECIESs.length()-1);
        validateTests(res, SPECIESs, -32, 1, true);
    }

    static void testShuffleIotaConst1I128() {
        long res = SPECIESi.iotaShuffle(-32, 1, true)
                           .laneSource(SPECIESi.length()-1);
        validateTests(res, SPECIESi, -32, 1, true);
    }

    static void testShuffleI() {
        IntVector iv = (IntVector) VectorShuffle.iota(SPECIESi, 0, 2, false).toVector();
        iv.intoArray(ai, 0);
    }

    public static void main(String[] args) {
        for (int i = 0; i < INVOC_COUNT; i++) {
            testShuffleI();
        }
        for (int i = 0; i < ai.length; i++) {
            System.out.print(ai[i] + ", ");
        }
        for (int i = 0; i < INVOC_COUNT; i++) {
            testShuffleIotaI128(128, 1, true);
            testShuffleIotaI128(128, 1, false);
            testShuffleIotaI128(-128, 1, true);
            testShuffleIotaI128(-128, 1, false);
            testShuffleIotaI128(1, 1, true);
            testShuffleIotaI128(1, 1, false);

            testShuffleIotaS128(128, 1, true);
            testShuffleIotaS128(128, 1, false);
            testShuffleIotaS128(-128, 1, true);
            testShuffleIotaS128(-128, 1, false);
            testShuffleIotaS128(1, 1, true);
            testShuffleIotaS128(1, 1, false);

            testShuffleIotaB128(128, 1, true);
            testShuffleIotaB128(128, 1, false);
            testShuffleIotaB128(-128, 1, true);
            testShuffleIotaB128(-128, 1, false);
            testShuffleIotaB128(1, 1, true);
            testShuffleIotaB128(1, 1, false);
            testShuffleIotaConst0B128();
            testShuffleIotaConst0S128();
            testShuffleIotaConst0I128();
            testShuffleIotaConst1B128();
            testShuffleIotaConst1S128();
            testShuffleIotaConst1I128();
        }
        System.out.println();
    }
}
