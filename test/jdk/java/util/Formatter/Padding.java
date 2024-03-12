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
 * @bug 4906370 8299677 8326718
 * @summary Tests to excercise padding on int and double values,
 *      with various flag combinations.
 * @run junit Padding
 */

import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class Padding {

    private static final String tenMillionZeros = "0".repeat(10_000_000);
    private static final String tenMillionBlanks = " ".repeat(10_000_000);

    static Arguments[] padding() {
        return new Arguments[] {
                /* blank padding, right adjusted, optional plus sign */
                arguments("12", "%1d", 12),
                arguments("12", "%2d", 12),
                arguments(" 12", "%3d", 12),
                arguments("  12", "%4d", 12),
                arguments("   12", "%5d", 12),
                arguments("        12", "%10d", 12),
                arguments(tenMillionBlanks + "12", "%10000002d", 12),

                arguments("-12", "%1d", -12),
                arguments("-12", "%2d", -12),
                arguments("-12", "%3d", -12),
                arguments(" -12", "%4d", -12),
                arguments("  -12", "%5d", -12),
                arguments("       -12", "%10d", -12),
                arguments(tenMillionBlanks + "-12", "%10000003d", -12),

                arguments("1.2", "%1.1f", 1.2),
                arguments("1.2", "%2.1f", 1.2),
                arguments("1.2", "%3.1f", 1.2),
                arguments(" 1.2", "%4.1f", 1.2),
                arguments("  1.2", "%5.1f", 1.2),
                arguments("       1.2", "%10.1f", 1.2),
                arguments(tenMillionBlanks + "1.2", "%10000003.1f", 1.2),

                arguments("-1.2", "%1.1f", -1.2),
                arguments("-1.2", "%2.1f", -1.2),
                arguments("-1.2", "%3.1f", -1.2),
                arguments("-1.2", "%4.1f", -1.2),
                arguments(" -1.2", "%5.1f", -1.2),
                arguments("      -1.2", "%10.1f", -1.2),
                arguments(tenMillionBlanks + "-1.2", "%10000004.1f", -1.2),

                /* blank padding, right adjusted, mandatory plus sign */
                arguments("+12", "%+1d", 12),
                arguments("+12", "%+2d", 12),
                arguments("+12", "%+3d", 12),
                arguments(" +12", "%+4d", 12),
                arguments("  +12", "%+5d", 12),
                arguments("       +12", "%+10d", 12),
                arguments(tenMillionBlanks + "+12", "%+10000003d", 12),

                arguments("-12", "%+1d", -12),
                arguments("-12", "%+2d", -12),
                arguments("-12", "%+3d", -12),
                arguments(" -12", "%+4d", -12),
                arguments("  -12", "%+5d", -12),
                arguments("       -12", "%+10d", -12),
                arguments(tenMillionBlanks + "-12", "%+10000003d", -12),

                arguments("+1.2", "%+1.1f", 1.2),
                arguments("+1.2", "%+2.1f", 1.2),
                arguments("+1.2", "%+3.1f", 1.2),
                arguments("+1.2", "%+4.1f", 1.2),
                arguments(" +1.2", "%+5.1f", 1.2),
                arguments("      +1.2", "%+10.1f", 1.2),
                arguments(tenMillionBlanks + "+1.2", "%+10000004.1f", 1.2),

                arguments("-1.2", "%+1.1f", -1.2),
                arguments("-1.2", "%+2.1f", -1.2),
                arguments("-1.2", "%+3.1f", -1.2),
                arguments("-1.2", "%+4.1f", -1.2),
                arguments(" -1.2", "%+5.1f", -1.2),
                arguments("      -1.2", "%+10.1f", -1.2),
                arguments(tenMillionBlanks + "-1.2", "%+10000004.1f", -1.2),

                /* blank padding, right adjusted, mandatory blank sign */
                arguments(" 12", "% 1d", 12),
                arguments(" 12", "% 2d", 12),
                arguments(" 12", "% 3d", 12),
                arguments("  12", "% 4d", 12),
                arguments("   12", "% 5d", 12),
                arguments("        12", "% 10d", 12),
                arguments(tenMillionBlanks + "12", "% 10000002d", 12),

                arguments("-12", "% 1d", -12),
                arguments("-12", "% 2d", -12),
                arguments("-12", "% 3d", -12),
                arguments(" -12", "% 4d", -12),
                arguments("  -12", "% 5d", -12),
                arguments("       -12", "% 10d", -12),
                arguments(tenMillionBlanks + "-12", "% 10000003d", -12),

                arguments(" 1.2", "% 1.1f", 1.2),
                arguments(" 1.2", "% 2.1f", 1.2),
                arguments(" 1.2", "% 3.1f", 1.2),
                arguments(" 1.2", "% 4.1f", 1.2),
                arguments("  1.2", "% 5.1f", 1.2),
                arguments("       1.2", "% 10.1f", 1.2),
                arguments(tenMillionBlanks + "1.2", "% 10000003.1f", 1.2),

                arguments("-1.2", "% 1.1f", -1.2),
                arguments("-1.2", "% 2.1f", -1.2),
                arguments("-1.2", "% 3.1f", -1.2),
                arguments("-1.2", "% 4.1f", -1.2),
                arguments(" -1.2", "% 5.1f", -1.2),
                arguments("      -1.2", "% 10.1f", -1.2),
                arguments(tenMillionBlanks + "-1.2", "% 10000004.1f", -1.2),

                /* blank padding, left adjusted, optional sign */
                arguments("12", "%-1d", 12),
                arguments("12", "%-2d", 12),
                arguments("12 ", "%-3d", 12),
                arguments("12  ", "%-4d", 12),
                arguments("12   ", "%-5d", 12),
                arguments("12        ", "%-10d", 12),
                arguments("12" + tenMillionBlanks, "%-10000002d", 12),

                arguments("-12", "%-1d", -12),
                arguments("-12", "%-2d", -12),
                arguments("-12", "%-3d", -12),
                arguments("-12 ", "%-4d", -12),
                arguments("-12  ", "%-5d", -12),
                arguments("-12       ", "%-10d", -12),
                arguments("-12" + tenMillionBlanks, "%-10000003d", -12),

                arguments("1.2", "%-1.1f", 1.2),
                arguments("1.2", "%-2.1f", 1.2),
                arguments("1.2", "%-3.1f", 1.2),
                arguments("1.2 ", "%-4.1f", 1.2),
                arguments("1.2  ", "%-5.1f", 1.2),
                arguments("1.2       ", "%-10.1f", 1.2),
                arguments("1.2" + tenMillionBlanks, "%-10000003.1f", 1.2),

                arguments("-1.2", "%-1.1f", -1.2),
                arguments("-1.2", "%-2.1f", -1.2),
                arguments("-1.2", "%-3.1f", -1.2),
                arguments("-1.2", "%-4.1f", -1.2),
                arguments("-1.2 ", "%-5.1f", -1.2),
                arguments("-1.2      ", "%-10.1f", -1.2),
                arguments("-1.2" + tenMillionBlanks, "%-10000004.1f", -1.2),

                /* blank padding, left adjusted, mandatory plus sign */
                arguments("+12", "%-+1d", 12),
                arguments("+12", "%-+2d", 12),
                arguments("+12", "%-+3d", 12),
                arguments("+12 ", "%-+4d", 12),
                arguments("+12  ", "%-+5d", 12),
                arguments("+12       ", "%-+10d", 12),
                arguments("+12" + tenMillionBlanks, "%-+10000003d", 12),

                arguments("-12", "%-+1d", -12),
                arguments("-12", "%-+2d", -12),
                arguments("-12", "%-+3d", -12),
                arguments("-12 ", "%-+4d", -12),
                arguments("-12  ", "%-+5d", -12),
                arguments("-12       ", "%-+10d", -12),
                arguments("-12" + tenMillionBlanks, "%-+10000003d", -12),

                arguments("+1.2", "%-+1.1f", 1.2),
                arguments("+1.2", "%-+2.1f", 1.2),
                arguments("+1.2", "%-+3.1f", 1.2),
                arguments("+1.2", "%-+4.1f", 1.2),
                arguments("+1.2 ", "%-+5.1f", 1.2),
                arguments("+1.2      ", "%-+10.1f", 1.2),
                arguments("+1.2" + tenMillionBlanks, "%-+10000004.1f", 1.2),

                arguments("-1.2", "%-+1.1f", -1.2),
                arguments("-1.2", "%-+2.1f", -1.2),
                arguments("-1.2", "%-+3.1f", -1.2),
                arguments("-1.2", "%-+4.1f", -1.2),
                arguments("-1.2 ", "%-+5.1f", -1.2),
                arguments("-1.2      ", "%-+10.1f", -1.2),
                arguments("-1.2" + tenMillionBlanks, "%-+10000004.1f", -1.2),

                /* blank padding, left adjusted, mandatory blank sign */
                arguments(" 12", "%- 1d", 12),
                arguments(" 12", "%- 2d", 12),
                arguments(" 12", "%- 3d", 12),
                arguments(" 12 ", "%- 4d", 12),
                arguments(" 12  ", "%- 5d", 12),
                arguments(" 12       ", "%- 10d", 12),
                arguments(" 12" + tenMillionBlanks, "%- 10000003d", 12),

                arguments("-12", "%- 1d", -12),
                arguments("-12", "%- 2d", -12),
                arguments("-12", "%- 3d", -12),
                arguments("-12 ", "%- 4d", -12),
                arguments("-12  ", "%- 5d", -12),
                arguments("-12       ", "%- 10d", -12),
                arguments("-12" + tenMillionBlanks, "%- 10000003d", -12),

                arguments(" 1.2", "%- 1.1f", 1.2),
                arguments(" 1.2", "%- 2.1f", 1.2),
                arguments(" 1.2", "%- 3.1f", 1.2),
                arguments(" 1.2", "%- 4.1f", 1.2),
                arguments(" 1.2 ", "%- 5.1f", 1.2),
                arguments(" 1.2      ", "%- 10.1f", 1.2),
                arguments(" 1.2" + tenMillionBlanks, "%- 10000004.1f", 1.2),

                arguments("-1.2", "%- 1.1f", -1.2),
                arguments("-1.2", "%- 2.1f", -1.2),
                arguments("-1.2", "%- 3.1f", -1.2),
                arguments("-1.2", "%- 4.1f", -1.2),
                arguments("-1.2 ", "%- 5.1f", -1.2),
                arguments("-1.2      ", "%- 10.1f", -1.2),
                arguments("-1.2" + tenMillionBlanks, "%- 10000004.1f", -1.2),

                /* zero padding, right adjusted, optional sign */
                arguments("12", "%01d", 12),
                arguments("12", "%02d", 12),
                arguments("012", "%03d", 12),
                arguments("0012", "%04d", 12),
                arguments("00012", "%05d", 12),
                arguments("0000000012", "%010d", 12),
                arguments(tenMillionZeros + "12", "%010000002d", 12),

                arguments("-12", "%01d", -12),
                arguments("-12", "%02d", -12),
                arguments("-12", "%03d", -12),
                arguments("-012", "%04d", -12),
                arguments("-0012", "%05d", -12),
                arguments("-000000012", "%010d", -12),
                arguments("-" + tenMillionZeros + "12", "%010000003d", -12),

                arguments("1.2", "%01.1f", 1.2),
                arguments("1.2", "%02.1f", 1.2),
                arguments("1.2", "%03.1f", 1.2),
                arguments("01.2", "%04.1f", 1.2),
                arguments("001.2", "%05.1f", 1.2),
                arguments("00000001.2", "%010.1f", 1.2),
                arguments(tenMillionZeros + "1.2", "%010000003.1f", 1.2),

                arguments("-1.2", "%01.1f", -1.2),
                arguments("-1.2", "%02.1f", -1.2),
                arguments("-1.2", "%03.1f", -1.2),
                arguments("-1.2", "%04.1f", -1.2),
                arguments("-01.2", "%05.1f", -1.2),
                arguments("-0000001.2", "%010.1f", -1.2),
                arguments("-" + tenMillionZeros + "1.2", "%010000004.1f", -1.2),

                /* zero padding, right adjusted, mandatory plus sign */
                arguments("+12", "%+01d", 12),
                arguments("+12", "%+02d", 12),
                arguments("+12", "%+03d", 12),
                arguments("+012", "%+04d", 12),
                arguments("+0012", "%+05d", 12),
                arguments("+000000012", "%+010d", 12),
                arguments("+" + tenMillionZeros + "12", "%+010000003d", 12),

                arguments("-12", "%+01d", -12),
                arguments("-12", "%+02d", -12),
                arguments("-12", "%+03d", -12),
                arguments("-012", "%+04d", -12),
                arguments("-0012", "%+05d", -12),
                arguments("-000000012", "%+010d", -12),
                arguments("-" + tenMillionZeros + "12", "%+010000003d", -12),

                arguments("+1.2", "%+01.1f", 1.2),
                arguments("+1.2", "%+02.1f", 1.2),
                arguments("+1.2", "%+03.1f", 1.2),
                arguments("+1.2", "%+04.1f", 1.2),
                arguments("+01.2", "%+05.1f", 1.2),
                arguments("+0000001.2", "%+010.1f", 1.2),
                arguments("+" + tenMillionZeros + "1.2", "%+010000004.1f", 1.2),

                arguments("-1.2", "%+01.1f", -1.2),
                arguments("-1.2", "%+02.1f", -1.2),
                arguments("-1.2", "%+03.1f", -1.2),
                arguments("-1.2", "%+04.1f", -1.2),
                arguments("-01.2", "%+05.1f", -1.2),
                arguments("-0000001.2", "%+010.1f", -1.2),
                arguments("-" + tenMillionZeros + "1.2", "%+010000004.1f", -1.2),

                /* zero padding, right adjusted, mandatory blank sign */
                arguments(" 12", "% 01d", 12),
                arguments(" 12", "% 02d", 12),
                arguments(" 12", "% 03d", 12),
                arguments(" 012", "% 04d", 12),
                arguments(" 0012", "% 05d", 12),
                arguments(" 000000012", "% 010d", 12),
                arguments(" " + tenMillionZeros + "12", "% 010000003d", 12),

                arguments("-12", "% 01d", -12),
                arguments("-12", "% 02d", -12),
                arguments("-12", "% 03d", -12),
                arguments("-012", "% 04d", -12),
                arguments("-0012", "% 05d", -12),
                arguments("-000000012", "% 010d", -12),
                arguments("-" + tenMillionZeros + "12", "% 010000003d", -12),

                arguments(" 1.2", "% 01.1f", 1.2),
                arguments(" 1.2", "% 02.1f", 1.2),
                arguments(" 1.2", "% 03.1f", 1.2),
                arguments(" 1.2", "% 04.1f", 1.2),
                arguments(" 01.2", "% 05.1f", 1.2),
                arguments(" 0000001.2", "% 010.1f", 1.2),
                arguments(" " + tenMillionZeros + "1.2", "% 010000004.1f", 1.2),

                arguments("-1.2", "% 01.1f", -1.2),
                arguments("-1.2", "% 02.1f", -1.2),
                arguments("-1.2", "% 03.1f", -1.2),
                arguments("-1.2", "% 04.1f", -1.2),
                arguments("-01.2", "% 05.1f", -1.2),
                arguments("-0000001.2", "% 010.1f", -1.2),
                arguments("-" + tenMillionZeros + "1.2", "% 010000004.1f", -1.2),

        };
    }

    @ParameterizedTest
    @MethodSource
    void padding(String expected, String format, Object value) {
        assertEquals(expected, String.format(Locale.US, format, value));
    }

}
