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

import java.sql.Time;
import java.time.LocalTime;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TimeTests {

    public TimeTests() {
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
     * Validate an IllegalArgumentException is thrown for calling getYear
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test1() {
        Time t = Time.valueOf("08:30:59");
        t.getYear();
    }

    /**
     * Validate an IllegalArgumentException is thrown for calling getMonth
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test2() {
        Time t = Time.valueOf("08:30:59");
        t.getMonth();
    }

    /**
     * Validate an IllegalArgumentException is thrown for calling getDay
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test3() {
        Time t = Time.valueOf("08:30:59");
        t.getDay();
    }

    /**
     * Validate an IllegalArgumentException is thrown for calling getDate
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test4() {
        Time t = Time.valueOf("08:30:59");
        t.getDate();
    }

    /**
     * Validate an IllegalArgumentException is thrown for calling setYear
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test5() {
        Time t = Time.valueOf("08:30:59");
        t.setYear(8);
    }

    /**
     * Validate an IllegalArgumentException is thrown for calling setMonth
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test6() {
        Time t = Time.valueOf("08:30:59");
        t.setMonth(8);
    }

    /**
     * Validate an IllegalArgumentException is thrown for calling setDate
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test7() {
        Time t = Time.valueOf("08:30:59");
        t.setDate(30);
    }

    /**
     * Validate an IllegalArgumentException is thrown for calling getDate
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test8() {
        Time t = Time.valueOf("08:30:59");
        t.getDate();
    }

    /**
     * Validate that a Time made from a toLocalTime() LocalTime are equal
     */
    @Test
    public void test13() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = Time.valueOf(t.toLocalTime());
        assertTrue(t.equals(t2), "Error t != t2");
    }

    /**
     * Validate that a Time LocalTime value, made from a LocalTime are equal
     */
    @Test
    public void test14() {
        LocalTime lt = LocalTime.of(8, 30, 59);
        Time t = Time.valueOf(lt);
        System.out.println("lt=" + lt + ",t=" + t.toLocalTime());
        assertTrue(lt.equals(t.toLocalTime()),
                "Error LocalTime values are not equal");
    }

    /**
     * Validate an NPE occurs when a null LocalDate is passed to valueOf
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void test15() throws Exception {
        LocalTime ld = null;
        Time.valueOf(ld);
    }

    /**
     * Validate an UnsupportedOperationException occurs when toInstant() is
     * called
     */
    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void test16() throws Exception {
        Time t = new Time(System.currentTimeMillis());
        t.toInstant();
    }

    /**
     * Validate that a Time made from valueOf(String) returns the same String
     * from Time.toString();
     */
    @Test
    public void test17() {
        String time = "08:30:59";
        Time t = Time.valueOf(time);
        assertTrue(time.equals(t.toString()), "Error t != t2");
    }

    /**
     * Validate that two Time objects are equal when one is created from the
     * toString() of the other
     */
    @Test
    public void test18() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = Time.valueOf(t.toString());
        assertTrue(t.equals(t2) && t2.equals(t), "Error t != t2");
    }

    /**
     * Validate that two Time values one created using valueOf and another via a
     * constructor are equal
     */
    @Test
    public void test19() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = new Time(8, 30, 59);
        assertTrue(t.equals(t2) && t2.equals(t), "Error t != t2");
    }

    /**
     * Validate that two Time values one created using valueOf and another via a
     * constructor are equal
     */
    @Test
    public void test20() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = new Time(t.getTime());
        assertTrue(t.equals(t2) && t2.equals(t), "Error t != t2");
    }

    /**
     * Validate an IllegalArgumentException is thrown for calling valueOf with a
     * null String
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test21() {
        String time = null;
        Time t = Time.valueOf(time);

    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid Time string
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test22() throws Exception {
        Time.valueOf("1961-08-30");
    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid Time string
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test23() throws Exception {
        Time.valueOf("8:");
    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid Time string
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test24() throws Exception {
        Time.valueOf("a:b:c");
    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid Time string
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test25() throws Exception {
        Time.valueOf("08:10");
    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid Time string
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test26() throws Exception {
        Time.valueOf("08:10:10:10");
    }

    /**
     * Validate an IllegalArgumentException is thrown for an invalid Time string
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void test27() throws Exception {
        Time.valueOf("08:10:Batman");
    }

    /**
     * Validate that Time.after() returns false when same date is compared
     */
    @Test
    public void test28() {
        Time t = Time.valueOf("08:30:59");
        assertFalse(t.after(t), "Error t.after(t) = true");
    }

    /**
     * Validate that Time.after() returns true when later date is compared to
     * earlier date
     */
    @Test
    public void test29() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = new Time(System.currentTimeMillis());
        assertTrue(t2.after(t), "Error t2.after(t) = false");
    }

    /**
     * Validate that Time.after() returns false when earlier date is compared to
     * itself
     */
    @Test
    public void test30() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = new Time(t.getTime());
        assertFalse(t.after(t2), "Error t.after(t2) = true");
        assertFalse(t2.after(t), "Error t2.after(t) = true");
    }

    /**
     * Validate that Time.before() returns false when same date is compared
     */
    @Test
    public void test31() {
        Time t = Time.valueOf("08:30:59");
        assertFalse(t.before(t), "Error t.before(t) = true");
    }

    /**
     * Validate that Time.before() returns true when earlier date is compared to
     * later date
     */
    @Test
    public void test32() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = new Time(System.currentTimeMillis());
        assertTrue(t.before(t2), "Error t.before(t2) = false");
    }

    /**
     * Validate that Time.before() returns false when earlier date is compared
     * to itself
     */
    @Test
    public void test33() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = new Time(t.getTime());
        assertFalse(t.before(t2), "Error t.after(t2) = true");
        assertFalse(t2.before(t), "Error t2.after(t) = true");
    }

    /**
     * Validate that Time.compareTo returns 0 when both Date objects are the
     * same
     */
    @Test
    public void test34() {
        Time t = Time.valueOf("08:30:59");
        assertTrue(t.compareTo(t) == 0, "Error t.compareTo(t) !=0");
    }

    /**
     * Validate thatTime.compareTo returns 0 when both Time objects are the same
     */
    @Test
    public void test35() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = new Time(t.getTime());
        assertTrue(t.compareTo(t2) == 0, "Error t.compareTo(t2) !=0");
    }

    /**
     * Validate that Time.compareTo returns 1 when comparing a later Time to an
     * earlier Time
     */
    @Test
    public void test36() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = new Time(t.getTime() + 1);
        assertTrue(t2.compareTo(t) == 1, "Error t2.compareTo(t) !=1");
    }

    /**
     * Validate thatTime.compareTo returns 1 when comparing a later Time to an
     * earlier Time
     */
    @Test
    public void test37() {
        Time t = Time.valueOf("08:30:59");
        Time t2 = new Time(t.getTime() + 1);
        assertTrue(t.compareTo(t2) == -1, "Error t.compareTo(t2) != -1");
    }
}
