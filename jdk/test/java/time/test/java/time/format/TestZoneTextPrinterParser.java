/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package test.java.time.format;

import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.format.DateTimeFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.zone.ZoneRulesProvider;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

/**
 * Test ZoneTextPrinterParser
 */
@Test(groups={"implementation"})
public class TestZoneTextPrinterParser extends AbstractTestPrinterParser {

    protected static DateTimeFormatter getFormatter(Locale locale, TextStyle style) {
        return new DateTimeFormatterBuilder().appendZoneText(style)
                                             .toFormatter(locale)
                                             .withSymbols(DateTimeFormatSymbols.of(locale));
    }

    public void test_printText() {
        Random r = new Random();
        int N = 50;
        Locale[] locales = Locale.getAvailableLocales();
        Set<String> zids = ZoneRulesProvider.getAvailableZoneIds();
        ZonedDateTime zdt = ZonedDateTime.now();

        //System.out.printf("locale==%d, timezone=%d%n", locales.length, zids.size());
        while (N-- > 0) {
            zdt = zdt.withDayOfYear(r.nextInt(365) + 1)
                     .with(ChronoField.SECOND_OF_DAY, r.nextInt(86400));
            for (String zid : zids) {
                zdt = zdt.withZoneSameLocal(ZoneId.of(zid));
                TimeZone tz = TimeZone.getTimeZone(zid);
                boolean isDST = tz.inDaylightTime(new Date(zdt.toInstant().toEpochMilli()));
                for (Locale locale : locales) {
                    printText(locale, zdt, TextStyle.FULL,
                              tz.getDisplayName(isDST, TimeZone.LONG, locale));
                    printText(locale, zdt, TextStyle.SHORT,
                              tz.getDisplayName(isDST, TimeZone.SHORT, locale));
                }
            }
        }
    }

    private void printText(Locale locale, ZonedDateTime zdt, TextStyle style, String expected) {
        String result = getFormatter(locale, style).print(zdt);
        if (!result.equals(expected)) {
            if (result.equals("FooLocation") || // from rules provider test if same vm
                result.startsWith("Etc/GMT") || result.equals("ROC")) {  // TBD: match jdk behavior?
                return;
            }
            System.out.println("----------------");
            System.out.printf("tdz[%s]%n", zdt.toString());
            System.out.printf("[%-4s, %5s] :[%s]%n", locale.toString(), style.toString(),result);
            System.out.printf("%4s, %5s  :[%s]%n", "", "", expected);
        }
        assertEquals(result, expected);
    }
}
