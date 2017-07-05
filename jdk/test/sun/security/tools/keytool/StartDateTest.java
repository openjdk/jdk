/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6468285
 * @summary keytool ability to backdate self-signed certificates to compensate for clock skew
 * @compile -XDignore.symbol.file StartDateTest.java
 * @run main StartDateTest
 */

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class StartDateTest {
    public static void main(String[] args) throws Exception {

        // Part 1: Test function
        Calendar cal = new GregorianCalendar();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);

        new File("jks").delete();

        run("-keystore jks -storetype jks -storepass changeit -keypass changeit -alias me " +
                "-keyalg rsa -genkeypair -dname CN=Haha -startdate +1y");
        cal.setTime(getIssueDate());
        System.out.println(cal);
        if (cal.get(Calendar.YEAR) != year + 1) {
            throw new Exception("Function check #1 fails");
        }

        run("-keystore jks -storetype jks -storepass changeit -keypass changeit -alias me " +
                "-selfcert -startdate +1m");
        cal.setTime(getIssueDate());
        System.out.println(cal);
        if (cal.get(Calendar.MONTH) != (month + 1) % 12) {
            throw new Exception("Function check #2 fails");
        }

        new File("jks").delete();

        // Part 2: Test format
        Method m = sun.security.tools.keytool.Main.class.getDeclaredMethod(
                   "getStartDate", String.class);
        m.setAccessible(true);
        for (String s: new String[] {
                null,       //NOW!
                "+1m+1d",
                "+1y-1m+1d",
                "+3H",
                "+1M",
                "-5M",
                "+011d",
                "+22S",
                "+500S",
                "2001/01/01",
                "15:15:15",
                "2001/01/01 11:11:11",
                }) {
            try {
                System.out.println(s + " " + m.invoke(null, s));
            } catch (Exception e) {
                e.printStackTrace();
                throw new Exception("Failed at " + s);
            }
        }
        for (String s: new String[] {
                "",         // empty
                "+3",
                "+3m+",
                "+3m+3",
                "1m",       // no sign
                "+0x011d",  // hex number
                "+1m1d",    // no sign for the 2nd sub value
                "m",
                "+1h",      // h is not H
                "-1m1d",
                "-m",
                "x",
                "+1m +1d",
                "2007/07",
                "01:01",
                "+01:01:01",                // what's this?
                "1:01:01",
                "12pm",
                "2007/07/07  12:12:12",     // extra blank between
                "2001/01/01-11:11:11",
                "2007-07-07",               // non-standard date delim
                "2007/7/7",                 // no padding
                "07/07/07",                 // year's length not 4
                "1:1:1",
                }) {
            boolean failed = false;
            try {
                System.out.println(m.invoke(null, s));
            } catch (Exception e) {
                System.out.println(s + " " + e.getCause());
                failed = true;
            }
            if (!failed) throw new Exception("Failed at " + s);
        }
    }

    static void run(String s) throws Exception {
        sun.security.tools.keytool.Main.main((s+" -debug").split(" "));
    }

    static Date getIssueDate() throws Exception {
        KeyStore ks = KeyStore.getInstance("jks");
        try (FileInputStream fis = new FileInputStream("jks")) {
            ks.load(fis, "changeit".toCharArray());
        }
        X509Certificate cert = (X509Certificate)ks.getCertificate("me");
        return cert.getNotBefore();
    }
}
