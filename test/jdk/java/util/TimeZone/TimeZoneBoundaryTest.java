/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary test Time Zone Boundary
 * @run junit TimeZoneBoundaryTest
 */

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A test which discovers the boundaries of DST programmatically and verifies
 * that they are correct.
 */
public class TimeZoneBoundaryTest
{
    static final int ONE_SECOND = 1000;
    static final int ONE_MINUTE = 60*ONE_SECOND;
    static final int ONE_HOUR = 60*ONE_MINUTE;
    static final long ONE_DAY = 24*ONE_HOUR;
    static final long ONE_YEAR = (long)(365.25 * ONE_DAY);
    static final long SIX_MONTHS = ONE_YEAR / 2;

    static final int MONTH_LENGTH[] = {31,29,31,30,31,30,31,31,30,31,30,31};

    // These values are empirically determined to be correct
    static final long PST_1997_BEG  = 860320800000L;
    static final long PST_1997_END  = 877856400000L;

    // Minimum interval for binary searches in ms; should be no larger
    // than 1000.
    static final long INTERVAL = 10; // Milliseconds

    static final String AUSTRALIA = "Australia/Adelaide";
    static final long AUSTRALIA_1997_BEG = 877797000000L;
    static final long AUSTRALIA_1997_END = 859653000000L;

    /**
     * Date.toString().substring() Boundary Test
     * Look for a DST changeover to occur within 6 months of the given Date.
     * The initial Date.toString() should yield a string containing the
     * startMode as a SUBSTRING.  The boundary will be tested to be
     * at the expectedBoundary value.
     */
    void findDaylightBoundaryUsingDate(Date d, String startMode, long expectedBoundary)
    {
        // Given a date with a year start, find the Daylight onset
        // and end.  The given date should be 1/1/xx in some year.

        if (d.toString().indexOf(startMode) == -1)
        {
            System.out.println("Error: " + startMode + " not present in " + d);
        }

        // Use a binary search, assuming that we have a Standard
        // time at the midpoint.
        long min = d.getTime();
        long max = min + SIX_MONTHS;

        while ((max - min) >  INTERVAL)
        {
            long mid = (min + max) >> 1;
            String s = new Date(mid).toString();
            // logln(s);
            if (s.indexOf(startMode) != -1)
            {
                min = mid;
            }
            else
            {
                max = mid;
            }
        }

        System.out.println("Date Before: " + showDate(min));
        System.out.println("Date After:  " + showDate(max));
        long mindelta = expectedBoundary - min;
        long maxdelta = max - expectedBoundary;
        if (mindelta >= 0 && mindelta <= INTERVAL &&
            mindelta >= 0 && mindelta <= INTERVAL)
            System.out.println("PASS: Expected boundary at " + expectedBoundary);
        else
            fail("FAIL: Expected boundary at " + expectedBoundary);
    }

    void findDaylightBoundaryUsingTimeZone(Date d, boolean startsInDST, long expectedBoundary)
    {
        findDaylightBoundaryUsingTimeZone(d, startsInDST, expectedBoundary,
                                          TimeZone.getDefault());
    }

    void findDaylightBoundaryUsingTimeZone(Date d, boolean startsInDST,
                                           long expectedBoundary, TimeZone tz)
    {
        // Given a date with a year start, find the Daylight onset
        // and end.  The given date should be 1/1/xx in some year.

        // Use a binary search, assuming that we have a Standard
        // time at the midpoint.
        long min = d.getTime();
        long max = min + SIX_MONTHS;

        if (tz.inDaylightTime(d) != startsInDST)
        {
            fail("FAIL: " + tz.getID() + " inDaylightTime(" +
                  d + ") != " + startsInDST);
            startsInDST = !startsInDST; // Flip over; find the apparent value
        }

        if (tz.inDaylightTime(new Date(max)) == startsInDST)
        {
            fail("FAIL: " + tz.getID() + " inDaylightTime(" +
                  (new Date(max)) + ") != " + (!startsInDST));
            return;
        }

        while ((max - min) >  INTERVAL)
        {
            long mid = (min + max) >> 1;
            boolean isIn = tz.inDaylightTime(new Date(mid));
            if (isIn == startsInDST)
            {
                min = mid;
            }
            else
            {
                max = mid;
            }
        }

        System.out.println(tz.getID() + " Before: " + showDate(min, tz));
        System.out.println(tz.getID() + " After:  " + showDate(max, tz));

        long mindelta = expectedBoundary - min;
        long maxdelta = max - expectedBoundary;
        if (mindelta >= 0 && mindelta <= INTERVAL &&
            mindelta >= 0 && mindelta <= INTERVAL)
            System.out.println("PASS: Expected boundary at " + expectedBoundary);
        else
            fail("FAIL: Expected boundary at " + expectedBoundary);
    }

    private static String showDate(long l)
    {
        return showDate(new Date(l));
    }

