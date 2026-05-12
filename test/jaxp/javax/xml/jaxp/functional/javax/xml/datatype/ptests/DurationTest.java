/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.datatype.ptests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.function.Function;

import static javax.xml.datatype.DatatypeConstants.DAYS;
import static javax.xml.datatype.DatatypeConstants.HOURS;
import static javax.xml.datatype.DatatypeConstants.MINUTES;
import static javax.xml.datatype.DatatypeConstants.MONTHS;
import static javax.xml.datatype.DatatypeConstants.SECONDS;
import static javax.xml.datatype.DatatypeConstants.YEARS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.datatype.ptests.DurationTest
 * @summary Class containing the test cases for Duration.
 */
public class DurationTest {

    private static DatatypeFactory datatypeFactory = null;

    @BeforeAll
    public static void setup() throws DatatypeConfigurationException {
        datatypeFactory = DatatypeFactory.newInstance();
    }

    public static Object[][] getLegalNumberDuration() {
        return new Object[][] {
                // is positive, year, month, day, hour, minute, second
                { true, 1, 1, 1, 1, 1, 1 },
                { false, 1, 1, 1, 1, 1, 1 },
                { true, 1, 0, 0, 0, 0, 0 },
                { false, 1, 0, 0, 0, 0, 0 }
        };
    }

    /*
     * Test for constructor Duration(boolean isPositive,int years,int months,
     * int days,int hours,int minutes,int seconds).
     */
    @ParameterizedTest
    @MethodSource("getLegalNumberDuration")
    public void checkNumberDurationPos(boolean isPositive, int years, int months, int days, int hours, int minutes, int seconds) {
        datatypeFactory.newDuration(isPositive, years, months, days, hours, minutes, seconds);
    }

    public static Object[][] getIllegalNumberDuration() {
        return new Object[][] {
                // is positive, year, month, day, hour, minute, second
                { true, 1, 1, -1, 1, 1, 1 },
                { false, 1, 1, -1, 1, 1, 1 },
                { true, undef, undef, undef, undef, undef, undef },
                { false, undef, undef, undef, undef, undef, undef }
        };
    }

    /*
     * Test for constructor Duration(boolean isPositive,int years,int months,
     * int days,int hours,int minutes,int seconds), if any of the fields is
     * negative should throw IllegalArgumentException.
     */
    @ParameterizedTest
    @MethodSource("getIllegalNumberDuration")
    public void checkDurationNumberNeg(boolean isPositive, int years, int months, int days, int hours, int minutes, int seconds) {
        assertThrows(
                IllegalArgumentException.class,
                () -> datatypeFactory.newDuration(isPositive, years, months, days, hours, minutes, seconds));
    }

    public static Object[][] getLegalBigIntegerDuration() {
        return new Object[][] {
                // is positive, year, month, day, hour, minute, second
                { true, zero, zero, zero, zero, zero, new BigDecimal(zero) },
                { false, zero, zero, zero, zero, zero, new BigDecimal(zero) },
                { true, one, one, one, one, one, new BigDecimal(one) },
                { false, one, one, one, one, one, new BigDecimal(one) },
                { true, null, null, null, null, null, new BigDecimal(one) },
                { false, null, null, null, null, null, new BigDecimal(one) } };
    }

    /*
     * Test for constructor Duration(boolean isPositive,BigInteger
     * years,BigInteger months, BigInteger days,BigInteger hours,BigInteger
     * minutes,BigDecimal seconds).
     */
    @ParameterizedTest
    @MethodSource("getLegalBigIntegerDuration")
    public void checkBigIntegerDurationPos(boolean isPositive, BigInteger years, BigInteger months, BigInteger days, BigInteger hours, BigInteger minutes,
            BigDecimal seconds) {
        datatypeFactory.newDuration(isPositive, years, months, days, hours, minutes, seconds);
    }

    public static Object[][] getIllegalBigIntegerDuration() {
        return new Object[][] {
                // is positive, year, month, day, hour, minute, second
                { true, null, null, null, null, null, null },
                { false, null, null, null, null, null, null }
        };
    }

    /*
     * Test for constructor Duration(boolean isPositive,BigInteger
     * years,BigInteger months, BigInteger days,BigInteger hours,BigInteger
     * minutes,BigDecimal seconds), if all the fields are null should throw
     * IllegalArgumentException.
     */
    @ParameterizedTest
    @MethodSource("getIllegalBigIntegerDuration")
    public void checkBigIntegerDurationNeg(
            boolean isPositive, BigInteger years, BigInteger months, BigInteger days, BigInteger hours, BigInteger minutes, BigDecimal seconds) {
        assertThrows(IllegalArgumentException.class, () -> datatypeFactory.newDuration(isPositive, years, months, days, hours, minutes, seconds));
    }

