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
package tck.java.time.calendar;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.calendar.MinguoChrono;
import java.time.temporal.Adjusters;
import java.time.temporal.ChronoUnit;
import java.time.temporal.ChronoZonedDateTime;
import java.time.temporal.Chrono;
import java.time.temporal.ChronoLocalDate;
import java.time.temporal.ChronoLocalDateTime;
import java.time.temporal.ISOChrono;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test.
 */
@Test
public class TestMinguoChrono {

    //-----------------------------------------------------------------------
    // Chrono.ofName("Minguo")  Lookup by name
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_chrono_byName() {
        Chrono<MinguoChrono> c = MinguoChrono.INSTANCE;
        Chrono<?> test = Chrono.of("Minguo");
        Assert.assertNotNull(test, "The Minguo calendar could not be found byName");
        Assert.assertEquals(test.getId(), "Minguo", "ID mismatch");
        Assert.assertEquals(test.getCalendarType(), "roc", "Type mismatch");
        Assert.assertEquals(test, c);
    }

    //-----------------------------------------------------------------------
    // creation, toLocalDate()
    //-----------------------------------------------------------------------
    @DataProvider(name="samples")
    Object[][] data_samples() {
        return new Object[][] {
            {MinguoChrono.INSTANCE.date(1, 1, 1), LocalDate.of(1912, 1, 1)},
            {MinguoChrono.INSTANCE.date(1, 1, 2), LocalDate.of(1912, 1, 2)},
            {MinguoChrono.INSTANCE.date(1, 1, 3), LocalDate.of(1912, 1, 3)},

            {MinguoChrono.INSTANCE.date(2, 1, 1), LocalDate.of(1913, 1, 1)},
            {MinguoChrono.INSTANCE.date(3, 1, 1), LocalDate.of(1914, 1, 1)},
            {MinguoChrono.INSTANCE.date(3, 12, 6), LocalDate.of(1914, 12, 6)},
            {MinguoChrono.INSTANCE.date(4, 1, 1), LocalDate.of(1915, 1, 1)},
            {MinguoChrono.INSTANCE.date(4, 7, 3), LocalDate.of(1915, 7, 3)},
            {MinguoChrono.INSTANCE.date(4, 7, 4), LocalDate.of(1915, 7, 4)},
            {MinguoChrono.INSTANCE.date(5, 1, 1), LocalDate.of(1916, 1, 1)},
            {MinguoChrono.INSTANCE.date(100, 3, 3), LocalDate.of(2011, 3, 3)},
            {MinguoChrono.INSTANCE.date(101, 10, 28), LocalDate.of(2012, 10, 28)},
            {MinguoChrono.INSTANCE.date(101, 10, 29), LocalDate.of(2012, 10, 29)},
        };
    }

    @Test(dataProvider="samples", groups={"tck"})
    public void test_toLocalDate(ChronoLocalDate<MinguoChrono> minguo, LocalDate iso) {
        assertEquals(LocalDate.from(minguo), iso);
    }

    @Test(dataProvider="samples", groups={"tck"})
    public void test_fromCalendrical(ChronoLocalDate<MinguoChrono> minguo, LocalDate iso) {
        assertEquals(MinguoChrono.INSTANCE.date(iso), minguo);
    }

    @SuppressWarnings("unused")
    @Test(dataProvider="samples", groups={"implementation"})
    public void test_MinguoDate(ChronoLocalDate<MinguoChrono> minguoDate, LocalDate iso) {
        ChronoLocalDate<MinguoChrono> hd = minguoDate;
        ChronoLocalDateTime<MinguoChrono> hdt = hd.atTime(LocalTime.NOON);
        ZoneOffset zo = ZoneOffset.ofHours(1);
        ChronoZonedDateTime<MinguoChrono> hzdt = hdt.atZone(zo);
        hdt = hdt.plus(1, ChronoUnit.YEARS);
        hdt = hdt.plus(1, ChronoUnit.MONTHS);
        hdt = hdt.plus(1, ChronoUnit.DAYS);
        hdt = hdt.plus(1, ChronoUnit.HOURS);
        hdt = hdt.plus(1, ChronoUnit.MINUTES);
        hdt = hdt.plus(1, ChronoUnit.SECONDS);
        hdt = hdt.plus(1, ChronoUnit.NANOS);
        ChronoLocalDateTime<MinguoChrono> a2 = hzdt.getDateTime();
        ChronoLocalDate<MinguoChrono> a3 = a2.getDate();
        ChronoLocalDate<MinguoChrono> a5 = hzdt.getDate();
        //System.out.printf(" d: %s, dt: %s; odt: %s; zodt: %s; a4: %s%n", date, hdt, hodt, hzdt, a5);
    }