    @SuppressWarnings("deprecation")
    private static String showDate(Date d)
    {
        return "" + d.getYear() + "/" + showNN(d.getMonth()+1) + "/" + showNN(d.getDate()) +
            " " + showNN(d.getHours()) + ":" + showNN(d.getMinutes()) +
            " \"" + d + "\" = " +
            d.getTime();
    }

    private static String showDate(long l, TimeZone z)
    {
        return showDate(new Date(l), z);
    }

    @SuppressWarnings("deprecation")
    private static String showDate(Date d, TimeZone zone)
    {
        DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);
        fmt.setTimeZone(zone);
        return "" + d.getYear() + "/" + showNN(d.getMonth()+1) + "/" + showNN(d.getDate()) +
            " " + showNN(d.getHours()) + ":" + showNN(d.getMinutes()) +
            " \"" + d + "\" = " +
            fmt.format(d);
    }

    private static String showNN(int n)
    {
        return ((n < 10) ? "0" : "") + n;
    }

    /**
     * Given a date, a TimeZone, and expected values for inDaylightTime,
     * useDaylightTime, zone and DST offset, verify that this is the case.
     */
    void verifyDST(Date d, TimeZone time_zone,
                   boolean expUseDaylightTime, boolean expInDaylightTime,
                   int expZoneOffset, int expDSTOffset)
    {
        System.out.println("-- Verifying time " + d +
              " in zone " + time_zone.getID());

        if (time_zone.inDaylightTime(d) == expInDaylightTime)
            System.out.println("PASS: inDaylightTime = " + time_zone.inDaylightTime(d));
        else
            fail("FAIL: inDaylightTime = " + time_zone.inDaylightTime(d));

        if (time_zone.useDaylightTime() == expUseDaylightTime)
            System.out.println("PASS: useDaylightTime = " + time_zone.useDaylightTime());
        else
            fail("FAIL: useDaylightTime = " + time_zone.useDaylightTime());

        if (time_zone.getRawOffset() == expZoneOffset)
            System.out.println("PASS: getRawOffset() = " + expZoneOffset/(double)ONE_HOUR);
        else
            fail("FAIL: getRawOffset() = " + time_zone.getRawOffset()/(double)ONE_HOUR +
                  "; expected " + expZoneOffset/(double)ONE_HOUR);

        GregorianCalendar gc = new GregorianCalendar(time_zone);
        gc.setTime(d);
        int offset = time_zone.getOffset(gc.get(gc.ERA), gc.get(gc.YEAR), gc.get(gc.MONTH),
                                         gc.get(gc.DAY_OF_MONTH), gc.get(gc.DAY_OF_WEEK),
                                         ((gc.get(gc.HOUR_OF_DAY) * 60 +
                                           gc.get(gc.MINUTE)) * 60 +
                                          gc.get(gc.SECOND)) * 1000 +
                                         gc.get(gc.MILLISECOND));
        if (offset == expDSTOffset)
            System.out.println("PASS: getOffset() = " + offset/(double)ONE_HOUR);
        else
            fail("FAIL: getOffset() = " + offset/(double)ONE_HOUR +
                  "; expected " + expDSTOffset/(double)ONE_HOUR);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void TestBoundaries()
    {
        TimeZone pst = TimeZone.getTimeZone("PST");
        TimeZone save = TimeZone.getDefault();
        try {
            TimeZone.setDefault(pst);

            // DST changeover for PST is 4/6/1997 at 2 hours past midnight
            Date d = new Date(97,Calendar.APRIL,6);

            // i is minutes past midnight standard time
            for (int i=60; i<=180; i+=15)
            {
                boolean inDST = (i >= 120);
                Date e = new Date(d.getTime() + i*60*1000);
                verifyDST(e, pst, true, inDST, -8*ONE_HOUR,
                          inDST ? -7*ONE_HOUR : -8*ONE_HOUR);
            }

            System.out.println("========================================");
            findDaylightBoundaryUsingDate(new Date(97,0,1), "PST", PST_1997_BEG);
            System.out.println("========================================");
            findDaylightBoundaryUsingDate(new Date(97,6,1), "PDT", PST_1997_END);

            // Southern hemisphere test
            System.out.println("========================================");
            TimeZone z = TimeZone.getTimeZone(AUSTRALIA);
            findDaylightBoundaryUsingTimeZone(new Date(97,0,1), true, AUSTRALIA_1997_END, z);

            System.out.println("========================================");
            findDaylightBoundaryUsingTimeZone(new Date(97,0,1), false, PST_1997_BEG);
            System.out.println("========================================");
            findDaylightBoundaryUsingTimeZone(new Date(97,6,1), true, PST_1997_END);
        } finally {
            TimeZone.setDefault(save);
        }
    }

    void testUsingBinarySearch(SimpleTimeZone tz, Date d, long expectedBoundary)
    {
        // Given a date with a year start, find the Daylight onset
        // and end.  The given date should be 1/1/xx in some year.

        // Use a binary search, assuming that we have a Standard
        // time at the midpoint.
        long min = d.getTime();
        long max = min + (long)(365.25 / 2 * ONE_DAY);

        // First check the boundaries
        boolean startsInDST = tz.inDaylightTime(d);

        if (tz.inDaylightTime(new Date(max)) == startsInDST)
        {
            System.out.println("Error: inDaylightTime(" + (new Date(max)) + ") != " + (!startsInDST));
        }

        while ((max - min) >  INTERVAL)
        {
            long mid = (min + max) >> 1;
            if (tz.inDaylightTime(new Date(mid)) == startsInDST)
            {
                min = mid;
            }
            else
            {
                max = mid;
            }
        }

        System.out.println("Binary Search Before: " + showDate(min));
        System.out.println("Binary Search After:  " + showDate(max));

        long mindelta = expectedBoundary - min;
        long maxdelta = max - expectedBoundary;
        if (mindelta >= 0 && mindelta <= INTERVAL &&
            mindelta >= 0 && mindelta <= INTERVAL)
            System.out.println("PASS: Expected boundary at " + expectedBoundary);
        else
            fail("FAIL: Expected boundary at " + expectedBoundary);
    }

    /**
     * Test new rule formats.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void TestNewRules() {
        // Doesn't matter what the default TimeZone is here, since we
        // are creating our own TimeZone objects.

        SimpleTimeZone tz;

        System.out.println("-----------------------------------------------------------------");
        System.out.println("Aug 2ndTues .. Mar 15");
        tz = new SimpleTimeZone(-8*ONE_HOUR, "Test_1",
                                Calendar.AUGUST, 2, Calendar.TUESDAY, 2*ONE_HOUR,
                                Calendar.MARCH, 15, 0, 2*ONE_HOUR);
        System.out.println("========================================");
        testUsingBinarySearch(tz, new Date(97,0,1), 858416400000L);
        System.out.println("========================================");
        testUsingBinarySearch(tz, new Date(97,6,1), 871380000000L);

        System.out.println("-----------------------------------------------------------------");
        System.out.println("Apr Wed>=14 .. Sep Sun<=20");
        tz = new SimpleTimeZone(-8*ONE_HOUR, "Test_2",
                                Calendar.APRIL, 14, -Calendar.WEDNESDAY, 2*ONE_HOUR,
                                Calendar.SEPTEMBER, -20, -Calendar.SUNDAY, 2*ONE_HOUR);
        System.out.println("========================================");
        testUsingBinarySearch(tz, new Date(97,0,1), 861184800000L);
        System.out.println("========================================");
        testUsingBinarySearch(tz, new Date(97,6,1), 874227600000L);
    }

    /**
     * Find boundaries by stepping.
     */
    @SuppressWarnings("deprecation")
    void findBoundariesStepwise(int year, long interval, TimeZone z, int expectedChanges)
    {
        Date d = new Date(year - 1900, Calendar.JANUARY, 1);
        long time = d.getTime(); // ms
        long limit = time + ONE_YEAR + ONE_DAY;
        boolean lastState = z.inDaylightTime(d);
        int changes = 0;
        System.out.println("-- Zone " + z.getID() + " starts in " + year + " with DST = " + lastState);
        System.out.println("useDaylightTime = " + z.useDaylightTime());
        while (time < limit)
        {
            d.setTime(time);
            boolean state = z.inDaylightTime(d);
            if (state != lastState)
            {
                System.out.println((state ? "Entry " : "Exit ") +
                      "at " + d);
                lastState = state;
                ++changes;
            }
            time += interval;
        }
        if (changes == 0)
        {
            if (!lastState && !z.useDaylightTime()) System.out.println("No DST");
            else fail("FAIL: Timezone<" + z.getID() + "> DST all year, or no DST with true useDaylightTime");
        }
        else if (changes != 2)
        {
            fail("FAIL: Timezone<" + z.getID() + "> " + changes + " changes seen; should see 0 or 2");
        }
        else if (!z.useDaylightTime())
        {
            fail("FAIL: Timezone<" + z.getID() + "> useDaylightTime false but 2 changes seen");
        }
        if (changes != expectedChanges)
        {
            fail("FAIL: Timezone<" + z.getID() + "> " + changes + " changes seen; expected " + expectedChanges);
        }
    }

    @Test
    public void TestStepwise()
    {
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("ACT"), 0);
        // "EST" is disabled because its behavior depends on the mapping property. (6466476).
        //findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("EST"), 2);
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("HST"), 0);
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("PST"), 2);
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("PST8PDT"), 2);
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("SystemV/PST"), 0);
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("SystemV/PST8PDT"), 2);
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("Japan"), 0);
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("Europe/Paris"), 2);
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone("America/Los_Angeles"), 2);
        findBoundariesStepwise(1997, ONE_DAY, TimeZone.getTimeZone(AUSTRALIA), 2);
    }
}
