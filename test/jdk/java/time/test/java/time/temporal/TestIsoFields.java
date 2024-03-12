/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package test.java.time.temporal;

import static java.time.temporal.IsoFields.DAY_OF_QUARTER;
import static java.time.temporal.IsoFields.QUARTER_OF_YEAR;
import static java.time.temporal.IsoFields.WEEK_BASED_YEAR;
import static java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.JapaneseDate;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistDate;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests fields in IsoFields class are supported in Japanese/Minguo/ThaiBuddhist
 * date classes
 * @bug 8279185
 */
@Test
public class TestIsoFields {
    private static final LocalDate ld = LocalDate.of(2022, 2, 25);
    private static final ChronoLocalDate J_DATE = JapaneseDate.from(ld);
    private static final ChronoLocalDate M_DATE = MinguoDate.from(ld);
    private static final ChronoLocalDate TB_DATE = ThaiBuddhistDate.from(ld);
    private static final List<ChronoLocalDate> CLDATES = List.of(J_DATE, M_DATE, TB_DATE);

    @DataProvider(name = "isSupported")
    Object[][] data_isSupported() {
        return new Object[][]{
                {DAY_OF_QUARTER},
                {QUARTER_OF_YEAR},
                {WEEK_BASED_YEAR},
                {WEEK_OF_WEEK_BASED_YEAR},
        };
    }

    @DataProvider(name = "range")
    Object[][] data_range() {
        return new Object[][]{
                {J_DATE, DAY_OF_QUARTER, ValueRange.of(1, 90)},
                {J_DATE, QUARTER_OF_YEAR, ValueRange.of(1, 4)},
                {J_DATE, WEEK_BASED_YEAR, ValueRange.of(1_873, 999_999_999)},
                {J_DATE, WEEK_OF_WEEK_BASED_YEAR, ValueRange.of(1, 52)},
                {M_DATE, DAY_OF_QUARTER, ValueRange.of(1, 90)},
                {M_DATE, QUARTER_OF_YEAR, ValueRange.of(1, 4)},
                {M_DATE, WEEK_BASED_YEAR, ValueRange.of(-999_999_999, 999_998_088)},
                {M_DATE, WEEK_OF_WEEK_BASED_YEAR, ValueRange.of(1, 52)},
                {TB_DATE, DAY_OF_QUARTER, ValueRange.of(1, 90)},
                {TB_DATE, QUARTER_OF_YEAR, ValueRange.of(1, 4)},
                {TB_DATE, WEEK_BASED_YEAR, ValueRange.of(-999_999_456, 999_999_999)},
                {TB_DATE, WEEK_OF_WEEK_BASED_YEAR, ValueRange.of(1, 52)},
        };
    }

    @DataProvider(name = "with_getLong")
    Object[][] data_with_getLong() {
        return new Object[][]{
                {DAY_OF_QUARTER, 45},
                {QUARTER_OF_YEAR, 2},
                {WEEK_BASED_YEAR, 2_022},
                {WEEK_OF_WEEK_BASED_YEAR, 10},
        };
    }

    @Test(dataProvider = "isSupported")
    public void test_isSupported(TemporalField f) {
        CLDATES.forEach(d -> assertTrue(d.isSupported(f)));
    }

    @Test(dataProvider = "range")
    public void test_range(ChronoLocalDate cld, TemporalField f, ValueRange r) {
        assertEquals(cld.range(f), r);
    }

    @Test(dataProvider = "with_getLong")
    public void test_with_getLong(TemporalField f, long val) {
        CLDATES.forEach(d -> {
            var min = d.range(f).getMinimum();
            var max = d.range(f).getMaximum();
            assertEquals(d.with(f, min).getLong(f), min);
            assertEquals(d.with(f, max).getLong(f), max);
            assertEquals(d.with(f, val).getLong(f), val);
        });
    }
}
