/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2012, Stephen Colebourne & Michael Nascimento Santos
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.DateTimeException;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.HijrahChronology;
import java.time.chrono.IsoChronology;
import java.time.chrono.JapaneseChronology;
import java.time.chrono.MinguoChronology;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Set;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test Chronology class.
 */
@Test
public class TCKChronology {

    //-----------------------------------------------------------------------
    // regular data factory for ID and calendarType of available calendars
    //-----------------------------------------------------------------------
    @DataProvider(name = "calendarNameAndType")
    Object[][] data_of_calendars() {
        return new Object[][] {
                    {"Hijrah-umalqura", "islamic-umalqura"},
                    {"ISO", "iso8601"},
                    {"Japanese", "japanese"},
                    {"Minguo", "roc"},
                    {"ThaiBuddhist", "buddhist"},
                };
    }

    @Test(dataProvider = "calendarNameAndType")
    public void test_getters(String chronoId, String calendarSystemType) {
        Chronology chrono = Chronology.of(chronoId);
        assertNotNull(chrono, "Required calendar not found by ID: " + chronoId);
        assertEquals(chrono.getId(), chronoId);
        assertEquals(chrono.getCalendarType(), calendarSystemType);
    }

    @Test(dataProvider = "calendarNameAndType")
    public void test_required_calendars(String chronoId, String calendarSystemType) {
        Chronology chrono = Chronology.of(chronoId);
        assertNotNull(chrono, "Required calendar not found by ID: " + chronoId);
        chrono = Chronology.of(calendarSystemType);
        assertNotNull(chrono, "Required calendar not found by type: " + chronoId);
        Set<Chronology> cals = Chronology.getAvailableChronologies();
        assertTrue(cals.contains(chrono), "Required calendar not found in set of available calendars");
    }

    @Test
    public void test_calendar_list() {
        Set<Chronology> chronos = Chronology.getAvailableChronologies();
        assertNotNull(chronos, "Required list of calendars must be non-null");
        for (Chronology chrono : chronos) {
            Chronology lookup = Chronology.of(chrono.getId());
            assertNotNull(lookup, "Required calendar not found: " + chrono);
        }
        assertEquals(chronos.size() >= data_of_calendars().length, true, "Chronology.getAvailableChronologies().size = " + chronos.size()
                + ", expected >= " + data_of_calendars().length);
    }

    /**
     * Compute the number of days from the Epoch and compute the date from the number of days.
     */
    @Test(dataProvider = "calendarNameAndType")
    public void test_epoch(String name, String alias) {
        Chronology chrono = Chronology.of(name); // a chronology. In practice this is rarely hardcoded
        ChronoLocalDate<?> date1 = chrono.dateNow();
        long epoch1 = date1.getLong(ChronoField.EPOCH_DAY);
        ChronoLocalDate<?> date2 = date1.with(ChronoField.EPOCH_DAY, epoch1);
        assertEquals(date1, date2, "Date from epoch day is not same date: " + date1 + " != " + date2);
        long epoch2 = date1.getLong(ChronoField.EPOCH_DAY);
        assertEquals(epoch1, epoch2, "Epoch day not the same: " + epoch1 + " != " + epoch2);
    }

    @Test(dataProvider = "calendarNameAndType")
    public void test_dateEpochDay(String name, String alias) {
        Chronology chrono = Chronology.of(name);
        ChronoLocalDate<?> date = chrono.dateNow();
        long epochDay = date.getLong(ChronoField.EPOCH_DAY);
        ChronoLocalDate<?> test = chrono.dateEpochDay(epochDay);
        assertEquals(test, date);
    }

    //-----------------------------------------------------------------------
    // locale based lookup
    //-----------------------------------------------------------------------
    @DataProvider(name = "calendarsystemtype")
    Object[][] data_CalendarType() {
        return new Object[][] {
            {HijrahChronology.INSTANCE, "islamic-umalqura"},
            {IsoChronology.INSTANCE, "iso8601"},
            {JapaneseChronology.INSTANCE, "japanese"},
            {MinguoChronology.INSTANCE, "roc"},
            {ThaiBuddhistChronology.INSTANCE, "buddhist"},
        };
    }

    @Test(dataProvider = "calendarsystemtype")
    public void test_getCalendarType(Chronology chrono, String calendarType) {
        String type = calendarType;
        assertEquals(chrono.getCalendarType(), type);
    }

    @Test(dataProvider = "calendarsystemtype")
    public void test_lookupLocale(Chronology chrono, String calendarType) {
        Locale.Builder builder = new Locale.Builder().setLanguage("en").setRegion("CA");
        builder.setUnicodeLocaleKeyword("ca", calendarType);
        Locale locale = builder.build();
        assertEquals(Chronology.ofLocale(locale), chrono);
    }

    /**
     * Test lookup by calendarType of each chronology.
     * Verify that the calendar can be found by {@link java.time.chrono.Chronology#ofLocale}.
     */
    @Test
    public void test_ofLocaleByType() {
        // Test that all available chronologies can be successfully found using ofLocale
        Set<Chronology> chronos = Chronology.getAvailableChronologies();
        for (Chronology chrono : chronos) {
            Locale.Builder builder = new Locale.Builder().setLanguage("en").setRegion("CA");
            builder.setUnicodeLocaleKeyword("ca", chrono.getCalendarType());
            Locale locale = builder.build();
            assertEquals(Chronology.ofLocale(locale), chrono, "Lookup by type");
        }
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void test_lookupLocale() {
        Locale.Builder builder = new Locale.Builder().setLanguage("en").setRegion("CA");
        builder.setUnicodeLocaleKeyword("ca", "xxx");

        Locale locale = builder.build();
        Chronology.ofLocale(locale);
    }

    @Test(expectedExceptions = DateTimeException.class)
    public void test_noChrono() {
        Chronology chrono = Chronology.of("FooFoo");
    }

    //-----------------------------------------------------------------------
    // serialization; serialize and check each calendar system
    //-----------------------------------------------------------------------
    @Test(dataProvider = "calendarNameAndType")
    public void test_chronoSerializationSingleton(String id, String _calendarType) throws Exception {
        Chronology original = Chronology.of(id);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(original);
        out.close();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream in = new ObjectInputStream(bais);
        Chronology ser = (Chronology) in.readObject();
        assertEquals(ser, original, "Deserialized Chronology is not correct");
    }

}
