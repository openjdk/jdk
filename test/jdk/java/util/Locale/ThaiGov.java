/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4474409 8174269
 * @summary Tests some localized methods with Thai locale
 * @author John O'Conner
 * @modules jdk.localedata
 * @run junit ThaiGov
 */

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThaiGov {

    private static final double VALUE = 12345678.234;
    private static final Locale TH = Locale.of("th", "TH", "TH");

    // Test number formatting for thai
    @Test
    public void numberTest() {
        final String strExpected = "๑๒,๓๔๕,๖๗๘.๒๓๔";
        NumberFormat nf = NumberFormat.getInstance(TH);
        String str = nf.format(VALUE);
        assertEquals(strExpected, str);
    }

    // Test currency formatting for Thai
    @Test
    public void currencyTest() {
        final String strExpected = "฿\u00a0๑๒,๓๔๕,๖๗๘.๒๓";
        NumberFormat nf = NumberFormat.getCurrencyInstance(TH);
        String str = nf.format(VALUE);
        assertEquals(strExpected, str);
    }

    // Test date formatting for Thai
    @Test
    public void dateTest() {
        TimeZone tz = TimeZone.getTimeZone("America/Los_Angeles");
        Calendar calGregorian = Calendar.getInstance(tz, Locale.US);
        calGregorian.clear();
        calGregorian.set(2002, 4, 1, 8, 30);
        final Date date = calGregorian.getTime();
        Calendar cal = Calendar.getInstance(tz, TH);
        cal.clear();
        cal.setTime(date);


        final String strExpected = "วันพุธที่ ๑ พฤษภาคม พุทธศักราช ๒๕๔๕ ๘ นาฬิกา ๓๐ นาที ๐๐ วินาที เวลาออมแสงแปซิฟิกในอเมริกาเหนือ";
        Date value = cal.getTime();

        // th_TH_TH test
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, TH);
        df.setTimeZone(tz);
        String str = df.format(value);
        assertEquals(strExpected, str);
    }
}
