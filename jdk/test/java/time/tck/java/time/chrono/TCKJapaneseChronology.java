/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Copyright (c) 2008-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tck.java.time.chrono;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.ERA;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoField.YEAR_OF_ERA;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Period;
import java.time.Year;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.Era;
import java.time.chrono.IsoChronology;
import java.time.chrono.JapaneseChronology;
import java.time.chrono.JapaneseDate;
import java.time.chrono.JapaneseEra;
import java.time.chrono.MinguoChronology;
import java.time.chrono.MinguoDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjuster;
import java.util.List;
import java.util.Locale;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test.
 */
@Test
public class TCKJapaneseChronology {
    private static final int YDIFF_HEISEI = 1988;
    private static final int YDIFF_MEIJI = 1867;
    private static final int YDIFF_SHOWA = 1925;
    private static final int YDIFF_TAISHO = 1911;

    //-----------------------------------------------------------------------
    // Chronology.of(String)
    //-----------------------------------------------------------------------
    @Test
    public void test_chrono_byName() {
        Chronology c = JapaneseChronology.INSTANCE;
        Chronology test = Chronology.of("Japanese");
        Assert.assertNotNull(test, "The Japanese calendar could not be found byName");
        Assert.assertEquals(test.getId(), "Japanese", "ID mismatch");
        Assert.assertEquals(test.getCalendarType(), "japanese", "Type mismatch");
        Assert.assertEquals(test, c);
    }

    //-----------------------------------------------------------------------
    // Chronology.ofLocale(Locale)
    //-----------------------------------------------------------------------
    @Test
    public void test_chrono_byLocale_fullTag_japaneseCalendarFromJapan() {
        Chronology test = Chronology.ofLocale(Locale.forLanguageTag("ja-JP-u-ca-japanese"));
        Assert.assertEquals(test.getId(), "Japanese");
        Assert.assertEquals(test, JapaneseChronology.INSTANCE);
    }

    @Test
    public void test_chrono_byLocale_fullTag_japaneseCalendarFromElsewhere() {
        Chronology test = Chronology.ofLocale(Locale.forLanguageTag("en-US-u-ca-japanese"));
        Assert.assertEquals(test.getId(), "Japanese");
        Assert.assertEquals(test, JapaneseChronology.INSTANCE);
    }

    @Test
    public void test_chrono_byLocale_oldJP_noVariant() {
        Chronology test = Chronology.ofLocale(new Locale("ja", "JP"));
        Assert.assertEquals(test.getId(), "ISO");
        Assert.assertEquals(test, IsoChronology.INSTANCE);
    }

    @Test
    public void test_chrono_byLocale_oldJP_variant() {
        Chronology test = Chronology.ofLocale(new Locale("ja", "JP", "JP"));
        Assert.assertEquals(test.getId(), "Japanese");
        Assert.assertEquals(test, JapaneseChronology.INSTANCE);
    }

    @Test
    public void test_chrono_byLocale_iso() {
        Assert.assertEquals(Chronology.ofLocale(new Locale("ja", "JP")).getId(), "ISO");
        Assert.assertEquals(Chronology.ofLocale(Locale.forLanguageTag("ja-JP")).getId(), "ISO");
        Assert.assertEquals(Chronology.ofLocale(Locale.forLanguageTag("ja-JP-JP")).getId(), "ISO");
    }

