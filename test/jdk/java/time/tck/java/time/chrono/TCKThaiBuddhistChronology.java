/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoField.YEAR_OF_ERA;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.ChronoPeriod;
import java.time.chrono.Chronology;
import java.time.chrono.Era;
import java.time.chrono.IsoChronology;
import java.time.chrono.MinguoChronology;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.chrono.ThaiBuddhistDate;
import java.time.chrono.ThaiBuddhistEra;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKThaiBuddhistChronology {

    private static final int YDIFF = 543;

    //-----------------------------------------------------------------------
    // Chronology.of(String)
    //-----------------------------------------------------------------------
    @Test
    public void test_chrono_byName() {
        Chronology c = ThaiBuddhistChronology.INSTANCE;
        Chronology test = Chronology.of("ThaiBuddhist");
        Assertions.assertNotNull(test, "The ThaiBuddhist calendar could not be found byName");
        assertEquals("ThaiBuddhist", test.getId(), "ID mismatch");
        assertEquals("buddhist", test.getCalendarType(), "Type mismatch");
        assertEquals(c, test);
    }

    //-----------------------------------------------------------------------
    // Chronology.ofLocale(Locale)
    //-----------------------------------------------------------------------
    @Test
    public void test_chrono_byLocale_fullTag_thaiCalendarFromThai() {
        Chronology test = Chronology.ofLocale(Locale.forLanguageTag("th-TH-u-ca-buddhist"));
        assertEquals("ThaiBuddhist", test.getId());
        assertEquals(ThaiBuddhistChronology.INSTANCE, test);
    }

    @Test
    public void test_chrono_byLocale_fullTag_thaiCalendarFromElsewhere() {
        Chronology test = Chronology.ofLocale(Locale.forLanguageTag("en-US-u-ca-buddhist"));
        assertEquals("ThaiBuddhist", test.getId());
        assertEquals(ThaiBuddhistChronology.INSTANCE, test);
    }

    @Test
    public void test_chrono_byLocale_oldTH_noVariant() {  // deliberately different to Calendar
        Chronology test = Chronology.ofLocale(Locale.of("th", "TH"));
        assertEquals("ISO", test.getId());
        assertEquals(IsoChronology.INSTANCE, test);
    }

    @Test
    public void test_chrono_byLocale_oldTH_variant() {
        Chronology test = Chronology.ofLocale(Locale.of("th", "TH", "TH"));
        assertEquals("ISO", test.getId());
        assertEquals(IsoChronology.INSTANCE, test);
    }

    @Test
    public void test_chrono_byLocale_iso() {
        assertEquals("ISO", Chronology.ofLocale(Locale.of("th", "TH")).getId());
        assertEquals("ISO", Chronology.ofLocale(Locale.forLanguageTag("th-TH")).getId());
        assertEquals("ISO", Chronology.ofLocale(Locale.forLanguageTag("th-TH-TH")).getId());
    }

    //-----------------------------------------------------------------------
    // creation, toLocalDate()
    //-----------------------------------------------------------------------
    Object[][] data_samples() {
        return new Object[][] {
            {ThaiBuddhistChronology.INSTANCE.date(1 + YDIFF, 1, 1), LocalDate.of(1, 1, 1)},
            {ThaiBuddhistChronology.INSTANCE.date(1 + YDIFF, 1, 2), LocalDate.of(1, 1, 2)},
            {ThaiBuddhistChronology.INSTANCE.date(1 + YDIFF, 1, 3), LocalDate.of(1, 1, 3)},

            {ThaiBuddhistChronology.INSTANCE.date(2 + YDIFF, 1, 1), LocalDate.of(2, 1, 1)},
            {ThaiBuddhistChronology.INSTANCE.date(3 + YDIFF, 1, 1), LocalDate.of(3, 1, 1)},
            {ThaiBuddhistChronology.INSTANCE.date(3 + YDIFF, 12, 6), LocalDate.of(3, 12, 6)},
            {ThaiBuddhistChronology.INSTANCE.date(4 + YDIFF, 1, 1), LocalDate.of(4, 1, 1)},
            {ThaiBuddhistChronology.INSTANCE.date(4 + YDIFF, 7, 3), LocalDate.of(4, 7, 3)},
            {ThaiBuddhistChronology.INSTANCE.date(4 + YDIFF, 7, 4), LocalDate.of(4, 7, 4)},
            {ThaiBuddhistChronology.INSTANCE.date(5 + YDIFF, 1, 1), LocalDate.of(5, 1, 1)},
            {ThaiBuddhistChronology.INSTANCE.date(1662 + YDIFF, 3, 3), LocalDate.of(1662, 3, 3)},
            {ThaiBuddhistChronology.INSTANCE.date(1728 + YDIFF, 10, 28), LocalDate.of(1728, 10, 28)},
            {ThaiBuddhistChronology.INSTANCE.date(1728 + YDIFF, 10, 29), LocalDate.of(1728, 10, 29)},
            {ThaiBuddhistChronology.INSTANCE.date(2555, 8, 29), LocalDate.of(2012, 8, 29)},

            {ThaiBuddhistChronology.INSTANCE.dateYearDay(4 + YDIFF, 60), LocalDate.of(4, 2, 29)},
            {ThaiBuddhistChronology.INSTANCE.dateYearDay(400 + YDIFF, 60), LocalDate.of(400, 2, 29)},
            {ThaiBuddhistChronology.INSTANCE.dateYearDay(2000 + YDIFF, 60), LocalDate.of(2000, 2, 29)},

            {ThaiBuddhistChronology.INSTANCE.dateYearDay(ThaiBuddhistEra.BE, 1916 + YDIFF, 60), LocalDate.of(1916, 2, 29)},
            {ThaiBuddhistChronology.INSTANCE.dateYearDay(ThaiBuddhistEra.BEFORE_BE, -1907 - YDIFF, 60), LocalDate.of(1908, 2, 29)},
            {ThaiBuddhistChronology.INSTANCE.dateYearDay(ThaiBuddhistEra.BE, 2000 + YDIFF, 60), LocalDate.of(2000, 2, 29)},
            {ThaiBuddhistChronology.INSTANCE.dateYearDay(ThaiBuddhistEra.BE, 2400 + YDIFF, 60), LocalDate.of(2400, 2, 29)},

            {ThaiBuddhistChronology.INSTANCE.date(ThaiBuddhistEra.BE, 1916 + YDIFF, 2, 29 ), LocalDate.of(1916, 2, 29)},
            {ThaiBuddhistChronology.INSTANCE.date(ThaiBuddhistEra.BEFORE_BE, -1907 - YDIFF, 2, 29), LocalDate.of(1908, 2, 29)},
            {ThaiBuddhistChronology.INSTANCE.date(ThaiBuddhistEra.BE, 2000 + YDIFF, 2, 29), LocalDate.of(2000, 2, 29)},
            {ThaiBuddhistChronology.INSTANCE.date(ThaiBuddhistEra.BE, 2400 + YDIFF, 2, 29), LocalDate.of(2400, 2, 29)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_samples")
    public void test_toLocalDate(ThaiBuddhistDate jdate, LocalDate iso) {
        assertEquals(iso, LocalDate.from(jdate));
    }

    @ParameterizedTest
    @MethodSource("data_samples")
    public void test_fromCalendrical(ThaiBuddhistDate jdate, LocalDate iso) {
        assertEquals(jdate, ThaiBuddhistChronology.INSTANCE.date(iso));
        assertEquals(jdate, ThaiBuddhistDate.from(iso));
    }

    @ParameterizedTest
    @MethodSource("data_samples")
    public void test_isEqual(ThaiBuddhistDate jdate, LocalDate iso) {
        assertTrue(jdate.isEqual(iso));
    }

    @ParameterizedTest
    @MethodSource("data_samples")
    public void test_date_equals(ThaiBuddhistDate jdate, LocalDate iso) {
        assertFalse(jdate.equals(iso));
        assertNotEquals(iso.hashCode(), jdate.hashCode());
    }

    @Test
    public void test_dateNow(){
        assertEquals(ThaiBuddhistDate.now(), ThaiBuddhistChronology.INSTANCE.dateNow()) ;
        assertEquals(ThaiBuddhistDate.now(ZoneId.systemDefault()), ThaiBuddhistChronology.INSTANCE.dateNow()) ;
        assertEquals(ThaiBuddhistDate.now(Clock.systemDefaultZone()), ThaiBuddhistChronology.INSTANCE.dateNow()) ;
        assertEquals(ThaiBuddhistDate.now(Clock.systemDefaultZone().getZone()), ThaiBuddhistChronology.INSTANCE.dateNow()) ;

        assertEquals(ThaiBuddhistChronology.INSTANCE.dateNow(ZoneId.systemDefault()), ThaiBuddhistChronology.INSTANCE.dateNow()) ;
        assertEquals(ThaiBuddhistChronology.INSTANCE.dateNow(Clock.systemDefaultZone()), ThaiBuddhistChronology.INSTANCE.dateNow()) ;
        assertEquals(ThaiBuddhistChronology.INSTANCE.dateNow(Clock.systemDefaultZone().getZone()), ThaiBuddhistChronology.INSTANCE.dateNow()) ;

        ZoneId zoneId = ZoneId.of("Europe/Paris");
        assertEquals(ThaiBuddhistChronology.INSTANCE.dateNow(Clock.system(zoneId)), ThaiBuddhistChronology.INSTANCE.dateNow(zoneId)) ;
        assertEquals(ThaiBuddhistChronology.INSTANCE.dateNow(Clock.system(zoneId).getZone()), ThaiBuddhistChronology.INSTANCE.dateNow(zoneId)) ;
        assertEquals(ThaiBuddhistDate.now(Clock.system(zoneId)), ThaiBuddhistChronology.INSTANCE.dateNow(zoneId)) ;
        assertEquals(ThaiBuddhistDate.now(Clock.system(zoneId).getZone()), ThaiBuddhistChronology.INSTANCE.dateNow(zoneId)) ;

        assertEquals(ThaiBuddhistChronology.INSTANCE.dateNow(Clock.systemUTC()), ThaiBuddhistChronology.INSTANCE.dateNow(ZoneId.of(ZoneOffset.UTC.getId()))) ;
    }

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

            {3 + YDIFF, 2, 29},
            {600 + YDIFF, 2, 29},
            {1501 + YDIFF, 2, 29},
        };
    }

    @ParameterizedTest
    @MethodSource("data_badDates")
    public void test_badDates(int year, int month, int dom) {
        Assertions.assertThrows(DateTimeException.class, () -> ThaiBuddhistChronology.INSTANCE.date(year, month, dom));
    }

    //-----------------------------------------------------------------------
    // prolepticYear() and is LeapYear()
    //-----------------------------------------------------------------------
    Object[][] data_prolepticYear() {
        return new Object[][] {
            {1, ThaiBuddhistEra.BE, 4 + YDIFF, 4 + YDIFF, true},
            {1, ThaiBuddhistEra.BE, 7 + YDIFF, 7 + YDIFF, false},
            {1, ThaiBuddhistEra.BE, 8 + YDIFF, 8 + YDIFF, true},
            {1, ThaiBuddhistEra.BE, 1000 + YDIFF, 1000 + YDIFF, false},
            {1, ThaiBuddhistEra.BE, 2000 + YDIFF, 2000 + YDIFF, true},
            {1, ThaiBuddhistEra.BE, 0, 0, false},
            {1, ThaiBuddhistEra.BE, -4 + YDIFF, -4 + YDIFF, true},
            {1, ThaiBuddhistEra.BE, -7 + YDIFF, -7 + YDIFF, false},
            {1, ThaiBuddhistEra.BE, -100 + YDIFF, -100 + YDIFF, false},
            {1, ThaiBuddhistEra.BE, -800 + YDIFF, -800 + YDIFF, true},

            {0, ThaiBuddhistEra.BEFORE_BE, -3 - YDIFF, 4 + YDIFF, true},
            {0, ThaiBuddhistEra.BEFORE_BE, -6 - YDIFF, 7 + YDIFF, false},
            {0, ThaiBuddhistEra.BEFORE_BE, -7 - YDIFF, 8 + YDIFF, true},
            {0, ThaiBuddhistEra.BEFORE_BE, -999 - YDIFF, 1000 + YDIFF, false},
            {0, ThaiBuddhistEra.BEFORE_BE, -1999 - YDIFF, 2000 + YDIFF, true},
            {0, ThaiBuddhistEra.BEFORE_BE, 1, 0, false},
            {0, ThaiBuddhistEra.BEFORE_BE, 5 - YDIFF, -4 + YDIFF, true},
            {0, ThaiBuddhistEra.BEFORE_BE, 8 - YDIFF, -7 + YDIFF, false},
            {0, ThaiBuddhistEra.BEFORE_BE, 101 - YDIFF, -100 + YDIFF, false},
            {0, ThaiBuddhistEra.BEFORE_BE, 801 - YDIFF, -800 + YDIFF, true},

        };
    }

    @ParameterizedTest
    @MethodSource("data_prolepticYear")
    public void test_prolepticYear(int eraValue, Era  era, int yearOfEra, int expectedProlepticYear, boolean isLeapYear) {
        Era eraObj = ThaiBuddhistChronology.INSTANCE.eraOf(eraValue);
        assertTrue(ThaiBuddhistChronology.INSTANCE.eras().contains(eraObj));
        assertEquals(era, eraObj);
        assertEquals(expectedProlepticYear, ThaiBuddhistChronology.INSTANCE.prolepticYear(era, yearOfEra));
    }

    @ParameterizedTest
    @MethodSource("data_prolepticYear")
    public void test_isLeapYear(int eraValue, Era  era, int yearOfEra, int expectedProlepticYear, boolean isLeapYear) {
        assertEquals(isLeapYear, ThaiBuddhistChronology.INSTANCE.isLeapYear(expectedProlepticYear)) ;
        assertEquals(Year.of(expectedProlepticYear - YDIFF).isLeap(), ThaiBuddhistChronology.INSTANCE.isLeapYear(expectedProlepticYear));

        ThaiBuddhistDate jdate = ThaiBuddhistDate.now();
        jdate = jdate.with(ChronoField.YEAR, expectedProlepticYear).with(ChronoField.MONTH_OF_YEAR, 2);
        if (isLeapYear) {
            assertEquals(29, jdate.lengthOfMonth());
        } else {
            assertEquals(28, jdate.lengthOfMonth());
        }
    }

    //-----------------------------------------------------------------------
    // Bad Era for Chronology.date(era,...) and Chronology.prolepticYear(Era,...)
    //-----------------------------------------------------------------------
    @Test
    public void test_InvalidEras() {
        // Verify that the eras from every other Chronology are invalid
        for (Chronology chrono : Chronology.getAvailableChronologies()) {
            if (chrono instanceof ThaiBuddhistChronology) {
                continue;
            }
            List<Era> eras = chrono.eras();
            for (Era era : eras) {
                try {
                    ChronoLocalDate date = ThaiBuddhistChronology.INSTANCE.date(era, 1, 1, 1);
                    fail("ThaiBuddhistChronology.date did not throw ClassCastException for Era: " + era);
                } catch (ClassCastException cex) {
                    ; // ignore expected exception
                }

                /* TODO: Test for missing ThaiBuddhistDate.of(Era, y, m, d) method.
                try {
                    @SuppressWarnings("unused")
                    ThaiBuddhistDate jdate = ThaiBuddhistDate.of(era, 1, 1, 1);
                    fail("ThaiBuddhistDate.of did not throw ClassCastException for Era: " + era);
                } catch (ClassCastException cex) {
                    ; // ignore expected exception
                }
                */

                try {
                    @SuppressWarnings("unused")
                    int year = ThaiBuddhistChronology.INSTANCE.prolepticYear(era, 1);
                    fail("ThaiBuddhistChronology.prolepticYear did not throw ClassCastException for Era: " + era);
                } catch (ClassCastException cex) {
                    ; // ignore expected exception
                }            }
        }
    }

    //-----------------------------------------------------------------------
    // with(WithAdjuster)
    //-----------------------------------------------------------------------
    @Test
    public void test_adjust1() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(1728, 10, 29);
        ThaiBuddhistDate test = base.with(TemporalAdjusters.lastDayOfMonth());
        assertEquals(ThaiBuddhistChronology.INSTANCE.date(1728, 10, 31), test);
    }

    @Test
    public void test_adjust2() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(1728, 12, 2);
        ThaiBuddhistDate test = base.with(TemporalAdjusters.lastDayOfMonth());
        assertEquals(ThaiBuddhistChronology.INSTANCE.date(1728, 12, 31), test);
    }

    //-----------------------------------------------------------------------
    // withYear()
    //-----------------------------------------------------------------------
    @Test
    public void test_withYear_BE() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(2555, 8, 29);
        ThaiBuddhistDate test = base.with(YEAR, 2554);
        assertEquals(ThaiBuddhistChronology.INSTANCE.date(2554, 8, 29), test);
    }

    @Test
    public void test_withYear_BBE() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(-2554, 8, 29);
        ThaiBuddhistDate test = base.with(YEAR_OF_ERA, 2554);
        assertEquals(ThaiBuddhistChronology.INSTANCE.date(-2553, 8, 29), test);
    }

    //-----------------------------------------------------------------------
    // withEra()
    //-----------------------------------------------------------------------
    @Test
    public void test_withEra_BE() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(2555, 8, 29);
        ThaiBuddhistDate test = base.with(ChronoField.ERA, ThaiBuddhistEra.BE.getValue());
        assertEquals(ThaiBuddhistChronology.INSTANCE.date(2555, 8, 29), test);
    }

    @Test
    public void test_withEra_BBE() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(-2554, 8, 29);
        ThaiBuddhistDate test = base.with(ChronoField.ERA, ThaiBuddhistEra.BEFORE_BE.getValue());
        assertEquals(ThaiBuddhistChronology.INSTANCE.date(-2554, 8, 29), test);
    }

    @Test
    public void test_withEra_swap() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(-2554, 8, 29);
        ThaiBuddhistDate test = base.with(ChronoField.ERA, ThaiBuddhistEra.BE.getValue());
        assertEquals(ThaiBuddhistChronology.INSTANCE.date(2555, 8, 29), test);
    }

    //-----------------------------------------------------------------------
    // BuddhistDate.with(Local*)
    //-----------------------------------------------------------------------
    @Test
    public void test_adjust_toLocalDate() {
        ThaiBuddhistDate jdate = ThaiBuddhistChronology.INSTANCE.date(1726, 1, 4);
        ThaiBuddhistDate test = jdate.with(LocalDate.of(2012, 7, 6));
        assertEquals(ThaiBuddhistChronology.INSTANCE.date(2555, 7, 6), test);
    }

    @Test
    public void test_adjust_toMonth() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            ThaiBuddhistDate jdate = ThaiBuddhistChronology.INSTANCE.date(1726, 1, 4);
            jdate.with(Month.APRIL);
        });
    }

    //-----------------------------------------------------------------------
    // LocalDate.with(BuddhistDate)
    //-----------------------------------------------------------------------
    @Test
    public void test_LocalDate_adjustToBuddhistDate() {
        ThaiBuddhistDate jdate = ThaiBuddhistChronology.INSTANCE.date(2555, 10, 29);
        LocalDate test = LocalDate.MIN.with(jdate);
        assertEquals(LocalDate.of(2012, 10, 29), test);
    }

    @Test
    public void test_LocalDateTime_adjustToBuddhistDate() {
        ThaiBuddhistDate jdate = ThaiBuddhistChronology.INSTANCE.date(2555, 10, 29);
        LocalDateTime test = LocalDateTime.MIN.with(jdate);
        assertEquals(LocalDateTime.of(2012, 10, 29, 0, 0), test);
    }

    //-----------------------------------------------------------------------
    // PeriodUntil()
    //-----------------------------------------------------------------------
    @Test
    public void test_periodUntilDate() {
        ThaiBuddhistDate mdate1 = ThaiBuddhistDate.of(1, 1, 1);
        ThaiBuddhistDate mdate2 = ThaiBuddhistDate.of(2, 2, 2);
        ChronoPeriod period = mdate1.until(mdate2);
        assertEquals(ThaiBuddhistChronology.INSTANCE.period(1, 1, 1), period);
    }

    @Test
    public void test_periodUntilUnit() {
        ThaiBuddhistDate mdate1 = ThaiBuddhistDate.of(1, 1, 1);
        ThaiBuddhistDate mdate2 = ThaiBuddhistDate.of(2, 2, 2);
        long months = mdate1.until(mdate2, ChronoUnit.MONTHS);
        assertEquals(13, months);
    }

    @Test
    public void test_periodUntilDiffChrono() {
        ThaiBuddhistDate mdate1 = ThaiBuddhistDate.of(1, 1, 1);
        ThaiBuddhistDate mdate2 = ThaiBuddhistDate.of(2, 2, 2);
        MinguoDate ldate2 = MinguoChronology.INSTANCE.date(mdate2);
        ChronoPeriod period = mdate1.until(ldate2);
        assertEquals(ThaiBuddhistChronology.INSTANCE.period(1, 1, 1), period);
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    Object[][] data_toString() {
        return new Object[][] {
            {ThaiBuddhistChronology.INSTANCE.date(544, 1, 1), "ThaiBuddhist BE 544-01-01"},
            {ThaiBuddhistChronology.INSTANCE.date(2271, 10, 28), "ThaiBuddhist BE 2271-10-28"},
            {ThaiBuddhistChronology.INSTANCE.date(2271, 10, 29), "ThaiBuddhist BE 2271-10-29"},
            {ThaiBuddhistChronology.INSTANCE.date(2270, 12, 5), "ThaiBuddhist BE 2270-12-05"},
            {ThaiBuddhistChronology.INSTANCE.date(2270, 12, 6), "ThaiBuddhist BE 2270-12-06"},
        };
    }

    @ParameterizedTest
    @MethodSource("data_toString")
    public void test_toString(ThaiBuddhistDate jdate, String expected) {
        assertEquals(expected, jdate.toString());
    }

    //-----------------------------------------------------------------------
    // chronology range(ChronoField)
    //-----------------------------------------------------------------------
    @Test
    public void test_Chrono_range() {
        long minYear = LocalDate.MIN.getYear() + YDIFF;
        long maxYear = LocalDate.MAX.getYear() + YDIFF;
        assertEquals(ValueRange.of(minYear, maxYear), ThaiBuddhistChronology.INSTANCE.range(YEAR));
        assertEquals(ValueRange.of(1, -minYear + 1, maxYear), ThaiBuddhistChronology.INSTANCE.range(YEAR_OF_ERA));

        assertEquals(DAY_OF_MONTH.range(), ThaiBuddhistChronology.INSTANCE.range(DAY_OF_MONTH));
        assertEquals(DAY_OF_YEAR.range(), ThaiBuddhistChronology.INSTANCE.range(DAY_OF_YEAR));
        assertEquals(MONTH_OF_YEAR.range(), ThaiBuddhistChronology.INSTANCE.range(MONTH_OF_YEAR));
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals_true() {
        assertTrue(ThaiBuddhistChronology.INSTANCE.equals(ThaiBuddhistChronology.INSTANCE));
    }

    @Test
    public void test_equals_false() {
        assertFalse(ThaiBuddhistChronology.INSTANCE.equals(IsoChronology.INSTANCE));
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] data_resolve_yearOfEra() {
        return new Object[][] {
                // era only
                {ResolverStyle.STRICT, -1, null, null, null, null},
                {ResolverStyle.SMART, -1, null, null, null, null},
                {ResolverStyle.LENIENT, -1, null, null, null, null},

                {ResolverStyle.STRICT, 0, null, null, ChronoField.ERA, 0},
                {ResolverStyle.SMART, 0, null, null, ChronoField.ERA, 0},
                {ResolverStyle.LENIENT, 0, null, null, ChronoField.ERA, 0},

                {ResolverStyle.STRICT, 1, null, null, ChronoField.ERA, 1},
                {ResolverStyle.SMART, 1, null, null, ChronoField.ERA, 1},
                {ResolverStyle.LENIENT, 1, null, null, ChronoField.ERA, 1},

                {ResolverStyle.STRICT, 2, null, null, null, null},
                {ResolverStyle.SMART, 2, null, null, null, null},
                {ResolverStyle.LENIENT, 2, null, null, null, null},

                // era and year-of-era
                {ResolverStyle.STRICT, -1, 2012, null, null, null},
                {ResolverStyle.SMART, -1, 2012, null, null, null},
                {ResolverStyle.LENIENT, -1, 2012, null, null, null},

                {ResolverStyle.STRICT, 0, 2012, null, ChronoField.YEAR, -2011},
                {ResolverStyle.SMART, 0, 2012, null, ChronoField.YEAR, -2011},
                {ResolverStyle.LENIENT, 0, 2012, null, ChronoField.YEAR, -2011},

                {ResolverStyle.STRICT, 1, 2012, null, ChronoField.YEAR, 2012},
                {ResolverStyle.SMART, 1, 2012, null, ChronoField.YEAR, 2012},
                {ResolverStyle.LENIENT, 1, 2012, null, ChronoField.YEAR, 2012},

                {ResolverStyle.STRICT, 2, 2012, null, null, null},
                {ResolverStyle.SMART, 2, 2012, null, null, null},
                {ResolverStyle.LENIENT, 2, 2012, null, null, null},

                // year-of-era only
                {ResolverStyle.STRICT, null, 2012, null, ChronoField.YEAR_OF_ERA, 2012},
                {ResolverStyle.SMART, null, 2012, null, ChronoField.YEAR, 2012},
                {ResolverStyle.LENIENT, null, 2012, null, ChronoField.YEAR, 2012},

                {ResolverStyle.STRICT, null, Integer.MAX_VALUE, null, null, null},
                {ResolverStyle.SMART, null, Integer.MAX_VALUE, null, null, null},
                {ResolverStyle.LENIENT, null, Integer.MAX_VALUE, null, ChronoField.YEAR, Integer.MAX_VALUE},

                // year-of-era and year
                {ResolverStyle.STRICT, null, 2012, 2012, ChronoField.YEAR, 2012},
                {ResolverStyle.SMART, null, 2012, 2012, ChronoField.YEAR, 2012},
                {ResolverStyle.LENIENT, null, 2012, 2012, ChronoField.YEAR, 2012},

                {ResolverStyle.STRICT, null, 2012, -2011, ChronoField.YEAR, -2011},
                {ResolverStyle.SMART, null, 2012, -2011, ChronoField.YEAR, -2011},
                {ResolverStyle.LENIENT, null, 2012, -2011, ChronoField.YEAR, -2011},

                {ResolverStyle.STRICT, null, 2012, 2013, null, null},
                {ResolverStyle.SMART, null, 2012, 2013, null, null},
                {ResolverStyle.LENIENT, null, 2012, 2013, null, null},

                {ResolverStyle.STRICT, null, 2012, -2013, null, null},
                {ResolverStyle.SMART, null, 2012, -2013, null, null},
                {ResolverStyle.LENIENT, null, 2012, -2013, null, null},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolve_yearOfEra")
    public void test_resolve_yearOfEra(ResolverStyle style, Integer e, Integer yoe, Integer y, ChronoField field, Integer expected) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        if (e != null) {
            fieldValues.put(ChronoField.ERA, (long) e);
        }
        if (yoe != null) {
            fieldValues.put(ChronoField.YEAR_OF_ERA, (long) yoe);
        }
        if (y != null) {
            fieldValues.put(ChronoField.YEAR, (long) y);
        }
        if (field != null) {
            ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, style);
            assertEquals(null, date);
            assertEquals((Long) expected.longValue(), fieldValues.get(field));
            assertEquals(1, fieldValues.size());
        } else {
            try {
                ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, style);
                fail("Should have failed");
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] data_resolve_ymd() {
        return new Object[][] {
                {YDIFF + 2012, 1, -365, date(YDIFF + 2010, 12, 31), false, false},
                {YDIFF + 2012, 1, -364, date(YDIFF + 2011, 1, 1), false, false},
                {YDIFF + 2012, 1, -31, date(YDIFF + 2011, 11, 30), false, false},
                {YDIFF + 2012, 1, -30, date(YDIFF + 2011, 12, 1), false, false},
                {YDIFF + 2012, 1, -12, date(YDIFF + 2011, 12, 19), false, false},
                {YDIFF + 2012, 1, 1, date(YDIFF + 2012, 1, 1), true, true},
                {YDIFF + 2012, 1, 27, date(YDIFF + 2012, 1, 27), true, true},
                {YDIFF + 2012, 1, 28, date(YDIFF + 2012, 1, 28), true, true},
                {YDIFF + 2012, 1, 29, date(YDIFF + 2012, 1, 29), true, true},
                {YDIFF + 2012, 1, 30, date(YDIFF + 2012, 1, 30), true, true},
                {YDIFF + 2012, 1, 31, date(YDIFF + 2012, 1, 31), true, true},
                {YDIFF + 2012, 1, 59, date(YDIFF + 2012, 2, 28), false, false},
                {YDIFF + 2012, 1, 60, date(YDIFF + 2012, 2, 29), false, false},
                {YDIFF + 2012, 1, 61, date(YDIFF + 2012, 3, 1), false, false},
                {YDIFF + 2012, 1, 365, date(YDIFF + 2012, 12, 30), false, false},
                {YDIFF + 2012, 1, 366, date(YDIFF + 2012, 12, 31), false, false},
                {YDIFF + 2012, 1, 367, date(YDIFF + 2013, 1, 1), false, false},
                {YDIFF + 2012, 1, 367 + 364, date(YDIFF + 2013, 12, 31), false, false},
                {YDIFF + 2012, 1, 367 + 365, date(YDIFF + 2014, 1, 1), false, false},

                {YDIFF + 2012, 2, 1, date(YDIFF + 2012, 2, 1), true, true},
                {YDIFF + 2012, 2, 28, date(YDIFF + 2012, 2, 28), true, true},
                {YDIFF + 2012, 2, 29, date(YDIFF + 2012, 2, 29), true, true},
                {YDIFF + 2012, 2, 30, date(YDIFF + 2012, 3, 1), date(YDIFF + 2012, 2, 29), false},
                {YDIFF + 2012, 2, 31, date(YDIFF + 2012, 3, 2), date(YDIFF + 2012, 2, 29), false},
                {YDIFF + 2012, 2, 32, date(YDIFF + 2012, 3, 3), false, false},

                {YDIFF + 2012, -12, 1, date(YDIFF + 2010, 12, 1), false, false},
                {YDIFF + 2012, -11, 1, date(YDIFF + 2011, 1, 1), false, false},
                {YDIFF + 2012, -1, 1, date(YDIFF + 2011, 11, 1), false, false},
                {YDIFF + 2012, 0, 1, date(YDIFF + 2011, 12, 1), false, false},
                {YDIFF + 2012, 1, 1, date(YDIFF + 2012, 1, 1), true, true},
                {YDIFF + 2012, 12, 1, date(YDIFF + 2012, 12, 1), true, true},
                {YDIFF + 2012, 13, 1, date(YDIFF + 2013, 1, 1), false, false},
                {YDIFF + 2012, 24, 1, date(YDIFF + 2013, 12, 1), false, false},
                {YDIFF + 2012, 25, 1, date(YDIFF + 2014, 1, 1), false, false},

                {YDIFF + 2012, 6, -31, date(YDIFF + 2012, 4, 30), false, false},
                {YDIFF + 2012, 6, -30, date(YDIFF + 2012, 5, 1), false, false},
                {YDIFF + 2012, 6, -1, date(YDIFF + 2012, 5, 30), false, false},
                {YDIFF + 2012, 6, 0, date(YDIFF + 2012, 5, 31), false, false},
                {YDIFF + 2012, 6, 1, date(YDIFF + 2012, 6, 1), true, true},
                {YDIFF + 2012, 6, 30, date(YDIFF + 2012, 6, 30), true, true},
                {YDIFF + 2012, 6, 31, date(YDIFF + 2012, 7, 1), date(YDIFF + 2012, 6, 30), false},
                {YDIFF + 2012, 6, 61, date(YDIFF + 2012, 7, 31), false, false},
                {YDIFF + 2012, 6, 62, date(YDIFF + 2012, 8, 1), false, false},

                {YDIFF + 2011, 2, 1, date(YDIFF + 2011, 2, 1), true, true},
                {YDIFF + 2011, 2, 28, date(YDIFF + 2011, 2, 28), true, true},
                {YDIFF + 2011, 2, 29, date(YDIFF + 2011, 3, 1), date(YDIFF + 2011, 2, 28), false},
                {YDIFF + 2011, 2, 30, date(YDIFF + 2011, 3, 2), date(YDIFF + 2011, 2, 28), false},
                {YDIFF + 2011, 2, 31, date(YDIFF + 2011, 3, 3), date(YDIFF + 2011, 2, 28), false},
                {YDIFF + 2011, 2, 32, date(YDIFF + 2011, 3, 4), false, false},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolve_ymd")
    public void test_resolve_ymd_lenient(int y, int m, int d, ThaiBuddhistDate expected, Object smart, boolean strict) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        fieldValues.put(ChronoField.YEAR, (long) y);
        fieldValues.put(ChronoField.MONTH_OF_YEAR, (long) m);
        fieldValues.put(ChronoField.DAY_OF_MONTH, (long) d);
        ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.LENIENT);
        assertEquals(expected, date);
        assertEquals(0, fieldValues.size());
    }

    @ParameterizedTest
    @MethodSource("data_resolve_ymd")
    public void test_resolve_ymd_smart(int y, int m, int d, ThaiBuddhistDate expected, Object smart, boolean strict) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        fieldValues.put(ChronoField.YEAR, (long) y);
        fieldValues.put(ChronoField.MONTH_OF_YEAR, (long) m);
        fieldValues.put(ChronoField.DAY_OF_MONTH, (long) d);
        if (Boolean.TRUE.equals(smart)) {
            ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.SMART);
            assertEquals(expected, date);
            assertEquals(0, fieldValues.size());
        } else if (smart instanceof ThaiBuddhistDate) {
            ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.SMART);
            assertEquals(smart, date);
        } else {
            try {
                ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.SMART);
                fail("Should have failed");
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    @ParameterizedTest
    @MethodSource("data_resolve_ymd")
    public void test_resolve_ymd_strict(int y, int m, int d, ThaiBuddhistDate expected, Object smart, boolean strict) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        fieldValues.put(ChronoField.YEAR, (long) y);
        fieldValues.put(ChronoField.MONTH_OF_YEAR, (long) m);
        fieldValues.put(ChronoField.DAY_OF_MONTH, (long) d);
        if (strict) {
            ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.STRICT);
            assertEquals(expected, date);
            assertEquals(0, fieldValues.size());
        } else {
            try {
                ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.STRICT);
                fail("Should have failed");
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] data_resolve_yd() {
        return new Object[][] {
                {YDIFF + 2012, -365, date(YDIFF + 2010, 12, 31), false, false},
                {YDIFF + 2012, -364, date(YDIFF + 2011, 1, 1), false, false},
                {YDIFF + 2012, -31, date(YDIFF + 2011, 11, 30), false, false},
                {YDIFF + 2012, -30, date(YDIFF + 2011, 12, 1), false, false},
                {YDIFF + 2012, -12, date(YDIFF + 2011, 12, 19), false, false},
                {YDIFF + 2012, -1, date(YDIFF + 2011, 12, 30), false, false},
                {YDIFF + 2012, 0, date(YDIFF + 2011, 12, 31), false, false},
                {YDIFF + 2012, 1, date(YDIFF + 2012, 1, 1), true, true},
                {YDIFF + 2012, 2, date(YDIFF + 2012, 1, 2), true, true},
                {YDIFF + 2012, 31, date(YDIFF + 2012, 1, 31), true, true},
                {YDIFF + 2012, 32, date(YDIFF + 2012, 2, 1), true, true},
                {YDIFF + 2012, 59, date(YDIFF + 2012, 2, 28), true, true},
                {YDIFF + 2012, 60, date(YDIFF + 2012, 2, 29), true, true},
                {YDIFF + 2012, 61, date(YDIFF + 2012, 3, 1), true, true},
                {YDIFF + 2012, 365, date(YDIFF + 2012, 12, 30), true, true},
                {YDIFF + 2012, 366, date(YDIFF + 2012, 12, 31), true, true},
                {YDIFF + 2012, 367, date(YDIFF + 2013, 1, 1), false, false},
                {YDIFF + 2012, 367 + 364, date(YDIFF + 2013, 12, 31), false, false},
                {YDIFF + 2012, 367 + 365, date(YDIFF + 2014, 1, 1), false, false},

                {YDIFF + 2011, 59, date(YDIFF + 2011, 2, 28), true, true},
                {YDIFF + 2011, 60, date(YDIFF + 2011, 3, 1), true, true},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolve_yd")
    public void test_resolve_yd_lenient(int y, int d, ThaiBuddhistDate expected, boolean smart, boolean strict) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        fieldValues.put(ChronoField.YEAR, (long) y);
        fieldValues.put(ChronoField.DAY_OF_YEAR, (long) d);
        ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.LENIENT);
        assertEquals(expected, date);
        assertEquals(0, fieldValues.size());
    }

    @ParameterizedTest
    @MethodSource("data_resolve_yd")
    public void test_resolve_yd_smart(int y, int d, ThaiBuddhistDate expected, boolean smart, boolean strict) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        fieldValues.put(ChronoField.YEAR, (long) y);
        fieldValues.put(ChronoField.DAY_OF_YEAR, (long) d);
        if (smart) {
            ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.SMART);
            assertEquals(expected, date);
            assertEquals(0, fieldValues.size());
        } else {
            try {
                ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.SMART);
                fail("Should have failed");
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    @ParameterizedTest
    @MethodSource("data_resolve_yd")
    public void test_resolve_yd_strict(int y, int d, ThaiBuddhistDate expected, boolean smart, boolean strict) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        fieldValues.put(ChronoField.YEAR, (long) y);
        fieldValues.put(ChronoField.DAY_OF_YEAR, (long) d);
        if (strict) {
            ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.STRICT);
            assertEquals(expected, date);
            assertEquals(0, fieldValues.size());
        } else {
            try {
                ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.STRICT);
                fail("Should have failed");
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] data_resolve_ymaa() {
        return new Object[][] {
                {YDIFF + 2012, 1, 1, -365, date(YDIFF + 2010, 12, 31), false, false},
                {YDIFF + 2012, 1, 1, -364, date(YDIFF + 2011, 1, 1), false, false},
                {YDIFF + 2012, 1, 1, -31, date(YDIFF + 2011, 11, 30), false, false},
                {YDIFF + 2012, 1, 1, -30, date(YDIFF + 2011, 12, 1), false, false},
                {YDIFF + 2012, 1, 1, -12, date(YDIFF + 2011, 12, 19), false, false},
                {YDIFF + 2012, 1, 1, 1, date(YDIFF + 2012, 1, 1), true, true},
                {YDIFF + 2012, 1, 1, 59, date(YDIFF + 2012, 2, 28), false, false},
                {YDIFF + 2012, 1, 1, 60, date(YDIFF + 2012, 2, 29), false, false},
                {YDIFF + 2012, 1, 1, 61, date(YDIFF + 2012, 3, 1), false, false},
                {YDIFF + 2012, 1, 1, 365, date(YDIFF + 2012, 12, 30), false, false},
                {YDIFF + 2012, 1, 1, 366, date(YDIFF + 2012, 12, 31), false, false},
                {YDIFF + 2012, 1, 1, 367, date(YDIFF + 2013, 1, 1), false, false},
                {YDIFF + 2012, 1, 1, 367 + 364, date(YDIFF + 2013, 12, 31), false, false},
                {YDIFF + 2012, 1, 1, 367 + 365, date(YDIFF + 2014, 1, 1), false, false},

                {YDIFF + 2012, 2, 0, 1, date(YDIFF + 2012, 1, 25), false, false},
                {YDIFF + 2012, 2, 0, 7, date(YDIFF + 2012, 1, 31), false, false},
                {YDIFF + 2012, 2, 1, 1, date(YDIFF + 2012, 2, 1), true, true},
                {YDIFF + 2012, 2, 1, 7, date(YDIFF + 2012, 2, 7), true, true},
                {YDIFF + 2012, 2, 2, 1, date(YDIFF + 2012, 2, 8), true, true},
                {YDIFF + 2012, 2, 2, 7, date(YDIFF + 2012, 2, 14), true, true},
                {YDIFF + 2012, 2, 3, 1, date(YDIFF + 2012, 2, 15), true, true},
                {YDIFF + 2012, 2, 3, 7, date(YDIFF + 2012, 2, 21), true, true},
                {YDIFF + 2012, 2, 4, 1, date(YDIFF + 2012, 2, 22), true, true},
                {YDIFF + 2012, 2, 4, 7, date(YDIFF + 2012, 2, 28), true, true},
                {YDIFF + 2012, 2, 5, 1, date(YDIFF + 2012, 2, 29), true, true},
                {YDIFF + 2012, 2, 5, 2, date(YDIFF + 2012, 3, 1), true, false},
                {YDIFF + 2012, 2, 5, 7, date(YDIFF + 2012, 3, 6), true, false},
                {YDIFF + 2012, 2, 6, 1, date(YDIFF + 2012, 3, 7), false, false},
                {YDIFF + 2012, 2, 6, 7, date(YDIFF + 2012, 3, 13), false, false},

                {YDIFF + 2012, 12, 1, 1, date(YDIFF + 2012, 12, 1), true, true},
                {YDIFF + 2012, 12, 5, 1, date(YDIFF + 2012, 12, 29), true, true},
                {YDIFF + 2012, 12, 5, 2, date(YDIFF + 2012, 12, 30), true, true},
                {YDIFF + 2012, 12, 5, 3, date(YDIFF + 2012, 12, 31), true, true},
                {YDIFF + 2012, 12, 5, 4, date(YDIFF + 2013, 1, 1), true, false},
                {YDIFF + 2012, 12, 5, 7, date(YDIFF + 2013, 1, 4), true, false},

                {YDIFF + 2012, -12, 1, 1, date(YDIFF + 2010, 12, 1), false, false},
                {YDIFF + 2012, -11, 1, 1, date(YDIFF + 2011, 1, 1), false, false},
                {YDIFF + 2012, -1, 1, 1, date(YDIFF + 2011, 11, 1), false, false},
                {YDIFF + 2012, 0, 1, 1, date(YDIFF + 2011, 12, 1), false, false},
                {YDIFF + 2012, 1, 1, 1, date(YDIFF + 2012, 1, 1), true, true},
                {YDIFF + 2012, 12, 1, 1, date(YDIFF + 2012, 12, 1), true, true},
                {YDIFF + 2012, 13, 1, 1, date(YDIFF + 2013, 1, 1), false, false},
                {YDIFF + 2012, 24, 1, 1, date(YDIFF + 2013, 12, 1), false, false},
                {YDIFF + 2012, 25, 1, 1, date(YDIFF + 2014, 1, 1), false, false},

                {YDIFF + 2011, 2, 1, 1, date(YDIFF + 2011, 2, 1), true, true},
                {YDIFF + 2011, 2, 4, 7, date(YDIFF + 2011, 2, 28), true, true},
                {YDIFF + 2011, 2, 5, 1, date(YDIFF + 2011, 3, 1), true, false},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolve_ymaa")
    public void test_resolve_ymaa_lenient(int y, int m, int w, int d, ThaiBuddhistDate expected, boolean smart, boolean strict) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        fieldValues.put(ChronoField.YEAR, (long) y);
        fieldValues.put(ChronoField.MONTH_OF_YEAR, (long) m);
        fieldValues.put(ChronoField.ALIGNED_WEEK_OF_MONTH, (long) w);
        fieldValues.put(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH, (long) d);
        ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.LENIENT);
        assertEquals(expected, date);
        assertEquals(0, fieldValues.size());
    }

    @ParameterizedTest
    @MethodSource("data_resolve_ymaa")
    public void test_resolve_ymaa_smart(int y, int m, int w, int d, ThaiBuddhistDate expected, boolean smart, boolean strict) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        fieldValues.put(ChronoField.YEAR, (long) y);
        fieldValues.put(ChronoField.MONTH_OF_YEAR, (long) m);
        fieldValues.put(ChronoField.ALIGNED_WEEK_OF_MONTH, (long) w);
        fieldValues.put(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH, (long) d);
        if (smart) {
            ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.SMART);
            assertEquals(expected, date);
            assertEquals(0, fieldValues.size());
        } else {
            try {
                ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.SMART);
                fail("Should have failed");
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    @ParameterizedTest
    @MethodSource("data_resolve_ymaa")
    public void test_resolve_ymaa_strict(int y, int m, int w, int d, ThaiBuddhistDate expected, boolean smart, boolean strict) {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        fieldValues.put(ChronoField.YEAR, (long) y);
        fieldValues.put(ChronoField.MONTH_OF_YEAR, (long) m);
        fieldValues.put(ChronoField.ALIGNED_WEEK_OF_MONTH, (long) w);
        fieldValues.put(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH, (long) d);
        if (strict) {
            ThaiBuddhistDate date = ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.STRICT);
            assertEquals(expected, date);
            assertEquals(0, fieldValues.size());
        } else {
            try {
                ThaiBuddhistChronology.INSTANCE.resolveDate(fieldValues, ResolverStyle.STRICT);
                fail("Should have failed");
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    //-----------------------------------------------------------------------
    private static ThaiBuddhistDate date(int y, int m, int d) {
        return ThaiBuddhistDate.of(y, m, d);
    }

}
