/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4906370 8299677 8326718 8328037
 * @summary Tests to exercise padding on int and double values,
 *      with various flag combinations.
 * @run junit Padding
 */

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Padding {
    /* blank padding, right adjusted, optional sign */
    static Stream<? extends Arguments> blankPaddingRightAdjustedOptionalSign() {
        var tenMillionBlanks = " ".repeat(10_000_000);
        return Stream.of(
            Arguments.of("12", "%1d", 12),
            Arguments.of("12", "%2d", 12),
            Arguments.of(" 12", "%3d", 12),
            Arguments.of("  12", "%4d", 12),
            Arguments.of("   12", "%5d", 12),
            Arguments.of("        12", "%10d", 12),
            Arguments.of(tenMillionBlanks + "12", "%10000002d", 12),

            Arguments.of("-12", "%1d", -12),
            Arguments.of("-12", "%2d", -12),
            Arguments.of("-12", "%3d", -12),
            Arguments.of(" -12", "%4d", -12),
            Arguments.of("  -12", "%5d", -12),
            Arguments.of("       -12", "%10d", -12),
            Arguments.of(tenMillionBlanks + "-12", "%10000003d", -12),

            Arguments.of("1.2", "%1.1f", 1.2),
            Arguments.of("1.2", "%2.1f", 1.2),
            Arguments.of("1.2", "%3.1f", 1.2),
            Arguments.of(" 1.2", "%4.1f", 1.2),
            Arguments.of("  1.2", "%5.1f", 1.2),
            Arguments.of("       1.2", "%10.1f", 1.2),
            Arguments.of(tenMillionBlanks + "1.2", "%10000003.1f", 1.2),

            Arguments.of("-1.2", "%1.1f", -1.2),
            Arguments.of("-1.2", "%2.1f", -1.2),
            Arguments.of("-1.2", "%3.1f", -1.2),
            Arguments.of("-1.2", "%4.1f", -1.2),
            Arguments.of(" -1.2", "%5.1f", -1.2),
            Arguments.of("      -1.2", "%10.1f", -1.2),
            Arguments.of(tenMillionBlanks + "-1.2", "%10000004.1f", -1.2));
    }

    @ParameterizedTest
    @MethodSource
    void blankPaddingRightAdjustedOptionalSign(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }

    /* blank padding, right adjusted, mandatory sign */
    static Stream<? extends Arguments> blankPaddingRightAdjustedMandatorySign() {
        var tenMillionBlanks = " ".repeat(10_000_000);
        return Stream.of(
            Arguments.of("+12", "%+1d", 12),
            Arguments.of("+12", "%+2d", 12),
            Arguments.of("+12", "%+3d", 12),
            Arguments.of(" +12", "%+4d", 12),
            Arguments.of("  +12", "%+5d", 12),
            Arguments.of("       +12", "%+10d", 12),
            Arguments.of(tenMillionBlanks + "+12", "%+10000003d", 12),

            Arguments.of("-12", "%+1d", -12),
            Arguments.of("-12", "%+2d", -12),
            Arguments.of("-12", "%+3d", -12),
            Arguments.of(" -12", "%+4d", -12),
            Arguments.of("  -12", "%+5d", -12),
            Arguments.of("       -12", "%+10d", -12),
            Arguments.of(tenMillionBlanks + "-12", "%+10000003d", -12),

            Arguments.of("+1.2", "%+1.1f", 1.2),
            Arguments.of("+1.2", "%+2.1f", 1.2),
            Arguments.of("+1.2", "%+3.1f", 1.2),
            Arguments.of("+1.2", "%+4.1f", 1.2),
            Arguments.of(" +1.2", "%+5.1f", 1.2),
            Arguments.of("      +1.2", "%+10.1f", 1.2),
            Arguments.of(tenMillionBlanks + "+1.2", "%+10000004.1f", 1.2),

            Arguments.of("-1.2", "%+1.1f", -1.2),
            Arguments.of("-1.2", "%+2.1f", -1.2),
            Arguments.of("-1.2", "%+3.1f", -1.2),
            Arguments.of("-1.2", "%+4.1f", -1.2),
            Arguments.of(" -1.2", "%+5.1f", -1.2),
            Arguments.of("      -1.2", "%+10.1f", -1.2),
            Arguments.of(tenMillionBlanks + "-1.2", "%+10000004.1f", -1.2));
    }

    @ParameterizedTest
    @MethodSource
    void blankPaddingRightAdjustedMandatorySign(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }

    /* blank padding, right adjusted, mandatory blank sign */
    static Stream<? extends Arguments> blankPaddingRightAdjustedMandatoryBlank() {
        var tenMillionBlanks = " ".repeat(10_000_000);
        return Stream.of(
            Arguments.of(" 12", "% 1d", 12),
            Arguments.of(" 12", "% 2d", 12),
            Arguments.of(" 12", "% 3d", 12),
            Arguments.of("  12", "% 4d", 12),
            Arguments.of("   12", "% 5d", 12),
            Arguments.of("        12", "% 10d", 12),
            Arguments.of(tenMillionBlanks + "12", "% 10000002d", 12),

            Arguments.of("-12", "% 1d", -12),
            Arguments.of("-12", "% 2d", -12),
            Arguments.of("-12", "% 3d", -12),
            Arguments.of(" -12", "% 4d", -12),
            Arguments.of("  -12", "% 5d", -12),
            Arguments.of("       -12", "% 10d", -12),
            Arguments.of(tenMillionBlanks + "-12", "% 10000003d", -12),

            Arguments.of(" 1.2", "% 1.1f", 1.2),
            Arguments.of(" 1.2", "% 2.1f", 1.2),
            Arguments.of(" 1.2", "% 3.1f", 1.2),
            Arguments.of(" 1.2", "% 4.1f", 1.2),
            Arguments.of("  1.2", "% 5.1f", 1.2),
            Arguments.of("       1.2", "% 10.1f", 1.2),
            Arguments.of(tenMillionBlanks + "1.2", "% 10000003.1f", 1.2),

            Arguments.of("-1.2", "% 1.1f", -1.2),
            Arguments.of("-1.2", "% 2.1f", -1.2),
            Arguments.of("-1.2", "% 3.1f", -1.2),
            Arguments.of("-1.2", "% 4.1f", -1.2),
            Arguments.of(" -1.2", "% 5.1f", -1.2),
            Arguments.of("      -1.2", "% 10.1f", -1.2),
            Arguments.of(tenMillionBlanks + "-1.2", "% 10000004.1f", -1.2));
    }

    @ParameterizedTest
    @MethodSource
    void blankPaddingRightAdjustedMandatoryBlank(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }

    /* blank padding, left adjusted, optional sign */
    static Stream<? extends Arguments> blankPaddingLeftAdjustedOptionalSign() {
        var tenMillionBlanks = " ".repeat(10_000_000);
        return Stream.of(
            Arguments.of("12", "%-1d", 12),
            Arguments.of("12", "%-2d", 12),
            Arguments.of("12 ", "%-3d", 12),
            Arguments.of("12  ", "%-4d", 12),
            Arguments.of("12   ", "%-5d", 12),
            Arguments.of("12        ", "%-10d", 12),
            Arguments.of("12" + tenMillionBlanks, "%-10000002d", 12),

            Arguments.of("-12", "%-1d", -12),
            Arguments.of("-12", "%-2d", -12),
            Arguments.of("-12", "%-3d", -12),
            Arguments.of("-12 ", "%-4d", -12),
            Arguments.of("-12  ", "%-5d", -12),
            Arguments.of("-12       ", "%-10d", -12),
            Arguments.of("-12" + tenMillionBlanks, "%-10000003d", -12),

            Arguments.of("1.2", "%-1.1f", 1.2),
            Arguments.of("1.2", "%-2.1f", 1.2),
            Arguments.of("1.2", "%-3.1f", 1.2),
            Arguments.of("1.2 ", "%-4.1f", 1.2),
            Arguments.of("1.2  ", "%-5.1f", 1.2),
            Arguments.of("1.2       ", "%-10.1f", 1.2),
            Arguments.of("1.2" + tenMillionBlanks, "%-10000003.1f", 1.2),

            Arguments.of("-1.2", "%-1.1f", -1.2),
            Arguments.of("-1.2", "%-2.1f", -1.2),
            Arguments.of("-1.2", "%-3.1f", -1.2),
            Arguments.of("-1.2", "%-4.1f", -1.2),
            Arguments.of("-1.2 ", "%-5.1f", -1.2),
            Arguments.of("-1.2      ", "%-10.1f", -1.2),
            Arguments.of("-1.2" + tenMillionBlanks, "%-10000004.1f", -1.2));
    }

    @ParameterizedTest
    @MethodSource
    void blankPaddingLeftAdjustedOptionalSign(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }

    /* blank padding, left adjusted, mandatory sign */
    static Stream<? extends Arguments> blankPaddingLeftAdjustedMandatorySign() {
        var tenMillionBlanks = " ".repeat(10_000_000);
        return Stream.of(
            Arguments.of("+12", "%-+1d", 12),
            Arguments.of("+12", "%-+2d", 12),
            Arguments.of("+12", "%-+3d", 12),
            Arguments.of("+12 ", "%-+4d", 12),
            Arguments.of("+12  ", "%-+5d", 12),
            Arguments.of("+12       ", "%-+10d", 12),
            Arguments.of("+12" + tenMillionBlanks, "%-+10000003d", 12),

            Arguments.of("-12", "%-+1d", -12),
            Arguments.of("-12", "%-+2d", -12),
            Arguments.of("-12", "%-+3d", -12),
            Arguments.of("-12 ", "%-+4d", -12),
            Arguments.of("-12  ", "%-+5d", -12),
            Arguments.of("-12       ", "%-+10d", -12),
            Arguments.of("-12" + tenMillionBlanks, "%-+10000003d", -12),

            Arguments.of("+1.2", "%-+1.1f", 1.2),
            Arguments.of("+1.2", "%-+2.1f", 1.2),
            Arguments.of("+1.2", "%-+3.1f", 1.2),
            Arguments.of("+1.2", "%-+4.1f", 1.2),
            Arguments.of("+1.2 ", "%-+5.1f", 1.2),
            Arguments.of("+1.2      ", "%-+10.1f", 1.2),
            Arguments.of("+1.2" + tenMillionBlanks, "%-+10000004.1f", 1.2),

            Arguments.of("-1.2", "%-+1.1f", -1.2),
            Arguments.of("-1.2", "%-+2.1f", -1.2),
            Arguments.of("-1.2", "%-+3.1f", -1.2),
            Arguments.of("-1.2", "%-+4.1f", -1.2),
            Arguments.of("-1.2 ", "%-+5.1f", -1.2),
            Arguments.of("-1.2      ", "%-+10.1f", -1.2),
            Arguments.of("-1.2" + tenMillionBlanks, "%-+10000004.1f", -1.2));
    }

    @ParameterizedTest
    @MethodSource
    void blankPaddingLeftAdjustedMandatorySign(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }

    /* blank padding, left adjusted, mandatory blank sign */
    static Stream<? extends Arguments> blankPaddingLeftAdjustedMandatoryBlank() {
        var tenMillionBlanks = " ".repeat(10_000_000);
        return Stream.of(
            Arguments.of(" 12", "%- 1d", 12),
            Arguments.of(" 12", "%- 2d", 12),
            Arguments.of(" 12", "%- 3d", 12),
            Arguments.of(" 12 ", "%- 4d", 12),
            Arguments.of(" 12  ", "%- 5d", 12),
            Arguments.of(" 12       ", "%- 10d", 12),
            Arguments.of(" 12" + tenMillionBlanks, "%- 10000003d", 12),

            Arguments.of("-12", "%- 1d", -12),
            Arguments.of("-12", "%- 2d", -12),
            Arguments.of("-12", "%- 3d", -12),
            Arguments.of("-12 ", "%- 4d", -12),
            Arguments.of("-12  ", "%- 5d", -12),
            Arguments.of("-12       ", "%- 10d", -12),
            Arguments.of("-12" + tenMillionBlanks, "%- 10000003d", -12),

            Arguments.of(" 1.2", "%- 1.1f", 1.2),
            Arguments.of(" 1.2", "%- 2.1f", 1.2),
            Arguments.of(" 1.2", "%- 3.1f", 1.2),
            Arguments.of(" 1.2", "%- 4.1f", 1.2),
            Arguments.of(" 1.2 ", "%- 5.1f", 1.2),
            Arguments.of(" 1.2      ", "%- 10.1f", 1.2),
            Arguments.of(" 1.2" + tenMillionBlanks, "%- 10000004.1f", 1.2),

            Arguments.of("-1.2", "%- 1.1f", -1.2),
            Arguments.of("-1.2", "%- 2.1f", -1.2),
            Arguments.of("-1.2", "%- 3.1f", -1.2),
            Arguments.of("-1.2", "%- 4.1f", -1.2),
            Arguments.of("-1.2 ", "%- 5.1f", -1.2),
            Arguments.of("-1.2      ", "%- 10.1f", -1.2),
            Arguments.of("-1.2" + tenMillionBlanks, "%- 10000004.1f", -1.2));
    }

    @ParameterizedTest
    @MethodSource
    void blankPaddingLeftAdjustedMandatoryBlank(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }

    /* zero padding, right adjusted, optional sign */
    static Stream<? extends Arguments> zeroPaddingRightAdjustedOptionalSign() {
        var tenMillionZeros = "0".repeat(10_000_000);
        return Stream.of(
            Arguments.of("12", "%01d", 12),
            Arguments.of("12", "%02d", 12),
            Arguments.of("012", "%03d", 12),
            Arguments.of("0012", "%04d", 12),
            Arguments.of("00012", "%05d", 12),
            Arguments.of("0000000012", "%010d", 12),
            Arguments.of(tenMillionZeros + "12", "%010000002d", 12),

            Arguments.of("-12", "%01d", -12),
            Arguments.of("-12", "%02d", -12),
            Arguments.of("-12", "%03d", -12),
            Arguments.of("-012", "%04d", -12),
            Arguments.of("-0012", "%05d", -12),
            Arguments.of("-000000012", "%010d", -12),
            Arguments.of("-" + tenMillionZeros + "12", "%010000003d", -12),

            Arguments.of("1.2", "%01.1f", 1.2),
            Arguments.of("1.2", "%02.1f", 1.2),
            Arguments.of("1.2", "%03.1f", 1.2),
            Arguments.of("01.2", "%04.1f", 1.2),
            Arguments.of("001.2", "%05.1f", 1.2),
            Arguments.of("00000001.2", "%010.1f", 1.2),
            Arguments.of(tenMillionZeros + "1.2", "%010000003.1f", 1.2),

            Arguments.of("-1.2", "%01.1f", -1.2),
            Arguments.of("-1.2", "%02.1f", -1.2),
            Arguments.of("-1.2", "%03.1f", -1.2),
            Arguments.of("-1.2", "%04.1f", -1.2),
            Arguments.of("-01.2", "%05.1f", -1.2),
            Arguments.of("-0000001.2", "%010.1f", -1.2),
            Arguments.of("-" + tenMillionZeros + "1.2", "%010000004.1f", -1.2));
    }

    @ParameterizedTest
    @MethodSource
    void zeroPaddingRightAdjustedOptionalSign(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }

    /* zero padding, right adjusted, mandatory sign */
    static Stream<? extends Arguments> zeroPaddingRightAdjustedMandatorySign() {
        var tenMillionZeros = "0".repeat(10_000_000);
        return Stream.of(
            Arguments.of("+12", "%+01d", 12),
            Arguments.of("+12", "%+02d", 12),
            Arguments.of("+12", "%+03d", 12),
            Arguments.of("+012", "%+04d", 12),
            Arguments.of("+0012", "%+05d", 12),
            Arguments.of("+000000012", "%+010d", 12),
            Arguments.of("+" + tenMillionZeros + "12", "%+010000003d", 12),

            Arguments.of("-12", "%+01d", -12),
            Arguments.of("-12", "%+02d", -12),
            Arguments.of("-12", "%+03d", -12),
            Arguments.of("-012", "%+04d", -12),
            Arguments.of("-0012", "%+05d", -12),
            Arguments.of("-000000012", "%+010d", -12),
            Arguments.of("-" + tenMillionZeros + "12", "%+010000003d", -12),

            Arguments.of("+1.2", "%+01.1f", 1.2),
            Arguments.of("+1.2", "%+02.1f", 1.2),
            Arguments.of("+1.2", "%+03.1f", 1.2),
            Arguments.of("+1.2", "%+04.1f", 1.2),
            Arguments.of("+01.2", "%+05.1f", 1.2),
            Arguments.of("+0000001.2", "%+010.1f", 1.2),
            Arguments.of("+" + tenMillionZeros + "1.2", "%+010000004.1f", 1.2),

            Arguments.of("-1.2", "%+01.1f", -1.2),
            Arguments.of("-1.2", "%+02.1f", -1.2),
            Arguments.of("-1.2", "%+03.1f", -1.2),
            Arguments.of("-1.2", "%+04.1f", -1.2),
            Arguments.of("-01.2", "%+05.1f", -1.2),
            Arguments.of("-0000001.2", "%+010.1f", -1.2),
            Arguments.of("-" + tenMillionZeros + "1.2", "%+010000004.1f", -1.2));
    }

    @ParameterizedTest
    @MethodSource
    void zeroPaddingRightAdjustedMandatorySign(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }

    /* zero padding, right adjusted, mandatory blank sign */
    static Stream<? extends Arguments> zeroPaddingRightAdjustedMandatoryBlank() {
        var tenMillionZeros = "0".repeat(10_000_000);
        return Stream.of(
            Arguments.of(" 12", "% 01d", 12),
            Arguments.of(" 12", "% 02d", 12),
            Arguments.of(" 12", "% 03d", 12),
            Arguments.of(" 012", "% 04d", 12),
            Arguments.of(" 0012", "% 05d", 12),
            Arguments.of(" 000000012", "% 010d", 12),
            Arguments.of(" " + tenMillionZeros + "12", "% 010000003d", 12),

            Arguments.of("-12", "% 01d", -12),
            Arguments.of("-12", "% 02d", -12),
            Arguments.of("-12", "% 03d", -12),
            Arguments.of("-012", "% 04d", -12),
            Arguments.of("-0012", "% 05d", -12),
            Arguments.of("-000000012", "% 010d", -12),
            Arguments.of("-" + tenMillionZeros + "12", "% 010000003d", -12),

            Arguments.of(" 1.2", "% 01.1f", 1.2),
            Arguments.of(" 1.2", "% 02.1f", 1.2),
            Arguments.of(" 1.2", "% 03.1f", 1.2),
            Arguments.of(" 1.2", "% 04.1f", 1.2),
            Arguments.of(" 01.2", "% 05.1f", 1.2),
            Arguments.of(" 0000001.2", "% 010.1f", 1.2),
            Arguments.of(" " + tenMillionZeros + "1.2", "% 010000004.1f", 1.2),

            Arguments.of("-1.2", "% 01.1f", -1.2),
            Arguments.of("-1.2", "% 02.1f", -1.2),
            Arguments.of("-1.2", "% 03.1f", -1.2),
            Arguments.of("-1.2", "% 04.1f", -1.2),
            Arguments.of("-01.2", "% 05.1f", -1.2),
            Arguments.of("-0000001.2", "% 010.1f", -1.2),
            Arguments.of("-" + tenMillionZeros + "1.2", "% 010000004.1f", -1.2));
    }

    @ParameterizedTest
    @MethodSource
    void zeroPaddingRightAdjustedMandatoryBlank(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }
}
