/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package test.sql;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Calendar;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TimestampTests {

    public TimestampTests() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid Timestamp
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalid_timestamp() throws Exception {
        String testTS = "2009-11-01-01 10:50";
        Timestamp.valueOf(testTS);
    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid Timestamp
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalid_year2() throws Exception {
        String testTS = "aaaa-11-01-01 10:50";
        Timestamp.valueOf(testTS);
    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid Timestamp
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalid_year3() throws Exception {
        String testTS = "aaaa-11-01 10:50";
        Timestamp.valueOf(testTS);
    }

    /**
     * Validate that two Timestamp are equal when the leading 0 in seconds is
     * omitted
     */
    @Test
    public void test1() throws Exception {
        String testTS = "2009-01-01 10:50:00";
        String ExpectedTS = "2009-01-01 10:50:0";
        Timestamp ts = Timestamp.valueOf(testTS);
        Timestamp ts2 = Timestamp.valueOf(ExpectedTS);
        assertEquals(ts, ts2, "Error ts1 != ts2");
    }

    /**
     * Validate two Timestamps created from the same string are equal
     */
    @Test
    public void test2() throws Exception {
        String testTS = "2009-01-01 10:50:0";
        Timestamp ts = Timestamp.valueOf(testTS);
        Timestamp ts2 = Timestamp.valueOf(testTS);
        assertEquals(ts, ts2, "Error ts1 != ts2");
    }

    /**
     * Validate that two Timestamp values one with leading 0s for month and day
     * equals same string without the leading 0s.
     */
    @Test
    public void test3() throws Exception {
        String testTS = "2009-1-1 10:50:0";
        String ExpectedTS = "2009-01-01 10:50:0";
        Timestamp ts = Timestamp.valueOf(testTS);
        Timestamp ts2 = Timestamp.valueOf(ExpectedTS);
        assertEquals(ts, ts2, "Error ts1 != ts2");
    }

    /**
     * Validate that two Timestamp values one with leading 0s for day omitted
     * are equal
     */
    @Test
    public void test4() throws Exception {
        String testTS = "2009-01-1 10:50:0";
        String ExpectedTS = "2009-01-01 10:50:0";
        Timestamp ts = Timestamp.valueOf(testTS);
        Timestamp ts2 = Timestamp.valueOf(ExpectedTS);
        assertEquals(ts, ts2, "Error ts1 != ts2");
    }

    /**
     * Validate that two Timestamp values one with leading 0s for month omitted
     * and both with leading 0s for seconds omitted are equal
     */
    @Test
    public void test5() throws Exception {
        String testTS = "2009-1-01 10:50:0";
        String ExpectedTS = "2009-01-01 10:50:0";
        Timestamp ts = Timestamp.valueOf(testTS);
        Timestamp ts2 = Timestamp.valueOf(ExpectedTS);
        assertEquals(ts, ts2, "Error ts1 != ts2");
    }

    /**
     * Validate that two Timestamp values one with leading 0s for month omitted
     */
    @Test
    public void test6() throws Exception {
        String testTS = "2005-1-01 10:20:50.00";
        String ExpectedTS = "2005-01-01 10:20:50.00";
        Timestamp ts = Timestamp.valueOf(testTS);
        Timestamp ts2 = Timestamp.valueOf(ExpectedTS);
        assertEquals(ts, ts2, "Error ts1 != ts2");
    }

    /**
     * Validate that two Timestamp values one created using valueOf and another
     * via a constructor are equal
     */
    @Test
    public void test7() {

        Timestamp ts1 = Timestamp.valueOf("1996-12-13 14:15:25.001");
        Timestamp ts2 = new Timestamp(96, 11, 13, 14, 15, 25, 1000000);
        assertTrue(ts1.equals(ts2), "Error ts1 != ts2");
    }

    /**
     * Validate that two Timestamp values one created using valueOf and another
     * via a constructor are equal
     */
    @Test
    public void test8() {
        Timestamp ts1 = Timestamp.valueOf("1996-12-13 14:15:25.001");
        Timestamp ts2 = new Timestamp(ts1.getTime());
        assertTrue(ts1.equals(ts2), "Error ts1 != ts2");
    }

    /**
     * Validate that two Timestamp values one created using valueOf and another
     * via a constructor are equal
     */
    @Test
    public void test9() {

        Timestamp ts1 = Timestamp.valueOf("1996-12-13 14:15:25.0");
        Timestamp ts2 = new Timestamp(96, 11, 13, 14, 15, 25, 0);
        assertTrue(ts1.equals(ts2), "Error ts1 != ts2");
    }

    /**
     * Validate that a Timestamp cannot be equal to null
     */
    @Test
    public void test10() {

        Timestamp ts1 = Timestamp.valueOf("1961-08-30 14:15:25.745634");
        assertFalse(ts1.equals(null), "Error ts1 == null");
    }

    /**
     * Validate that a Timestamp is equal to another timestamp created with the
     * using the same value but not equal to a Timestamp which is one day later
     */
    @Test
    public void test11() {

        Timestamp ts1 = Timestamp.valueOf("1996-12-10 12:26:19.12");
        Timestamp ts2 = Timestamp.valueOf("1996-12-10 12:26:19.12");
        Timestamp ts3 = Timestamp.valueOf("1996-12-11 12:24:19.12");
        assertTrue(ts1.equals(ts2) && ts2.equals(ts1), "Error ts1 != ts2");
        assertFalse(ts1.equals(ts3) && ts3.equals(ts1), "Error ts1 == ts3");

    }

    /**
     * Validate that a Timestamp is equal to itself
     */
    @Test
    public void test12() {
        Timestamp ts1 = Timestamp.valueOf("1996-10-15 12:26:19.12");
        assertTrue(ts1.equals(ts1), "Error ts1 != ts1");
    }

    /**
     * Validate that two Timestamps are equal when one is created from the
     * toString() of the other
     */
    @Test
    public void test13() {
        Timestamp ts1 = Timestamp.valueOf("1996-12-10 12:26:19.12");
        Timestamp ts2 = Timestamp.valueOf(ts1.toString());
        assertTrue(ts1.equals(ts2) && ts2.equals(ts1), "Error ts1 != ts2");
    }

    // Before Tests
    /**
     * Validate that Timestamp ts1 is before Timestamp ts2
     */
    @Test
    public void test14() {
        Timestamp ts1 = Timestamp.valueOf("1996-12-13 14:15:25.745634");
        Timestamp ts2 = Timestamp.valueOf("1996-12-13 15:15:25.645634");
        assertTrue(ts1.before(ts2), "Error ts1 not before ts2");
    }

    /**
     * Validate that Timestamp ts1 is before Timestamp ts2
     */
    @Test
    public void test15() {
        Timestamp ts1 = Timestamp.valueOf("1961-08-30 14:15:25");
        Timestamp ts2 = Timestamp.valueOf("1999-12-13 15:15:25");
        assertTrue(ts1.before(ts2), "Error ts1 not before ts2");
    }

    /**
     * Validate that Timestamp ts1 is before Timestamp ts2
     */
    @Test
    public void test16() {

        Timestamp ts1 = Timestamp.valueOf("1999-12-13 14:15:25.745634");
        Timestamp ts2 = Timestamp.valueOf("1999-11-13 15:15:25.645634");
        assertFalse(ts1.before(ts2), "Error ts1 before ts2");
    }

    /*
     * Validate that a NullPointerException is thrown if a null is passed to
     * the before method
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void test17() throws Exception {
        Timestamp ts1 = Timestamp.valueOf("1996-12-13 14:15:25.745634");
        ts1.before(null);
    }

    /*
     * Validate a Timestamp cannot be before itself
     */
    @Test
    public void test18() {
        Timestamp ts1 = Timestamp.valueOf("1999-11-10 12:26:19.3456543");
        assertFalse(ts1.before(ts1), "Error ts1 before ts1!");
    }

    /**
     * Create 3 Timestamps and make sure the 1st is before the other two
     * Timestamps which are each greater than the one before it
     */
    @Test
    public void test19() {

        Timestamp ts1 = new Timestamp(1234560000);
        Timestamp ts2 = new Timestamp(1234567000);
        Timestamp ts3 = new Timestamp(1234569000);
        assertTrue(ts1.before(ts2) && ts2.before(ts3) && ts1.before(ts3));
    }

    /**
     * Validate that Timestamp ts1 is not after Timestamp ts2
     */
    @Test
    public void test20() {
        Timestamp ts1 = Timestamp.valueOf("1999-12-13 14:15:25.745634");
        Timestamp ts2 = Timestamp.valueOf("1999-12-13 15:15:25.645634");
        assertFalse(ts1.after(ts2), "Error ts1 is after ts2");

    }

    /**
     * Validate that Timestamp ts1 is after Timestamp ts2
     */
    @Test
    public void test21() {
        Timestamp ts1 = Timestamp.valueOf("1996-12-13 14:15:25.745634");
        Timestamp ts2 = Timestamp.valueOf("1996-11-13 15:15:25.645634");
        assertTrue(ts1.after(ts2), "Error ts1 not after ts2");
    }

    /**
     * Validate that a NullPointerException is thrown if a null is passed to the
     * after method
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void test22() throws Exception {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        ts1.after(null);
    }

    /**
     * Validate that a Timestamp cannot be after itself
     */
    @Test
    public void test23() {
        Timestamp ts1 = Timestamp.valueOf("1999-11-10 12:26:19.3456543");
        assertFalse(ts1.after(ts1), "Error ts1 is after itself");
    }
    /**
     * Validate that a Timestamp after() works correctly with Timestamp
     * created using milliseconds
     */
    @Test
    public void test24() {

        Timestamp ts1 = new Timestamp(1234568000);
        Timestamp ts2 = new Timestamp(1234565000);
        Timestamp ts3 = new Timestamp(1234562000);
        assertTrue(ts1.after(ts2) && ts2.after(ts3) && ts1.after(ts3));
    }

    /**
     * Validate compareTo returns 0 for Timestamps that are the same
     */
    @Test
    public void test25() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        Timestamp ts2 = new Timestamp(ts1.getTime());
        assertTrue(ts1.compareTo(ts2) == 0, "Error ts1 != ts2");
    }

    /**
     * Validate compareTo returns -1 for when the 1st Timestamp is earlier than
     * the 2nd Timestamp
     */
    @Test
    public void test26() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        Timestamp ts2 = new Timestamp(ts1.getTime() + 1000);
        assertTrue(ts1.compareTo(ts2) == -1, "Error ts1 not before ts2");
        assertTrue(ts2.compareTo(ts1) == 1, "Error ts1 is not before ts2");
    }

    /**
     * Validate compareTo returns 1 for when the 1st Timestamp is later than the
     * 2nd Timestamp
     */
    @Test
    public void test27() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        Timestamp ts2 = new Timestamp(ts1.getTime() - 1000);
        assertTrue(ts1.compareTo(ts2) == 1, "Error ts1 not after ts2");
        assertTrue(ts2.compareTo(ts1) == -1, "Error ts1 not after ts2");
    }

    /**
     * Validate compareTo returns 0 for Timestamps that are the same
     */
    @Test
    public void test28() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        java.util.Date ts2 = new java.util.Date(ts1.getTime());
        assertTrue(ts1.compareTo(ts2) == 0, "Error ts1 != ts2");
    }

    /**
     * Validate compareTo returns 0 for Timestamps that are the same
     */
    @Test
    public void test29() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        java.util.Date d = new java.util.Date(ts1.getTime());
        assertFalse(ts1.equals(d), "Error ts1 == d");
    }

    /**
     * Validate compareTo returns 0 for Timestamps that are the same
     */
    @Test
    public void test30() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        java.util.Date d = new Timestamp(ts1.getTime());
        assertTrue(ts1.equals(d), "Error ts1 != d");
    }

    /**
     * Validate equals returns false when a Date object is passed to equals
     */
    @Test
    public void test31() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        Date d = new Date(ts1.getTime());
        assertFalse(ts1.equals(d), "Error ts1 != d");
    }

    /**
     * Validate equals returns false when a Date object is passed to equals
     */
    @Test
    public void test32() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        java.util.Date d = new Date(ts1.getTime());
        assertFalse(ts1.equals(d), "Error ts1 != d");
    }

    /**
     * Validate equals returns false when a Time object is passed to equals
     */
    @Test
    public void test33() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        Time t1 = new Time(ts1.getTime());
        assertFalse(ts1.equals(t1), "Error ts1 == t1");
    }

    /**
     * Validate equals returns false when a String object is passed to equals
     */
    @Test
    public void test34() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        assertFalse(ts1.equals("1966-08-30 08:08:08"), "Error ts1 == a String");
    }

    /**
     * Validate getTime() returns the same value from 2 timeStamps created by
     */
    @Test
    public void test35() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        Timestamp ts2 = Timestamp.valueOf("1966-08-30 08:08:08");
        assertTrue(ts2.getTime() == ts1.getTime(),
                "ts1.getTime() != ts2.getTime()");
        assertTrue(ts1.equals(ts2), "Error ts1 != ts2");
    }

    /**
     * Validate getTime() returns the same value from 2 timeStamps when
     * setTime() is used to specify the same value for both Timestamps
     */
    @Test
    public void test36() {
        Timestamp ts1 = Timestamp.valueOf("1966-08-30 08:08:08");
        Timestamp ts2 = Timestamp.valueOf("1961-08-30 00:00:00");
        ts2.setTime(ts1.getTime());
        assertTrue(ts2.getTime() == ts1.getTime(),
                "ts1.getTime() != ts2.getTime()");
        assertTrue(ts1.equals(ts2), "Error ts1 != ts2");
    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid nanos value
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test38() throws Exception {
        Timestamp ts1 = Timestamp.valueOf("1961-08-30 00:00:00");
        ts1.setNanos(-1);

    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid nanos value
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test39() throws Exception {
        int nanos = 999999999;
        Timestamp ts1 = Timestamp.valueOf("1961-08-30 00:00:00");
        ts1.setNanos(nanos + 1);

    }

    /**
     * Validate you can set nanos to 999999999
     */
    @Test
    public void test40() throws Exception {
        int nanos = 999999999;
        Timestamp ts1 = Timestamp.valueOf("1961-08-30 00:00:00");
        ts1.setNanos(nanos);
        assertTrue(ts1.getNanos() == nanos, "Error Invalid Nanos value");

    }

    /**
     * Validate you can set nanos to 0
     */
    @Test
    public void test41() throws Exception {
        int nanos = 0;
        Timestamp ts1 = Timestamp.valueOf("1961-08-30 00:00:00");
        ts1.setNanos(nanos);
        assertTrue(ts1.getNanos() == nanos, "Error Invalid Nanos value");

    }

    /**
     * Validate that a Timestamp made from a LocalDateTime are equal
     */
    @Test
    public void test42() throws Exception {
        Timestamp ts1 = Timestamp.valueOf("1961-08-30 00:00:00");
        LocalDateTime ldt = ts1.toLocalDateTime();
        Timestamp ts2 = Timestamp.valueOf(ldt);
        assertTrue(ts1.equals(ts2), "Error ts1 != ts2");
    }

    /**
     * Validate that a Timestamp LocalDateTime value, made from a LocalDateTime
     * are equal
     */
    @Test
    public void test43() throws Exception {
        LocalDateTime ldt = LocalDateTime.now();
        Timestamp ts2 = Timestamp.valueOf(ldt);
        assertTrue(ldt.equals(ts2.toLocalDateTime()),
                "Error LocalDateTime values are not equal");
    }

    /**
     * Validate an NPE occurs when a null LocalDateTime is passed to valueOF
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void test44() throws Exception {
        LocalDateTime ldt = null;
        Timestamp.valueOf(ldt);
    }

    /**
     * Validate that a Timestamp made from a Instant are equal
     */
    @Test
    public void test45() throws Exception {
        Timestamp ts1 = Timestamp.valueOf("1961-08-30 00:00:00");
        Instant instant = ts1.toInstant();
        Timestamp ts2 = Timestamp.from(instant);
        assertTrue(ts1.equals(ts2), "Error ts1 != ts2");
    }

    /**
     * Validate that a Timestamp made from a Instant are equal
     */
    @Test
    public void test46() throws Exception {
        Instant instant = Instant.now();
        Timestamp ts2 = Timestamp.from(instant);
        assertTrue(instant.equals(ts2.toInstant()),
                "Error Instant values do not match");
    }

    /**
     * Validate an NPE occurs when a null instant is passed to from
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void test47() throws Exception {
        Instant instant = null;
        Timestamp.from(instant);
    }

    // Added SQE tests
    /**
     * Create a Timestamp and a 2nd Timestamp that is 1 month earlier and
     * validate that it is not before or after the original Timestamp
     */
    @Test
    public void test48() {
        Calendar cal = Calendar.getInstance();
        Timestamp ts1 = new Timestamp(System.currentTimeMillis());
        cal.setTimeInMillis(ts1.getTime());
        cal.add(Calendar.MONTH, -1);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        Timestamp ts2 = new Timestamp(cal.getTimeInMillis());
        assertFalse(ts1.before(ts2) || ts2.after(ts1));
    }

    /**
     * Create two Timestamps and validate that compareTo returns 1 to indicate
     * the 1st Timestamp is greater than the 2nd Timestamp
     */
    @Test
    public void test49() {
        Calendar cal = Calendar.getInstance();
        Timestamp ts1 = new Timestamp(System.currentTimeMillis());
        cal.setTimeInMillis(ts1.getTime());
        cal.add(Calendar.MONTH, -1);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        Timestamp ts2 = new Timestamp(cal.getTimeInMillis());
        assertTrue(ts1.compareTo(ts2) == 1);
    }

    /**
     * Create two Timestamps and validate that the 1st Timestamp is not equal to
     * the 2nd Timestamp but equal to itself
     */
    @Test
    public void test50() {
        Calendar cal = Calendar.getInstance();
        Timestamp ts1 = new Timestamp(System.currentTimeMillis());
        cal.setTimeInMillis(ts1.getTime());
        cal.add(Calendar.MONTH, -1);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        Timestamp ts2 = new Timestamp(cal.getTimeInMillis());
        assertTrue(!ts1.equals(ts2) && ts1.equals(ts1));
    }

}
