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
 * @test
 * @bug 6317929 6409419 8008577 8174269
 * @modules jdk.localedata
 * @summary Test case for tzdata2005m support for 9 locales
 * @run main Bug6317929
 */

import java.util.Locale;
import java.util.TimeZone;

public class Bug6317929 {
    static Locale[] locales2Test = new Locale[] {
        Locale.ENGLISH,
        Locale.GERMAN,
        Locale.of("es"),
        Locale.FRENCH,
        Locale.ITALIAN,
        Locale.JAPANESE,
        Locale.KOREAN,
        Locale.of("sv"),
        Locale.SIMPLIFIED_CHINESE,
        Locale.TRADITIONAL_CHINESE
    };

    public static void main(String[] args) {
        Locale tzLocale;

        TimeZone Coral_Harbour = TimeZone.getTimeZone("America/Coral_Harbour");
        tzLocale = locales2Test[0];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Eastern Standard Time"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"Eastern Standard Time\"");
        tzLocale = locales2Test[1];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Nordamerikanische Ostk\u00fcsten-Normalzeit"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"Nordamerikanische Ostk\u00fcsten-Normalzeit\"");
        tzLocale = locales2Test[2];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("hora est\u00e1ndar oriental"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"hora est\u00e1ndar oriental\"");
        tzLocale = locales2Test[3];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("heure normale de l\u2019Est nord-am\u00e9ricain"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"heure normale de l\u2019Est nord-am\u00e9ricain\"");
        tzLocale = locales2Test[4];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Ora standard orientale USA"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"Ora standard orientale USA\"");
        tzLocale = locales2Test[5];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\u30a2\u30e1\u30ea\u30ab\u6771\u90e8\u6a19\u6e96\u6642"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"\u30a2\u30e1\u30ea\u30ab\u6771\u90e8\u6a19\u6e96\u6642\"");
        tzLocale = locales2Test[6];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\ubbf8 \ub3d9\ubd80 \ud45c\uc900\uc2dc"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"\ubbf8 \ub3d9\ubd80 \ud45c\uc900\uc2dc\"");
        tzLocale = locales2Test[7];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\u00f6stnordamerikansk normaltid"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"\u00f6stnordamerikansk normaltid\"");
        tzLocale = locales2Test[8];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\u5317\u7f8e\u4e1c\u90e8\u6807\u51c6\u65f6\u95f4"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"\u5317\u7f8e\u4e1c\u90e8\u6807\u51c6\u65f6\u95f4\"");
        tzLocale = locales2Test[9];
        if (!Coral_Harbour.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\u6771\u90e8\u6a19\u6e96\u6642\u9593"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "America/Coral_Harbour should be " +
                                       "\"\u6771\u90e8\u6a19\u6e96\u6642\u9593\"");

        TimeZone Currie = TimeZone.getTimeZone("Australia/Currie");
        tzLocale = locales2Test[0];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Australian Eastern Standard Time"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"Australian Eastern Standard Time\"");
        tzLocale = locales2Test[1];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Ostaustralische Normalzeit"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"Ostaustralische Normalzeit\"");
        tzLocale = locales2Test[2];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("hora est\u00e1ndar de Australia oriental"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"hora est\u00e1ndar de Australia oriental\"");
        tzLocale = locales2Test[3];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("heure normale de l\u2019Est de l\u2019Australie"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"heure normale de l\u2019Est de l\u2019Australie\"");
        tzLocale = locales2Test[4];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("Ora standard dell\u2019Australia orientale"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"Ora standard dell\u2019Australia orientale\"");
        tzLocale = locales2Test[5];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\u30aa\u30fc\u30b9\u30c8\u30e9\u30ea\u30a2\u6771\u90e8\u6a19\u6e96\u6642"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"\u30aa\u30fc\u30b9\u30c8\u30e9\u30ea\u30a2\u6771\u90e8\u6a19\u6e96\u6642\"");
        tzLocale = locales2Test[6];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\uc624\uc2a4\ud2b8\ub808\uc77c\ub9ac\uc544 \ub3d9\ubd80 \ud45c\uc900\uc2dc"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"\uc624\uc2a4\ud2b8\ub808\uc77c\ub9ac\uc544 \ub3d9\ubd80 \ud45c\uc900\uc2dc\"");
        tzLocale = locales2Test[7];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\u00f6staustralisk normaltid"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"\u00f6staustralisk normaltid\"");
        tzLocale = locales2Test[8];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\u6fb3\u5927\u5229\u4e9a\u4e1c\u90e8\u6807\u51c6\u65f6\u95f4"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"\u6fb3\u5927\u5229\u4e9a\u4e1c\u90e8\u6807\u51c6\u65f6\u95f4\"");
        tzLocale = locales2Test[9];
        if (!Currie.getDisplayName(false, TimeZone.LONG, tzLocale).equals
           ("\u6fb3\u6d32\u6771\u90e8\u6a19\u6e96\u6642\u9593"))
            throw new RuntimeException("\n" + tzLocale + ": LONG, " +
                                       "non-daylight saving name for " +
                                       "Australia/Currie should be " +
                                       "\"\u6fb3\u6d32\u6771\u90e8\u6a19\u6e96\u6642\u9593\"");
   }
}