    /*
     * Test for constructor Duration(long durationInMilliSeconds)
     */
    @ParameterizedTest
    @ValueSource(longs={ 1000000, 0, Long.MAX_VALUE, Long.MIN_VALUE })
    public void checkMilliSecondDuration(long millisec) {
        datatypeFactory.newDuration(millisec);
    }

    /*
     * Test for constructor Duration(java.lang.String lexicalRepresentation)
     */
    @ParameterizedTest
    @ValueSource(strings={ "P1Y1M1DT1H1M1S", "-P1Y1M1DT1H1M1S" })
    public void checkLexicalDurationPos(String lexRepresentation) {
        datatypeFactory.newDuration(lexRepresentation);
    }

    /*
     * Test for constructor Duration(java.lang.String lexicalRepresentation),
     * null should throw NullPointerException
     */
    @Test
    public void checkLexicalDurationNull() {
        assertThrows(NullPointerException.class, () -> datatypeFactory.newDuration(null));
    }

    /*
     * Test for constructor Duration(java.lang.String lexicalRepresentation),
     * invalid lex should throw IllegalArgumentException
     */
    @ParameterizedTest
    @ValueSource(strings={ "P1Y1M1DT1H1M1S ", " P1Y1M1DT1H1M1S", "X1Y1M1DT1H1M1S", "", "P1Y2MT" })
    public void checkLexicalDurationNeg(String lexRepresentation) {
        assertThrows(IllegalArgumentException.class, () -> datatypeFactory.newDuration(lexRepresentation));
    }

    public static Object[][] getEqualDurations() {
        return new Object[][] { { "P1Y1M1DT1H1M1S", "P1Y1M1DT1H1M1S" } };
    }

    /*
     * Test for compare() both durations valid and equal.
     */
    @ParameterizedTest
    @MethodSource("getEqualDurations")
    public void checkDurationEqual(String lexRepresentation1, String lexRepresentation2) {
        Duration duration1 = datatypeFactory.newDuration(lexRepresentation1);
        Duration duration2 = datatypeFactory.newDuration(lexRepresentation2);
        assertEquals(duration1, duration2);
    }

    public static Object[][] getGreaterDuration() {
        return new Object[][] {
                { "P1Y1M1DT1H1M2S", "P1Y1M1DT1H1M1S" },
                { "P1Y1M1DT1H1M1S", "-P1Y1M1DT1H1M2S" },
                { "P1Y1M1DT1H1M2S", "-P1Y1M1DT1H1M1S" },
                { "-P1Y1M1DT1H1M1S", "-P1Y1M1DT1H1M2S" }, };
    }

    /*
     * Test for compare() both durations valid and lhs > rhs.
     */
    @ParameterizedTest
    @MethodSource("getGreaterDuration")
    public void checkDurationCompare(String lexRepresentation1, String lexRepresentation2) {
        Duration duration1 = datatypeFactory.newDuration(lexRepresentation1);
        Duration duration2 = datatypeFactory.newDuration(lexRepresentation2);
        assertEquals(DatatypeConstants.GREATER, duration1.compare(duration2));
    }

    public static Object[][] getNotEqualDurations() {
        return new Object[][] {
                { "P1Y1M1DT1H1M1S", "-P1Y1M1DT1H1M1S" },
                { "P2Y1M1DT1H1M1S", "P1Y1M1DT1H1M1S" } };
    }

    /*
     * Test for equals() both durations valid and lhs not equals rhs.
     */
    @ParameterizedTest
    @MethodSource("getNotEqualDurations")
    public void checkDurationNotEqual(String lexRepresentation1, String lexRepresentation2) {
        Duration duration1 = datatypeFactory.newDuration(lexRepresentation1);
        Duration duration2 = datatypeFactory.newDuration(lexRepresentation2);
        assertNotEquals(duration1, duration2);
    }

    public static Object[][] getDurationAndSign() {
        return new Object[][] {
                { "P0Y0M0DT0H0M0S", 0 },
                { "P1Y0M0DT0H0M0S", 1 },
                { "-P1Y0M0DT0H0M0S", -1 } };
    }

    /*
     * Test for Duration.getSign().
     */
    @ParameterizedTest
    @MethodSource("getDurationAndSign")
    public void checkDurationSign(String lexRepresentation, int expectedSign) {
        Duration duration = datatypeFactory.newDuration(lexRepresentation);
        assertEquals(expectedSign, duration.getSign());
    }

