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
 * Copyright (c) 2009-2012, Stephen Colebourne & Michael Nascimento Santos
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
package test.java.time;

import java.time.*;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.time.temporal.ChronoUnit.YEARS;
import static org.testng.Assert.assertEquals;

import java.time.format.DateTimeParseException;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test PeriodParser.
 */
@Test
public class TestPeriodParser {

    //-----------------------------------------------------------------------
    // parse(String)
    //-----------------------------------------------------------------------
    @DataProvider(name="Parse")
    Object[][] provider_factory_parse() {
        return new Object[][] {
            {"Pt0S", Period.ZERO},
            {"pT0S", Period.ZERO},
            {"PT0S", Period.ZERO},
            {"Pt0s", Period.ZERO},
            {"pt0s", Period.ZERO},
            {"P0Y0M0DT0H0M0.0S", Period.ZERO},

            {"P1Y", Period.of(1, YEARS)},
            {"P100Y", Period.of(100, YEARS)},
            {"P-25Y", Period.of(-25, YEARS)},
            {"P" + Integer.MAX_VALUE + "Y", Period.of(Integer.MAX_VALUE, YEARS)},
            {"P" + Integer.MIN_VALUE + "Y", Period.of(Integer.MIN_VALUE, YEARS)},

            {"P1M", Period.of(1, MONTHS)},
            {"P0M", Period.of(0, MONTHS)},
            {"P-1M", Period.of(-1, MONTHS)},
            {"P" + Integer.MAX_VALUE + "M", Period.of(Integer.MAX_VALUE, MONTHS)},
            {"P" + Integer.MIN_VALUE + "M", Period.of(Integer.MIN_VALUE, MONTHS)},

            {"P1D", Period.of(1, DAYS)},
            {"P0D", Period.of(0, DAYS)},
            {"P-1D", Period.of(-1, DAYS)},
            {"P" + Integer.MAX_VALUE + "D", Period.of(Integer.MAX_VALUE, DAYS)},
            {"P" + Integer.MIN_VALUE + "D", Period.of(Integer.MIN_VALUE, DAYS)},

            {"P2Y3M25D", Period.ofDate(2, 3, 25)},

            {"PT1H", Period.of(1, HOURS)},
            {"PT-1H", Period.of(-1, HOURS)},
            {"PT24H", Period.of(24, HOURS)},
            {"PT-24H", Period.of(-24, HOURS)},
            {"PT" + Integer.MAX_VALUE / (3600 * 8) + "H", Period.of(Integer.MAX_VALUE / (3600 * 8), HOURS)},
            {"PT" + Integer.MIN_VALUE / (3600 * 8) + "H", Period.of(Integer.MIN_VALUE / (3600 * 8), HOURS)},

            {"PT1M", Period.of(1, MINUTES)},
            {"PT-1M", Period.of(-1, MINUTES)},
            {"PT60M", Period.of(60, MINUTES)},
            {"PT-60M", Period.of(-60, MINUTES)},
            {"PT" + Integer.MAX_VALUE / (60 * 8) + "M", Period.of(Integer.MAX_VALUE / (60 * 8), MINUTES)},
            {"PT" + Integer.MIN_VALUE / (60 * 8) + "M", Period.of(Integer.MIN_VALUE / (60 * 8), MINUTES)},

            {"PT1S", Period.of(1, SECONDS)},
            {"PT-1S", Period.of(-1, SECONDS)},
            {"PT60S", Period.of(60, SECONDS)},
            {"PT-60S", Period.of(-60, SECONDS)},
            {"PT" + Integer.MAX_VALUE + "S", Period.of(Integer.MAX_VALUE, SECONDS)},
            {"PT" + Integer.MIN_VALUE + "S", Period.of(Integer.MIN_VALUE, SECONDS)},

            {"PT0.1S", Period.of( 0, 0, 0, 0, 0, 0, 100000000 ) },
            {"PT-0.1S", Period.of( 0, 0, 0, 0, 0, 0, -100000000 ) },
            {"PT1.1S", Period.of( 0, 0, 0, 0, 0, 1, 100000000 ) },
            {"PT-1.1S", Period.of( 0, 0, 0, 0, 0, -1, -100000000 ) },
            {"PT1.0001S", Period.of(1, SECONDS).plus( 100000, NANOS ) },
            {"PT1.0000001S", Period.of(1, SECONDS).plus( 100, NANOS ) },
            {"PT1.123456789S", Period.of( 0, 0, 0, 0, 0, 1, 123456789 ) },
            {"PT1.999999999S", Period.of( 0, 0, 0, 0, 0, 1, 999999999 ) },

        };
    }

