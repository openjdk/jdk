/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8000525 8380549
 * @library /test/lib
 */

import java.net.*;
import java.util.*;
import java.io.*;
import java.text.*;
import jdk.test.lib.net.URIBuilder;

import static jdk.test.lib.Asserts.assertEquals;

public class ExpiredCookieTest {
    // lifted from HttpCookie.java
    private final static String[] COOKIE_DATE_FORMATS = {
        "EEE',' dd-MMM-yy HH:mm:ss 'GMT'",
        "EEE',' dd MMM yy HH:mm:ss 'GMT'",
        "EEE MMM dd yy HH:mm:ss 'GMT'Z",
        "EEE',' dd-MMM-yyyy HH:mm:ss 'GMT'",
        "EEE',' dd MMM yyyy HH:mm:ss 'GMT'",
        "EEE MMM dd yyyy HH:mm:ss 'GMT'Z"
    };
    static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    public static void main(String[] args) throws Exception {
        Calendar cal = Calendar.getInstance(GMT);

        for (int i = 0; i < COOKIE_DATE_FORMATS.length; i++) {
            SimpleDateFormat df = new SimpleDateFormat(COOKIE_DATE_FORMATS[i],
                                                     Locale.US);
            cal.set(1970, 0, 1, 0, 0, 0);
            df.setTimeZone(GMT);
            df.setLenient(false);
            df.set2DigitYearStart(cal.getTime());
            CookieManager cm = new CookieManager(
                null, CookiePolicy.ACCEPT_ALL);
            CookieHandler.setDefault(cm);
            Map<String,List<String>> header = new HashMap<>();
            List<String> values = new ArrayList<>();

            cal.set(1970, 6, 9, 10, 10, 1);
            StringBuilder datestring =
                new StringBuilder(df.format(cal.getTime()));
            values.add(
                "TEST1=TEST1; Path=/; Expires=" + datestring.toString());

            cal.set(1969, 6, 9, 10, 10, 2);
            datestring = new StringBuilder(df.format(cal.getTime()));
            values.add(
                "TEST2=TEST2; Path=/; Expires=" + datestring.toString());

            cal.set(2070, 6, 9, 10, 10, 3);
            datestring = new StringBuilder(df.format(cal.getTime()));
            values.add(
                "TEST3=TEST3; Path=/; Expires=" + datestring.toString());

            cal.set(2069, 6, 9, 10, 10, 4);
            datestring = new StringBuilder(df.format(cal.getTime()));
            values.add(
                "TEST4=TEST4; Path=/; Expires=" + datestring.toString());

            header.put("Set-Cookie", values);
            URI uri = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .path("/")
                .buildUnchecked();
            System.out.println("URI: " + uri);
            cm.put(uri, header);

            CookieStore cookieJar =  cm.getCookieStore();
            Set<String> names = new TreeSet<>();
            for (HttpCookie cookie : cookieJar.getCookies())
                names.add(cookie.getName());

            Set<String> expected;
            if (COOKIE_DATE_FORMATS[i].contains("yyyy")) {
                // Four-digit years parse unambiguously: TEST1 and TEST2 are
                // in the past and expire, while TEST3 and TEST4 remain.
                expected = new TreeSet<>(List.of("TEST3", "TEST4"));
            } else {
                // Two-digit years make TEST2 and TEST3 resolve to a mismatched
                // day-of-week, so strict parsing rejects the Expires value; per
                // RFC 6265 section 5.2.1 an unparseable Expires is ignored, so
                // they remain as session cookies. TEST1 parses cleanly but is
                // already expired, so it is dropped. TEST4's two-digit year
                // round-trips to itself (69 -> 2069), so it parses and remains
                // because its expiry is still in the future.
                expected = new TreeSet<>(List.of("TEST2", "TEST3", "TEST4"));
            }
            assertEquals(expected, names,
                "Incorrectly parsing a bad date, format: "
                + COOKIE_DATE_FORMATS[i]);
        }
    }
}
