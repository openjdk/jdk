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
 * Copyright (c) 2011-2012, Stephen Colebourne & Michael Nascimento Santos
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
package tck.java.time.format;

import static org.testng.Assert.assertEquals;

import java.time.format.DateTimeFormatSymbols;
import java.util.Arrays;
import java.util.Locale;

import org.testng.annotations.Test;

/**
 * Test DateTimeFormatSymbols.
 */
@Test
public class TCKDateTimeFormatSymbols {

    @Test
    public void test_getAvailableLocales() {
        Locale[] locales = DateTimeFormatSymbols.getAvailableLocales();
        assertEquals(locales.length > 0, true);
        assertEquals(Arrays.asList(locales).contains(Locale.US), true);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_of_Locale() {
        DateTimeFormatSymbols loc1 = DateTimeFormatSymbols.of(Locale.CANADA);
        assertEquals(loc1.getZeroDigit(), '0');
        assertEquals(loc1.getPositiveSign(), '+');
        assertEquals(loc1.getNegativeSign(), '-');
        assertEquals(loc1.getDecimalSeparator(), '.');
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_STANDARD() {
        DateTimeFormatSymbols loc1 = DateTimeFormatSymbols.STANDARD;
        assertEquals(loc1.getZeroDigit(), '0');
        assertEquals(loc1.getPositiveSign(), '+');
        assertEquals(loc1.getNegativeSign(), '-');
        assertEquals(loc1.getDecimalSeparator(), '.');
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_zeroDigit() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.STANDARD;
        assertEquals(base.withZeroDigit('A').getZeroDigit(), 'A');
    }

    @Test
    public void test_positiveSign() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.STANDARD;
        assertEquals(base.withPositiveSign('A').getPositiveSign(), 'A');
    }

    @Test
    public void test_negativeSign() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.STANDARD;
        assertEquals(base.withNegativeSign('A').getNegativeSign(), 'A');
    }

    @Test
    public void test_decimalSeparator() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.STANDARD;
        assertEquals(base.withDecimalSeparator('A').getDecimalSeparator(), 'A');
    }

    //-----------------------------------------------------------------------
    /* TBD: convertToDigit and convertNumberToI18N are package-private methods
    @Test
    public void test_convertToDigit_base() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.STANDARD;
        assertEquals(base.convertToDigit('0'), 0);
        assertEquals(base.convertToDigit('1'), 1);
        assertEquals(base.convertToDigit('9'), 9);
        assertEquals(base.convertToDigit(' '), -1);
        assertEquals(base.convertToDigit('A'), -1);
    }

    @Test
    public void test_convertToDigit_altered() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.STANDARD.withZeroDigit('A');
        assertEquals(base.convertToDigit('A'), 0);
        assertEquals(base.convertToDigit('B'), 1);
        assertEquals(base.convertToDigit('J'), 9);
        assertEquals(base.convertToDigit(' '), -1);
        assertEquals(base.convertToDigit('0'), -1);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_convertNumberToI18N_base() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.STANDARD;
        assertEquals(base.convertNumberToI18N("134"), "134");
    }

    @Test
    public void test_convertNumberToI18N_altered() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.STANDARD.withZeroDigit('A');
        assertEquals(base.convertNumberToI18N("134"), "BDE");
    }
    */
    //-----------------------------------------------------------------------
    @Test
    public void test_equalsHashCode1() {
        DateTimeFormatSymbols a = DateTimeFormatSymbols.STANDARD;
        DateTimeFormatSymbols b = DateTimeFormatSymbols.STANDARD;
        assertEquals(a.equals(b), true);
        assertEquals(b.equals(a), true);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void test_equalsHashCode2() {
        DateTimeFormatSymbols a = DateTimeFormatSymbols.STANDARD.withZeroDigit('A');
        DateTimeFormatSymbols b = DateTimeFormatSymbols.STANDARD.withZeroDigit('A');
        assertEquals(a.equals(b), true);
        assertEquals(b.equals(a), true);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void test_equalsHashCode3() {
        DateTimeFormatSymbols a = DateTimeFormatSymbols.STANDARD.withZeroDigit('A');
        DateTimeFormatSymbols b = DateTimeFormatSymbols.STANDARD.withDecimalSeparator('A');
        assertEquals(a.equals(b), false);
        assertEquals(b.equals(a), false);
    }

    @Test
    public void test_equalsHashCode_bad() {
        DateTimeFormatSymbols a = DateTimeFormatSymbols.STANDARD;
        assertEquals(a.equals(""), false);
        assertEquals(a.equals(null), false);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_toString_base() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.STANDARD;
        assertEquals(base.toString(), "Symbols[0+-.]");
    }

    @Test
    public void test_toString_altered() {
        DateTimeFormatSymbols base = DateTimeFormatSymbols.of(Locale.US).withZeroDigit('A').withDecimalSeparator('@');
        assertEquals(base.toString(), "Symbols[A+-@]");
    }

}