    @Test(dataProvider="Parse")
    public void factory_parse(String text, Period expected) {
        Period p = Period.parse(text);
        assertEquals(p, expected);
    }

    @Test(dataProvider="Parse")
    public void factory_parse_comma(String text, Period expected) {
        if (text.contains(".")) {
            text = text.replace('.', ',');
            Period p = Period.parse(text);
            assertEquals(p, expected);
        }
    }

    @DataProvider(name="ParseFailures")
    Object[][] provider_factory_parseFailures() {
        return new Object[][] {
            {"", 0},
            {"PTS", 2},
            {"AT0S", 0},
            {"PA0S", 1},
            {"PT0A", 3},

            {"PT+S", 2},
            {"PT-S", 2},
            {"PT.S", 2},
            {"PTAS", 2},

            {"PT+0S", 2},
            {"PT-0S", 2},
            {"PT+1S", 2},
            {"PT-.S", 2},

            {"PT1ABC2S", 3},
            {"PT1.1ABC2S", 5},

            {"PT123456789123456789123456789S", 2},
            {"PT0.1234567891S", 4},
            {"PT1.S", 2},
            {"PT.1S", 2},

            {"PT2.-3S", 2},
            {"PT-2.-3S", 2},

            {"P1Y1MT1DT1M1S", 7},
            {"P1Y1MT1HT1M1S", 8},
            {"P1YMD", 3},
            {"PT1ST1D", 4},
            {"P1Y2Y", 4},
            {"PT1M+3S", 4},

            {"PT1S1", 4},
            {"PT1S.", 4},
            {"PT1SA", 4},
            {"PT1M1", 4},
            {"PT1M.", 4},
            {"PT1MA", 4},
        };
    }

    @Test(dataProvider="ParseFailures", expectedExceptions=DateTimeParseException.class)
    public void factory_parseFailures(String text, int errPos) {
        try {
            Period.parse(text);
        } catch (DateTimeParseException ex) {
            assertEquals(ex.getParsedString(), text);
            assertEquals(ex.getErrorIndex(), errPos);
            throw ex;
        }
    }

    @Test(dataProvider="ParseFailures", expectedExceptions=DateTimeParseException.class)
    public void factory_parseFailures_comma(String text, int errPos) {
        text = text.replace('.', ',');
        try {
            Period.parse(text);
        } catch (DateTimeParseException ex) {
            assertEquals(ex.getParsedString(), text);
            assertEquals(ex.getErrorIndex(), errPos);
            throw ex;
        }
    }

    @Test(expectedExceptions=DateTimeParseException.class)
    public void factory_parse_tooBig() {
        String text = "PT" + Long.MAX_VALUE + "1S";
        Period.parse(text);
    }

    @Test(expectedExceptions=DateTimeParseException.class)
    public void factory_parse_tooBig_decimal() {
        String text = "PT" + Long.MAX_VALUE + "1.1S";
        Period.parse(text);
    }

    @Test(expectedExceptions=DateTimeParseException.class)
    public void factory_parse_tooSmall() {
        String text = "PT" + Long.MIN_VALUE + "1S";
        Period.parse(text);
    }

    @Test(expectedExceptions=DateTimeParseException.class)
    public void factory_parse_tooSmall_decimal() {
        String text = "PT" + Long.MIN_VALUE + ".1S";
        Period.parse(text);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void factory_parse_null() {
        Period.parse(null);
    }

    @DataProvider(name="ParseSequenceFailures")
    Object[][] provider_factory_parseSequenceFailures() {
        return new Object[][] {
            {"P0M0Y0DT0H0M0.0S"},
            {"P0M0D0YT0H0M0.0S"},
            {"P0S0D0YT0S0M0.0H"},
            {"PT0M0H0.0S"},
            {"PT0M0H"},
            {"PT0S0M"},
            {"PT0.0M2S"},
        };
    }

    @Test(dataProvider="ParseSequenceFailures", expectedExceptions=DateTimeParseException.class)
    public void factory_parse_badSequence(String text) {
        Period.parse(text);
    }

}
