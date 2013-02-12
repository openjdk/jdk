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
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoField.YEAR_OF_ERA;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.chrono.ThaiBuddhistDate;
import java.time.chrono.Chronology;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Era;
import java.time.chrono.IsoChronology;
import java.time.temporal.Adjusters;
import java.time.temporal.ChronoField;
import java.time.temporal.ValueRange;
import java.util.Locale;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test.
 */
@Test
public class TestThaiBuddhistChronology {

    private static final int YDIFF = 543;

    //-----------------------------------------------------------------------
    // Chronology.of(String)
    //-----------------------------------------------------------------------
    @Test
    public void test_chrono_byName() {
        Chronology c = ThaiBuddhistChronology.INSTANCE;
        Chronology test = Chronology.of("ThaiBuddhist");
        Assert.assertNotNull(test, "The ThaiBuddhist calendar could not be found byName");
        Assert.assertEquals(test.getId(), "ThaiBuddhist", "ID mismatch");
        Assert.assertEquals(test.getCalendarType(), "buddhist", "Type mismatch");
        Assert.assertEquals(test, c);
    }

    //-----------------------------------------------------------------------
    // Chronology.ofLocale(Locale)
    //-----------------------------------------------------------------------
    @Test
    public void test_chrono_byLocale_fullTag_thaiCalendarFromThai() {
        Chronology test = Chronology.ofLocale(Locale.forLanguageTag("th-TH-u-ca-buddhist"));
        Assert.assertEquals(test.getId(), "ThaiBuddhist");
        Assert.assertEquals(test, ThaiBuddhistChronology.INSTANCE);
    }

    @Test
    public void test_chrono_byLocale_fullTag_thaiCalendarFromElsewhere() {
        Chronology test = Chronology.ofLocale(Locale.forLanguageTag("en-US-u-ca-buddhist"));
        Assert.assertEquals(test.getId(), "ThaiBuddhist");
        Assert.assertEquals(test, ThaiBuddhistChronology.INSTANCE);
    }

    @Test
    public void test_chrono_byLocale_oldTH_noVariant() {  // deliberately different to Calendar
        Chronology test = Chronology.ofLocale(new Locale("th", "TH"));
        Assert.assertEquals(test.getId(), "ISO");
        Assert.assertEquals(test, IsoChronology.INSTANCE);
    }

    @Test
    public void test_chrono_byLocale_oldTH_variant() {
        Chronology test = Chronology.ofLocale(new Locale("th", "TH", "TH"));
        Assert.assertEquals(test.getId(), "ISO");
        Assert.assertEquals(test, IsoChronology.INSTANCE);
    }

    @Test
    public void test_chrono_byLocale_iso() {
        Assert.assertEquals(Chronology.ofLocale(new Locale("th", "TH")).getId(), "ISO");
        Assert.assertEquals(Chronology.ofLocale(Locale.forLanguageTag("th-TH")).getId(), "ISO");
        Assert.assertEquals(Chronology.ofLocale(Locale.forLanguageTag("th-TH-TH")).getId(), "ISO");
    }

