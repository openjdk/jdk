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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import static java.util.Calendar.HOUR;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.YEAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 5049592 5041845 5048932 5064587 5040542 5049531 5049528
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.datatype.ptests.XMLGregorianCalendarTest
 * @summary Class containing the test cases for XMLGregorianCalendar
 */
public class XMLGregorianCalendarTest {

    private DatatypeFactory datatypeFactory;

    @BeforeEach
    public void setup() throws DatatypeConfigurationException {
        datatypeFactory = DatatypeFactory.newInstance();
    }

    /*
     * Test DatatypeFactory.newXMLGregorianCalendar(..) with milliseconds > 1.
     *
     * Bug # 5049592
     */
    @ParameterizedTest
    @ValueSource(ints={ 0, 1, 2, 16, 1000 })
    public void checkNewCalendar(int expectedMillis) {
        // valid milliseconds
        XMLGregorianCalendar calendar = datatypeFactory.newXMLGregorianCalendar(2004, // year
                6, // month
                2, // day
                19, // hour
                20, // minute
                59, // second
                expectedMillis, // milliseconds
                840 // timezone
                );
        // expected success

        assertEquals(expectedMillis, calendar.getMillisecond());
    }

    /*
     * Test DatatypeFactory.newXMLGregorianCalendarTime(..).
     *
     * Bug # 5049592
     */
    @ParameterizedTest
    @ValueSource(ints={ 0, 1, 2, 16, 1000 })
    public void checkNewTime(int expectedMillis) {
        // valid milliseconds
        XMLGregorianCalendar calendar2 = datatypeFactory.newXMLGregorianCalendarTime(19, // hour
                20, // minute
                59, // second
                expectedMillis, // milliseconds
                840 // timezone
                );
        // expected success

        assertEquals(expectedMillis, calendar2.getMillisecond());
    }

    /*
     * Test DatatypeFactory.newXMLGregorianCalendar(..).
     *
     * Bug # 5049592 IllegalArgumentException is thrown if milliseconds < 0 or >
     * 1001.
     *
     */
    @ParameterizedTest
    @ValueSource(ints={ -1, 1001 })
    public void checkNewCalendarNeg(int invalidMilliseconds) {
        // invalid milliseconds
        assertThrows(
                IllegalArgumentException.class,
                () -> datatypeFactory.newXMLGregorianCalendar(2004, // year
                        6, // month
                        2, // day
                        19, // hour
                        20, // minute
                        59, // second
                        invalidMilliseconds, // milliseconds
                        840 // timezone
                ));
    }

    /*
     * Test DatatypeFactory.newXMLGregorianCalendarTime(..).
     *
     * Bug # 5049592 IllegalArgumentException is thrown if milliseconds < 0 or >
     * 1001.
     *
     */
    @ParameterizedTest
    @ValueSource(ints={ -1, 1001 })
    public void checkNewTimeNeg(int invalidMilliseconds) {
        // invalid milliseconds
        assertThrows(
                IllegalArgumentException.class,
                () -> datatypeFactory.newXMLGregorianCalendarTime(19, // hour
                        20, // minute
                        59, // second
                        invalidMilliseconds, // milliseconds
                        840 // timezone
                ));
    }

    public static Object[][] getDataForAdd() {
        return new Object[][] {
                //calendar1, calendar2, duration
                { "1999-12-31T00:00:00Z", "2000-01-01T00:00:00Z", "P1D" },
                { "2000-12-31T00:00:00Z", "2001-01-01T00:00:00Z", "P1D" },
                { "1998-12-31T00:00:00Z", "1999-01-01T00:00:00Z", "P1D" },
                { "2001-12-31T00:00:00Z", "2002-01-01T00:00:00Z", "P1D" },
                { "2003-04-11T00:00:00Z", "2003-04-12T00:00:00Z", "P1D" },
                { "2003-04-11T00:00:00Z", "2003-04-14T00:00:00Z", "P3D" },
                { "2003-04-30T00:00:00Z", "2003-05-01T00:00:00Z", "P1D" },
                { "2003-02-28T00:00:00Z", "2003-03-01T00:00:00Z", "P1D" },
                { "2000-02-29T00:00:00Z", "2000-03-01T00:00:00Z", "P1D" },
                { "2000-02-28T00:00:00Z", "2000-02-29T00:00:00Z", "P1D" },
                { "1998-01-11T00:00:00Z", "1998-04-11T00:00:00Z", "P90D" },
                { "1999-05-11T00:00:00Z", "2002-05-11T00:00:00Z", "P1096D" }};
    }

    /*
     * Test XMLGregorianCalendar.add(Duration).
     *
     */
    @ParameterizedTest
    @MethodSource("getDataForAdd")
    public void checkAddDays(String cal1, String cal2, String dur) {

        XMLGregorianCalendar calendar1 = datatypeFactory.newXMLGregorianCalendar(cal1);
        XMLGregorianCalendar calendar2 = datatypeFactory.newXMLGregorianCalendar(cal2);

        Duration duration = datatypeFactory.newDuration(dur);

        XMLGregorianCalendar calendar1Clone = (XMLGregorianCalendar)calendar1.clone();

        calendar1Clone.add(duration);
        assertEquals(calendar2, calendar1Clone);

        calendar2.add(duration.negate());
        assertEquals(calendar1, calendar2);

    }

    /*
     * Test XMLGregorianCalendar#isValid(). for gMonth
     *
     * Bug # 5041845
     */
    @ParameterizedTest
    @ValueSource(strings={ "2000-02", "2000-03", "2018-02" })
    public void checkIsValid(String month) {
        XMLGregorianCalendar gMonth = datatypeFactory.newXMLGregorianCalendar(month);
        gMonth.setYear(null);
        assertTrue(gMonth.isValid(), gMonth + " should isValid");
    }

