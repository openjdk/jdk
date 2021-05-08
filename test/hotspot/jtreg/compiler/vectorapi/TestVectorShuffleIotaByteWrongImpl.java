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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorShuffle;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8266720
 * @modules jdk.incubator.vector
 * @run testng/othervm compiler.vectorapi.TestVectorShuffleIotaByteWrongImpl
 */

@Test
public class TestVectorShuffleIotaByteWrongImpl {
    static final VectorSpecies<Byte> SPECIESb_64 = ByteVector.SPECIES_64;
    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 50000);

    static byte[] ab_64 = {87, 65, 78, 71, 72, 69, 82, 69};
    static byte[] expected_64 = {0, 2, 4, 6, -8, -6, -4, -2};

    @Test
    static void testShuffleIota_64() {
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            ByteVector bv = (ByteVector) VectorShuffle.iota(SPECIESb_64, 0, 2, false).toVector();
            bv.intoArray(ab_64, 0);
        }
        Assert.assertEquals(ab_64, expected_64);
    }
}