    //-----------------------------------------------------------------------
    // creation, toLocalDate()
    //-----------------------------------------------------------------------
    @DataProvider(name="samples")
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

        };
    }

    @Test(dataProvider="samples", groups={"tck"})
    public void test_toLocalDate(ThaiBuddhistDate jdate, LocalDate iso) {
        assertEquals(LocalDate.from(jdate), iso);
    }

    @Test(dataProvider="samples", groups={"tck"})
    public void test_fromCalendrical(ThaiBuddhistDate jdate, LocalDate iso) {
        assertEquals(ThaiBuddhistChronology.INSTANCE.date(iso), jdate);
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

            {3 + YDIFF, 2, 29},
            {600 + YDIFF, 2, 29},
            {1501 + YDIFF, 2, 29},
        };
    }

    @Test(dataProvider="badDates", groups={"tck"}, expectedExceptions=DateTimeException.class)
    public void test_badDates(int year, int month, int dom) {
        ThaiBuddhistChronology.INSTANCE.date(year, month, dom);
    }

  //-----------------------------------------------------------------------
  // prolepticYear() and is LeapYear()
  //-----------------------------------------------------------------------
  @DataProvider(name="prolepticYear")
  Object[][] data_prolepticYear() {
      return new Object[][] {
          {1, ThaiBuddhistChronology.ERA_BE, 4 + YDIFF, 4 + YDIFF, true},
          {1, ThaiBuddhistChronology.ERA_BE, 7 + YDIFF, 7 + YDIFF, false},
          {1, ThaiBuddhistChronology.ERA_BE, 8 + YDIFF, 8 + YDIFF, true},
          {1, ThaiBuddhistChronology.ERA_BE, 1000 + YDIFF, 1000 + YDIFF, false},
          {1, ThaiBuddhistChronology.ERA_BE, 2000 + YDIFF, 2000 + YDIFF, true},
          {1, ThaiBuddhistChronology.ERA_BE, 0, 0, false},
          {1, ThaiBuddhistChronology.ERA_BE, -4 + YDIFF, -4 + YDIFF, true},
          {1, ThaiBuddhistChronology.ERA_BE, -7 + YDIFF, -7 + YDIFF, false},
          {1, ThaiBuddhistChronology.ERA_BE, -100 + YDIFF, -100 + YDIFF, false},
          {1, ThaiBuddhistChronology.ERA_BE, -800 + YDIFF, -800 + YDIFF, true},

          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, -3 - YDIFF, 4 + YDIFF, true},
          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, -6 - YDIFF, 7 + YDIFF, false},
          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, -7 - YDIFF, 8 + YDIFF, true},
          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, -999 - YDIFF, 1000 + YDIFF, false},
          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, -1999 - YDIFF, 2000 + YDIFF, true},
          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, 1, 0, false},
          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, 5 - YDIFF, -4 + YDIFF, true},
          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, 8 - YDIFF, -7 + YDIFF, false},
          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, 101 - YDIFF, -100 + YDIFF, false},
          {0, ThaiBuddhistChronology.ERA_BEFORE_BE, 801 - YDIFF, -800 + YDIFF, true},

      };
  }

  @Test(dataProvider="prolepticYear", groups={"tck"})
  public void test_prolepticYear(int eraValue, Era  era, int yearOfEra, int expectedProlepticYear, boolean isLeapYear) {
      Era eraObj = ThaiBuddhistChronology.INSTANCE.eraOf(eraValue) ;
      assertTrue(ThaiBuddhistChronology.INSTANCE.eras().contains(eraObj));
      assertEquals(eraObj, era);
      assertEquals(ThaiBuddhistChronology.INSTANCE.prolepticYear(era, yearOfEra), expectedProlepticYear);
      assertEquals(ThaiBuddhistChronology.INSTANCE.isLeapYear(expectedProlepticYear), isLeapYear) ;
      assertEquals(ThaiBuddhistChronology.INSTANCE.isLeapYear(expectedProlepticYear), Year.of(expectedProlepticYear - YDIFF).isLeap()) ;
  }

    //-----------------------------------------------------------------------
    // with(WithAdjuster)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_adjust1() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(1728, 10, 29);
        ThaiBuddhistDate test = base.with(Adjusters.lastDayOfMonth());
        assertEquals(test, ThaiBuddhistChronology.INSTANCE.date(1728, 10, 31));
    }

    @Test(groups={"tck"})
    public void test_adjust2() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(1728, 12, 2);
        ThaiBuddhistDate test = base.with(Adjusters.lastDayOfMonth());
        assertEquals(test, ThaiBuddhistChronology.INSTANCE.date(1728, 12, 31));
    }

    //-----------------------------------------------------------------------
    // withYear()
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_withYear_BE() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(2555, 8, 29);
        ThaiBuddhistDate test = base.with(YEAR, 2554);
        assertEquals(test, ThaiBuddhistChronology.INSTANCE.date(2554, 8, 29));
    }

    @Test(groups={"tck"})
    public void test_withYear_BBE() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(-2554, 8, 29);
        ThaiBuddhistDate test = base.with(YEAR_OF_ERA, 2554);
        assertEquals(test, ThaiBuddhistChronology.INSTANCE.date(-2553, 8, 29));
    }

    //-----------------------------------------------------------------------
    // withEra()
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_withEra_BE() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(2555, 8, 29);
        ThaiBuddhistDate test = base.with(ChronoField.ERA, ThaiBuddhistChronology.ERA_BE.getValue());
        assertEquals(test, ThaiBuddhistChronology.INSTANCE.date(2555, 8, 29));
    }

    @Test(groups={"tck"})
    public void test_withEra_BBE() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(-2554, 8, 29);
        ThaiBuddhistDate test = base.with(ChronoField.ERA, ThaiBuddhistChronology.ERA_BEFORE_BE.getValue());
        assertEquals(test, ThaiBuddhistChronology.INSTANCE.date(-2554, 8, 29));
    }

    @Test(groups={"tck"})
    public void test_withEra_swap() {
        ThaiBuddhistDate base = ThaiBuddhistChronology.INSTANCE.date(-2554, 8, 29);
        ThaiBuddhistDate test = base.with(ChronoField.ERA, ThaiBuddhistChronology.ERA_BE.getValue());
        assertEquals(test, ThaiBuddhistChronology.INSTANCE.date(2555, 8, 29));
    }

    //-----------------------------------------------------------------------
    // BuddhistDate.with(Local*)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_adjust_toLocalDate() {
        ThaiBuddhistDate jdate = ThaiBuddhistChronology.INSTANCE.date(1726, 1, 4);
        ThaiBuddhistDate test = jdate.with(LocalDate.of(2012, 7, 6));
        assertEquals(test, ThaiBuddhistChronology.INSTANCE.date(2555, 7, 6));
    }

    @Test(groups={"tck"}, expectedExceptions=DateTimeException.class)
    public void test_adjust_toMonth() {
        ThaiBuddhistDate jdate = ThaiBuddhistChronology.INSTANCE.date(1726, 1, 4);
        jdate.with(Month.APRIL);
    }

    //-----------------------------------------------------------------------
    // LocalDate.with(BuddhistDate)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_LocalDate_adjustToBuddhistDate() {
        ThaiBuddhistDate jdate = ThaiBuddhistChronology.INSTANCE.date(2555, 10, 29);
        LocalDate test = LocalDate.MIN.with(jdate);
        assertEquals(test, LocalDate.of(2012, 10, 29));
    }

    @Test(groups={"tck"})
    public void test_LocalDateTime_adjustToBuddhistDate() {
        ThaiBuddhistDate jdate = ThaiBuddhistChronology.INSTANCE.date(2555, 10, 29);
        LocalDateTime test = LocalDateTime.MIN.with(jdate);
        assertEquals(test, LocalDateTime.of(2012, 10, 29, 0, 0));
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @DataProvider(name="toString")
    Object[][] data_toString() {
        return new Object[][] {
            {ThaiBuddhistChronology.INSTANCE.date(544, 1, 1), "ThaiBuddhist BE 544-01-01"},
            {ThaiBuddhistChronology.INSTANCE.date(2271, 10, 28), "ThaiBuddhist BE 2271-10-28"},
            {ThaiBuddhistChronology.INSTANCE.date(2271, 10, 29), "ThaiBuddhist BE 2271-10-29"},
            {ThaiBuddhistChronology.INSTANCE.date(2270, 12, 5), "ThaiBuddhist BE 2270-12-05"},
            {ThaiBuddhistChronology.INSTANCE.date(2270, 12, 6), "ThaiBuddhist BE 2270-12-06"},
        };
    }

    @Test(dataProvider="toString", groups={"tck"})
    public void test_toString(ThaiBuddhistDate jdate, String expected) {
        assertEquals(jdate.toString(), expected);
    }

    //-----------------------------------------------------------------------
    // chronology range(ChronoField)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_Chrono_range() {
        long minYear = LocalDate.MIN.getYear() + YDIFF;
        long maxYear = LocalDate.MAX.getYear() + YDIFF;
        assertEquals(ThaiBuddhistChronology.INSTANCE.range(YEAR), ValueRange.of(minYear, maxYear));
        assertEquals(ThaiBuddhistChronology.INSTANCE.range(YEAR_OF_ERA), ValueRange.of(1, -minYear + 1, maxYear));

        assertEquals(ThaiBuddhistChronology.INSTANCE.range(DAY_OF_MONTH), DAY_OF_MONTH.range());
        assertEquals(ThaiBuddhistChronology.INSTANCE.range(DAY_OF_YEAR), DAY_OF_YEAR.range());
        assertEquals(ThaiBuddhistChronology.INSTANCE.range(MONTH_OF_YEAR), MONTH_OF_YEAR.range());
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test(groups="tck")
    public void test_equals_true() {
        assertTrue(ThaiBuddhistChronology.INSTANCE.equals(ThaiBuddhistChronology.INSTANCE));
    }

    @Test(groups="tck")
    public void test_equals_false() {
        assertFalse(ThaiBuddhistChronology.INSTANCE.equals(IsoChronology.INSTANCE));
    }

}
