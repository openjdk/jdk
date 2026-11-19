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
 * @bug 8362448
 * @summary Verify DecimalFormat::format on doubles.
 * @run junit DoubleFormattingTest
 * @run junit/othervm -Djdk.compat.DecimalFormat=true DoubleFormattingTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Formatter;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class DoubleFormattingTest {

    private static final boolean COMPAT = Boolean.getBoolean("jdk.compat.DecimalFormat");

    static Stream<Arguments> testFormat() {
        return Stream.of(
                Arguments.of(4.8726570057E288, 0),
                Arguments.of(7.3879E20, 0),
                Arguments.of(1.9400994884341945E25, 0),
                Arguments.of(6.3E-322, 324)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFormat(double v, int minFractionDigits) {
        DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
        df.setGroupingUsed(false);
        df.setMinimumFractionDigits(minFractionDigits);
        String actual = df.format(v);
        Formatter fmt = new Formatter(Locale.ROOT);
        fmt.format("%." + minFractionDigits + "f", v);
        String expected = fmt.toString();
        if (COMPAT) {
            assertNotEquals(expected, actual);
        } else {
            assertEquals(expected, actual);
        }
    }

}
