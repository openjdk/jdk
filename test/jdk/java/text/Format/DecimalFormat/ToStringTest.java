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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToStringTest {

    // Check a normal expected value. There is no null locale test as
    // DecimalFormatSymbols will throw an exception when created with a null locale.
    @Test
    public void expectedValueTest() {
        String expectedStr =
                "DecimalFormat [locale: \"English (Canada)\", pattern: \"foo#00.00bar\"]\n";
        var d = new DecimalFormat("foo#00.00bar", new DecimalFormatSymbols(Locale.CANADA));
        assertEquals(expectedStr, d.toString());

        String expectedStr2 =
                "DecimalFormat [locale: \"English (Canada)\", pattern: \"#,##0.###\"]\n";
        var d2 = NumberFormat.getInstance(Locale.CANADA);
        assertEquals(expectedStr2, d2.toString());
    }
}
