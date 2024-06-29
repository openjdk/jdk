/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *@test
 *@bug 4858517 6300580 8008577 8174269
 *@summary Test case for tzdata2003a support for 9 locales
 *@run main Bug4858517
 */

import java.util.Locale;
import java.util.TimeZone;

public class Bug4858517 {

    static Locale[] locales2Test = new Locale[] {
        Locale.ENGLISH,
        Locale.GERMAN,
        Locale.of("es"),
//        Locale.FRENCH,   "fr" returns "UTC\u221203:00" with CLDR
        Locale.ITALIAN,
        Locale.JAPANESE,
        Locale.KOREAN,
//        Locale.of("sv"), "sv" returns "GMT\u221203:00" with CLDR
        Locale.SIMPLIFIED_CHINESE,
        Locale.TRADITIONAL_CHINESE
    };

    public static void main(String[] args) {

        Locale tzLocale;

        for (int i = 0; i < locales2Test.length; i++){

            tzLocale = locales2Test[i];
            TimeZone Rothera = TimeZone.getTimeZone("Antarctica/Rothera");
            if (!Rothera.getDisplayName(false, TimeZone.SHORT, tzLocale).equals ("GMT-03:00"))
                throw new RuntimeException("\n" + tzLocale + ": short name, non-daylight time for Rothera should be \"GMT-03:00\"");

            if (!Rothera.getDisplayName(true, TimeZone.SHORT, tzLocale).equals ("GMT-03:00"))
                throw new RuntimeException("\n" + tzLocale + ": short name, daylight time for Rothera should be \"GMT-03:00\"");

            TimeZone IRT = TimeZone.getTimeZone("Iran");

            if (!IRT.getDisplayName(false, TimeZone.SHORT, tzLocale).equals ("GMT+03:30"))
                throw new RuntimeException("\n" + tzLocale + ": short name, non-daylight time for IRT should be \"GMT+03:30\"");

            if (!IRT.getDisplayName(true, TimeZone.SHORT, tzLocale).equals ("GMT+04:30"))
                throw new RuntimeException("\n" + tzLocale + ": short name, daylight time for IRT should be \"GMT+04:30\"");
        }

   }
}
