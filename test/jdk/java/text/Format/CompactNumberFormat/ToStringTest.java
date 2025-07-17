/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8321545
 * @summary Ensure value returned by overridden toString method is as expected
 * @run junit ToStringTest
 */

import java.text.CompactNumberFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToStringTest {

    // Check a normal expected value. There is no null test as the getInstance
    // methods and constructor (which takes DFS) throw NPE if the locale param is null.
    @Test
    public void expectedValueTest() {
        String expectedStr = "CompactNumberFormat [locale: \"English (Canada)\", decimal pattern: \"#,##0.###\", compact patterns: \"[, , , {one:0' 'thousand other:0' 'thousand}, {one:00' 'thousand other:00' 'thousand}, {one:000' 'thousand other:000' 'thousand}, {one:0' 'million other:0' 'million}, {one:00' 'million other:00' 'million}, {one:000' 'million other:000' 'million}, {one:0' 'billion other:0' 'billion}, {one:00' 'billion other:00' 'billion}, {one:000' 'billion other:000' 'billion}, {one:0' 'trillion other:0' 'trillion}, {one:00' 'trillion other:00' 'trillion}, {one:000' 'trillion other:000' 'trillion}]\"]\n";
        var c = NumberFormat.getCompactNumberInstance(Locale.CANADA, NumberFormat.Style.LONG);
        assertEquals(expectedStr, c.toString());
    }

    // Check an odd value with empty decimal pattern and compact patterns.
    @Test
    public void oddValueTest() {
        String expectedStr = "CompactNumberFormat [locale: \"English (Canada)\", decimal pattern: \"\", compact patterns: \"[]\"]\n";
        var c = new CompactNumberFormat("", new DecimalFormatSymbols(Locale.CANADA), new String[]{});
        assertEquals(expectedStr, c.toString());
    }
}
