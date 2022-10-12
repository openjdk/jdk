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

import java.util.random.*;
import jdk.internal.util.random.RandomSupport;

/**
 * @test
 * @summary RandomSupport.convertSeedBytesToLongs sign extension overwrites previous bytes.
 * @bug 8282144
 * @modules java.base/jdk.internal.util.random
 * @run main T8282144
 * @key randomness
 */


public class T8282144 {
    public static void main(String[] args) {
        RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(42);

        for (int i = 1; i < 8; i++) {
            byte[] seed = new byte[i];

            for (int j = 0; j < 10; j++) {
                rng.nextBytes(seed);

                long[] existing = RandomSupport.convertSeedBytesToLongs(seed, 1, 1);
                long[] testing = convertSeedBytesToLongsFixed(seed, 1, 1);

                for (int k = 0; k < existing.length; k++) {
                    if (existing[k] != testing[k]) {
                        throw new RuntimeException("convertSeedBytesToLongs incorrect");
                    }
                }
            }
        }
    }


    public static long[] convertSeedBytesToLongsFixed(byte[] seed, int n, int z) {
        final long[] result = new long[n];
        final int m = Math.min(seed.length, n << 3);

        // Distribute seed bytes into the words to be formed.
        for (int j = 0; j < m; j++) {
            result[j >> 3] = (result[j >> 3] << 8) | (seed[j] & 0xff);
        }

        return result;
    }
}
