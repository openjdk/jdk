/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @test
 * @bug 8206120 8306116 8333582
 * @modules jdk.localedata
 */

package test.java.time.format;

import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.HijrahChronology;
import java.time.chrono.IsoChronology;
import java.time.chrono.JapaneseChronology;
import java.time.chrono.MinguoChronology;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.format.DecimalStyle;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Locale;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test DateTimeFormatter with non-ISO chronology.
 *
 * Strings in test data are all dependent on CLDR data which may change
 * in future CLDR releases.
 */
@Test
public class TestNonIsoFormatter {
    private static final Chronology ISO8601 = IsoChronology.INSTANCE;
    private static final Chronology JAPANESE = JapaneseChronology.INSTANCE;
    private static final Chronology HIJRAH = HijrahChronology.INSTANCE;
    private static final Chronology MINGUO = MinguoChronology.INSTANCE;
    private static final Chronology BUDDHIST = ThaiBuddhistChronology.INSTANCE;

    private static final LocalDate IsoDate = LocalDate.of(2013, 2, 11);

    private static final Locale ARABIC = Locale.of("ar");
    private static final Locale thTH = Locale.of("th", "TH");
    private static final Locale thTHTH = Locale.forLanguageTag("th-TH-u-nu-thai");
    private static final Locale jaJPJP = Locale.forLanguageTag("ja-JP-u-ca-japanese");

    @BeforeMethod
    public void setUp() {
    }

    @DataProvider(name="format_data")
    Object[][] formatData() {
        return new Object[][] {
            // Chronology, Format Locale, Numbering Locale, ChronoLocalDate, expected string
            { JAPANESE, Locale.JAPANESE, Locale.JAPANESE, JAPANESE.date(IsoDate),
              "平成25年2月11日月曜日" }, // Japanese Heisei 25-02-11
            { HIJRAH, ARABIC, ARABIC, HIJRAH.date(IsoDate),
              "الاثنين، 1 ربيع "
              + "الآخر 1434 هـ" }, // Hijrah AH 1434-04-01 (Mon)
            { MINGUO, Locale.TAIWAN, Locale.TAIWAN, MINGUO.date(IsoDate),
              "民國102年2月11日 星期一" }, // Minguo ROC 102-02-11 (Mon)
            { BUDDHIST, thTH, thTH, BUDDHIST.date(IsoDate),
              "วันจันทร์ที่"
              + " 11 กุมภาพันธ์"
              + " พ.ศ. 2556" }, // ThaiBuddhist BE 2556-02-11
            { BUDDHIST, thTH, thTHTH, BUDDHIST.date(IsoDate),
              "วันจันทร์ที่ ๑๑ "
              + "กุมภาพันธ์ พ.ศ. "
              + "๒๕๕๖" }, // ThaiBuddhist BE 2556-02-11 (with Thai digits)
        };
    }

    @DataProvider(name="invalid_text")
    Object[][] invalidText() {
        return new Object[][] {
            // TODO: currently fixed Chronology and Locale.
            // line commented out, as S64.01.09 seems like a reasonable thing to parse
            // (era "S" ended on S64.01.07, but a little leniency is a good thing
//            { "昭和64年1月9日月曜日" }, // S64.01.09 (Mon)
            { "昭和65年1月1日月曜日" }, // S65.01.01 (Mon)
        };
    }

    @DataProvider(name="chrono_names")
    Object[][] chronoNamesData() {
        return new Object[][] {
            // Chronology, Locale, Chronology Name
            { ISO8601,  Locale.ENGLISH, "ISO" },    // No data in CLDR; Use Id.
            { BUDDHIST, Locale.ENGLISH, "Buddhist Calendar" },
            { HIJRAH,   Locale.ENGLISH, "Hijri Calendar (Umm al-Qura)" },
            { JAPANESE, Locale.ENGLISH, "Japanese Calendar" },
            { MINGUO,   Locale.ENGLISH, "Minguo Calendar" },

            { ISO8601,  Locale.JAPANESE, "ISO" },    // No data in CLDR; Use Id.
            { JAPANESE, Locale.JAPANESE, "和暦" },
            { BUDDHIST, Locale.JAPANESE, "仏暦" },

            { ISO8601,  thTH, "ISO" },    // No data in CLDR; Use Id.
            { JAPANESE, thTH, "ปฏิทินญี่ปุ่น" },
            { BUDDHIST, thTH, "ปฏิทินพุทธ" },

            { HIJRAH,   ARABIC, "التقويم "
                                + "الهجري "
                                + "(أم القرى)" },
        };
    }

    @DataProvider(name="lenient_eraYear")
    Object[][] lenientEraYear() {
        return new Object[][] {
            // Chronology, lenient era/year, strict era/year
            { JAPANESE, "Meiji 123", "Heisei 2" },
            { JAPANESE, "Shōwa 65", "Heisei 2" },
            { JAPANESE, "Heisei 32", "Reiwa 2" },
        };
    }

    @Test(dataProvider="format_data")
    public void test_formatLocalizedDate(Chronology chrono, Locale formatLocale, Locale numberingLocale,
                                         ChronoLocalDate date, String expected) {
        DateTimeFormatter dtf = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withChronology(chrono).withLocale(formatLocale)
            .withDecimalStyle(DecimalStyle.of(numberingLocale));
        String text = dtf.format(date);
        assertEquals(text, expected);
    }

    @Test(dataProvider="format_data")
    public void test_parseLocalizedText(Chronology chrono, Locale formatLocale, Locale numberingLocale,
                                        ChronoLocalDate expected, String text) {
        DateTimeFormatter dtf = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withChronology(chrono).withLocale(formatLocale)
            .withDecimalStyle(DecimalStyle.of(numberingLocale));
        TemporalAccessor temporal = dtf.parse(text);
        ChronoLocalDate date = chrono.date(temporal);
        assertEquals(date, expected);
    }

    @Test(dataProvider="invalid_text", expectedExceptions=DateTimeParseException.class)
    public void test_parseInvalidText(String text) {
        DateTimeFormatter dtf = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withChronology(JAPANESE).withLocale(Locale.JAPANESE);
        dtf.parse(text);
    }

    @Test(dataProvider="chrono_names")
    public void test_chronoNames(Chronology chrono, Locale locale, String expected) {
        DateTimeFormatter dtf = new DateTimeFormatterBuilder().appendChronologyText(TextStyle.SHORT)
            .toFormatter(locale);
        String text = dtf.format(chrono.dateNow());
        assertEquals(text, expected);
        TemporalAccessor ta = dtf.parse(text);
        Chronology cal = ta.query(TemporalQueries.chronology());
        assertEquals(cal, chrono);
    }

    @Test(dataProvider="lenient_eraYear")
    public void test_lenientEraYear(Chronology chrono, String lenient, String strict) {
        String mdStr = "-01-01";
        DateTimeFormatter dtf = new DateTimeFormatterBuilder()
            .appendPattern("GGGG y-M-d")
            .toFormatter(Locale.ROOT)
            .withChronology(chrono);
        DateTimeFormatter dtfLenient = dtf.withResolverStyle(ResolverStyle.LENIENT);
        assertEquals(LocalDate.parse(lenient+mdStr, dtfLenient), LocalDate.parse(strict+mdStr, dtf));
    }
}
