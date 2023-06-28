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
 * @run main/othervm compiler.vectorapi.TestVectorShuffleIota
 */

public class TestVectorShuffleIota {
    static final VectorSpecies<Integer> SPECIESi = IntVector.SPECIES_128;
    static final VectorSpecies<Short> SPECIESs = ShortVector.SPECIES_128;
    static final VectorSpecies<Byte> SPECIESb = ByteVector.SPECIES_128;

    static final int INVOC_COUNT = 5000;

    static int[] ai = {87, 65, 78, 71};

    interface compute_kernel {
        long apply(int start, int step, boolean wrap);
    }

    static void validateTests(compute_kernel agen, compute_kernel egen, int start, int step, boolean wrap) {
        long actual   = agen.apply(start, step, wrap);
        long expected = egen.apply(start, step, wrap);
        if (actual != expected) {
            throw new AssertionError("Result Mismatch!, actual = " + actual + " expected = " + expected);
        }
    }

    static void testShuffleIota (VectorSpecies<?> SPECIES, int start, int step, boolean wrap) {
        compute_kernel sobj = new compute_kernel()  {
            public long apply(int start, int step, boolean wrap) {
                long res = 0;
                int lanesM1 = SPECIES.length() - 1;
                if (wrap) {
                    for (int i = 0; i < 1024; i++) {
                        start += i;
                        res += (lanesM1 & (start + step * lanesM1)) * i;
                    }
                } else {
                    for (int i = 0; i < 1024; i++) {
                        start += i;
                        int effective_index = start + step * lanesM1;
                        int wrapped_effective_index = effective_index & lanesM1;
                        res += (effective_index == wrapped_effective_index ?
                                 wrapped_effective_index :
                                 -SPECIES.length() + wrapped_effective_index) * i;
                    }
                }
                return res;
            }
        };

        compute_kernel vobj = new compute_kernel()  {
            public long apply(int start, int step, boolean wrap) {
                long res = 0;
                for (int i = 0; i < 1024; i++) {
                    start += i;
                    res += SPECIES.iotaShuffle(start, step, wrap)
                                  .laneSource(SPECIES.length()-1) * i;
                }
                return res;
            }
        };

        validateTests(vobj, sobj, start, step, wrap);
    }

    static void testShuffleI() {
        IntVector iv = (IntVector) VectorShuffle.iota(SPECIESi, 0, 2, false).toVector();
        iv.intoArray(ai, 0);
    }

    public static void main(String[] args) {
        for (int i = 0; i < INVOC_COUNT; i++) {
            testShuffleI();

            testShuffleIota(SPECIESi, 128, 1, true);
            testShuffleIota(SPECIESi, 128, 1, false);
            testShuffleIota(SPECIESi, -128, 1, true);
            testShuffleIota(SPECIESi, -128, 1, false);
            testShuffleIota(SPECIESi, 1, 1, true);
            testShuffleIota(SPECIESi, 1, 1, false);

            testShuffleIota(SPECIESs, 128, 1, true);
            testShuffleIota(SPECIESs, 128, 1, false);
            testShuffleIota(SPECIESs, -128, 1, true);
            testShuffleIota(SPECIESs, -128, 1, false);
            testShuffleIota(SPECIESs, 1, 1, true);
            testShuffleIota(SPECIESs, 1, 1, false);

            testShuffleIota(SPECIESb, 128, 1, true);
            testShuffleIota(SPECIESb, 128, 1, false);
            testShuffleIota(SPECIESb, -128, 1, true);
            testShuffleIota(SPECIESb, -128, 1, false);
            testShuffleIota(SPECIESb, 1, 1, true);
            testShuffleIota(SPECIESb, 1, 1, false);
        }
        for (int i = 0; i < ai.length; i++) {
            System.out.print(ai[i] + ", ");
        }
        System.out.println();
    }
}
