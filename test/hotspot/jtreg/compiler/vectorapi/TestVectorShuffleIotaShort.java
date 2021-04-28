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

import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorShuffle;

/*
 * @test
 * @bug 8265956
 * @modules jdk.incubator.vector
 * @run main/othervm compiler.vectorapi.TestVectorShuffleIotaShort
 */

public class TestVectorShuffleIotaShort {
    static final VectorSpecies<Short> SPECIESs = ShortVector.SPECIES_128;

    static final int INVOC_COUNT = 50000;

    static short[] as = {87, 65, 78, 71, 72, 69, 82, 69};

    public static void testShuffleS() {
        ShortVector sv = (ShortVector) VectorShuffle.iota(SPECIESs, 0, 2, false).toVector();
        sv.intoArray(as, 0);
    }

    public static void main(String[] args) {

        for (int i = 0; i < INVOC_COUNT; i++) {
            testShuffleS();
        }
        for (int i = 0; i < as.length; i++) {
            System.out.print(as[i] + ", ");
        }
        System.out.println();
    }
}
