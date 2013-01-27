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
package test.java.time.format;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import java.text.ParsePosition;
import java.time.ZoneId;
import java.time.format.DateTimeBuilder;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.Queries;
import java.time.zone.ZoneRulesProvider;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test ZonePrinterParser.
 */
@Test(groups={"implementation"})
public class TestZoneIdParser extends AbstractTestPrinterParser {

    private static final String AMERICA_DENVER = "America/Denver";
    private static final ZoneId TIME_ZONE_DENVER = ZoneId.of(AMERICA_DENVER);

    private DateTimeFormatter getFormatter0(TextStyle style) {
        if (style == null)
            return builder.appendZoneId().toFormatter(locale).withSymbols(symbols);
        return builder.appendZoneText(style).toFormatter(locale).withSymbols(symbols);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="error")
    Object[][] data_error() {
        return new Object[][] {
            {null, "hello", -1, IndexOutOfBoundsException.class},
            {null, "hello", 6, IndexOutOfBoundsException.class},
        };
    }

    @Test(dataProvider="error")
    public void test_parse_error(TextStyle style, String text, int pos, Class<?> expected) {
        try {
            getFormatter0(style).parseToBuilder(text, new ParsePosition(pos));
            assertTrue(false);
        } catch (RuntimeException ex) {
            assertTrue(expected.isInstance(ex));
        }
    }

    //-----------------------------------------------------------------------
    public void test_parse_exactMatch_Denver() throws Exception {
        ParsePosition pos = new ParsePosition(0);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder(AMERICA_DENVER, pos);
        assertEquals(pos.getIndex(), AMERICA_DENVER.length());
        assertParsed(dtb, TIME_ZONE_DENVER);
    }

    public void test_parse_startStringMatch_Denver() throws Exception {
        ParsePosition pos = new ParsePosition(0);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder(AMERICA_DENVER + "OTHER", pos);
        assertEquals(pos.getIndex(), AMERICA_DENVER.length());
        assertParsed(dtb, TIME_ZONE_DENVER);
    }

    public void test_parse_midStringMatch_Denver() throws Exception {
        ParsePosition pos = new ParsePosition(5);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder("OTHER" + AMERICA_DENVER + "OTHER", pos);
        assertEquals(pos.getIndex(), 5 + AMERICA_DENVER.length());
        assertParsed(dtb, TIME_ZONE_DENVER);
    }

    public void test_parse_endStringMatch_Denver() throws Exception {
        ParsePosition pos = new ParsePosition(5);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder("OTHER" + AMERICA_DENVER, pos);
        assertEquals(pos.getIndex(), 5 + AMERICA_DENVER.length());
        assertParsed(dtb, TIME_ZONE_DENVER);
    }

    public void test_parse_partialMatch() throws Exception {
        ParsePosition pos = new ParsePosition(5);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder("OTHERAmerica/Bogusville", pos);
        assertEquals(pos.getErrorIndex(), 5);  // TBD: -6 ?
        assertEquals(dtb, null);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="zones")
    Object[][] populateTestData() {
        Set<String> ids = ZoneRulesProvider.getAvailableZoneIds();
        Object[][] rtnval = new Object[ids.size()][];
        int i = 0;
        for (String id : ids) {
            rtnval[i++] = new Object[] { id, ZoneId.of(id) };
        }
        return rtnval;
    }

    @Test(dataProvider="zones")
    public void test_parse_exactMatch(String parse, ZoneId expected) throws Exception {
        ParsePosition pos = new ParsePosition(0);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder(parse, pos);
        assertEquals(pos.getIndex(), parse.length());
        assertParsed(dtb, expected);
    }

    @Test(dataProvider="zones")
    public void test_parse_startMatch(String parse, ZoneId expected) throws Exception {
        String append = " OTHER";
        parse += append;
        ParsePosition pos = new ParsePosition(0);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder(parse, pos);
        assertEquals(pos.getIndex(), parse.length() - append.length());
        assertParsed(dtb, expected);
    }

    //-----------------------------------------------------------------------
    public void test_parse_caseInsensitive() throws Exception {
        DateTimeFormatter fmt = new DateTimeFormatterBuilder().appendZoneId().toFormatter();
        DateTimeFormatter fmtCI = new DateTimeFormatterBuilder().parseCaseInsensitive()
                                                                .appendZoneId()
                                                                .toFormatter();
        for (String zidStr : ZoneRulesProvider.getAvailableZoneIds()) {
            ZoneId zid = ZoneId.of(zidStr);
            assertEquals(fmt.parse(zidStr, Queries.zoneId()), zid);
            assertEquals(fmtCI.parse(zidStr.toLowerCase(), Queries.zoneId()), zid);
            assertEquals(fmtCI.parse(zidStr.toUpperCase(), Queries.zoneId()), zid);
            ParsePosition pos = new ParsePosition(5);
            assertEquals(fmtCI.parseToBuilder("OTHER" + zidStr.toLowerCase() + "OTHER", pos)
                              .query(Queries.zoneId()), zid);
            assertEquals(pos.getIndex(), zidStr.length() + 5);
            pos = new ParsePosition(5);
            assertEquals(fmtCI.parseToBuilder("OTHER" + zidStr.toUpperCase() + "OTHER", pos)
                              .query(Queries.zoneId()), zid);
            assertEquals(pos.getIndex(), zidStr.length() + 5);
        }
    }

    //-----------------------------------------------------------------------
    /*
    public void test_parse_endStringMatch_utc() throws Exception {
        ParsePosition pos = new ParsePosition(5);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder("OTHERUTC", pos);
        assertEquals(pos.getIndex(), 8);
        assertParsed(dtb, ZoneOffset.UTC);
    }

    public void test_parse_endStringMatch_utc_plus1() throws Exception {
        ParsePosition pos = new ParsePosition(5);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder("OTHERUTC+01:00", pos);
        assertEquals(pos.getIndex(), 14);
        assertParsed(dtb, ZoneId.of("UTC+01:00"));
    }

    //-----------------------------------------------------------------------
    public void test_parse_midStringMatch_utc() throws Exception {
        ParsePosition pos = new ParsePosition(5);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder("OTHERUTCOTHER", pos);
        assertEquals(pos.getIndex(), 8);
        assertParsed(dtb, ZoneOffset.UTC);
    }

    public void test_parse_midStringMatch_utc_plus1() throws Exception {
        ParsePosition pos = new ParsePosition(5);
        DateTimeBuilder dtb = getFormatter0(null).parseToBuilder("OTHERUTC+01:00OTHER", pos);
        assertEquals(pos.getIndex(), 14);
        assertParsed(dtb, ZoneId.of("UTC+01:00"));
    }
    */
    //-----------------------------------------------------------------------
    public void test_toString_id() {
        assertEquals(getFormatter0(null).toString(), "ZoneId()");
    }

    public void test_toString_text() {
        assertEquals(getFormatter0(TextStyle.FULL).toString(), "ZoneText(FULL)");
    }

    private void assertParsed(DateTimeBuilder dtb, ZoneId expectedZone) {
        assertEquals(dtb.query(ZoneId::from), expectedZone);
    }

}
