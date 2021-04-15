/*
 * Copyright (c) 2021, Huawei Technologies Co. Ltd. All rights reserved.
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

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;


/*
 * @test
 * @bug 8265244
 * @modules jdk.incubator.vector
 * @run main/othervm compiler.vectorapi.TestCast4STo2I
 */

public class TestCast4STo2I {
    static final VectorSpecies<Short> SPECIESs = ShortVector.SPECIES_64;
    static final VectorSpecies<Integer> SPECIESi = IntVector.SPECIES_64;

    static final int INVOC_COUNT = 50000;
    static final int SIZE = 64;

    static short[] as = {46, 110, 117, 115, 32, 121, 109, 32, 101, 114,
                         97, 32, 111, 104, 119, 32, 117, 111, 121, 32,
                         115, 105, 32, 116, 105, 32, 100, 110, 65, 32,
                         46, 110, 117, 115, 32, 101, 104, 116, 32, 121,
                         98, 32, 121, 108, 110, 111, 32, 115, 101, 110,
                         105, 104, 115, 32, 114, 101, 116, 97, 119, 32,
                         101, 104, 84};
    static int[] ai =  {46, 103, 110, 97, 117, 72, 32, 71, 78, 65, 87,
                        32, 45, 45, 33, 117, 111, 121, 32, 103, 110,
                        105, 115, 115, 105, 77, 46, 117, 111, 121, 32,
                        111, 116, 32, 114, 101, 116, 116, 101, 108, 32,
                        100, 110, 111, 99, 101, 115, 32, 121, 109, 32,
                        115, 105, 32, 115, 105, 104, 116, 44, 121, 116,
                        101, 101, 119, 83};

    static void testVectorCastS2I(short[] input, int[] output) {
        ShortVector sv = ShortVector.fromArray(SPECIESs, input, 0);
        IntVector iv = (IntVector) sv.castShape(SPECIESi, 0);
        iv.intoArray(output, 0);
    }

    public static void main(String[] args) {
        for (int i = 0; i < INVOC_COUNT; i++) {
            testVectorCastS2I(as, ai);
        }
        for (int i = 0; i < SIZE; i++) {
            System.out.print(ai[i] + ", ");
        }
        System.out.println();
    }
}
