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

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class NextBytes {

    private static final long SEED = 2398579034L;

    private static List<Arguments> params() {
        return List.of(
            Arguments.of(
                "Random",
                new byte[]{27, -105, -24, 83, -77, -29, 119, -74, -106, 68, 54, 46, 50, 46, 25, -16}
            ),
            Arguments.of(
                "L32X64MixRandom",
                new byte[]{-57, 102, 42, 34, -3, -113, 78, -20, 24, -17, 59, 11, -29, -86, -98, -37}
            ),
            Arguments.of(
                "L64X128StarStarRandom",
                new byte[]{109, -78, 16, -38, -12, -24, 77, 109, -79, -97, -9, 40, 123, 118, 43, 7}
            ),
            Arguments.of(
                "Xoshiro256PlusPlus",
                new byte[]{121, -17, 31, -115, 26, -119, 64, 25, -15, 63, 29, -125, -72, 53, -20, 7}
            )
        );
    }

    @ParameterizedTest
    @MethodSource("params")
    void testNextBytes(String algo, byte[] expected) throws Throwable {
        RandomGeneratorFactory factory = RandomGeneratorFactory.of(algo);
        assertAll(IntStream.rangeClosed(0, expected.length).mapToObj(i -> () -> {
            byte[] actual = new byte[i];
            factory.create(SEED).nextBytes(actual);
            assertArrayEquals(Arrays.copyOf(expected, i), actual);
        }));
    }

}
