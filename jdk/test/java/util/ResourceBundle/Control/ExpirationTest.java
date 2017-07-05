/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * This class is used by ExpirationTest.sh. See the timing information in
 * the shell script.
 */

import java.util.*;

public class ExpirationTest {
    static final Locale AUSTRIA = new Locale("de", "AT");
    static String format;
    static String fileType;

    public static void main(String[] args) {
        // If -latency is specified, try sleeping for 3 seconds 3
        // times to see its latency. If the latency is too large, then
        // the program exits with 2. (See sleep())
        if ("-latency".equals(args[0])) {
            System.out.print("Checking latency... ");
            for (int i = 0; i < 3; i++) {
                sleep(3);
            }
            System.out.println("done");
            System.exit(0);
        }

        format = args[0];
        fileType = args[1];

        Locale loc = Locale.getDefault();
        try {
            Locale.setDefault(Locale.JAPAN);
            ResourceBundle.Control control = new TestControl();
            ResourceBundle rb = ResourceBundle.getBundle("ExpirationData", Locale.GERMAN,
                                                         control);
            check(rb.getString("data"), "German");

            rb = ResourceBundle.getBundle("ExpirationData", AUSTRIA, control);
            check(rb.getString("january"), "Januar");

            // Wait until the instance gets expired in the cache in 7 seconds.
            sleep(7);

            // At this point, it should be determined that reloading is not needed.
            rb = ResourceBundle.getBundle("ExpirationData", Locale.GERMAN, control);
            check(rb.getString("data"), "German");

            rb = ResourceBundle.getBundle("ExpirationData", AUSTRIA, control);
            check(rb.getString("january"), "Januar");

            // Wait until the instance in the cache gets expired again and
            // ExpirationData_de gets updated.
            // 33 = 40 - 7 (See the timing chart in ExpirationTest.sh)
            sleep(33);

            // At this point, getBundle must reload the updated
            // ExpirationData_de and ExpirationData_de_AT must be
            // avaible.

            rb = ResourceBundle.getBundle("ExpirationData", Locale.GERMAN, control);
            try {
                check(rb.getString("data"), "Deutsch");
            } catch (RuntimeException e) {
                if (format.equals("class")) {
                    // Class loader doesn't load updated classes.
                    System.out.println("Known class limitation: " + e.getMessage());
                }
            }

            rb = ResourceBundle.getBundle("ExpirationData", AUSTRIA, control);
            try {
                check(rb.getString("january"), "J\u00e4nner");
            } catch (RuntimeException e) {
                if (fileType.equals("jar")) {
                    // Jar doesn't load new entries.
                    System.out.println("Known jar limitation: " + e.getMessage());
                } else {
                    throw e;
                }
            }
        } finally {
            Locale.setDefault(loc);
        }
    }

    private static void check(String s, String expected) {
        String time = getTime();
        if (!s.equals(expected)) {
            throw new RuntimeException("got '" + s + "', expected '" + expected + "' at "
                                       + time);
        }
        System.out.println("ExpirationTest: got '" + s + "' at " + time);
    }

    private static void sleep(int seconds) {
        long millis = seconds * 1000;
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
        long end = System.currentTimeMillis();
        long latency = end - start - millis;
        // If the latecy is more than 1% of the requested sleep time,
        // then give up the testing.
        if (latency > millis/100) {
            System.err.printf("Latency is too large: slept for %d [ms], "
                              + "expected %d [ms] latency rate: %+.2f%% (expected not more than 1%%)%n"
                              + "exiting...%n",
                              end - start, millis, (double)latency*100.0/millis);
            System.exit(2);
        }
    }

    private static final String getTime() {
        return new Date().toString().substring(11, 19);
    }

    private static class TestControl extends ResourceBundle.Control {
        @Override
        public long getTimeToLive(String name, Locale loc) {
            return 5000; // 5 seconds
        }

        @Override
        public ResourceBundle newBundle(String name, Locale loc,
                                        String fmt, ClassLoader cl, boolean reload)
            throws IllegalAccessException, InstantiationException, java.io.IOException {
            ResourceBundle bundle = super.newBundle(name, loc, fmt, cl, reload);
            if (bundle != null) {
                System.out.println("newBundle: " + (reload ? "**re" : "")
                                   + "loaded '" + toName(name, loc , fmt) + "' at " + getTime());
            }
            return bundle;
        }

        @Override
        public boolean needsReload(String name, Locale loc,
                                   String fmt, ClassLoader cl,
                                   ResourceBundle rb, long time) {
            boolean b = super.needsReload(name, loc, fmt, cl, rb, time);
            System.out.println("needsReload: '" + b + "' for " + toName(name, loc, fmt)
                               + " at " + getTime());
            return b;
        }

        private String toName(String name, Locale loc, String fmt) {
            return toResourceName(toBundleName(name, loc), fmt.substring(5));
        }
    }
}
