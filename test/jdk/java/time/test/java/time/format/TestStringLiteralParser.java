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
package test.java.time.format;

import static java.time.temporal.ChronoField.YEAR;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.text.ParsePosition;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test StringLiteralPrinterParser.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestStringLiteralParser extends AbstractTestPrinterParser {

    Object[][] data_success() {
        return new Object[][] {
            // match
            {"hello", true, "hello", 0, 5},
            {"hello", true, "helloOTHER", 0, 5},
            {"hello", true, "OTHERhelloOTHER", 5, 10},
            {"hello", true, "OTHERhello", 5, 10},

            // no match
            {"hello", true, "", 0, 0},
            {"hello", true, "a", 1, 1},
            {"hello", true, "HELLO", 0, 0},
            {"hello", true, "hlloo", 0, 0},
            {"hello", true, "OTHERhllooOTHER", 5, 5},
            {"hello", true, "OTHERhlloo", 5, 5},
            {"hello", true, "h", 0, 0},
            {"hello", true, "OTHERh", 5, 5},

            // case insensitive
            {"hello", false, "hello", 0, 5},
            {"hello", false, "HELLO", 0, 5},
            {"hello", false, "HelLo", 0, 5},
            {"hello", false, "HelLO", 0, 5},
        };
    }

    @ParameterizedTest
    @MethodSource("data_success")
    public void test_parse_success(String s, boolean caseSensitive, String text, int pos, int expectedPos) {
        setCaseSensitive(caseSensitive);
        ParsePosition ppos = new ParsePosition(pos);
        TemporalAccessor parsed = getFormatter(s).parseUnresolved(text, ppos);
        if (ppos.getErrorIndex() != -1) {
            assertEquals(expectedPos, ppos.getIndex());
        } else {
            assertEquals(expectedPos, ppos.getIndex());
            assertEquals(false, parsed.isSupported(YEAR));
            assertEquals(null, parsed.query(TemporalQueries.chronology()));
            assertEquals(null, parsed.query(TemporalQueries.zoneId()));
        }
    }

    //-----------------------------------------------------------------------
    Object[][] data_error() {
        return new Object[][] {
            {"hello", "hello", -1, IndexOutOfBoundsException.class},
            {"hello", "hello", 6, IndexOutOfBoundsException.class},
        };
    }

    @ParameterizedTest
    @MethodSource("data_error")
    public void test_parse_error(String s, String text, int pos, Class<?> expected) {
        try {
            ParsePosition ppos = new ParsePosition(pos);
            getFormatter(s).parseUnresolved(text, ppos);
            fail();
        } catch (RuntimeException ex) {
            assertTrue(expected.isInstance(ex));
        }
    }
}
