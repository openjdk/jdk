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
 * @bug 8318761
 * @summary Test MessageFormatPattern ability to recognize and produce
 *          appropriate FormatType and FormatStyle for CompactNumberFormat.
 * @run junit CompactSubFormats
 */

import java.text.CompactNumberFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompactSubFormats {

    // Ensure the built-in FormatType and FormatStyles for cnFmt are as expected
    @Test
    public void applyPatternTest() {
        var mFmt = new MessageFormat(
                "{0,number,compact-short}{1,number,compact-long}");
        var compactShort = NumberFormat.getCompactNumberInstance(
                mFmt.getLocale(), NumberFormat.Style.SHORT);
        var compactLong = NumberFormat.getCompactNumberInstance(
                mFmt.getLocale(), NumberFormat.Style.LONG);
        assertEquals(mFmt.getFormatsByArgumentIndex()[0], compactShort);
        assertEquals(mFmt.getFormatsByArgumentIndex()[1], compactLong);
    }

    // Ensure that only 'compact-short' and 'compact-long' are recognized
    @Test
    public void badApplyPatternTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new MessageFormat("{0,number,compact-regular"));
    }

    // SHORT and LONG CompactNumberFormats should produce correct patterns
    @Test
    public void toPatternTest() {
        var mFmt = new MessageFormat("{0}{1}");
        mFmt.setFormatByArgumentIndex(0, NumberFormat.getCompactNumberInstance(
                mFmt.getLocale(), NumberFormat.Style.SHORT));
        mFmt.setFormatByArgumentIndex(1, NumberFormat.getCompactNumberInstance(
                mFmt.getLocale(), NumberFormat.Style.LONG));
        assertEquals("{0,number,compact-short}{1,number,compact-long}", mFmt.toPattern());
    }

    // A custom cnFmt cannot be recognized, thus does not produce any built-in pattern
    @Test
    public void badToPatternTest() {
        var mFmt = new MessageFormat("{0}");
        // Non-recognizable compactNumberFormat
        mFmt.setFormatByArgumentIndex(0, new CompactNumberFormat("",
                        DecimalFormatSymbols.getInstance(Locale.US), new String[]{""}));
        // Default behavior of unrecognizable Formats is a FormatElement
        // in the form of { ArgumentIndex }
        assertEquals("{0}", mFmt.toPattern());
    }

    // Test that the cnFmt Subformats format properly within the MessageFormat
    @Test
    public void formatTest() {
        long[] values = new long[]{1, 10, 100, 1000, 10000, 100000};
        var mFmt = new MessageFormat(
                "foo{0,number,compact-short}foo");
        var compactShort = NumberFormat.getCompactNumberInstance(
                mFmt.getLocale(), NumberFormat.Style.SHORT);
        for (long value : values) {
            Object[] data = {value};
            // Check cnFmt sub-format is formatting properly
            assertEquals(mFmt.format(data), "foo"+compactShort.format(value)+"foo");
        }
    }
}
