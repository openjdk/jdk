/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigDecimal;
import java.math.BigInteger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.namespace.QName;

import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 6937964
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm -DrunSecMngr=true datatype.Bug6937964Test
 * @run testng/othervm datatype.Bug6937964Test
 * @summary Test Duration is normalized.
 */
@Listeners({jaxp.library.BasePolicy.class})
public class Bug6937964Test {
    /**
     * Print debugging to System.err.
     */
    private static final boolean DEBUG = false;
    /**
     * Constant to indicate expected lexical test failure.
     */
    private static final String TEST_VALUE_FAIL = "*FAIL*";

    private static final String FIELD_UNDEFINED = "FIELD_UNDEFINED";
    static final DatatypeConstants.Field[] fields = { DatatypeConstants.YEARS, DatatypeConstants.MONTHS, DatatypeConstants.DAYS, DatatypeConstants.HOURS,
            DatatypeConstants.MINUTES, DatatypeConstants.SECONDS };

    @Test
    public void test() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationYearMonth("P20Y15M");
        int years = d.getYears();
        System.out.println(d.getYears() == 21 ? "pass" : "fail");
    }

    @Test
    public void testNewDurationYearMonthLexicalRepresentation() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationYearMonth("P20Y15M");
        int years = d.getYears();
        Assert.assertTrue(years == 21, "Return value should be normalized");
    }

    @Test
    public void testNewDurationYearMonthMilliseconds() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationYearMonth(671976000000L);
        int years = d.getYears();
        System.out.println("Years: " + years);
        Assert.assertTrue(years == 21, "Return value should be normalized");
    }

    @Test
    public void testNewDurationYearMonthBigInteger() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        BigInteger year = new BigInteger("20");
        BigInteger mon = new BigInteger("15");
        Duration d = dtf.newDurationYearMonth(true, year, mon);
        int years = d.getYears();
        Assert.assertTrue(years == 21, "Return value should be normalized");
    }

    @Test
    public void testNewDurationYearMonthInt() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationYearMonth(true, 20, 15);
        int years = d.getYears();
        Assert.assertTrue(years == 21, "Return value should be normalized");
    }

    @Test
    public void testNewDurationDayTimeLexicalRepresentation() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationDayTime("P1DT23H59M65S");
        int days = d.getDays();
        Assert.assertTrue(days == 2, "Return value should be normalized");
    }

    @Test
    public void testNewDurationDayTimeMilliseconds() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationDayTime(172805000L);
        int days = d.getDays();
        Assert.assertTrue(days == 2, "Return value should be normalized");
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
        System.out.println("Days: " + days);
        Assert.assertTrue(days == 2, "Return value should be normalized");
    }

    @Test
    public void testNewDurationDayTimeInt() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        Duration d = dtf.newDurationDayTime(true, 1, 23, 59, 65);
        int days = d.getDays();
        System.out.println("Days: " + days);
        Assert.assertTrue(days == 2, "Return value should be normalized");
    }

    @Test
    public final void testNewDurationYearMonthLexicalRepresentation1() {

        /**
         * Lexical test values to test.
         */
        final String[] TEST_VALUES_LEXICAL = { "P13M", "P1Y1M", "-P13M", "-P1Y1M", "P1Y", "P1Y", "-P1Y", "-P1Y", "P1Y25M", "P3Y1M", "-P1Y25M", "-P3Y1M" };

        DatatypeFactory datatypeFactory = null;
        try {
            datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException datatypeConfigurationException) {
            Assert.fail(datatypeConfigurationException.toString());
        }

        if (DEBUG) {
            System.err.println("DatatypeFactory created: " + datatypeFactory.toString());
        }

        // test each value
        for (int onTestValue = 0; onTestValue < TEST_VALUES_LEXICAL.length; onTestValue = onTestValue + 2) {

            if (DEBUG) {
                System.err.println("testing value: \"" + TEST_VALUES_LEXICAL[onTestValue] + "\", expecting: \"" + TEST_VALUES_LEXICAL[onTestValue + 1] + "\"");
            }

            try {
                Duration duration = datatypeFactory.newDurationYearMonth(TEST_VALUES_LEXICAL[onTestValue]);

                if (DEBUG) {
                    System.err.println("Duration created: \"" + duration.toString() + "\"");
                }

                // was this expected to fail?
                if (TEST_VALUES_LEXICAL[onTestValue + 1].equals(TEST_VALUE_FAIL)) {
                    Assert.fail("the value \"" + TEST_VALUES_LEXICAL[onTestValue] + "\" is invalid yet it created the Duration \"" + duration.toString() + "\"");
                }

                // right XMLSchemaType?
                // TODO: enable test, it should pass, it fails with Exception(s)
                // for now due to a bug
                try {
                    QName xmlSchemaType = duration.getXMLSchemaType();
                    if (!xmlSchemaType.equals(DatatypeConstants.DURATION_YEARMONTH)) {
                        Assert.fail("Duration created with XMLSchemaType of\"" + xmlSchemaType + "\" was expected to be \""
                                + DatatypeConstants.DURATION_YEARMONTH + "\" and has the value \"" + duration.toString() + "\"");
                    }
                } catch (IllegalStateException illegalStateException) {
                    // TODO; this test really should pass
                    System.err.println("Please fix this bug that is being ignored, for now: " + illegalStateException.getMessage());
                }

                // does it have the right value?
                if (!TEST_VALUES_LEXICAL[onTestValue + 1].equals(duration.toString())) {
                    Assert.fail("Duration created with \"" + TEST_VALUES_LEXICAL[onTestValue] + "\" was expected to be \""
                            + TEST_VALUES_LEXICAL[onTestValue + 1] + "\" and has the value \"" + duration.toString() + "\"");
                }

                // Duration created with correct value
            } catch (Exception exception) {

                if (DEBUG) {
                    System.err.println("Exception in creating duration: \"" + exception.toString() + "\"");
                }

                // was this expected to succed?
                if (!TEST_VALUES_LEXICAL[onTestValue + 1].equals(TEST_VALUE_FAIL)) {
                    Assert.fail("the value \"" + TEST_VALUES_LEXICAL[onTestValue] + "\" is valid yet it failed with \"" + exception.toString() + "\"");
                }
                // expected failure
            }
        }
    }

    /**
     * TCK test failure
     */
    @Test
    public void testNewDurationDayTime005() {
        BigInteger one = new BigInteger("1");
        BigInteger zero = new BigInteger("0");
        BigDecimal bdZero = new BigDecimal("0");
        BigDecimal bdOne = new BigDecimal("1");

        Object[][] values = {
                // lex, isPositive, years, month, days, hours, minutes, seconds
                { "P1D", Boolean.TRUE, null, null, one, zero, zero, bdZero }, { "PT1H", Boolean.TRUE, null, null, zero, one, zero, bdZero },
                { "PT1M", Boolean.TRUE, null, null, zero, zero, one, bdZero }, { "PT1.1S", Boolean.TRUE, null, null, zero, zero, zero, bdOne },
                { "-PT1H1.1S", Boolean.FALSE, null, null, zero, one, zero, bdOne }, };

        StringBuffer result = new StringBuffer();
        DatatypeFactory df = null;

        try {
            df = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            Assert.fail(e.toString());
        }

        for (int valueIndex = 0; valueIndex < values.length; ++valueIndex) {
            Duration duration = null;
            try {
                duration = df.newDurationDayTime(values[valueIndex][1].equals(Boolean.TRUE), ((BigInteger) values[valueIndex][4]).intValue(),
                        ((BigInteger) values[valueIndex][5]).intValue(), ((BigInteger) values[valueIndex][6]).intValue(),
                        ((BigDecimal) values[valueIndex][7]).intValue());
            } catch (IllegalArgumentException e) {
                result.append("; unexpected " + e + " trying to create duration \'" + values[valueIndex][0] + "\'");
            }
            if (duration != null) {
                if ((duration.getSign() == 1) != values[valueIndex][1].equals(Boolean.TRUE)) {
                    result.append("; " + values[valueIndex][0] + ": wrong sign " + duration.getSign() + ", expected " + values[valueIndex][1]);
                }
                for (int i = 0; i < fields.length; ++i) {
                    Number value = duration.getField(fields[i]);
                    if ((value != null && values[valueIndex][2 + i] == null) || (value == null && values[valueIndex][2 + i] != null)
                            || (value != null && !value.equals(values[valueIndex][2 + i]))) {
                        result.append("; " + values[valueIndex][0] + ": wrong value of the field " + fields[i] + ": \'" + value + "\'" + ", expected \'"
                                + values[valueIndex][2 + i] + "\'");
                    }
                }
            }
        }

        if (result.length() > 0) {
            Assert.fail(result.substring(2));
        }
        System.out.println("OK");

    }
}