    /*
     * Test XMLGregorianCalendar#normalize(...).
     *
     * Bug # 5048932 XMLGregorianCalendar.normalize works
     */
    @ParameterizedTest
    @ValueSource(strings={ "2000-01-16T12:00:00Z", "2000-01-16T12:00:00" })
    public void checkNormalize01(String lexical) {
        XMLGregorianCalendar lhs = datatypeFactory.newXMLGregorianCalendar(lexical);
        lhs.normalize();
    }

    /*
     * Test XMLGregorianCalendar#normalize(...).
     *
     * Bug # 5064587 XMLGregorianCalendar.normalize shall not change timezone
     */
    @ParameterizedTest
    @ValueSource(strings={ "2000-01-16T00:00:00.01Z", "2000-01-16T00:00:00.01", "13:20:00" })
    public void checkNormalize02(String lexical) {
        XMLGregorianCalendar orig = datatypeFactory.newXMLGregorianCalendar(lexical);
        XMLGregorianCalendar normalized = datatypeFactory.newXMLGregorianCalendar(lexical).normalize();

        assertEquals(orig.getTimezone(), normalized.getTimezone());
        assertEquals(orig.getMillisecond(), normalized.getMillisecond());
    }

    /*
     * Test XMLGregorianCalendar#toGregorianCalendar( TimeZone timezone, Locale
     * aLocale, XMLGregorianCalendar defaults)
     *
     * Bug # 5040542 the defaults XMLGregorianCalendar parameter shall take
     * effect
     *
     */
    @Test
    public void checkToGregorianCalendar01() {

        XMLGregorianCalendar time_16_17_18 = datatypeFactory.newXMLGregorianCalendar("16:17:18");
        XMLGregorianCalendar date_2001_02_03 = datatypeFactory.newXMLGregorianCalendar("2001-02-03");
        GregorianCalendar calendar = date_2001_02_03.toGregorianCalendar(null, null, time_16_17_18);

        int year = calendar.get(YEAR);
        int minute = calendar.get(MINUTE);

        assertTrue((year == 2001 && minute == 17), " expecting year == 2001, minute == 17" + ", result is year == " + year + ", minute == " + minute);


        calendar = time_16_17_18.toGregorianCalendar(null, null, date_2001_02_03);

        year = calendar.get(YEAR);
        minute = calendar.get(MINUTE);

        assertTrue((year == 2001 && minute == 17), " expecting year == 2001, minute == 17" + ", result is year == " + year + ", minute == " + minute);


        date_2001_02_03.setMinute(3);
        date_2001_02_03.setYear(null);

        XMLGregorianCalendar date_time = datatypeFactory.newXMLGregorianCalendar("2003-04-11T02:13:01Z");

        calendar = date_2001_02_03.toGregorianCalendar(null, null, date_time);

        year = calendar.get(YEAR);
        minute = calendar.get(MINUTE);
        int hour = calendar.get(HOUR);

        assertTrue((year == 2003 && hour == 2 && minute == 3), " expecting year == 2003, hour == 2, minute == 3" + ", result is year == " + year + ", hour == " + hour + ", minute == " + minute);
    }

    /*
     * Test XMLGregorianCalendar#toGregorianCalendar( TimeZone timezone, Locale
     * aLocale, XMLGregorianCalendar defaults) with the 'defaults' parameter
     * being null.
     *
     * Bug # 5049531 XMLGregorianCalendar.toGregorianCalendar(..) can accept
     * 'defaults' is null
     *
     */
    @Test
    public void checkToGregorianCalendar02() {

        XMLGregorianCalendar calendar = datatypeFactory.newXMLGregorianCalendar("2004-05-19T12:00:00+06:00");
        calendar.toGregorianCalendar(TimeZone.getDefault(), Locale.getDefault(), null);
    }

    public static Object[][] getXMLGregorianCalendarData() {
        return new Object[][] {
                // year, month, day, hour, minute, second
                { 1970, 1, 1, 0, 0, 0 }, // DATETIME
                { 1970, 1, 1, undef, undef, undef }, // DATE
                { undef, undef, undef, 1, 0, 0 }, // TIME
                { 1970, 1, undef, undef, undef, undef }, // GYEARMONTH
                { undef, 1, 1, undef, undef, undef }, // GMONTHDAY
                { 1970, undef, undef, undef, undef, undef }, // GYEAR
                { undef, 1, undef, undef, undef, undef }, // GMONTH
                { undef, undef, 1, undef, undef, undef } // GDAY
        };
    }

    /*
     * Test XMLGregorianCalendar#toString()
     *
     * Bug # 5049528
     *
     */
    @ParameterizedTest
    @MethodSource("getXMLGregorianCalendarData")
    public void checkToStringPos(final int year, final int month, final int day, final int hour, final int minute, final int second) {
        XMLGregorianCalendar calendar = datatypeFactory.newXMLGregorianCalendar(year, month, day, hour, minute, second, undef, undef);
        assertNotNull(calendar.toString());
        assertFalse(calendar.toString().isEmpty());
    }

    /*
     * Negative Test XMLGregorianCalendar#toString()
     *
     * Bug # 5049528 XMLGregorianCalendar.toString throws IllegalStateException
     * if all parameters are undef
     *
     */
    @Test
    public void checkToStringNeg() {
        XMLGregorianCalendar calendar = datatypeFactory.newXMLGregorianCalendar(undef, undef, undef, undef, undef, undef, undef, undef);
        // expected to fail
        assertThrows(IllegalStateException.class, calendar::toString);
    }

    private static final int undef = DatatypeConstants.FIELD_UNDEFINED;

}
