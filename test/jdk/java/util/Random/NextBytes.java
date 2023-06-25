/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4261170
 * @summary Tests for RandomGenerator.nextBytes
 * @author Martin Buchholz
 * @run junit NextBytes
 */

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class NextBytes {

    private static final long SEED = 2398579034L;

    @Test
    void testJURandom() throws Throwable {
        byte[] expected = new byte[]
            {27, -105, -24, 83, -77, -29, 119, -74, -106, 68, 54};
        Random r = new java.util.Random(SEED);

        assertAll(IntStream.range(0, expected.length).mapToObj(i -> () -> {
            r.setSeed(SEED);
            byte[] actual = new byte[i];
            r.nextBytes(actual);
            assertArrayEquals(Arrays.copyOf(expected, i), actual);
        }));
    }

    @Test
    void testL32X64MixRandom() throws Throwable {
        byte[] expected = new byte[]
            {-57, 102, 42, 34, -3, -113, 78, -20, 24, -17, 59 };

        RandomGeneratorFactory factory = RandomGeneratorFactory.of("L32X64MixRandom");
        assertAll(IntStream.range(0, expected.length).mapToObj(i -> () -> {
            RandomGenerator r = factory.create(SEED);
            byte[] actual = new byte[i];
            r.nextBytes(actual);
            assertArrayEquals(Arrays.copyOf(expected, i), actual);
        }));
    }

}