    /*
     * Test for Duration.negate().
     */
    @Test
    public void checkDurationNegate() {
        Duration durationPos = datatypeFactory.newDuration("P1Y0M0DT0H0M0S");
        Duration durationNeg = datatypeFactory.newDuration("-P1Y0M0DT0H0M0S");

        assertEquals(durationNeg, durationPos.negate());
        assertEquals(durationPos, durationNeg.negate());
        assertEquals(durationPos, durationPos.negate().negate());
    }

    /*
     * Test for Duration.isShorterThan(Duration) and
     * Duration.isLongerThan(Duration).
     */
    @Test
    public void checkDurationShorterLonger() {
        Duration shorter = datatypeFactory.newDuration("P1Y1M1DT1H1M1S");
        Duration longer = datatypeFactory.newDuration("P2Y1M1DT1H1M1S");

        assertTrue(shorter.isShorterThan(longer));
        assertFalse(longer.isShorterThan(shorter));
        assertFalse(shorter.isShorterThan(shorter));

        assertTrue(longer.isLongerThan(shorter));
        assertFalse(shorter.isLongerThan(longer));
        assertFalse(shorter.isLongerThan(shorter));
    }

    /*
     * Test for Duration.isSet().
     */
    @Test
    public void checkDurationIsSet() {
        Duration duration1 = datatypeFactory.newDuration(true, 1, 1, 1, 1, 1, 1);
        Duration duration2 = datatypeFactory.newDuration(true, 0, 0, 0, 0, 0, 0);

        assertTrue(duration1.isSet(YEARS));
        assertTrue(duration1.isSet(MONTHS));
        assertTrue(duration1.isSet(DAYS));
        assertTrue(duration1.isSet(HOURS));
        assertTrue(duration1.isSet(MINUTES));
        assertTrue(duration1.isSet(SECONDS));

        assertTrue(duration2.isSet(YEARS));
        assertTrue(duration2.isSet(MONTHS));
        assertTrue(duration2.isSet(DAYS));
        assertTrue(duration2.isSet(HOURS));
        assertTrue(duration2.isSet(MINUTES));
        assertTrue(duration2.isSet(SECONDS));

        Duration duration66 = datatypeFactory.newDuration(true, null, null, zero, null, null, null);
        assertFalse(duration66.isSet(YEARS));
        assertFalse(duration66.isSet(MONTHS));
        assertFalse(duration66.isSet(HOURS));
        assertFalse(duration66.isSet(MINUTES));
        assertFalse(duration66.isSet(SECONDS));

        Duration duration3 = datatypeFactory.newDuration("P1D");
        assertFalse(duration3.isSet(YEARS));
        assertFalse(duration3.isSet(MONTHS));
        assertFalse(duration3.isSet(HOURS));
        assertFalse(duration3.isSet(MINUTES));
        assertFalse(duration3.isSet(SECONDS));
    }

    /*
     * Test Duration.isSet(Field) throws NPE if the field parameter is null.
     */
    public void checkDurationIsSetNeg() {
        Duration duration = datatypeFactory.newDuration(true, 0, 0, 0, 0, 0, 0);
        assertThrows(NullPointerException.class, () -> duration.isSet(null));
    }

    /*
     * Test for -getField(DatatypeConstants.Field) DatatypeConstants.Field is
     * null - throws NPE.
     */
    @Test
    public void checkDurationGetFieldNeg() {
        Duration duration = datatypeFactory.newDuration("P1Y1M1DT1H1M1S");
        assertThrows(NullPointerException.class, () -> duration.getField(null));
    }

    public static Object[][] getDurationAndFields() {
        return new Object[][] {
                { "P1Y1M1DT1H1M1S", one, one, one, one, one, new BigDecimal(one) },
                { "PT1M", null, null, null, null, one, null },
                { "P1M", null, one, null, null, null, null } };
    }

    /*
     * Test for Duration.getField(DatatypeConstants.Field).
     */
    @ParameterizedTest
    @MethodSource("getDurationAndFields")
    public void checkDurationGetField(String lexRepresentation, BigInteger years, BigInteger months, BigInteger days, BigInteger hours, BigInteger minutes,
            BigDecimal seconds) {
        Duration duration = datatypeFactory.newDuration(lexRepresentation);

        assertEquals(years, duration.getField(YEARS));
        assertEquals(months, duration.getField(MONTHS));
        assertEquals(days, duration.getField(DAYS));
        assertEquals(hours, duration.getField(HOURS));
        assertEquals(minutes, duration.getField(MINUTES));
        assertEquals(seconds, duration.getField(SECONDS));
    }