    //-----------------------------------------------------------------------
    // creation, toLocalDate()
    //-----------------------------------------------------------------------
    @DataProvider(name="samples")
    Object[][] data_samples() {
        return new Object[][] {
            {JapaneseChronology.INSTANCE.date(1, 1, 1), LocalDate.of(1, 1, 1)},
            {JapaneseChronology.INSTANCE.date(1, 1, 2), LocalDate.of(1, 1, 2)},
            {JapaneseChronology.INSTANCE.date(1, 1, 3), LocalDate.of(1, 1, 3)},

            {JapaneseChronology.INSTANCE.date(2, 1, 1), LocalDate.of(2, 1, 1)},
            {JapaneseChronology.INSTANCE.date(3, 1, 1), LocalDate.of(3, 1, 1)},
            {JapaneseChronology.INSTANCE.date(3, 12, 6), LocalDate.of(3, 12, 6)},
            {JapaneseChronology.INSTANCE.date(4, 1, 1), LocalDate.of(4, 1, 1)},
            {JapaneseChronology.INSTANCE.date(4, 7, 3), LocalDate.of(4, 7, 3)},
            {JapaneseChronology.INSTANCE.date(4, 7, 4), LocalDate.of(4, 7, 4)},
            {JapaneseChronology.INSTANCE.date(5, 1, 1), LocalDate.of(5, 1, 1)},
            {JapaneseChronology.INSTANCE.date(1662, 3, 3), LocalDate.of(1662, 3, 3)},
            {JapaneseChronology.INSTANCE.date(1728, 10, 28), LocalDate.of(1728, 10, 28)},
            {JapaneseChronology.INSTANCE.date(1728, 10, 29), LocalDate.of(1728, 10, 29)},

            {JapaneseChronology.INSTANCE.date(JapaneseEra.HEISEI, 1996 - YDIFF_HEISEI, 2, 29), LocalDate.of(1996, 2, 29)},
            {JapaneseChronology.INSTANCE.date(JapaneseEra.HEISEI, 2000 - YDIFF_HEISEI, 2, 29), LocalDate.of(2000, 2, 29)},
            {JapaneseChronology.INSTANCE.date(JapaneseEra.MEIJI, 1868 - YDIFF_MEIJI, 2, 29), LocalDate.of(1868, 2, 29)},
            {JapaneseChronology.INSTANCE.date(JapaneseEra.SHOWA, 1928 - YDIFF_SHOWA, 12, 25), LocalDate.of(1928, 12, 25)},
            {JapaneseChronology.INSTANCE.date(JapaneseEra.TAISHO, 1912 - YDIFF_TAISHO, 7, 30), LocalDate.of(1912, 7, 30)},

            {JapaneseChronology.INSTANCE.dateYearDay(1996, 60), LocalDate.of(1996, 2, 29)},
            {JapaneseChronology.INSTANCE.dateYearDay(1868, 60), LocalDate.of(1868, 2, 29)},
            {JapaneseChronology.INSTANCE.dateYearDay(1928, 60), LocalDate.of(1928, 2, 29)},
            {JapaneseChronology.INSTANCE.dateYearDay(1912, 60), LocalDate.of(1912, 2, 29)},
        };
    }

    @Test(dataProvider="samples")
    public void test_toLocalDate(JapaneseDate jdate, LocalDate iso) {
        assertEquals(LocalDate.from(jdate), iso);
    }

    @Test(dataProvider="samples")
    public void test_fromCalendrical(JapaneseDate jdate, LocalDate iso) {
        assertEquals(JapaneseChronology.INSTANCE.date(iso), jdate);
    }

    @DataProvider(name="badDates")
    Object[][] data_badDates() {
        return new Object[][] {
            {1728, 0, 0},

            {1728, -1, 1},
            {1728, 0, 1},
            {1728, 14, 1},
            {1728, 15, 1},

            {1728, 1, -1},
            {1728, 1, 0},
            {1728, 1, 32},

            {1728, 12, -1},
            {1728, 12, 0},
            {1728, 12, 32},

            {1725, 2, 29},
            {500, 2, 29},
            {2100, 2, 29},
        };
    }

    @Test(dataProvider="badDates", expectedExceptions=DateTimeException.class)
    public void test_badDates(int year, int month, int dom) {
        JapaneseChronology.INSTANCE.date(year, month, dom);
    }

    //-----------------------------------------------------------------------
    // prolepticYear() and is LeapYear()
    //-----------------------------------------------------------------------
    @DataProvider(name="prolepticYear")
    Object[][] data_prolepticYear() {
        return new Object[][] {
                {2, JapaneseEra.HEISEI, 1, 1 + YDIFF_HEISEI, false},
                {2, JapaneseEra.HEISEI, 100, 100 + YDIFF_HEISEI, true},
                {2, JapaneseEra.HEISEI, 0, YDIFF_HEISEI, true},
                {2, JapaneseEra.HEISEI, -10, -10 + YDIFF_HEISEI, false},

                {-1, JapaneseEra.MEIJI, 1, 1 + YDIFF_MEIJI, true},
                {-1, JapaneseEra.MEIJI, 4, 4 + YDIFF_MEIJI, false},

                {1, JapaneseEra.SHOWA, 1, 1 + YDIFF_SHOWA, false},
                {1, JapaneseEra.SHOWA, 7, 7 + YDIFF_SHOWA, true},

                {0, JapaneseEra.TAISHO, 1, 1 + YDIFF_TAISHO, true},
                {0, JapaneseEra.TAISHO, 4, 4 + YDIFF_TAISHO, false},
        };
    }

