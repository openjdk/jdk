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
import java.time.Month;
import java.time.calendar.HijrahChrono;
import java.time.temporal.ChronoLocalDate;
import java.time.temporal.Adjusters;
import java.time.temporal.Chrono;
import java.time.temporal.ISOChrono;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test.
 */
@Test
public class TestHijrahChrono {

    //-----------------------------------------------------------------------
    // Chrono.ofName("Hijrah")  Lookup by name
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_chrono_byName() {
        Chrono<HijrahChrono> c = HijrahChrono.INSTANCE;
        Chrono<?> test = Chrono.of("Hijrah");
        Assert.assertNotNull(test, "The Hijrah calendar could not be found byName");
        Assert.assertEquals(test.getId(), "Hijrah", "ID mismatch");
        Assert.assertEquals(test.getCalendarType(), "islamicc", "Type mismatch");
        Assert.assertEquals(test, c);
    }

    //-----------------------------------------------------------------------
    // creation, toLocalDate()
    //-----------------------------------------------------------------------
    @DataProvider(name="samples")
    Object[][] data_samples() {
        return new Object[][] {
            {HijrahChrono.INSTANCE.date(1, 1, 1), LocalDate.of(622, 7, 19)},
            {HijrahChrono.INSTANCE.date(1, 1, 2), LocalDate.of(622, 7, 20)},
            {HijrahChrono.INSTANCE.date(1, 1, 3), LocalDate.of(622, 7, 21)},

            {HijrahChrono.INSTANCE.date(2, 1, 1), LocalDate.of(623, 7, 8)},
            {HijrahChrono.INSTANCE.date(3, 1, 1), LocalDate.of(624, 6, 27)},
            {HijrahChrono.INSTANCE.date(3, 12, 6), LocalDate.of(625, 5, 23)},
            {HijrahChrono.INSTANCE.date(4, 1, 1), LocalDate.of(625, 6, 16)},
            {HijrahChrono.INSTANCE.date(4, 7, 3), LocalDate.of(625, 12, 12)},
            {HijrahChrono.INSTANCE.date(4, 7, 4), LocalDate.of(625, 12, 13)},
            {HijrahChrono.INSTANCE.date(5, 1, 1), LocalDate.of(626, 6, 5)},
            {HijrahChrono.INSTANCE.date(1662, 3, 3), LocalDate.of(2234, 4, 3)},
            {HijrahChrono.INSTANCE.date(1728, 10, 28), LocalDate.of(2298, 12, 03)},
            {HijrahChrono.INSTANCE.date(1728, 10, 29), LocalDate.of(2298, 12, 04)},
        };
    }

    @Test(dataProvider="samples", groups={"tck"})
    public void test_toLocalDate(ChronoLocalDate<?> hijrahDate, LocalDate iso) {
        assertEquals(LocalDate.from(hijrahDate), iso);
    }

    @Test(dataProvider="samples", groups={"tck"})
    public void test_fromCalendrical(ChronoLocalDate<?> hijrahDate, LocalDate iso) {
        assertEquals(HijrahChrono.INSTANCE.date(iso), hijrahDate);
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
        };
    }

    @Test(dataProvider="badDates", groups={"tck"}, expectedExceptions=DateTimeException.class)
    public void test_badDates(int year, int month, int dom) {
        HijrahChrono.INSTANCE.date(year, month, dom);
    }

    //-----------------------------------------------------------------------
    // with(WithAdjuster)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_adjust1() {
        ChronoLocalDate<?> base = HijrahChrono.INSTANCE.date(1728, 10, 28);
        ChronoLocalDate<?> test = base.with(Adjusters.lastDayOfMonth());
        assertEquals(test, HijrahChrono.INSTANCE.date(1728, 10, 29));
    }

    @Test(groups={"tck"})
    public void test_adjust2() {
        ChronoLocalDate<?> base = HijrahChrono.INSTANCE.date(1728, 12, 2);
        ChronoLocalDate<?> test = base.with(Adjusters.lastDayOfMonth());
        assertEquals(test, HijrahChrono.INSTANCE.date(1728, 12, 30));
    }

    //-----------------------------------------------------------------------
    // HijrahDate.with(Local*)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_adjust_toLocalDate() {
        ChronoLocalDate<?> hijrahDate = HijrahChrono.INSTANCE.date(1726, 1, 4);
        ChronoLocalDate<?> test = hijrahDate.with(LocalDate.of(2012, 7, 6));
        assertEquals(test, HijrahChrono.INSTANCE.date(1433, 8, 16));
    }

    @Test(groups={"tck"}, expectedExceptions=DateTimeException.class)
    public void test_adjust_toMonth() {
        ChronoLocalDate<?> hijrahDate = HijrahChrono.INSTANCE.date(1726, 1, 4);
        hijrahDate.with(Month.APRIL);
    }

    //-----------------------------------------------------------------------
    // LocalDate.with(HijrahDate)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_LocalDate_adjustToHijrahDate() {
        ChronoLocalDate<?> hijrahDate = HijrahChrono.INSTANCE.date(1728, 10, 29);
        LocalDate test = LocalDate.MIN.with(hijrahDate);
        assertEquals(test, LocalDate.of(2298, 12, 4));
    }

    @Test(groups={"tck"})
    public void test_LocalDateTime_adjustToHijrahDate() {
        ChronoLocalDate<?> hijrahDate = HijrahChrono.INSTANCE.date(1728, 10, 29);
        LocalDateTime test = LocalDateTime.MIN.with(hijrahDate);
        assertEquals(test, LocalDateTime.of(2298, 12, 4, 0, 0));
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @DataProvider(name="toString")
    Object[][] data_toString() {
        return new Object[][] {
            {HijrahChrono.INSTANCE.date(1, 1, 1), "Hijrah AH 1-01-01"},
            {HijrahChrono.INSTANCE.date(1728, 10, 28), "Hijrah AH 1728-10-28"},
            {HijrahChrono.INSTANCE.date(1728, 10, 29), "Hijrah AH 1728-10-29"},
            {HijrahChrono.INSTANCE.date(1727, 12, 5), "Hijrah AH 1727-12-05"},
            {HijrahChrono.INSTANCE.date(1727, 12, 6), "Hijrah AH 1727-12-06"},
        };
    }

    @Test(dataProvider="toString", groups={"tck"})
    public void test_toString(ChronoLocalDate<?> hijrahDate, String expected) {
        assertEquals(hijrahDate.toString(), expected);
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test(groups="tck")
    public void test_equals_true() {
        assertTrue(HijrahChrono.INSTANCE.equals(HijrahChrono.INSTANCE));
    }

    @Test(groups="tck")
    public void test_equals_false() {
        assertFalse(HijrahChrono.INSTANCE.equals(ISOChrono.INSTANCE));
    }

}
