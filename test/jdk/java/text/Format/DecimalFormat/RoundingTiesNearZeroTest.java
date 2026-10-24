/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8369050
 * @summary Check rounding of DecimalFormat on tie cases when the maximum
 *      fraction digits allowed is one less than the position of the first
 *      significant digit in the double.
 * @run junit RoundingTiesNearZeroTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoundingTiesNearZeroTest {

    // Safe to re-use since we are not testing any fast-path cases
    // so state is irrelevant
    private static final NumberFormat format = NumberFormat.getInstance(Locale.US);

    @ParameterizedTest
    @MethodSource("ties")
    void roundingTieTest(RoundingMode rm, int maxDigits, double db, String expected) {
        format.setRoundingMode(rm);
        format.setMaximumFractionDigits(maxDigits);
        assertEquals(expected, format.format(db), "Rounding failed under " + rm);
    }

    static Stream<Arguments> ties() {
        return Stream.of(
                // 1) String is exact as binary
                // 0.5 -> 0.5
                Arguments.of(RoundingMode.HALF_EVEN, 0, 0.5, "0"),
                Arguments.of(RoundingMode.HALF_UP, 0, 0.5, "1"),
                Arguments.of(RoundingMode.HALF_DOWN, 0, 0.5, "0"),
                // 2) String is rounded up from binary
                // 0.0000005 -> 4.999999999999999773740559129431293428069693618454039096832275390625E-7
                Arguments.of(RoundingMode.HALF_EVEN, 6, 0.0000005, "0"),
                Arguments.of(RoundingMode.HALF_UP, 6, 0.0000005, "0"),
                Arguments.of(RoundingMode.HALF_DOWN, 6, 0.0000005, "0"),
                // 3) String is not rounded up from binary
                // Non-exponential notation
                // 0.05 -> 0.05000000000000000277555756156289135105907917022705078125
                Arguments.of(RoundingMode.HALF_EVEN, 1, 0.05, "0.1"),
                Arguments.of(RoundingMode.HALF_UP, 1, 0.05, "0.1"),
                Arguments.of(RoundingMode.HALF_DOWN, 1, 0.05, "0.1"),
                // Exponential notation
                // 0.00005 -> 0.0000500000000000000023960868011929647991564706899225711822509765625
                Arguments.of(RoundingMode.HALF_EVEN, 4, 0.00005, "0.0001"),
                Arguments.of(RoundingMode.HALF_UP, 4, 0.00005, "0.0001"),
                Arguments.of(RoundingMode.HALF_DOWN, 4, 0.00005, "0.0001")
        );
    }
}
