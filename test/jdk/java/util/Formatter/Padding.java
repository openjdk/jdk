/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit Padding
 */

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Padding {

    @Nested
    class BlankPaddingInt {

        @Test
        void blankPad_d() {
            assertEquals("12", String.format("%1d", 12));
            assertEquals("12", String.format("%2d", 12));
            assertEquals(" 12", String.format("%3d", 12));
            assertEquals("  12", String.format("%4d", 12));
            assertEquals("   12", String.format("%5d", 12));
            assertEquals("        12", String.format("%10d", 12));

            assertEquals("-12", String.format("%1d", -12));
            assertEquals("-12", String.format("%2d", -12));
            assertEquals("-12", String.format("%3d", -12));
            assertEquals(" -12", String.format("%4d", -12));
            assertEquals("  -12", String.format("%5d", -12));
            assertEquals("        12", String.format("%10d", 12));
        }

        @Test
        void blankPad_plus_d() {
            assertEquals("+12", String.format("%+1d", 12));
            assertEquals("+12", String.format("%+2d", 12));
            assertEquals("+12", String.format("%+3d", 12));
            assertEquals(" +12", String.format("%+4d", 12));
            assertEquals("  +12", String.format("%+5d", 12));
            assertEquals("       +12", String.format("%+10d", 12));

            assertEquals("-12", String.format("%+1d", -12));
            assertEquals("-12", String.format("%+2d", -12));
            assertEquals("-12", String.format("%+3d", -12));
            assertEquals(" -12", String.format("%+4d", -12));
            assertEquals("  -12", String.format("%+5d", -12));
            assertEquals("       -12", String.format("%+10d", -12));
        }

        @Test
        void blankPad_blank_d() {
            assertEquals(" 12", String.format("% 1d", 12));
            assertEquals(" 12", String.format("% 2d", 12));
            assertEquals(" 12", String.format("% 3d", 12));
            assertEquals("  12", String.format("% 4d", 12));
            assertEquals("   12", String.format("% 5d", 12));
            assertEquals("        12", String.format("% 10d", 12));

            assertEquals("-12", String.format("% 1d", -12));
            assertEquals("-12", String.format("% 2d", -12));
            assertEquals("-12", String.format("% 3d", -12));
            assertEquals(" -12", String.format("% 4d", -12));
            assertEquals("  -12", String.format("% 5d", -12));
            assertEquals("       -12", String.format("% 10d", -12));
        }

        @Test
        void blankPad_minus_d() {
            assertEquals("12", String.format("%-1d", 12));
            assertEquals("12", String.format("%-2d", 12));
            assertEquals("12 ", String.format("%-3d", 12));
            assertEquals("12  ", String.format("%-4d", 12));
            assertEquals("12   ", String.format("%-5d", 12));
            assertEquals("12        ", String.format("%-10d", 12));

            assertEquals("-12", String.format("%-1d", -12));
            assertEquals("-12", String.format("%-2d", -12));
            assertEquals("-12", String.format("%-3d", -12));
            assertEquals("-12 ", String.format("%-4d", -12));
            assertEquals("-12  ", String.format("%-5d", -12));
            assertEquals("-12       ", String.format("%-10d", -12));
        }

        @Test
        void blankPad_minus_plus_d() {
            assertEquals("+12", String.format("%-+1d", 12));
            assertEquals("+12", String.format("%-+2d", 12));
            assertEquals("+12", String.format("%-+3d", 12));
            assertEquals("+12 ", String.format("%-+4d", 12));
            assertEquals("+12  ", String.format("%-+5d", 12));
            assertEquals("+12       ", String.format("%-+10d", 12));

            assertEquals("-12", String.format("%-+1d", -12));
            assertEquals("-12", String.format("%-+2d", -12));
            assertEquals("-12", String.format("%-+3d", -12));
            assertEquals("-12 ", String.format("%-+4d", -12));
            assertEquals("-12  ", String.format("%-+5d", -12));
            assertEquals("-12       ", String.format("%-+10d", -12));
        }

        @Test
        void blankPad_minus_blank_d() {
            assertEquals(" 12", String.format("%- 1d", 12));
            assertEquals(" 12", String.format("%- 2d", 12));
            assertEquals(" 12", String.format("%- 3d", 12));
            assertEquals(" 12 ", String.format("%- 4d", 12));
            assertEquals(" 12  ", String.format("%- 5d", 12));
            assertEquals(" 12       ", String.format("%- 10d", 12));

            assertEquals("-12", String.format("%- 1d", -12));
            assertEquals("-12", String.format("%- 2d", -12));
            assertEquals("-12", String.format("%- 3d", -12));
            assertEquals("-12 ", String.format("%- 4d", -12));
            assertEquals("-12  ", String.format("%- 5d", -12));
            assertEquals("-12       ", String.format("%- 10d", -12));
        }

    }

    @Nested
    class BlankPaddingDouble {

        @Test
        void blankPad_d() {
            assertEquals("1.2", String.format("%1.1f", 1.2));
            assertEquals("1.2", String.format("%2.1f", 1.2));
            assertEquals("1.2", String.format("%3.1f", 1.2));
            assertEquals(" 1.2", String.format("%4.1f", 1.2));
            assertEquals("  1.2", String.format("%5.1f", 1.2));
            assertEquals("       1.2", String.format("%10.1f", 1.2));

            assertEquals("-1.2", String.format("%1.1f", -1.2));
            assertEquals("-1.2", String.format("%2.1f", -1.2));
            assertEquals("-1.2", String.format("%3.1f", -1.2));
            assertEquals("-1.2", String.format("%4.1f", -1.2));
            assertEquals(" -1.2", String.format("%5.1f", -1.2));
            assertEquals("      -1.2", String.format("%10.1f", -1.2));
        }

        @Test
        void blankPad_plus_d() {
            assertEquals("+1.2", String.format("%+1.1f", 1.2));
            assertEquals("+1.2", String.format("%+2.1f", 1.2));
            assertEquals("+1.2", String.format("%+3.1f", 1.2));
            assertEquals("+1.2", String.format("%+4.1f", 1.2));
            assertEquals(" +1.2", String.format("%+5.1f", 1.2));
            assertEquals("      +1.2", String.format("%+10.1f", 1.2));

            assertEquals("-1.2", String.format("%+1.1f", -1.2));
            assertEquals("-1.2", String.format("%+2.1f", -1.2));
            assertEquals("-1.2", String.format("%+3.1f", -1.2));
            assertEquals("-1.2", String.format("%+4.1f", -1.2));
            assertEquals(" -1.2", String.format("%+5.1f", -1.2));
            assertEquals("      -1.2", String.format("%+10.1f", -1.2));
        }

        @Test
        void blankPad_blank_d() {
            assertEquals(" 1.2", String.format("% 1.1f", 1.2));
            assertEquals(" 1.2", String.format("% 2.1f", 1.2));
            assertEquals(" 1.2", String.format("% 3.1f", 1.2));
            assertEquals(" 1.2", String.format("% 4.1f", 1.2));
            assertEquals("  1.2", String.format("% 5.1f", 1.2));
            assertEquals("       1.2", String.format("% 10.1f", 1.2));

            assertEquals("-1.2", String.format("% 1.1f", -1.2));
            assertEquals("-1.2", String.format("% 2.1f", -1.2));
            assertEquals("-1.2", String.format("% 3.1f", -1.2));
            assertEquals("-1.2", String.format("% 4.1f", -1.2));
            assertEquals(" -1.2", String.format("% 5.1f", -1.2));
            assertEquals("      -1.2", String.format("% 10.1f", -1.2));
        }

        @Test
        void blankPad_minus_d() {
            assertEquals("1.2", String.format("%-1.1f", 1.2));
            assertEquals("1.2", String.format("%-2.1f", 1.2));
            assertEquals("1.2", String.format("%-3.1f", 1.2));
            assertEquals("1.2 ", String.format("%-4.1f", 1.2));
            assertEquals("1.2  ", String.format("%-5.1f", 1.2));
            assertEquals("1.2       ", String.format("%-10.1f", 1.2));

            assertEquals("-1.2", String.format("%-1.1f", -1.2));
            assertEquals("-1.2", String.format("%-2.1f", -1.2));
            assertEquals("-1.2", String.format("%-3.1f", -1.2));
            assertEquals("-1.2", String.format("%-4.1f", -1.2));
            assertEquals("-1.2 ", String.format("%-5.1f", -1.2));
            assertEquals("-1.2      ", String.format("%-10.1f", -1.2));
        }

        @Test
        void blankPad_minus_plus_d() {
            assertEquals("+1.2", String.format("%-+1.1f", 1.2));
            assertEquals("+1.2", String.format("%-+2.1f", 1.2));
            assertEquals("+1.2", String.format("%-+3.1f", 1.2));
            assertEquals("+1.2", String.format("%-+4.1f", 1.2));
            assertEquals("+1.2 ", String.format("%-+5.1f", 1.2));
            assertEquals("+1.2      ", String.format("%-+10.1f", 1.2));

            assertEquals("-1.2", String.format("%-+1.1f", -1.2));
            assertEquals("-1.2", String.format("%-+2.1f", -1.2));
            assertEquals("-1.2", String.format("%-+3.1f", -1.2));
            assertEquals("-1.2", String.format("%-+4.1f", -1.2));
            assertEquals("-1.2 ", String.format("%-+5.1f", -1.2));
            assertEquals("-1.2      ", String.format("%-+10.1f", -1.2));
        }

        @Test
        void blankPad_minus_blank_d() {
            assertEquals(" 1.2", String.format("%- 1.1f", 1.2));
            assertEquals(" 1.2", String.format("%- 2.1f", 1.2));
            assertEquals(" 1.2", String.format("%- 3.1f", 1.2));
            assertEquals(" 1.2", String.format("%- 4.1f", 1.2));
            assertEquals(" 1.2 ", String.format("%- 5.1f", 1.2));
            assertEquals(" 1.2      ", String.format("%- 10.1f", 1.2));

            assertEquals("-1.2", String.format("%- 1.1f", -1.2));
            assertEquals("-1.2", String.format("%- 2.1f", -1.2));
            assertEquals("-1.2", String.format("%- 3.1f", -1.2));
            assertEquals("-1.2", String.format("%- 4.1f", -1.2));
            assertEquals("-1.2 ", String.format("%- 5.1f", -1.2));
            assertEquals("-1.2      ", String.format("%- 10.1f", -1.2));
        }

    }

}