    @Test(dataProvider="prolepticYear")
    public void test_prolepticYear(int eraValue, Era  era, int yearOfEra, int expectedProlepticYear, boolean isLeapYear) {
        Era eraObj = JapaneseChronology.INSTANCE.eraOf(eraValue) ;
        assertTrue(JapaneseChronology.INSTANCE.eras().contains(eraObj));
        assertEquals(eraObj, era);
        assertEquals(JapaneseChronology.INSTANCE.prolepticYear(era, yearOfEra), expectedProlepticYear);
        assertEquals(JapaneseChronology.INSTANCE.isLeapYear(expectedProlepticYear), isLeapYear) ;
        assertEquals(JapaneseChronology.INSTANCE.isLeapYear(expectedProlepticYear), Year.of(expectedProlepticYear).isLeap()) ;
    }

    @DataProvider(name="prolepticYearError")
    Object[][] data_prolepticYearError() {
        return new Object[][] {
                {JapaneseEra.MEIJI, 100},
                {JapaneseEra.MEIJI, 0},
                {JapaneseEra.MEIJI, -10},

                {JapaneseEra.SHOWA, 100},
                {JapaneseEra.SHOWA, 0},
                {JapaneseEra.SHOWA, -10},

                {JapaneseEra.TAISHO, 100},
                {JapaneseEra.TAISHO, 0},
                {JapaneseEra.TAISHO, -10},
        };
    }

    @Test(dataProvider="prolepticYearError", expectedExceptions=DateTimeException.class)
    public void test_prolepticYearError(Era era, int yearOfEra) {
        JapaneseChronology.INSTANCE.prolepticYear(era, yearOfEra);
    }