    @Test()
    public void test_MinguoChrono() {
        ChronoLocalDate<MinguoChrono> h1 = MinguoChrono.ERA_ROC.date(1, 2, 3);
        ChronoLocalDate<MinguoChrono> h2 = h1;
        ChronoLocalDateTime<MinguoChrono> h3 = h2.atTime(LocalTime.NOON);
        @SuppressWarnings("unused")
        ChronoZonedDateTime<MinguoChrono> h4 = h3.atZone(ZoneOffset.UTC);
    }

    @DataProvider(name="badDates")
    Object[][] data_badDates() {
        return new Object[][] {
            {1912, 0, 0},

            {1912, -1, 1},
            {1912, 0, 1},
            {1912, 14, 1},
            {1912, 15, 1},

            {1912, 1, -1},
            {1912, 1, 0},
            {1912, 1, 32},
            {1912, 2, 29},
            {1912, 2, 30},

            {1912, 12, -1},
            {1912, 12, 0},
            {1912, 12, 32},
            };
    }

    @Test(dataProvider="badDates", groups={"tck"}, expectedExceptions=DateTimeException.class)
    public void test_badDates(int year, int month, int dom) {
        MinguoChrono.INSTANCE.date(year, month, dom);
    }

    //-----------------------------------------------------------------------
    // with(DateTimeAdjuster)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_adjust1() {
        ChronoLocalDate<MinguoChrono> base = MinguoChrono.INSTANCE.date(2012, 10, 29);
        ChronoLocalDate<MinguoChrono> test = base.with(Adjusters.lastDayOfMonth());
        assertEquals(test, MinguoChrono.INSTANCE.date(2012, 10, 31));
    }

    @Test(groups={"tck"})
    public void test_adjust2() {
        ChronoLocalDate<MinguoChrono> base = MinguoChrono.INSTANCE.date(1728, 12, 2);
        ChronoLocalDate<MinguoChrono> test = base.with(Adjusters.lastDayOfMonth());
        assertEquals(test, MinguoChrono.INSTANCE.date(1728, 12, 31));
    }

    //-----------------------------------------------------------------------
    // MinguoDate.with(Local*)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_adjust_toLocalDate() {
        ChronoLocalDate<MinguoChrono> minguo = MinguoChrono.INSTANCE.date(99, 1, 4);
        ChronoLocalDate<MinguoChrono> test = minguo.with(LocalDate.of(2012, 7, 6));
        assertEquals(test, MinguoChrono.INSTANCE.date(101, 7, 6));
    }

    @Test(groups={"tck"}, expectedExceptions=DateTimeException.class)
    public void test_adjust_toMonth() {
        ChronoLocalDate<MinguoChrono> minguo = MinguoChrono.INSTANCE.date(1726, 1, 4);
        minguo.with(Month.APRIL);
    }

    //-----------------------------------------------------------------------
    // LocalDate.with(MinguoDate)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_LocalDate_adjustToMinguoDate() {
        ChronoLocalDate<MinguoChrono> minguo = MinguoChrono.INSTANCE.date(101, 10, 29);
        LocalDate test = LocalDate.MIN.with(minguo);
        assertEquals(test, LocalDate.of(2012, 10, 29));
    }

    @Test(groups={"tck"})
    public void test_LocalDateTime_adjustToMinguoDate() {
        ChronoLocalDate<MinguoChrono> minguo = MinguoChrono.INSTANCE.date(101, 10, 29);
        LocalDateTime test = LocalDateTime.MIN.with(minguo);
        assertEquals(test, LocalDateTime.of(2012, 10, 29, 0, 0));
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @DataProvider(name="toString")
    Object[][] data_toString() {
        return new Object[][] {
            {MinguoChrono.INSTANCE.date(1, 1, 1), "Minguo ROC 1-01-01"},
            {MinguoChrono.INSTANCE.date(1728, 10, 28), "Minguo ROC 1728-10-28"},
            {MinguoChrono.INSTANCE.date(1728, 10, 29), "Minguo ROC 1728-10-29"},
            {MinguoChrono.INSTANCE.date(1727, 12, 5), "Minguo ROC 1727-12-05"},
            {MinguoChrono.INSTANCE.date(1727, 12, 6), "Minguo ROC 1727-12-06"},
        };
    }

    @Test(dataProvider="toString", groups={"tck"})
    public void test_toString(ChronoLocalDate<MinguoChrono> minguo, String expected) {
        assertEquals(minguo.toString(), expected);
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test(groups="tck")
    public void test_equals_true() {
        assertTrue(MinguoChrono.INSTANCE.equals(MinguoChrono.INSTANCE));
    }

    @Test(groups="tck")
    public void test_equals_false() {
        assertFalse(MinguoChrono.INSTANCE.equals(ISOChrono.INSTANCE));
    }

}