    public static Object[][] getNumberAndString() {
        return new Object[][] {
                // is positive, year, month, day, hour, minute, second, lexical
                { true, 1, 1, 1, 1, 1, 1, "P1Y1M1DT1H1M1S" },
                { false, 1, 1, 1, 1, 1, 1, "-P1Y1M1DT1H1M1S" },
                { true, 0, 0, 0, 0, 0, 0, "P0Y0M0DT0H0M0S" },
                { false, 0, 0, 0, 0, 0, 0, "P0Y0M0DT0H0M0S" }
        };
    }

    /*
     * Test for - toString().
     */
    @ParameterizedTest
    @MethodSource("getNumberAndString")
    public void checkDurationToString(boolean isPositive, int years, int months, int days, int hours, int minutes, int seconds, String lexical) {
        Duration duration = datatypeFactory.newDuration(isPositive, years, months, days, hours, minutes, seconds);
        assertEquals(lexical, duration.toString());

        assertEquals(duration, datatypeFactory.newDuration(duration.toString()));
    }

    public static Object[][] getDurationAndField() {
        Function<Duration, Integer> getyears = Duration::getYears;
        Function<Duration, Integer> getmonths = Duration::getMonths;
        Function<Duration, Integer> getdays = Duration::getDays;
        Function<Duration, Integer> gethours = Duration::getHours;
        Function<Duration, Integer> getminutes = Duration::getMinutes;
        Function<Duration, Integer> getseconds = Duration::getSeconds;
        return new Object[][] {
                { "P1Y1M1DT1H1M1S", getyears, 1 },
                { "P1M1DT1H1M1S", getyears, 0 },
                { "P1Y1M1DT1H1M1S", getmonths, 1 },
                { "P1Y1DT1H1M1S", getmonths, 0 },
                { "P1Y1M1DT1H1M1S", getdays, 1 },
                { "P1Y1MT1H1M1S", getdays, 0 },
                { "P1Y1M1DT1H1M1S", gethours, 1 },
                { "P1Y1M1DT1M1S", gethours, 0 },
                { "P1Y1M1DT1H1M1S", getminutes, 1 },
                { "P1Y1M1DT1H1S", getminutes, 0 },
                { "P1Y1M1DT1H1M1S", getseconds, 1 },
                { "P1Y1M1DT1H1M", getseconds, 0 },
                { "P1Y1M1DT1H1M100000000S", getseconds, 100000000 }, };
    }

    /*
     * Test for Duration.getYears(), getMonths(), etc.
     */
    @ParameterizedTest
    @MethodSource("getDurationAndField")
    public void checkDurationGetOneField(String lexRepresentation, Function<Duration, Integer> getter, int expectedValue) {
        Duration duration = datatypeFactory.newDuration(lexRepresentation);
        assertEquals(expectedValue, getter.apply(duration).intValue());
    }

    /*
     * Test for - getField(SECONDS)
     */
    @Test
    public void checkDurationGetSecondsField() {
        Duration duration85 = datatypeFactory.newDuration("P1Y1M1DT1H1M100000000S");
        assertEquals(100000000, (duration85.getField(SECONDS)).intValue());
    }

    /*
     * getTimeInMillis(java.util.Calendar startInstant) returns milliseconds
     * between startInstant and startInstant plus this Duration.
     */
    @Test
    public void checkDurationGetTimeInMillis() {
        Duration duration = datatypeFactory.newDuration("PT1M1S");
        Calendar calendar = Calendar.getInstance();
        assertEquals(61000, duration.getTimeInMillis(calendar));
    }

    /*
     * getTimeInMillis(java.util.Calendar startInstant) returns milliseconds
     * between startInstant and startInstant plus this Duration throws NPE if
     * startInstant parameter is null.
     */
    @Test
    public void checkDurationGetTimeInMillisNeg() {
        Duration duration = datatypeFactory.newDuration("PT1M1S");
        assertThrows(NullPointerException.class, () -> duration.getTimeInMillis((Calendar) null));
    }

    public static Object[][] getDurationsForHash() {
        return new Object[][] {
                { "P1Y1M1DT1H1M1S", "P1Y1M1DT1H1M1S" },
                { "P1D", "PT24H" },
                { "PT1H", "PT60M" },
                { "PT1M", "PT60S" },
                { "P1Y", "P12M" } };
    }

    /*
     * Test for Duration.hashcode(). hashcode() should return same value for
     * some equal durations.
     */
    @ParameterizedTest
    @MethodSource("getDurationsForHash")
    public void checkDurationHashCode(String lexRepresentation1, String lexRepresentation2) {
        Duration duration1 = datatypeFactory.newDuration(lexRepresentation1);
        Duration duration2 = datatypeFactory.newDuration(lexRepresentation2);
        int hash1 = duration1.hashCode();
        int hash2 = duration2.hashCode();
        assertEquals(hash1, hash2, " generated hash1 : " + hash1 + " generated hash2 : " + hash2);
    }