    //-----------------------------------------------------------------------
    // Bad Era for Chronology.date(era,...) and Chronology.prolepticYear(Era,...)
    //-----------------------------------------------------------------------
    @Test
    public void test_InvalidEras() {
        // Verify that the eras from every other Chronology are invalid
        for (Chronology chrono : Chronology.getAvailableChronologies()) {
            if (chrono instanceof JapaneseChronology) {
                continue;
            }
            List<Era> eras = chrono.eras();
            for (Era era : eras) {
                try {
                    ChronoLocalDate date = JapaneseChronology.INSTANCE.date(era, 1, 1, 1);
                    fail("JapaneseChronology.date did not throw ClassCastException for Era: " + era);
                } catch (ClassCastException cex) {
                    ; // ignore expected exception
                }

                try {
                    @SuppressWarnings("unused")
                    JapaneseDate jdate = JapaneseDate.of(era, 1, 1, 1);
                    fail("JapaneseDate.of did not throw ClassCastException for Era: " + era);
                } catch (ClassCastException cex) {
                    ; // ignore expected exception
                }

                try {
                    @SuppressWarnings("unused")
                    int year = JapaneseChronology.INSTANCE.prolepticYear(era, 1);
                    fail("JapaneseChronology.prolepticYear did not throw ClassCastException for Era: " + era);
                } catch (ClassCastException cex) {
                    ; // ignore expected exception
                }

            }
        }
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_getLong() {
        JapaneseDate base = JapaneseChronology.INSTANCE.date(JapaneseEra.SHOWA, 63, 6, 30);
        assertEquals(base.getLong(ERA), JapaneseEra.SHOWA.getValue());
        assertEquals(base.getLong(YEAR), 1988L);
        assertEquals(base.getLong(YEAR_OF_ERA), 63L);
        assertEquals(base.getLong(MONTH_OF_YEAR), 6L);
        assertEquals(base.getLong(DAY_OF_MONTH), 30L);
    }

    //-----------------------------------------------------------------------
    // with(TemporalField, long)
    //-----------------------------------------------------------------------
    @Test
    public void test_with_TemporalField_long() {
        JapaneseDate base = JapaneseChronology.INSTANCE.date(JapaneseEra.SHOWA, 63, 6, 30);
        JapaneseDate test = base.with(YEAR, 1987);
        assertEquals(test, JapaneseChronology.INSTANCE.date(JapaneseEra.SHOWA, 62, 6, 30));

        test = test.with(YEAR_OF_ERA, 2);
        assertEquals(test, JapaneseChronology.INSTANCE.date(JapaneseEra.SHOWA, 2, 6, 30));

        test = test.with(ERA, JapaneseEra.HEISEI.getValue());
        assertEquals(test, JapaneseChronology.INSTANCE.date(JapaneseEra.HEISEI, 2, 6, 30));

        test = test.with(MONTH_OF_YEAR, 3);
        assertEquals(test, JapaneseChronology.INSTANCE.date(JapaneseEra.HEISEI, 2, 3, 30));

        test = test.with(DAY_OF_MONTH, 4);
        assertEquals(test, JapaneseChronology.INSTANCE.date(JapaneseEra.HEISEI, 2, 3, 4));
    }

    //-----------------------------------------------------------------------
    // with(WithAdjuster)
    //-----------------------------------------------------------------------
    @Test
    public void test_adjust1() {
        JapaneseDate base = JapaneseChronology.INSTANCE.date(1728, 10, 29);
        JapaneseDate test = base.with(TemporalAdjuster.lastDayOfMonth());
        assertEquals(test, JapaneseChronology.INSTANCE.date(1728, 10, 31));
    }

    @Test
    public void test_adjust2() {
        JapaneseDate base = JapaneseChronology.INSTANCE.date(1728, 12, 2);
        JapaneseDate test = base.with(TemporalAdjuster.lastDayOfMonth());
        assertEquals(test, JapaneseChronology.INSTANCE.date(1728, 12, 31));
    }

    //-----------------------------------------------------------------------
    // JapaneseDate.with(Local*)
    //-----------------------------------------------------------------------
    @Test
    public void test_adjust_toLocalDate() {
        JapaneseDate jdate = JapaneseChronology.INSTANCE.date(1726, 1, 4);
        JapaneseDate test = jdate.with(LocalDate.of(2012, 7, 6));
        assertEquals(test, JapaneseChronology.INSTANCE.date(2012, 7, 6));
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void test_adjust_toMonth() {
        JapaneseDate jdate = JapaneseChronology.INSTANCE.date(1726, 1, 4);
        jdate.with(Month.APRIL);
    }

    //-----------------------------------------------------------------------
    // LocalDate.with(JapaneseDate)
    //-----------------------------------------------------------------------
    @Test
    public void test_LocalDate_adjustToJapaneseDate() {
        JapaneseDate jdate = JapaneseChronology.INSTANCE.date(1728, 10, 29);
        LocalDate test = LocalDate.MIN.with(jdate);
        assertEquals(test, LocalDate.of(1728, 10, 29));
    }

    @Test
    public void test_LocalDateTime_adjustToJapaneseDate() {
        JapaneseDate jdate = JapaneseChronology.INSTANCE.date(1728, 10, 29);
        LocalDateTime test = LocalDateTime.MIN.with(jdate);
        assertEquals(test, LocalDateTime.of(1728, 10, 29, 0, 0));
    }

    //-----------------------------------------------------------------------
    // Check Japanese Eras
    //-----------------------------------------------------------------------
    @DataProvider(name="japaneseEras")
    Object[][] data_japanseseEras() {
        return new Object[][] {
            { JapaneseEra.SEIREKI, -999, "Seireki"},
            { JapaneseEra.MEIJI, -1, "Meiji"},
            { JapaneseEra.TAISHO, 0, "Taisho"},
            { JapaneseEra.SHOWA, 1, "Showa"},
            { JapaneseEra.HEISEI, 2, "Heisei"},
        };
    }

    @Test(dataProvider="japaneseEras")
    public void test_Japanese_Eras(Era era, int eraValue, String name) {
        assertEquals(era.getValue(), eraValue, "EraValue");
        assertEquals(era.toString(), name, "Era Name");
        assertEquals(era, JapaneseChronology.INSTANCE.eraOf(eraValue), "JapaneseChronology.eraOf()");
        List<Era> eras = JapaneseChronology.INSTANCE.eras();
        assertTrue(eras.contains(era), "Era is not present in JapaneseChronology.INSTANCE.eras()");
    }

    @Test
    public void test_Japanese_badEras() {
        int badEras[] = {-1000, -998, -997, -2, 3, 4, 1000};
        for (int badEra : badEras) {
            try {
                Era era = JapaneseChronology.INSTANCE.eraOf(badEra);
                fail("JapaneseChronology.eraOf returned " + era + " + for invalid eraValue " + badEra);
            } catch (DateTimeException ex) {
                // ignore expected exception
            }
        }
    }

    @Test(dataProvider="japaneseEras")
    public void test_JapaneseEra_singletons(Era expectedEra, int eraValue, String name) {
        JapaneseEra actualEra = JapaneseEra.valueOf(name);
        assertEquals(actualEra, expectedEra, "JapaneseEra.valueOf(name)");

        actualEra = JapaneseEra.of(eraValue);
        assertEquals(actualEra, expectedEra, "JapaneseEra.of(value)");

        String string = actualEra.toString();
        assertEquals(string, name, "JapaneseEra.toString()");
    }

    @Test
    public void test_JapaneseEra_values() {
        JapaneseEra[] actualEras = JapaneseEra.values();
        Object[][] erasInfo = data_japanseseEras();
        assertEquals(actualEras.length, erasInfo.length, "Wrong number of Eras");

        for (int i = 0; i < erasInfo.length; i++) {
            Object[] eraInfo = erasInfo[i];
            assertEquals(actualEras[i], eraInfo[0], "Singleton mismatch");
        }
    }

    @Test
    public void test_JapaneseChronology_eras() {
        List<Era> actualEras = JapaneseChronology.INSTANCE.eras();
        Object[][] erasInfo = data_japanseseEras();
        assertEquals(actualEras.size(), erasInfo.length, "Wrong number of Eras");

        for (int i = 0; i < erasInfo.length; i++) {
            Object[] eraInfo = erasInfo[i];
            assertEquals(actualEras.get(i), eraInfo[0], "Singleton mismatch");
        }
    }

    //-----------------------------------------------------------------------
    // PeriodUntil()
    //-----------------------------------------------------------------------
    @Test
    public void test_periodUntilDate() {
        JapaneseDate mdate1 = JapaneseDate.of(1970, 1, 1);
        JapaneseDate mdate2 = JapaneseDate.of(1971, 2, 2);
        Period period = mdate1.periodUntil(mdate2);
        assertEquals(period, Period.of(1, 1, 1));
    }

    @Test
    public void test_periodUntilUnit() {
        JapaneseDate mdate1 = JapaneseDate.of(1970, 1, 1);
        JapaneseDate mdate2 = JapaneseDate.of(1971, 2, 2);
        long months = mdate1.periodUntil(mdate2, ChronoUnit.MONTHS);
        assertEquals(months, 13);
    }

    @Test
    public void test_periodUntilDiffChrono() {
        JapaneseDate mdate1 = JapaneseDate.of(1970, 1, 1);
        JapaneseDate mdate2 = JapaneseDate.of(1971, 2, 2);
        MinguoDate ldate2 = MinguoChronology.INSTANCE.date(mdate2);
        Period period = mdate1.periodUntil(ldate2);
        assertEquals(period, Period.of(1, 1, 1));
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @DataProvider(name="toString")
    Object[][] data_toString() {
        return new Object[][] {
            {JapaneseChronology.INSTANCE.date(0001,  1,  1), "Japanese 0001-01-01"},
            {JapaneseChronology.INSTANCE.date(1728, 10, 28), "Japanese 1728-10-28"},
            {JapaneseChronology.INSTANCE.date(1728, 10, 29), "Japanese 1728-10-29"},
            {JapaneseChronology.INSTANCE.date(1727, 12,  5), "Japanese 1727-12-05"},
            {JapaneseChronology.INSTANCE.date(1727, 12,  6), "Japanese 1727-12-06"},
            {JapaneseChronology.INSTANCE.date(1868,  9,  8), "Japanese Meiji 1-09-08"},
            {JapaneseChronology.INSTANCE.date(1912,  7, 29), "Japanese Meiji 45-07-29"},
            {JapaneseChronology.INSTANCE.date(1912,  7, 30), "Japanese Taisho 1-07-30"},
            {JapaneseChronology.INSTANCE.date(1926, 12, 24), "Japanese Taisho 15-12-24"},
            {JapaneseChronology.INSTANCE.date(1926, 12, 25), "Japanese Showa 1-12-25"},
            {JapaneseChronology.INSTANCE.date(1989,  1,  7), "Japanese Showa 64-01-07"},
            {JapaneseChronology.INSTANCE.date(1989,  1,  8), "Japanese Heisei 1-01-08"},
            {JapaneseChronology.INSTANCE.date(2012, 12,  6), "Japanese Heisei 24-12-06"},
        };
    }

    @Test(dataProvider="toString")
    public void test_toString(JapaneseDate jdate, String expected) {
        assertEquals(jdate.toString(), expected);
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals_true() {
        assertTrue(JapaneseChronology.INSTANCE.equals(JapaneseChronology.INSTANCE));
    }

    @Test
    public void test_equals_false() {
        assertFalse(JapaneseChronology.INSTANCE.equals(IsoChronology.INSTANCE));
    }

}
