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
 * @bug 4143459
 * @summary test Date
 * @run junit DateTest
 */

import java.text.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("deprecation")
public class DateTest
{
    /**
     * @bug 4143459
     * Warning: Use TestDefaultZone() for complete testing of this bug.
     */
    @Test
    public void TestDefaultZoneLite() {
        // Note: This test is redundant with TestDefaultZone().  It was added by
        // request to provide a short&sweet test for this bug.  It does not test
        // all cases though, so IF THIS TEST PASSES, THE BUG MAY STILL BE
        // PRESENT.  Use TestDefaultZone() to be sure.
        TimeZone save = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
            Date d = new Date();
            d.setYear(98);
            d.setMonth(Calendar.JANUARY);
            d.setDate(1);
            d.setHours(6);
            TimeZone.setDefault(TimeZone.getTimeZone("PST"));
            if (d.getHours() != 22) {
                fail("Fail: Date.setHours()/getHours() ignoring default zone");
            }
        }
        finally { TimeZone.setDefault(save); }
    }

    /**
     * @bug 4143459
     */
    @Test
    public void TestDefaultZone() {
        // Various problems can creep up, with the current implementation of Date,
        // when the default zone is changed.

        TimeZone saveZone = TimeZone.getDefault();
        try {

            Date d = new Date(); // Trigger static init
            Date ref = new Date(883634400000L); // This is Thu Jan 1 1998 6:00 am GMT
            String refstr = "Jan 1 1998 6:00";
            TimeZone GMT = TimeZone.getTimeZone("GMT");
            TimeZone PST = TimeZone.getTimeZone("PST");

            String[] names = { "year", "month", "date", "day of week", "hour", "offset" };
            int[] GMT_EXP = { 98, Calendar.JANUARY, 1, Calendar.THURSDAY - Calendar.SUNDAY, 6, 0 };
            int[] PST_EXP = { 97, Calendar.DECEMBER, 31, Calendar.WEDNESDAY - Calendar.SUNDAY, 22, 480 };

            // There are two cases to consider: a Date object with no Calendar
            // sub-object (most Date objects), and a Date object with a Calendar
            // sub-object.  We make two passes to cover the two cases.
            for (int pass=0; pass<2; ++pass) {
                System.out.println(pass == 0 ? "Normal Date object" : "Date with Calendar sub-object");

                TimeZone.setDefault(GMT);
                d = new Date(refstr);
                if (pass == 1) {
                    // Force creation of Calendar sub-object
                    d.setYear(d.getYear());
                }
                if (d.getTime() != ref.getTime()) {
                    fail("FAIL: new Date(\"" + refstr + "\") x GMT -> " + d +
                          " " + d.getTime() + " ms");
                }

                int[] fields = { d.getYear(), d.getMonth(), d.getDate(),
                                 d.getDay(), d.getHours(), d.getTimezoneOffset() };
                for (int i=0; i<fields.length; ++i) {
                    if (fields[i] != GMT_EXP[i]) {
                        fail("FAIL: GMT Expected " + names[i] + " of " + GMT_EXP[i] +
                              ", got " + fields[i]);
                    }
                }

                TimeZone.setDefault(PST);
                int[] fields2 = { d.getYear(), d.getMonth(), d.getDate(),
                                  d.getDay(), d.getHours(), d.getTimezoneOffset() };
                for (int i=0; i<fields2.length; ++i) {
                    if (fields2[i] != PST_EXP[i]) {
                        fail("FAIL: PST Expected " + names[i] + " of " + PST_EXP[i] +
                              ", got " + fields2[i]);
                    }
                }
            }
        }
        finally {
            TimeZone.setDefault(saveZone);
        }
    }

    // Test the performance of Date
    @Test
    public void TestPerformance592()
    {
        int REPS = 500;

        // Do timing test with Date
        long start = new Date().getTime();
        for (int i=0; i<REPS; ++i)
        {
            Date d = new Date();
            int y = d.getYear();
        }
        long ms = new Date().getTime() - start;

        double perLoop = ((double)ms) / REPS;
        System.out.println(REPS + " iterations at " + perLoop + " ms/loop");
        if (perLoop > PER_LOOP_LIMIT)
            System.out.println("WARNING: Date constructor/getYear slower than " +
                  PER_LOOP_LIMIT + " ms");
    }
    static double PER_LOOP_LIMIT = 3.0;

    /**
     * Verify that the Date(String) constructor works.
     */
    @Test
    public void TestParseOfGMT()
    {
        Date OUT = null;

        /* Input values */
        String stringVal = "Jan 01 00:00:00 GMT 1900";
        long expectedVal = -2208988800000L;

        OUT = new Date( stringVal );

        if( OUT.getTime( ) == expectedVal ) {
            // logln("PASS");
        }
        else {
            fail( "Expected: " +
                   new Date( expectedVal ) +
                   ": " +
                   expectedVal +
                   "  Received: " +
                   OUT.toString() +
                   ": " +
                   OUT.getTime() );
        }
    }

    // Check out Date's behavior with large negative year values; bug 664
    // As of the fix to bug 4056585, Date should work correctly with
    // large negative years.
    @Test
    public void TestDateNegativeYears()
    {
        Date d1= new Date(80,-1,2);
        System.out.println(d1.toString());
        d1= new Date(-80,-1,2);
        System.out.println(d1.toString());
        boolean e = false;
        try {
            d1= new Date(-800000,-1,2);
            System.out.println(d1.toString());
        }
        catch (IllegalArgumentException ex) {
            e = true;
        }
        if (e) fail("FAIL: Saw exception for year -800000");
        else System.out.println("Pass: No exception for year -800000");
    }

    // Verify the behavior of Date
    @Test
    public void TestDate480()
    {
      TimeZone save = TimeZone.getDefault();
      try {
        TimeZone.setDefault(TimeZone.getTimeZone("PST"));
        Date d1=new java.util.Date(97,8,13,10,8,13);
        System.out.println("d       = "+d1);
        Date d2=new java.util.Date(97,8,13,30,8,13); // 20 hours later
        System.out.println("d+20h   = "+d2);

        double delta = (d2.getTime() - d1.getTime()) / 3600000;

        System.out.println("delta   = " + delta + "h");

        if (delta != 20.0) fail("Expected delta of 20; got " + delta);

        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(1997,8,13,10,8,13);
        Date t1 = cal.getTime();
        System.out.println("d       = "+t1);
        cal.clear();
        cal.set(1997,8,13,30,8,13); // 20 hours later
        Date t2 = cal.getTime();
        System.out.println("d+20h   = "+t2);

        double delta2 = (t2.getTime() - t1.getTime()) / 3600000;

        System.out.println("delta   = " + delta2 + "h");

        if (delta != 20.0) fail("Expected delta of 20; got " + delta2);
      }
      finally {
        TimeZone.setDefault(save);
      }
    }
}
