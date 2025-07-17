/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8132125 8202537
 * @summary Checks Swiss' number elements
 * @modules jdk.localedata
 * @run junit Bug8132125
 */

import java.text.NumberFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Bug8132125 {

    // Ensure the CLDRConverter does not omit the Swiss number elements
    @Test
    public void swissNumElementsTest() {
        Locale deCH = Locale.of("de", "CH");
        NumberFormat nf = NumberFormat.getInstance(deCH);

        // "\u002E" as decimal separator, "\u2019" as grouping separator
        String expected = "54\u2019839\u2019483.142";
        String actual = nf.format(54839483.1415);
        assertEquals(expected, actual, "incorrect number elements for de_CH");
    }
}
