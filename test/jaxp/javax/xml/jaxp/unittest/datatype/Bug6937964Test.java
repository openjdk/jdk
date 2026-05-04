/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

package datatype;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/*
 * @test
 * @bug 6937964
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit/othervm datatype.Bug6937964Test
 * @summary Test Duration is normalized.
 */
public class Bug6937964Test {
    /**
     * Constant to indicate expected lexical test failure.
     */
    private static final String TEST_VALUE_FAIL = "*FAIL*";

    @Test
    public void test() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationYearMonth("P20Y15M");
        assertEquals(21, d.getYears());
    }

    @Test
    public void testNewDurationYearMonthLexicalRepresentation() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationYearMonth("P20Y15M");
        int years = d.getYears();
        assertEquals(21, years, "Return value should be normalized");
    }

    @Test
    public void testNewDurationYearMonthMilliseconds() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationYearMonth(671976000000L);
        int years = d.getYears();
        assertEquals(21, years, "Return value should be normalized");
    }

    @Test
    public void testNewDurationYearMonthBigInteger() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        BigInteger year = new BigInteger("20");
        BigInteger mon = new BigInteger("15");
        Duration d = dtf.newDurationYearMonth(true, year, mon);
        int years = d.getYears();
        assertEquals(21, years, "Return value should be normalized");
    }

    @Test
    public void testNewDurationYearMonthInt() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationYearMonth(true, 20, 15);
        int years = d.getYears();
        assertEquals(21, years, "Return value should be normalized");
    }

    @Test
    public void testNewDurationDayTimeLexicalRepresentation() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationDayTime("P1DT23H59M65S");
        int days = d.getDays();
        assertEquals(2, days, "Return value should be normalized");
    }

    @Test
    public void testNewDurationDayTimeMilliseconds() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationDayTime(172805000L);
        int days = d.getDays();
        assertEquals(2, days, "Return value should be normalized");
    }

    @Test
    public void testNewDurationDayTimeBigInteger() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        BigInteger day = new BigInteger("1");
        BigInteger hour = new BigInteger("23");
        BigInteger min = new BigInteger("59");
        BigInteger sec = new BigInteger("65");
        Duration d = dtf.newDurationDayTime(true, day, hour, min, sec);
        int days = d.getDays();
        assertEquals(2, days, "Return value should be normalized");
    }

    @Test
    public void testNewDurationDayTimeInt() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationDayTime(true, 1, 23, 59, 65);
        int days = d.getDays();
        assertEquals(2, days, "Return value should be normalized");
    }

    public static Object[][] lexicalvalues() {
        return new Object[][] {
                { "P13M", "P1Y1M" },
                { "-P13M", "-P1Y1M" },
                { "P1Y", "P1Y" },
                { "-P1Y", "-P1Y" },
                { "P1Y25M", "P3Y1M" },
                { "-P1Y25M", "-P3Y1M" }
        };
    }

    @ParameterizedTest
    @MethodSource("lexicalvalues")
    public void testNewDurationYearMonthLexicalRepresentation1(String actualLex, String expectedLex)
            throws DatatypeConfigurationException {
        DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

        try {
            Duration duration = datatypeFactory.newDurationYearMonth(actualLex);

            // was this expected to fail?
            assertNotEquals(TEST_VALUE_FAIL, expectedLex, "the value \"" + actualLex + "\" is invalid " +
                    "yet it created the Duration \"" + duration.toString() + "\"");

            // right XMLSchemaType?
            // TODO: enable test, it should pass, it fails with Exception(s)
            // for now due to a bug
            try {
                QName xmlSchemaType = duration.getXMLSchemaType();
                assertEquals(DatatypeConstants.DURATION_YEARMONTH, xmlSchemaType, "Duration created with " +
                        "XMLSchemaType of\"" + xmlSchemaType + "\" was expected to be \""
                        + DatatypeConstants.DURATION_YEARMONTH + "\" and has the value \"" + duration + "\"");
            } catch (IllegalStateException illegalStateException) {
                // TODO; this test really should pass
                System.err.println("Please fix this bug that is being ignored, for now: " + illegalStateException.getMessage());
            }

            // does it have the right value?
            assertEquals(expectedLex, duration.toString(), "Duration created with \"" + actualLex +
                    "\" was expected to be \"" + expectedLex + "\" and has the value \"" + duration + "\"");
            // Duration created with correct value
        } catch (Exception exception) {
            // was this expected to succed?
            assertEquals(TEST_VALUE_FAIL, expectedLex, "the value \"" + actualLex + "\" is valid yet " +
                    "it failed with \"" + exception + "\"");
            // expected failure
        }
    }

    public static Object[][] lexDayTimeData() {
        BigInteger one = new BigInteger("1");
        BigInteger zero = new BigInteger("0");
        BigDecimal bdZero = new BigDecimal("0");
        BigDecimal bdOne = new BigDecimal("1");
        return new Object[][] {
                // lex, isPositive, years, month, days, hours, minutes, seconds
                { "P1D", Boolean.TRUE, null, null, one, zero, zero, bdZero },
                { "PT1H", Boolean.TRUE, null, null, zero, one, zero, bdZero },
                { "PT1M", Boolean.TRUE, null, null, zero, zero, one, bdZero },
                { "PT1.1S", Boolean.TRUE, null, null, zero, zero, zero, bdOne },
                { "-PT1H1.1S", Boolean.FALSE, null, null, zero, one, zero, bdOne }, };
    }

    /**
     * TCK test failure
     */
    @ParameterizedTest
    @MethodSource("lexDayTimeData")
    public void testNewDurationDayTime005(String lex, boolean isPositive, BigInteger year, BigInteger month, BigInteger days,
                                          BigInteger hour, BigInteger minutes, BigDecimal seconds)
            throws DatatypeConfigurationException {
        StringBuilder result = new StringBuilder();
        DatatypeFactory df = DatatypeFactory.newInstance();

        Duration duration = null;
        try {
            duration = df.newDurationDayTime(isPositive, days, hour, minutes, seconds.toBigInteger());
        } catch (IllegalArgumentException e) {
            result.append("; unexpected " + e + " trying to create duration '" + lex + "'");
        }
        if (duration != null) {
            if ((duration.getSign() == 1) != isPositive) {
                result.append("; " + lex + ": wrong sign " + duration.getSign() + ", expected " + isPositive);
            }

            Number value = duration.getField(DatatypeConstants.YEARS);
            if ((value != null && year == null) || (value == null && year != null) || (value != null && !value.equals(year))) {
                result.append("; " + lex + ": wrong value of the field " + DatatypeConstants.YEARS + ": '" + value + "'" + ", expected '"
                        + year + "'");
            }

            value = duration.getField(DatatypeConstants.MONTHS);
            if ((value != null && month == null) || (value == null && month != null) || (value != null && !value.equals(month))) {
                result.append("; " + lex + ": wrong value of the field " + DatatypeConstants.MONTHS + ": '" + value + "'" + ", expected '"
                        + month + "'");
            }

            value = duration.getField(DatatypeConstants.DAYS);
            if ((value != null && days == null) || (value == null && days != null) || (value != null && !value.equals(days))) {
                result.append("; " + lex + ": wrong value of the field " + DatatypeConstants.DAYS + ": '" + value + "'" + ", expected '"
                        + days + "'");
            }

            value = duration.getField(DatatypeConstants.HOURS);
            if ((value != null && hour == null) || (value == null && hour != null) || (value != null && !value.equals(hour))) {
                result.append("; " + lex + ": wrong value of the field " + DatatypeConstants.HOURS + ": '" + value + "'" + ", expected '"
                        + hour + "'");
            }

            value = duration.getField(DatatypeConstants.MINUTES);
            if ((value != null && minutes == null) || (value == null && minutes != null) || (value != null && !value.equals(minutes))) {
                result.append("; " + lex + ": wrong value of the field " + DatatypeConstants.MINUTES + ": '" + value + "'" + ", expected '"
                        + minutes + "'");
            }

            value = duration.getField(DatatypeConstants.SECONDS);
            if (value == null || !value.equals(seconds)) {
                result.append("; " + lex + ": wrong value of the field " + DatatypeConstants.SECONDS + ": '" + value + "'" + ", expected '"
                        + seconds + "'");
            }
        }
    }
}