    public static Object[][] getDurationsForAdd() {
        return new Object[][] {
                // initVal, addVal, resultVal
                { "P1Y1M1DT1H1M1S", "P1Y1M1DT1H1M1S", "P2Y2M2DT2H2M2S" },
                { "P1Y1M1DT1H1M1S", "-P1Y1M1DT1H1M1S", "P0Y0M0DT0H0M0S" },
                { "-P1Y1M1DT1H1M1S", "-P1Y1M1DT1H1M1S", "-P2Y2M2DT2H2M2S" }, };
    }

    /*
     * Test for add(Duration rhs).
     */
    @ParameterizedTest
    @MethodSource("getDurationsForAdd")
    public void checkDurationAdd(String initVal, String addVal, String result) {
        Duration durationInit = datatypeFactory.newDuration(initVal);
        Duration durationAdd = datatypeFactory.newDuration(addVal);
        Duration durationResult = datatypeFactory.newDuration(result);

        assertEquals(durationInit.add(durationAdd), durationResult);
    }

    public static Object[][] getDurationsForAddNeg() {
        return new Object[][] {
                // initVal, addVal
                { "P1Y", "-P1D" },
                { "-P1Y", "P1D" }, };
    }

    /*
     * Test for add(Duration rhs).
     * "1 year" + "-1 day" or "-1 year" + "1 day" should throw IllegalStateException
     */
    @ParameterizedTest
    @MethodSource("getDurationsForAddNeg")
    public void checkDurationAddNeg(String initVal, String addVal) {
        Duration durationInit = datatypeFactory.newDuration(initVal);
        Duration durationAdd = addVal == null ? null : datatypeFactory.newDuration(addVal);
        assertThrows(IllegalStateException.class, () -> durationInit.add(durationAdd));
    }

    /*
     * Test for add(Duration rhs) 'rhs' is null , should throw NPE.
     */
    @Test
    public void checkDurationAddNull() {
        Duration durationInit = datatypeFactory.newDuration("P1Y1M1DT1H1M1S");
        assertThrows(NullPointerException.class, () -> durationInit.add(null));
    }

    /*
     * Test Duration#compare(Duration duration) with large durations.
     *
     * Bug # 4972785 UnsupportedOperationException is expected
     *
     */
    @Test
    public void checkDurationCompareLarge() {
        String duration1Lex = "P100000000000000000000D";
        String duration2Lex = "PT2400000000000000000000H";

        Duration duration1 = datatypeFactory.newDuration(duration1Lex);
        Duration duration2 = datatypeFactory.newDuration(duration2Lex);
        assertThrows(UnsupportedOperationException.class, () -> duration1.compare(duration2));
    }

    /*
     * Test Duration#getXMLSchemaType().
     *
     * Bug # 5049544 Duration.getXMLSchemaType shall return the correct result
     *
     */
    @Test
    public void checkDurationGetXMLSchemaType() {
        // DURATION
        Duration duration = datatypeFactory.newDuration("P1Y1M1DT1H1M1S");
        QName duration_xmlSchemaType = duration.getXMLSchemaType();
        assertEquals(DatatypeConstants.DURATION, duration_xmlSchemaType, "Expected DatatypeConstants.DURATION, returned " + duration_xmlSchemaType.toString());

        // DURATION_DAYTIME
        Duration duration_dayTime = datatypeFactory.newDuration("P1DT1H1M1S");
        QName duration_dayTime_xmlSchemaType = duration_dayTime.getXMLSchemaType();
        assertEquals(DatatypeConstants.DURATION_DAYTIME, duration_dayTime_xmlSchemaType, "Expected DatatypeConstants.DURATION_DAYTIME, returned "
                + duration_dayTime_xmlSchemaType.toString());

        // DURATION_YEARMONTH
        Duration duration_yearMonth = datatypeFactory.newDuration("P1Y1M");
        QName duration_yearMonth_xmlSchemaType = duration_yearMonth.getXMLSchemaType();
        assertEquals(DatatypeConstants.DURATION_YEARMONTH, duration_yearMonth_xmlSchemaType, "Expected DatatypeConstants.DURATION_YEARMONTH, returned "
                + duration_yearMonth_xmlSchemaType.toString());

    }


    private final static int undef = DatatypeConstants.FIELD_UNDEFINED;
    private final static BigInteger zero = BigInteger.ZERO;
    private final static BigInteger one = BigInteger.ONE;
}
