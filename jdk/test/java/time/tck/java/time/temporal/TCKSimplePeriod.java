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
package tck.java.time.temporal;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.time.temporal.ChronoUnit.YEARS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ISOFields;
import java.time.temporal.SimplePeriod;
import java.time.temporal.TemporalUnit;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tck.java.time.AbstractTCKTest;

/**
 * Test.
 */
@Test
public class TCKSimplePeriod extends AbstractTCKTest {

    private static final SimplePeriod TEST_12_MONTHS = SimplePeriod.of(12, MONTHS);

    //-----------------------------------------------------------------------
    @Test(dataProvider="samples")
    public void test_serialization(long amount, TemporalUnit unit) throws ClassNotFoundException, IOException {
        SimplePeriod test = SimplePeriod.of(amount, unit);
        assertSerializable(test);
    }

    @Test
    public void test_serialization_format_zoneOffset() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos) ) {
            dos.writeByte(10);
            dos.writeLong(12);
        }
        byte[] bytes = baos.toByteArray();
        assertSerializedBySer(TEST_12_MONTHS, bytes, new byte[0]);
    }

    //-----------------------------------------------------------------------
    // of(long, TenmporalUnit)
    //-----------------------------------------------------------------------
    @DataProvider(name="samples")
    Object[][] data_samples() {
        return new Object[][] {
                {0, YEARS},
                {1, YEARS},
                {-1, YEARS},
                {2, MONTHS},
                {-2, MONTHS},
                {43, ISOFields.WEEK_BASED_YEARS},
                {Long.MAX_VALUE, NANOS},
                {Long.MIN_VALUE, NANOS},
        };
    }

    @Test(dataProvider="samples")
    public void factory_of(long amount, TemporalUnit unit) {
        SimplePeriod test = SimplePeriod.of(amount, unit);
        assertEquals(test.getAmount(), amount);
        assertEquals(test.getUnit(), unit);
    }

    //-----------------------------------------------------------------------
    // addTo()
    //-----------------------------------------------------------------------
    @DataProvider(name="addTo")
    Object[][] data_addTo() {
        return new Object[][] {
            {SimplePeriod.of(0, DAYS),  date(2012, 6, 30), date(2012, 6, 30)},

            {SimplePeriod.of(1, DAYS),  date(2012, 6, 30), date(2012, 7, 1)},
            {SimplePeriod.of(-1, DAYS),  date(2012, 6, 30), date(2012, 6, 29)},

            {SimplePeriod.of(2, DAYS),  date(2012, 6, 30), date(2012, 7, 2)},
            {SimplePeriod.of(-2, DAYS),  date(2012, 6, 30), date(2012, 6, 28)},

            {SimplePeriod.of(3, MONTHS),  date(2012, 5, 31), date(2012, 8, 31)},
            {SimplePeriod.of(4, MONTHS),  date(2012, 5, 31), date(2012, 9, 30)},
            {SimplePeriod.of(-3, MONTHS),  date(2012, 5, 31), date(2012, 2, 29)},
            {SimplePeriod.of(-4, MONTHS),  date(2012, 5, 31), date(2012, 1, 31)},
        };
    }

    @Test(dataProvider="addTo")
    public void test_addTo(SimplePeriod period, LocalDate baseDate, LocalDate expected) {
        assertEquals(period.addTo(baseDate), expected);
    }

    @Test(dataProvider="addTo")
    public void test_addTo_usingLocalDatePlus(SimplePeriod period, LocalDate baseDate, LocalDate expected) {
        assertEquals(baseDate.plus(period), expected);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_addTo_nullZero() {
        SimplePeriod.of(0, DAYS).addTo(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_addTo_nullNonZero() {
        SimplePeriod.of(2, DAYS).addTo(null);
    }

    //-----------------------------------------------------------------------
    // subtractFrom()
    //-----------------------------------------------------------------------
    @DataProvider(name="subtractFrom")
    Object[][] data_subtractFrom() {
        return new Object[][] {
            {SimplePeriod.of(0, DAYS),  date(2012, 6, 30), date(2012, 6, 30)},

            {SimplePeriod.of(1, DAYS),  date(2012, 6, 30), date(2012, 6, 29)},
            {SimplePeriod.of(-1, DAYS),  date(2012, 6, 30), date(2012, 7, 1)},

            {SimplePeriod.of(2, DAYS),  date(2012, 6, 30), date(2012, 6, 28)},
            {SimplePeriod.of(-2, DAYS),  date(2012, 6, 30), date(2012, 7, 2)},

            {SimplePeriod.of(3, MONTHS),  date(2012, 5, 31), date(2012, 2, 29)},
            {SimplePeriod.of(4, MONTHS),  date(2012, 5, 31), date(2012, 1, 31)},
            {SimplePeriod.of(-3, MONTHS),  date(2012, 5, 31), date(2012, 8, 31)},
            {SimplePeriod.of(-4, MONTHS),  date(2012, 5, 31), date(2012, 9, 30)},
        };
    }

    @Test(dataProvider="subtractFrom")
    public void test_subtractFrom(SimplePeriod period, LocalDate baseDate, LocalDate expected) {
        assertEquals(period.subtractFrom(baseDate), expected);
    }

    @Test(dataProvider="subtractFrom")
    public void test_subtractFrom_usingLocalDateMinus(SimplePeriod period, LocalDate baseDate, LocalDate expected) {
        assertEquals(baseDate.minus(period), expected);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_subtractFrom_nullZero() {
        SimplePeriod.of(0, DAYS).subtractFrom(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_subtractFrom_nullNonZero() {
        SimplePeriod.of(2, DAYS).subtractFrom(null);
    }

    //-----------------------------------------------------------------------
    // abs()
    //-----------------------------------------------------------------------
    @Test(dataProvider="samples")
    public void test_abs(long amount, TemporalUnit unit) {
        SimplePeriod test = SimplePeriod.of(amount, unit);
        if (amount >= 0) {
            assertSame(test.abs(), test);  // spec requires assertSame
        } else if (amount == Long.MIN_VALUE) {
            // ignore, separately tested
        } else {
            assertEquals(test.abs(), SimplePeriod.of(-amount, unit));
        }
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_abs_minValue() {
        SimplePeriod.of(Long.MIN_VALUE, SECONDS).abs();
    }

    //-----------------------------------------------------------------------
    // equals() / hashCode()
    //-----------------------------------------------------------------------
    @Test(dataProvider="samples")
    public void test_equals(long amount, TemporalUnit unit) {
        SimplePeriod test1 = SimplePeriod.of(amount, unit);
        SimplePeriod test2 = SimplePeriod.of(amount, unit);
        assertEquals(test1, test2);
    }

    @Test(dataProvider="samples")
    public void test_equals_self(long amount, TemporalUnit unit) {
        SimplePeriod test = SimplePeriod.of(amount, unit);
        assertEquals(test.equals(test), true);
    }

    public void test_equals_null() {
        assertEquals(TEST_12_MONTHS.equals(null), false);
    }

    public void test_equals_otherClass() {
        Period test = Period.of(1, 2, 3, 4, 5, 6);
        assertEquals(test.equals(""), false);
    }

    //-----------------------------------------------------------------------
    public void test_hashCode() {
        SimplePeriod test5 = SimplePeriod.of(5, DAYS);
        SimplePeriod test6 = SimplePeriod.of(6, DAYS);
        SimplePeriod test5M = SimplePeriod.of(5, MONTHS);
        SimplePeriod test5Y = SimplePeriod.of(5, YEARS);
        assertEquals(test5.hashCode() == test5.hashCode(), true);
        assertEquals(test5.hashCode() == test6.hashCode(), false);
        assertEquals(test5.hashCode() == test5M.hashCode(), false);
        assertEquals(test5.hashCode() == test5Y.hashCode(), false);
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @Test(dataProvider="samples")
    public void test_toString(long amount, TemporalUnit unit) {
        SimplePeriod test = SimplePeriod.of(amount, unit);
        assertEquals(test.toString(), amount + " " + unit.getName());
    }

    private static LocalDate date(int y, int m, int d) {
        return LocalDate.of(y, m, d);
    }

}
