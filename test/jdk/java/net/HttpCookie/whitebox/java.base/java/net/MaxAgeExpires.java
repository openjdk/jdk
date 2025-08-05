/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

public class MaxAgeExpires {

    static final DateTimeFormatter dtFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME;

    static final String NOW_PLUS_500_SEC =
        dtFormatter.format(ZonedDateTime.ofInstant(Instant.now()
                                        .plusSeconds(500), ZoneId.of("UTC")));

    // Test dates 1 minute apart
    static final String DT1 = "Mon, 01 Jan 2024 01:00:00 GMT";
    static final String DT2 = "Mon, 01 Jan 2024 01:01:00 GMT";
    static final String DT3 = "Mon, 01 Jan 2024 01:02:00 GMT";

    static final String FAR_FUTURE = "Mon, 01 Jan 4024 01:02:00 GMT";

    static final ZonedDateTime zdt1 = ZonedDateTime.parse(DT1, dtFormatter);
    static final ZonedDateTime zdt2 = ZonedDateTime.parse(DT2, dtFormatter);
    static final ZonedDateTime zdt3 = ZonedDateTime.parse(DT3, dtFormatter);

    static long zdtToMillis(ZonedDateTime zdt) {
        return zdt.toInstant().getEpochSecond() * 1000; // always exact seconds
    }

    @DataProvider(name = "testData")
    public Object[][] testData() {
        return new Object[][] {
            // Date string in past. But checking based on current time.
            {-1L, -1L, -1L, DT1, 0, true},
            {-1L, -1L, 1000, DT1, 1000, false},
            {-1L, -1L, 0, DT1, 0, true},
            {-1L, -1L, 1000, NOW_PLUS_500_SEC, 1000, false},

            // Far in the future. Just check hasExpired() not the exact maxAge
            {-1L, -1L, -1L, FAR_FUTURE, -1L, false},

            // Tests using fixed creation and verification dates
            // (independent of current time)
            //                                        expires=
            //                                   maxAge= |
            // create time      expiry check time   |    |expected maxAge
            //      |                   |           |    |    |  hasExpired()
            //      |                   |           |    |    |   |  expected
            {zdtToMillis(zdt1), zdtToMillis(zdt3), -1L, DT2, 60, true},
            {zdtToMillis(zdt1), zdtToMillis(zdt3),  20, DT2, 20, true},
            {zdtToMillis(zdt1), zdtToMillis(zdt2),  40, DT3, 40, true},
            {zdtToMillis(zdt1), zdtToMillis(zdt2), -1L, DT3,120, false}

        };
    };


    @Test(dataProvider = "testData")
    public void test1(long creationInstant, // if -1, then current time is used
                    long expiryCheckInstant,  // if -1 then current time is used
                    long maxAge, // if -1, then not included in String
                    String expires, // if null, then not included in String
                    long expectedAge, // expected return value from getMaxAge()
                    boolean hasExpired) // expected return value from hasExpired()
    throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Set-Cookie: name=value");
        if (expires != null)
            sb.append("; expires=" + expires);
        if (maxAge != -1)
            sb.append("; max-age=" + Long.toString(maxAge));

        String s = sb.toString();
        System.out.println(s);
        HttpCookie cookie;
        if (creationInstant == -1)
            cookie = HttpCookie.parse(s).get(0);
        else
            cookie = HttpCookie.parse(s, false, creationInstant).get(0);

        if (expectedAge != -1 && cookie.getMaxAge() != expectedAge) {
            System.out.println("getMaxAge returned " + cookie.getMaxAge());
            System.out.println("expectedAge was " + expectedAge);
            throw new RuntimeException("Test failed: wrong age");
        }

        boolean expired = expiryCheckInstant == -1
            ? cookie.hasExpired()
            : cookie.hasExpired(expiryCheckInstant);

        if (expired != hasExpired) {
            System.out.println("cookie.hasExpired() returned " + expired);
            System.out.println("hasExpired was " + hasExpired);
            System.out.println("getMaxAge() returned " + cookie.getMaxAge());
            throw new RuntimeException("Test failed: wrong hasExpired");
        }
    }

    @Test
    public void test2() {
        // Miscellaneous tests that setMaxAge() overrides whatever was set already
        HttpCookie cookie = HttpCookie.parse("Set-Cookie: name=value; max-age=100").get(0);
        Assert.assertEquals(cookie.getMaxAge(), 100);
        cookie.setMaxAge(200);
        Assert.assertEquals(cookie.getMaxAge(), 200);
        cookie.setMaxAge(-2);
        Assert.assertEquals(cookie.getMaxAge(), -2);
    }
}
