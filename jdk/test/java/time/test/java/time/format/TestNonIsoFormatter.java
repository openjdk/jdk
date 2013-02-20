/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.time.*;
import java.time.chrono.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.Locale;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

/**
 * Test DateTimeFormatter with non-ISO chronology.
 *
 * Strings in test data are all dependent on CLDR data which may change
 * in future CLDR releases.
 */
@Test(groups={"implementation"})
public class TestNonIsoFormatter {
    private static final Chronology JAPANESE = JapaneseChronology.INSTANCE;
    private static final Chronology HIJRAH = HijrahChronology.INSTANCE;
    private static final Chronology MINGUO = MinguoChronology.INSTANCE;
    private static final Chronology BUDDHIST = ThaiBuddhistChronology.INSTANCE;

    private static final LocalDate IsoDate = LocalDate.of(2013, 2, 11);

    private static final Locale ARABIC = new Locale("ar");
    private static final Locale thTH = new Locale("th", "TH");
    private static final Locale thTHTH = new Locale("th", "TH", "TH");

    @BeforeMethod
    public void setUp() {
    }

    @DataProvider(name="format_data")
    Object[][] formatData() {
        return new Object[][] {
            // Chronology, Locale, ChronoLocalDate, expected string
            { JAPANESE, Locale.JAPANESE, JAPANESE.date(IsoDate),
              "\u5e73\u621025\u5e742\u670811\u65e5\u6708\u66dc\u65e5" }, // Japanese Heisei 25-02-11 (Mon)
            { HIJRAH, ARABIC, HIJRAH.date(IsoDate),
              "\u0627\u0644\u0627\u062b\u0646\u064a\u0646\u060c 30 \u0631\u0628\u064a\u0639 "
              + "\u0627\u0644\u0623\u0648\u0644 1434" }, // Hijrah AH 1434-03-30 (Mon)
            { MINGUO, Locale.TAIWAN, MINGUO.date(IsoDate),
              "\u6c11\u570b102\u5e742\u670811\u65e5\u661f\u671f\u4e00" }, // Minguo ROC 102-02-11 (Mon)
            { BUDDHIST, thTH, BUDDHIST.date(IsoDate),
              "\u0e27\u0e31\u0e19\u0e08\u0e31\u0e19\u0e17\u0e23\u0e4c\u0e17\u0e35\u0e48"
              + " 11 \u0e01\u0e38\u0e21\u0e20\u0e32\u0e1e\u0e31\u0e19\u0e18\u0e4c"
              + " \u0e1e.\u0e28. 2556" }, // ThaiBuddhist BE 2556-02-11
         // { BUDDHIST, thTHTH, BUDDHIST.date(IsoDate), "<TBS>" }, // doesn't work
        };
    }

    @DataProvider(name="invalid_text")
    Object[][] invalidText() {
        return new Object[][] {
            // TODO: currently fixed Chronology and Locale.
            { "\u662d\u548c64\u5e741\u67089\u65e5\u6708\u66dc\u65e5" }, // S64.01.09 (Mon)
            { "\u662d\u548c65\u5e741\u67081\u65e5\u6708\u66dc\u65e5" }, // S65.01.01 (Mon)
        };
    }

    @Test(dataProvider="format_data")
    public void test_formatLocalizedDate(Chronology chrono, Locale locale, ChronoLocalDate<?> date, String expected) {
        DateTimeFormatter dtf = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withChronology(chrono).withLocale(locale);
        String text = dtf.format(date);
        assertEquals(text, expected);
    }

    @Test(dataProvider="format_data")
    public void test_parseLocalizedText(Chronology chrono, Locale locale, ChronoLocalDate<?> expected, String text) {
        DateTimeFormatter dtf = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withChronology(chrono).withLocale(locale);
        TemporalAccessor temporal = dtf.parse(text);
        ChronoLocalDate<?> date = chrono.date(temporal);
        assertEquals(date, expected);
    }

    @Test(dataProvider="invalid_text", expectedExceptions=DateTimeParseException.class)
    public void test_parseInvalidText(String text) {
        DateTimeFormatter dtf = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withChronology(JAPANESE).withLocale(Locale.JAPANESE);
        TemporalAccessor temporal = dtf.parse(text);
    }
}
