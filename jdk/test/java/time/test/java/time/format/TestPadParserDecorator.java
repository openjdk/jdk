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
package test.java.time.format;

import java.time.format.*;

import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static org.testng.Assert.assertEquals;

import java.text.ParsePosition;
import java.time.format.DateTimeBuilder;

import org.testng.annotations.Test;

/**
 * Test PadPrinterParserDecorator.
 */
@Test(groups={"implementation"})
public class TestPadParserDecorator extends AbstractTestPrinterParser {

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=IndexOutOfBoundsException.class)
    public void test_parse_negativePosition() throws Exception {
        builder.padNext(3, '-').appendLiteral('Z');
        getFormatter().parseToBuilder("--Z", new ParsePosition(-1));
    }

    @Test(expectedExceptions=IndexOutOfBoundsException.class)
    public void test_parse_offEndPosition() throws Exception {
        builder.padNext(3, '-').appendLiteral('Z');
        getFormatter().parseToBuilder("--Z", new ParsePosition(4));
    }

    //-----------------------------------------------------------------------
    public void test_parse() throws Exception {
        ParsePosition pos = new ParsePosition(0);
        builder.padNext(3, '-').appendValue(MONTH_OF_YEAR, 1, 3, SignStyle.NEVER);
        DateTimeBuilder dtb = getFormatter().parseToBuilder("--2", pos);
        assertEquals(pos.getIndex(), 3);
        assertEquals(dtb.getFieldValueMap().size(), 1);
        assertEquals(dtb.getLong(MONTH_OF_YEAR), 2L);
    }

    public void test_parse_noReadBeyond() throws Exception {
        ParsePosition pos = new ParsePosition(0);
        builder.padNext(3, '-').appendValue(MONTH_OF_YEAR, 1, 3, SignStyle.NEVER);
        DateTimeBuilder dtb = getFormatter().parseToBuilder("--22", pos);
        assertEquals(pos.getIndex(), 3);
        assertEquals(dtb.getFieldValueMap().size(), 1);
        assertEquals(dtb.getLong(MONTH_OF_YEAR), 2L);
    }

    public void test_parse_textLessThanPadWidth() throws Exception {
        ParsePosition pos = new ParsePosition(0);
        builder.padNext(3, '-').appendValue(MONTH_OF_YEAR, 1, 3, SignStyle.NEVER);
        DateTimeBuilder dtb = getFormatter().parseToBuilder("-1", pos);
        assertEquals(pos.getErrorIndex(), 0);
    }

    public void test_parse_decoratedErrorPassedBack() throws Exception {
        ParsePosition pos = new ParsePosition(0);
        builder.padNext(3, '-').appendValue(MONTH_OF_YEAR, 1, 3, SignStyle.NEVER);
        DateTimeBuilder dtb = getFormatter().parseToBuilder("--A", pos);
        assertEquals(pos.getErrorIndex(), 2);
    }

    public void test_parse_decoratedDidNotParseToPadWidth() throws Exception {
        ParsePosition pos = new ParsePosition(0);
        builder.padNext(3, '-').appendValue(MONTH_OF_YEAR, 1, 3, SignStyle.NEVER);
        DateTimeBuilder dtb = getFormatter().parseToBuilder("-1X", pos);
        assertEquals(pos.getErrorIndex(), 0);
    }

    //-----------------------------------------------------------------------
    public void test_parse_decoratedStartsWithPad() throws Exception {
        ParsePosition pos = new ParsePosition(0);
        builder.padNext(8, '-').appendLiteral("-HELLO-");
        DateTimeBuilder dtb = getFormatter().parseToBuilder("--HELLO-", pos);
        assertEquals(pos.getIndex(), 8);
        assertEquals(dtb.getFieldValueMap().size(), 0);
    }

}
