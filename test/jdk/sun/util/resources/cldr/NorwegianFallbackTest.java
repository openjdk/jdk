/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282227 8174269
 * @modules jdk.localedata
 * @summary Checks Norwegian locale fallback retrieves resource bundles correctly.
 * @run main NorwegianFallbackTest nb
 * @run main NorwegianFallbackTest nn
 * @run main NorwegianFallbackTest no
 */

import java.text.DateFormatSymbols;
import java.util.List;
import java.util.Locale;
import static java.util.Calendar.SUNDAY;

public class NorwegianFallbackTest {

    private final static String SUN_ROOT = DateFormatSymbols.getInstance(Locale.ROOT).getShortWeekdays()[SUNDAY];
    private final static List<Locale> TEST_LOCS = List.of(
            Locale.forLanguageTag("nb"),
            Locale.forLanguageTag("nn"),
            Locale.forLanguageTag("no")
    );

    public static void main(String... args) {
        // Dummy instance
        var startup_loc = Locale.forLanguageTag(args[0]);
        DateFormatSymbols.getInstance(startup_loc);

        TEST_LOCS.stream()
            .peek(l -> System.out.print("Testing locale: " + l + ", (startup locale: " + startup_loc + ")... "))
            .map(l -> DateFormatSymbols.getInstance(l).getShortWeekdays()[SUNDAY])
            .forEach(sun -> {
                if (sun.equals(SUN_ROOT)) {
                    throw new RuntimeException("Norwegian fallback failed");
                } else {
                    System.out.println("Got " + "\"" + sun + "\" for Sunday short name");
                }
            });
    }
}
